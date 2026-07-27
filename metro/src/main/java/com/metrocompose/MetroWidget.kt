package com.metrocompose

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Everything a WP8 tile needs to render. Returned fresh on every refresh. */
data class MetroTile(
    val color: Int,
    val glyph: String,
    val label: String,
    val targetScreen: String = "Start"
)

/**
 * Reusable framework base for WP8 home-screen tiles.
 *
 * A concrete tile only overrides [tile]; the base handles RemoteViews rendering, the
 * tap-to-open intent (via the app's own launcher activity — no hard dependency on any
 * class), and optional forced self-refresh through AlarmManager.
 *
 * To add a tile:
 *   class MyTile : MetroWidgetProvider() {
 *       override fun tile(context: Context) = MetroTile(0xFF008A00.toInt(), "★", "favorites")
 *   }
 * ...then register one <receiver> in the app manifest pointing at @xml/metro_tile_info.
 */
abstract class MetroWidgetProvider : AppWidgetProvider() {

    /** Describe the tile. Called on every render, so dynamic tiles can return live data. */
    abstract fun tile(context: Context): MetroTile

    /** Forced-refresh interval in ms. 0 = rely on the system schedule only (min 30 min). */
    protected open val refreshIntervalMs: Long = 0L

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAll(context, manager, appWidgetIds)
        if (refreshIntervalMs > 0 && appWidgetIds.isNotEmpty()) scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            if (ids.isNotEmpty()) {
                renderAll(context, manager, ids)
                if (refreshIntervalMs > 0) scheduleNext(context)
            }
        }
    }

    override fun onDisabled(context: Context) {
        cancelRefresh(context)
    }

    // ---- rendering ----

    private fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, render(context, id))
    }

    private fun render(context: Context, widgetId: Int): RemoteViews {
        val t = tile(context)
        val views = RemoteViews(context.packageName, R.layout.widget_metro_tile)
        // Plain color fill = square corners on our side (Android 12+ still rounds via the launcher).
        views.setInt(R.id.tile_root, "setBackgroundColor", t.color)
        views.setTextViewText(R.id.tile_glyph, t.glyph)
        views.setTextViewText(R.id.tile_label, t.label)

        // Open the host app's own launcher activity, carrying the requested screen.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("screen", t.targetScreen)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: Intent()
        views.setOnClickPendingIntent(
            R.id.tile_root,
            PendingIntent.getActivity(
                context, widgetId, open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return views
    }

    // ---- forced refresh via AlarmManager ----

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, javaClass).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + refreshIntervalMs,
            refreshPendingIntent(context)
        )
    }

    private fun cancelRefresh(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(refreshPendingIntent(context))
    }

    companion object {
        const val ACTION_REFRESH = "com.metrocompose.ACTION_REFRESH"
    }
}

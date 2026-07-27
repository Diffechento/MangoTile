package com.metrocompose.sample

import android.content.Context
import com.metrocompose.MetroTile
import com.metrocompose.MetroWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cyan "collection" tile -> opens the panorama. */
class MetroTileWidget : MetroWidgetProvider() {
    override fun tile(context: Context) =
        MetroTile(0xFF1BA1E2.toInt(), "♪", "collection", "Panorama")
}

/** Purple settings tile -> opens Settings. */
class MetroSettingsTileWidget : MetroWidgetProvider() {
    override fun tile(context: Context) =
        MetroTile(0xFFAA00FF.toInt(), "⚙", "settings", "Settings")
}

/** Red live tile -> refreshes itself every 10s with an unread badge + clock. */
class MetroLiveTileWidget : MetroWidgetProvider() {
    override val refreshIntervalMs = 10_000L

    override fun tile(context: Context): MetroTile {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val unread = (0..12).random()
        return MetroTile(0xFFE51400.toInt(), unread.toString(), "mail · $time", "Start")
    }
}

package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroBannerLingerMillis
import com.metrocompose.MetroButton
import com.metrocompose.MetroIcon
import com.metrocompose.MetroLight
import com.metrocompose.MetroLineIcon
import com.metrocompose.MetroPage
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTheme
import com.metrocompose.MetroTopBanner
import com.metrocompose.MetroVolumeBanner
import com.metrocompose.TransportButton

/**
 * The strip the phone drops over the top of everything to say something changed, and which leaves by
 * itself. Not a dialog: nothing is dimmed and nothing is blocked.
 *
 * The volume one is live — press the hardware volume keys. `MainActivity.onKeyDown` consumes them
 * before the system sees them, which is the only way an app can replace a panel the OS owns, and the
 * honest limit of it is that on the lock screen you still get Android's.
 */
@Composable
fun BannersScreen() {
    val colors = MetroTheme.colors
    val volume = LocalVolume.current
    var message by remember { mutableStateOf<String?>(null) }

    MetroPage("ELEMENTS", "banners") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            BannerLabel("MetroVolumeBanner")
            Text(
                "Press the volume keys. The banner covers the status bar rather than starting " +
                    "under it — the system's own clock draws over any window, so it stays readable.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroLineIcon(
                    if (volume.muted) MetroIcon.SpeakerMuted else MetroIcon.Speaker,
                    colors.fg,
                    Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${volume.step} / ${volume.steps}",
                    color = colors.fg, fontFamily = MetroLight, fontSize = 26.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroButton("volume −") { volume.nudge(-1) }
                Spacer(Modifier.width(12.dp))
                MetroButton("volume +") { volume.nudge(+1) }
            }

            Spacer(Modifier.height(18.dp))
            MetroButton(
                if (volume.showMedia) "hide now-playing row" else "show now-playing row"
            ) {
                volume.showMedia = !volume.showMedia
                volume.nudge(0) // show it again so the change is visible immediately
            }
            Text(
                "On the phone the banner grows to carry the track and its transport whenever music " +
                    "is playing — that slot is the `media` parameter.",
                color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(30.dp))
            BannerLabel("MetroTopBanner")
            Text(
                "The general form: any content, dropped over the top, gone again after the linger.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton("ringer + vibrate") { message = "ringer + vibrate" }
                MetroButton("airplane mode on") { message = "airplane mode on" }
                MetroButton("call ended") { message = "call ended · 4:12" }
            }
        }
    }

    // The banner lives in a Popup, so where it is written in the tree does not decide where it
    // lands — only which subtree owns it.
    MetroTopBanner(
        visible = message != null,
        onHide = { message = null },
        // The banner leaves by itself; `resetKey` restarts the wait when the message changes, so a
        // second press gives the full time again rather than the remainder of the last one.
        resetKey = message,
        lingerMillis = MetroBannerLingerMillis
    ) {
        Text(
            "STATUS",
            color = colors.subtle, fontFamily = MetroSemilight, fontSize = 11.sp, letterSpacing = 2.sp
        )
        Text(
            message.orEmpty(),
            color = colors.fg, fontFamily = MetroLight, fontSize = 26.sp
        )
    }
}

/**
 * The volume banner the whole app carries — rendered once at the root, not per screen, because the
 * keys work everywhere.
 */
@Composable
fun AppVolumeBanner() {
    val volume = LocalVolume.current
    val track = DemoTracks[0]

    MetroVolumeBanner(
        visible = volume.visible,
        level = volume.level,
        onHide = volume::hide,
        value = volume.step.toString(),
        muted = volume.muted,
        // Pressing volume again while it is up should give the full wait again, not the remainder
        // of the last one — so the reset key is the thing that changed.
        resetKey = volume.step,
        media = if (volume.showMedia) ({ NowPlayingRow(track) }) else null
    )
}

/** The banner's `media` slot: what is playing, and enough transport to act on it. */
@Composable
private fun NowPlayingRow(track: Track) {
    val colors = MetroTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(track.tint))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = colors.fg, fontFamily = MetroRegular, fontSize = 15.sp, maxLines = 1)
            Text(track.artist, color = colors.subtle, fontFamily = MetroRegular, fontSize = 12.sp, maxLines = 1)
        }
        TransportButton(MetroIcon.Pause, "pause", touchSize = 40.dp, iconSize = 16.dp) {}
        TransportButton(MetroIcon.Next, "next", touchSize = 40.dp, iconSize = 16.dp) {}
    }
}

@Composable
private fun BannerLabel(text: String) {
    Text(
        text,
        color = MetroTheme.colors.subtle,
        fontFamily = MetroSemilight,
        fontSize = 22.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

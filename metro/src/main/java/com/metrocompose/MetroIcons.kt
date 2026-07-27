package com.metrocompose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The icons WP8 draws rather than types.
 *
 * Typed symbols were the first attempt and they don't survive contact with Android's font
 * fallback: ♥, ⇄ and ↻ each arrive from whichever font happens to have them, at their own weight
 * and baseline, so beside a 2dp ring they read as text that wandered in. ❚❚ is the worst of it —
 * its ink box is far taller than ▶'s at the same font size, so a play/pause button visibly changes
 * size when you press it. These are paths: one stroke width across the set, proportions fixed
 * relative to each other, and they scale with the box rather than with a font size.
 *
 * Toggles are outlines, transport is solid — the same split both the phone and its clones use.
 */
enum class MetroIcon {
    Star, StarFilled, Shuffle, Repeat, RepeatOne,
    Previous, Play, Pause, Next,
    Speaker, SpeakerMuted
}

/**
 * Draws [icon] to fill [modifier]'s box. [strokeWidth] defaults to the 2dp the rings and borders
 * elsewhere in the library use, which is what keeps a toggle and a ringed button looking related.
 */
@Composable
fun MetroLineIcon(
    icon: MetroIcon,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp
) {
    val stroke = with(LocalDensity.current) { strokeWidth.toPx() }
    Canvas(modifier) {
        when (icon) {
            MetroIcon.Star -> drawStar(color, stroke, filled = false)
            MetroIcon.StarFilled -> drawStar(color, stroke, filled = true)
            MetroIcon.Shuffle -> drawShuffle(color, stroke)
            MetroIcon.Repeat -> drawRepeat(color, stroke, one = false)
            MetroIcon.RepeatOne -> drawRepeat(color, stroke, one = true)
            MetroIcon.Previous -> drawSkip(color, forward = false)
            MetroIcon.Play -> drawPlay(color)
            MetroIcon.Pause -> drawPause(color)
            MetroIcon.Next -> drawSkip(color, forward = true)
            MetroIcon.Speaker -> drawSpeaker(color, stroke, muted = false)
            MetroIcon.SpeakerMuted -> drawSpeaker(color, stroke, muted = true)
        }
    }
}

/**
 * The transport shapes share one height and one optical weight, which is the whole point of drawing
 * them: [TransportHeight] of the box tall, and the play triangle's area roughly matched by the two
 * pause bars, so the middle button doesn't jump when playback starts.
 */
private const val TransportHeight = 0.78f
private const val PauseBarWidth = 0.24f
private const val PauseGap = 0.16f

private fun DrawScope.drawPlay(color: Color) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    val half = TransportHeight / 2f
    drawPath(
        path = Path().apply {
            moveTo(left + s * 0.20f, top + s * (0.5f - half))
            lineTo(left + s * 0.20f, top + s * (0.5f + half))
            lineTo(left + s * 0.84f, top + s * 0.5f)
            close()
        },
        color = color,
        style = Fill
    )
}

private fun DrawScope.drawPause(color: Color) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    val barsWidth = PauseBarWidth * 2f + PauseGap
    val startX = left + s * (0.5f - barsWidth / 2f)
    val y = top + s * (0.5f - TransportHeight / 2f)
    listOf(0f, PauseBarWidth + PauseGap).forEach { offset ->
        drawRect(
            color = color,
            topLeft = Offset(startX + s * offset, y),
            size = Size(s * PauseBarWidth, s * TransportHeight)
        )
    }
}

/**
 * The speaker: a solid cone, and either two waves leaving it or a cross where they were.
 *
 * Solid body with stroked waves rather than an outline throughout — at 20dp an outlined cone turns
 * into a grey smudge, while the filled one keeps the silhouette that says "sound" at a glance. The
 * muted variant keeps the cone in exactly the same place, so a banner does not appear to shift when
 * the volume reaches zero.
 */
private fun DrawScope.drawSpeaker(color: Color, stroke: Float, muted: Boolean) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun x(v: Float) = left + s * v
    fun y(v: Float) = top + s * v

    // Cone: a narrow neck at the left opening out into the full-height mouth.
    drawPath(
        path = Path().apply {
            moveTo(x(0.06f), y(0.38f))
            lineTo(x(0.24f), y(0.38f))
            lineTo(x(0.48f), y(0.14f))
            lineTo(x(0.48f), y(0.86f))
            lineTo(x(0.24f), y(0.62f))
            lineTo(x(0.06f), y(0.62f))
            close()
        },
        color = color,
        style = Fill
    )

    if (muted) {
        drawLine(color, Offset(x(0.60f), y(0.34f)), Offset(x(0.92f), y(0.66f)), stroke)
        drawLine(color, Offset(x(0.92f), y(0.34f)), Offset(x(0.60f), y(0.66f)), stroke)
        return
    }

    // Two arcs of one circle centred on the cone's mouth, the outer one wider — the phone's
    // radiating pair, not concentric rings around the whole icon.
    listOf(0.16f to 0.34f, 0.30f to 0.44f).forEach { (inset, sweepHalf) ->
        val radius = s * (0.44f + inset * 0.5f)
        val centre = Offset(x(0.42f), y(0.5f))
        drawArc(
            color = color,
            startAngle = -sweepHalf * 90f,
            sweepAngle = sweepHalf * 180f,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Two triangles against a bar — ⏮ and ⏭, mirrored about the vertical axis. */
private fun DrawScope.drawSkip(color: Color, forward: Boolean) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun x(v: Float) = left + s * (if (forward) v else 1f - v)
    fun y(v: Float) = top + s * v

    val half = TransportHeight / 2f
    val barWidth = 0.13f
    // Slightly shorter than the triangles, the way the phone draws it.
    val barInset = 0.06f
    drawRect(
        color = color,
        topLeft = Offset(
            if (forward) x(1f - barWidth) else x(1f),
            y(0.5f - half + barInset)
        ),
        size = Size(s * barWidth, s * (TransportHeight - barInset * 2f))
    )
    listOf(0.06f, 0.44f).forEach { startX ->
        drawPath(
            path = Path().apply {
                moveTo(x(startX), y(0.5f - half))
                lineTo(x(startX), y(0.5f + half))
                lineTo(x(startX + 0.42f), y(0.5f))
                close()
            },
            color = color,
            style = Fill
        )
    }
}

/** Five-pointed star, outlined or solid. Round joins — the phone's star has no sharp corners. */
private fun DrawScope.drawStar(color: Color, stroke: Float, filled: Boolean) {
    val s = size.minDimension
    val cx = size.width / 2f
    // A star's ink sits above its geometric centre, so nudge it down to look centred.
    val cy = size.height / 2f + s * 0.03f
    val outer = s / 2f - stroke / 2f
    val inner = outer * 0.46f

    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val x = cx + radius * cos(angle)
        val y = cy + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = if (filled) {
            Fill
        } else {
            Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
    )
}

/**
 * Two arrows that cross: tails swept out along the bottom, heads meeting near the top. Mirrored
 * about the vertical axis, so the pair is drawn once and flipped.
 */
private fun DrawScope.drawShuffle(color: Color, stroke: Float) {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun at(x: Float, y: Float) = Offset(left + x * s, top + y * s)

    val headHalf = s * 0.12f
    val headLength = s * 0.22f

    for (mirrored in listOf(false, true)) {
        fun fx(x: Float) = if (mirrored) 1f - x else x

        val tip = at(fx(0.70f), 0.10f)
        val tail = at(fx(0.06f), 0.86f)
        val path = Path().apply {
            moveTo(tail.x, tail.y)
            cubicTo(
                at(fx(0.38f), 0.94f).x, at(fx(0.38f), 0.94f).y,
                at(fx(0.52f), 0.58f).x, at(fx(0.52f), 0.58f).y,
                // Stop inside the arrowhead so the joint is covered.
                tip.x, tip.y + headLength * 0.7f
            )
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        drawPath(
            path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - headHalf, tip.y + headLength)
                lineTo(tip.x + headHalf, tip.y + headLength)
                close()
            },
            color = color,
            style = Fill
        )
    }
}

/**
 * A ring open at the top with an arrowhead on the left end, pointing the way it travels. [one] adds
 * the numeral inside, which is how WP8 tells repeat-one from repeat-all.
 */
private fun DrawScope.drawRepeat(color: Color, stroke: Float, one: Boolean) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = s / 2f - stroke / 2f - s * 0.04f

    val gap = 54f
    val start = -90f + gap / 2f
    val sweep = 360f - gap
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Butt)
    )

    // The sweep ends left of twelve o'clock; its clockwise tangent there points up and right. The
    // base is pulled back over the arc's last pixels so the two meet without a notch.
    val end = ((start + sweep) * PI / 180.0).toFloat()
    val forward = Offset(-sin(end), cos(end))
    val across = Offset(-forward.y, forward.x)
    val base = Offset(
        cx + radius * cos(end) - forward.x * stroke * 0.6f,
        cy + radius * sin(end) - forward.y * stroke * 0.6f
    )
    val headLength = s * 0.32f
    val headHalf = s * 0.17f
    drawPath(
        path = Path().apply {
            moveTo(base.x + forward.x * headLength, base.y + forward.y * headLength)
            lineTo(base.x + across.x * headHalf, base.y + across.y * headHalf)
            lineTo(base.x - across.x * headHalf, base.y - across.y * headHalf)
            close()
        },
        color = color,
        style = Fill
    )

    if (one) {
        val thin = stroke * 0.9f
        val stemX = cx + s * 0.02f
        drawLine(
            color = color,
            start = Offset(stemX, cy - s * 0.17f),
            end = Offset(stemX, cy + s * 0.17f),
            strokeWidth = thin,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(stemX - s * 0.10f, cy - s * 0.09f),
            end = Offset(stemX, cy - s * 0.17f),
            strokeWidth = thin,
            cap = StrokeCap.Round
        )
    }
}

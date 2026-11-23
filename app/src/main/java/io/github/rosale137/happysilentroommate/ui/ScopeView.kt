package io.github.rosale137.happysilentroommate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosale137.happysilentroommate.audio.AudioLevelMeter
import io.github.rosale137.happysilentroommate.audio.HISTORY_SECONDS
import io.github.rosale137.happysilentroommate.audio.MAX_WINDOW_SECONDS
import io.github.rosale137.happysilentroommate.audio.movingAverageDb
import io.github.rosale137.happysilentroommate.audio.powerToDb
import io.github.rosale137.happysilentroommate.ui.theme.Amber
import io.github.rosale137.happysilentroommate.ui.theme.Coral
import io.github.rosale137.happysilentroommate.ui.theme.Navy
import io.github.rosale137.happysilentroommate.ui.theme.OnAmber
import io.github.rosale137.happysilentroommate.ui.theme.Teal
import io.github.rosale137.happysilentroommate.ui.theme.TextMuted
import io.github.rosale137.happysilentroommate.ui.theme.Track
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

const val HISTORY_CHUNKS = HISTORY_SECONDS * 1000 / AudioLevelMeter.CHUNK_MS

private const val CHUNKS_PER_SECOND = 1000 / AudioLevelMeter.CHUNK_MS
// Cropped scale: quieter than -80 sits on the bottom edge (mic noise), louder than -10 on the top.
private const val SCOPE_MIN_DB = -80f
private const val SCOPE_MAX_DB = -10f

/** The limit can be dragged over the whole scale. */
const val LIMIT_MIN_DB = SCOPE_MIN_DB
const val LIMIT_MAX_DB = SCOPE_MAX_DB

// Margins around the data area: the left one holds the dB scale and the limit pill,
// the bottom one holds the integration-time pill, so neither covers measurement values.
private val LEFT_MARGIN: Dp = 48.dp
private val BOTTOM_MARGIN: Dp = 32.dp
private val PLOT_HEIGHT: Dp = 260.dp

private fun dbToFraction(db: Float) =
    ((db - SCOPE_MIN_DB) / (SCOPE_MAX_DB - SCOPE_MIN_DB)).coerceIn(0f, 1f)

private enum class Handle { Limit, Window }

/**
 * Oscilloscope-style plot of the last [HISTORY_SECONDS] seconds, newest on the right.
 *
 * Thin line = level per 100 ms, thick line = integrated level (coral where above the limit).
 * Two handles make the plot the control:
 *  - drag the dashed amber line up or down (or its pill on the left scale) to set the limit,
 *    over the full scale up to 0 dBFS;
 *  - drag the left edge of the shaded band (or its pill below the plot) left or right to set the
 *    integration time, up to the whole 30 s window.
 * Touches that hit neither handle are left alone, so the page can still scroll.
 */
@Composable
fun ScopeView(
    powers: List<Double>,
    windowSeconds: Float,
    onWindowSecondsChange: (Float) -> Unit,
    limitDb: Float,
    onLimitChange: (Float) -> Unit,
    hysteresisDb: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextMuted, fontSize = 10.sp)
    val knobStyle = TextStyle(color = OnAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val windowChunks = (windowSeconds * CHUNKS_PER_SECOND).roundToInt().coerceAtLeast(1)
    val averaged = remember(powers, windowChunks) { movingAverageDb(powers, windowChunks) }

    val currentLimit by rememberUpdatedState(limitDb)
    val currentWindow by rememberUpdatedState(windowSeconds)
    val setLimit by rememberUpdatedState(onLimitChange)
    val setWindow by rememberUpdatedState(onWindowSecondsChange)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PLOT_HEIGHT + BOTTOM_MARGIN)
                .clip(RoundedCornerShape(12.dp))
                .background(Navy)
                .pointerInput(Unit) {
                    val leftPad = LEFT_MARGIN.toPx()
                    val plotH = PLOT_HEIGHT.toPx()
                    val grab = 32.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val plotW = w - leftPad
                        val step = plotW / (HISTORY_CHUNKS - 1)

                        val limitY = plotH * (1f - dbToFraction(currentLimit))
                        val bandX = (w - currentWindow * CHUNKS_PER_SECOND * step).coerceAtLeast(leftPad)
                        val dLimit = abs(down.position.y - limitY)
                        val dWindow = abs(down.position.x - bandX)
                        val handle = when {
                            minOf(dLimit, dWindow) > grab -> return@awaitEachGesture
                            dLimit <= dWindow -> Handle.Limit
                            else -> Handle.Window
                        }

                        fun apply(pos: Offset) {
                            when (handle) {
                                Handle.Limit -> {
                                    val db = SCOPE_MIN_DB + (1f - pos.y / plotH) * (SCOPE_MAX_DB - SCOPE_MIN_DB)
                                    setLimit(db.coerceIn(LIMIT_MIN_DB, LIMIT_MAX_DB))
                                }
                                Handle.Window -> {
                                    val seconds = (w - pos.x) / step / CHUNKS_PER_SECOND
                                    setWindow(seconds.roundToInt().coerceIn(1, MAX_WINDOW_SECONDS).toFloat())
                                }
                            }
                        }

                        down.consume()
                        apply(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            apply(change.position)
                        }
                    }
                },
        ) {
            val leftPad = LEFT_MARGIN.toPx()
            val plotH = PLOT_HEIGHT.toPx()
            val plotWidth = size.width - leftPad
            val step = plotWidth / (HISTORY_CHUNKS - 1)

            fun x(index: Int) = size.width - (powers.size - 1 - index) * step
            fun y(db: Float) = plotH * (1f - dbToFraction(db))

            // Grid and dB labels, in the left margin.
            for (db in listOf(-70, -60, -50, -40, -30, -20)) {
                val gy = y(db.toFloat())
                drawLine(Track, Offset(leftPad, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(db.toString(), labelStyle)
                drawText(label, topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), gy - label.size.height / 2f))
            }

            // The window that is currently integrated.
            val bandWidth = (windowChunks * step).coerceAtMost(plotWidth)
            val bandLeft = size.width - bandWidth
            drawRect(
                color = Amber.copy(alpha = 0.14f),
                topLeft = Offset(bandLeft, 0f),
                size = Size(bandWidth, plotH),
            )
            // Edge line runs down into the bottom strip to reach its pill.
            drawLine(Amber.copy(alpha = 0.7f), Offset(bandLeft, 0f), Offset(bandLeft, size.height), strokeWidth = 2.dp.toPx())

            if (powers.size >= 2) {
                // Level per chunk, thin.
                val raw = Path()
                powers.forEachIndexed { i, p ->
                    val px = x(i)
                    val py = y(powerToDb(p).toFloat())
                    if (i == 0) raw.moveTo(px, py) else raw.lineTo(px, py)
                }
                drawPath(raw, TextMuted.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))

                // Integrated level, thick, coral where above the limit.
                for (i in 1 until averaged.size) {
                    drawLine(
                        color = if (averaged[i] > limitDb) Coral else Teal,
                        start = Offset(x(i - 1), y(averaged[i - 1].toFloat())),
                        end = Offset(x(i), y(averaged[i].toFloat())),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }

            // Where the hysteresis ends: after a buzz the level must drop under this line.
            if (hysteresisDb > 0f) {
                val hy = y(limitDb - hysteresisDb)
                drawLine(
                    Amber.copy(alpha = 0.45f),
                    Offset(leftPad, hy),
                    Offset(size.width, hy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f)),
                )
            }

            // Limit line, reaching into the left margin where its pill sits.
            val ly = y(limitDb)
            drawLine(
                Amber,
                Offset(0f, ly),
                Offset(size.width, ly),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )

            // Handles with their values, both outside the data area.
            drawKnob(
                measurer, knobStyle, "${limitDb.roundToInt()}",
                centerX = leftPad / 2, centerY = ly, minX = 0f, minY = 0f, maxY = plotH,
            )
            drawKnob(
                measurer, knobStyle, "${windowSeconds.roundToInt()} s",
                centerX = bandLeft, centerY = plotH + BOTTOM_MARGIN.toPx() / 2,
                minX = leftPad, minY = plotH, maxY = size.height,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = LEFT_MARGIN),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("-$HISTORY_SECONDS s", color = TextMuted, fontSize = 11.sp)
            Text("-${HISTORY_SECONDS / 2} s", color = TextMuted, fontSize = 11.sp)
            Text("now", color = TextMuted, fontSize = 11.sp)
        }
    }
}

/** An amber pill with [text], centred on the point and kept within the given bounds. */
private fun DrawScope.drawKnob(
    measurer: TextMeasurer,
    style: TextStyle,
    text: String,
    centerX: Float,
    centerY: Float,
    minX: Float,
    minY: Float,
    maxY: Float,
) {
    val layout = measurer.measure(text, style)
    val kw = max(layout.size.width + 16.dp.toPx(), 40.dp.toPx())
    val kh = 26.dp.toPx()
    val left = (centerX - kw / 2).coerceIn(minX, size.width - kw)
    val top = (centerY - kh / 2).coerceIn(minY, maxY - kh)
    drawRoundRect(Amber, Offset(left, top), Size(kw, kh), CornerRadius(kh / 2))
    drawText(
        layout,
        topLeft = Offset(left + (kw - layout.size.width) / 2, top + (kh - layout.size.height) / 2),
    )
}

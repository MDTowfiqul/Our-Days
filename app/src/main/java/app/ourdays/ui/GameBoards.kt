package app.ourdays.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.material3.MaterialTheme
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ourdays.data.DotsBoxes
import app.ourdays.data.DotsBoxesState
import app.ourdays.data.DotsMove
import app.ourdays.data.Gomoku
import app.ourdays.data.GomokuMove
import app.ourdays.data.GomokuState
import kotlin.math.min

private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return kotlin.math.hypot(px - x1, py - y1)
    val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
    return kotlin.math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

/** Board padding, shared by drawing and tap mapping so taps land on the drawn grid. */
private val BOARD_PAD = 28.dp

/**
 * Dots and Boxes board. Dots are drawn, claimed edges are solid, unclaimed edges
 * are faint so the grid stays visible. Tapping near an edge plays it.
 */
@Composable
fun DotsBoxesBoard(
    state: DotsBoxesState,
    interactive: Boolean,
    onMove: (DotsMove) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { sizePx = it }
            .pointerInput(state, sizePx, interactive) {
                if (!interactive || sizePx == IntSize.Zero) return@pointerInput
                detectTapGestures { offset ->
                    // Same geometry as the Canvas below: pointerInput has Density,
                    // so 28.dp converts to exactly the padding used for drawing.
                    val pad = BOARD_PAD.toPx()
                    val minDim = min(sizePx.width, sizePx.height).toFloat()
                    val step = (minDim - 2 * pad) / (DotsBoxes.DOTS - 1)
                    fun dotX(c: Int) = pad + c * step
                    fun dotY(r: Int) = pad + r * step
                    var best: DotsMove? = null
                    var bestDist = Float.MAX_VALUE
                    for (r in 0 until DotsBoxes.DOTS) {
                        for (c in 0 until DotsBoxes.DOTS - 1) {
                            val d = distanceToSegment(offset.x, offset.y, dotX(c), dotY(r), dotX(c + 1), dotY(r))
                            if (d < bestDist) {
                                bestDist = d
                                best = DotsMove(horizontal = true, index = r * (DotsBoxes.DOTS - 1) + c)
                            }
                        }
                    }
                    for (r in 0 until DotsBoxes.DOTS - 1) {
                        for (c in 0 until DotsBoxes.DOTS) {
                            val d = distanceToSegment(offset.x, offset.y, dotX(c), dotY(r), dotX(c), dotY(r + 1))
                            if (d < bestDist) {
                                bestDist = d
                                best = DotsMove(horizontal = false, index = r * DotsBoxes.DOTS + c)
                            }
                        }
                    }
                    if (bestDist <= step * 0.45f && best != null) onMove(best)
                }
            },
    ) {
        val pad = BOARD_PAD.toPx()
        val step = (min(size.width, size.height) - 2 * pad) / (DotsBoxes.DOTS - 1)
        fun dotX(c: Int) = pad + c * step
        fun dotY(r: Int) = pad + r * step
        // Claimed boxes, translucent in the owner's colour
        for (r in 0 until DotsBoxes.BOX_ROWS) {
            for (c in 0 until DotsBoxes.BOX_COLS) {
                val owner = state.boxes[r * DotsBoxes.BOX_COLS + c]
                if (owner != 0) {
                    drawRect(
                        color = (if (owner == 1) primary else tertiary).copy(alpha = 0.25f),
                        topLeft = Offset(dotX(c), dotY(r)),
                        size = androidx.compose.ui.geometry.Size(step, step),
                    )
                }
            }
        }
        // Edges: claimed solid, unclaimed faint
        val edgeWidth = 5.dp.toPx()
        for (r in 0 until DotsBoxes.DOTS) {
            for (c in 0 until DotsBoxes.DOTS - 1) {
                val index = r * (DotsBoxes.DOTS - 1) + c
                val claimed = index in state.hEdges
                drawLine(
                    color = if (claimed) primary else onSurface.copy(alpha = 0.18f),
                    start = Offset(dotX(c), dotY(r)),
                    end = Offset(dotX(c + 1), dotY(r)),
                    strokeWidth = edgeWidth,
                )
            }
        }
        for (r in 0 until DotsBoxes.DOTS - 1) {
            for (c in 0 until DotsBoxes.DOTS) {
                val index = r * DotsBoxes.DOTS + c
                val claimed = index in state.vEdges
                drawLine(
                    color = if (claimed) primary else onSurface.copy(alpha = 0.18f),
                    start = Offset(dotX(c), dotY(r)),
                    end = Offset(dotX(c), dotY(r + 1)),
                    strokeWidth = edgeWidth,
                )
            }
        }
        // Dots on top
        for (r in 0 until DotsBoxes.DOTS) {
            for (c in 0 until DotsBoxes.DOTS) {
                drawCircle(color = onSurface, radius = 7.dp.toPx(), center = Offset(dotX(c), dotY(r)))
            }
        }
    }
}

/**
 * Gomoku board: 13x13 intersections with star points. Tapping near an
 * intersection plays there.
 */
@Composable
fun GomokuBoard(
    state: GomokuState,
    interactive: Boolean,
    onMove: (GomokuMove) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { sizePx = it }
            .pointerInput(state, sizePx, interactive) {
                if (!interactive || sizePx == IntSize.Zero) return@pointerInput
                detectTapGestures { offset ->
                    val pad = BOARD_PAD.toPx()
                    val minDim = min(sizePx.width, sizePx.height).toFloat()
                    val step = (minDim - 2 * pad) / (Gomoku.SIZE - 1)
                    val c = ((offset.x - pad) / step + 0.5f).toInt()
                    val r = ((offset.y - pad) / step + 0.5f).toInt()
                    if (r in 0 until Gomoku.SIZE && c in 0 until Gomoku.SIZE) {
                        val cx = pad + c * step
                        val cy = pad + r * step
                        if (kotlin.math.hypot(offset.x - cx, offset.y - cy) <= step * 0.45f) {
                            onMove(GomokuMove(r * Gomoku.SIZE + c))
                        }
                    }
                }
            },
    ) {
        val pad = BOARD_PAD.toPx()
        val step = (min(size.width, size.height) - 2 * pad) / (Gomoku.SIZE - 1)
        fun px(c: Int) = pad + c * step
        fun py(r: Int) = pad + r * step
        // Grid
        for (i in 0 until Gomoku.SIZE) {
            drawLine(
                color = onSurface.copy(alpha = 0.35f),
                start = Offset(px(0), py(i)),
                end = Offset(px(Gomoku.SIZE - 1), py(i)),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = onSurface.copy(alpha = 0.35f),
                start = Offset(px(i), py(0)),
                end = Offset(px(i), py(Gomoku.SIZE - 1)),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // Star points
        listOf(3 to 3, 3 to 9, 9 to 3, 9 to 9, 6 to 6).forEach { (r, c) ->
            drawCircle(color = onSurface.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = Offset(px(c), py(r)))
        }
        // Stones
        for (r in 0 until Gomoku.SIZE) {
            for (c in 0 until Gomoku.SIZE) {
                val cell = state.cells[r * Gomoku.SIZE + c]
                if (cell == 0) continue
                val color = if (cell == 1) primary else tertiary
                drawCircle(color = color, radius = step * 0.42f, center = Offset(px(c), py(r)))
                if (state.lastMove == r * Gomoku.SIZE + c) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = step * 0.12f,
                        center = Offset(px(c), py(r)),
                    )
                }
            }
        }
    }
}

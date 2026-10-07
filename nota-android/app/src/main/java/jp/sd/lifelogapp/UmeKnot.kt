package jp.sd.lifelogapp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val KnotCord = Color(0xFFB3262E)
val KnotEdge = Color(0xFFD9B15A)

private const val KNOT_N = 900
private const val KNOT_WIN = 13

private class OverVisit(val idx: Int, val x: Double, val y: Double)

private class KnotGeometry(val xs: DoubleArray, val ys: DoubleArray, val overs: List<OverVisit>)

private val knotGeometry: KnotGeometry by lazy { buildKnotGeometry() }

private fun buildKnotGeometry(): KnotGeometry {
    val radius = 54.0
    val amplitude = 32.0
    val bights = 5.0
    val leads = 3.0
    val total = 2 * PI * leads
    val xs = DoubleArray(KNOT_N)
    val ys = DoubleArray(KNOT_N)
    for (i in 0 until KNOT_N) {
        val th = total * i / KNOT_N
        val r = radius + amplitude * cos(bights * th / leads)
        xs[i] = 100.0 + r * cos(th)
        ys[i] = 100.0 + r * sin(th)
    }

    val visits = mutableListOf<OverVisit>()
    for (i in 0 until KNOT_N) {
        val x1 = xs[i]
        val y1 = ys[i]
        val x2 = xs[(i + 1) % KNOT_N]
        val y2 = ys[(i + 1) % KNOT_N]
        for (j in i + 2 until KNOT_N) {
            if (i == 0 && j == KNOT_N - 1) continue
            val x3 = xs[j]
            val y3 = ys[j]
            val x4 = xs[(j + 1) % KNOT_N]
            val y4 = ys[(j + 1) % KNOT_N]
            val d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3)
            if (abs(d) < 1e-12) continue
            val t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d
            val u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d
            if (t >= 0 && t < 1 && u >= 0 && u < 1) {
                val cx = x1 + t * (x2 - x1)
                val cy = y1 + t * (y2 - y1)
                visits.add(OverVisit(i, cx, cy))
                visits.add(OverVisit(j, cx, cy))
            }
        }
    }
    visits.sortBy { it.idx }
    val overs = visits.filterIndexed { n, _ -> n % 2 == 0 }
    return KnotGeometry(xs, ys, overs)
}

@Composable
fun UmeKnot(modifier: Modifier = Modifier, progress: Float = 1f, weight: Float = 1f) {
    Canvas(modifier = modifier) {
        val g = knotGeometry
        val side = minOf(size.width, size.height)
        val s = side / 200f
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val endIdx = (KNOT_N * progress.coerceIn(0f, 1f)).toInt()
        if (endIdx < 2) return@Canvas

        fun pt(i: Int): Offset {
            val k = ((i % KNOT_N) + KNOT_N) % KNOT_N
            return Offset(ox + g.xs[k].toFloat() * s, oy + g.ys[k].toFloat() * s)
        }

        val cordW = 8.5f * s * weight
        val edgeW = cordW + 5f * s * weight
        val discR = 13f * s * weight.coerceAtLeast(1f)

        val base = Path().apply {
            val p0 = pt(0)
            moveTo(p0.x, p0.y)
            for (i in 1..minOf(endIdx, KNOT_N)) {
                val p = pt(i)
                lineTo(p.x, p.y)
            }
            if (progress >= 1f) close()
        }
        drawPath(base, KnotEdge, style = Stroke(width = edgeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(base, KnotCord, style = Stroke(width = cordW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        g.overs.forEach { v ->
            if (v.idx + KNOT_WIN + 1 > endIdx && progress < 1f) return@forEach
            val window = Path().apply {
                val start = pt(v.idx - KNOT_WIN)
                moveTo(start.x, start.y)
                for (m in (v.idx - KNOT_WIN + 1)..(v.idx + KNOT_WIN + 1)) {
                    val p = pt(m)
                    lineTo(p.x, p.y)
                }
            }
            val center = Offset(ox + v.x.toFloat() * s, oy + v.y.toFloat() * s)
            val clip = Path().apply {
                addOval(Rect(Offset(center.x - discR, center.y - discR), Size(discR * 2, discR * 2)))
            }
            clipPath(clip) {
                drawPath(window, KnotEdge, style = Stroke(width = edgeW, cap = StrokeCap.Butt, join = StrokeJoin.Round))
                drawPath(window, KnotCord, style = Stroke(width = cordW, cap = StrokeCap.Butt, join = StrokeJoin.Round))
            }
        }
    }
}

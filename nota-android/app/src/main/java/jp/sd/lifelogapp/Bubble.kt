package jp.sd.lifelogapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.rotate

// シャボン玉の縁の、虹色（パステル）
private val RimColors = listOf(
    Color(0xFFFF9EC4), Color(0xFFB7A4F2), Color(0xFF6FC4F0),
    Color(0xFF7EE2C0), Color(0xFFFFE07E), Color(0xFFFF9EC4)
)

private fun DrawScope.drawBubble(center: Offset, r: Float, alpha: Float = 1f, spin: Float = 0f) {
    if (alpha <= 0f || r <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.7f * alpha), Color(0xFFBFE4F4).copy(alpha = 0.26f * alpha), Color(0xFFC9B8F2).copy(alpha = 0.3f * alpha)),
            center = Offset(center.x - r * 0.36f, center.y - r * 0.4f),
            radius = r * 1.7f
        ),
        radius = r,
        center = center
    )
    val rimW = r * 0.14f
    rotate(spin, center) {
        drawCircle(
            brush = Brush.sweepGradient(RimColors.map { it.copy(alpha = 0.95f * alpha) }, center),
            radius = r - rimW / 2f,
            center = center,
            style = Stroke(width = rimW)
        )
    }
    val hr = r * 0.66f
    drawArc(
        color = Color.White.copy(alpha = 0.95f * alpha),
        startAngle = 200f,
        sweepAngle = 42f,
        useCenter = false,
        topLeft = Offset(center.x - hr, center.y - hr),
        size = Size(hr * 2, hr * 2),
        style = Stroke(width = (r * 0.1f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
    )
}

// 「会えた日」の印：2つのシャボン玉が重なる
@Composable
fun BubblePair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val r = side * 0.33f
        val cy = size.height / 2f
        drawBubble(Offset(size.width / 2f - r * 0.5f, cy), r)
        drawBubble(Offset(size.width / 2f + r * 0.5f, cy), r)
    }
}

// 「会えなかった日」の印：中が透けた、淡い輪だけ
@Composable
fun BubbleHollow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val r = side * 0.34f
        drawCircle(color = color, radius = r, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(width = side * 0.08f))
    }
}

// ログイン画面のロゴ：3つの泡が、ふわっと集まって、重なる
@Composable
fun BubbleLogo(modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val p = progress.coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p)
        val r = side * 0.27f
        val center = Offset(ox + side * 0.5f, oy + side * 0.52f)
        val finals = listOf(Offset(0f, -0.15f), Offset(-0.19f, 0.15f), Offset(0.19f, 0.15f))
        val starts = listOf(Offset(0.05f, -0.55f), Offset(-0.5f, 0.5f), Offset(0.5f, 0.45f))
        finals.forEachIndexed { i, f ->
            val s = starts[i]
            val x = s.x + (f.x - s.x) * ease
            val y = s.y + (f.y - s.y) * ease
            drawBubble(Offset(center.x + x * side, center.y + y * side), r, alpha = ease, spin = (1f - ease) * 120f * (if (i % 2 == 0) 1 else -1))
        }
        // 小さな泡
        drawBubble(Offset(ox + side * 0.88f, oy + side * 0.14f), side * 0.05f, alpha = ease)
        drawBubble(Offset(ox + side * 0.1f, oy + side * 0.84f), side * 0.035f, alpha = ease)
    }
}

// ログイン画面の余白に、ふわふわ浮かぶ泡
private class FloatSpec(val x: Float, val y: Float, val rDp: Float, val cycles: Int, val off: Float, val alpha: Float)

private val FLOAT_SPECS = listOf(
    FloatSpec(0.10f, 0.09f, 26f, 1, 0.00f, 0.95f), FloatSpec(0.86f, 0.06f, 16f, 2, 0.30f, 0.90f),
    FloatSpec(0.80f, 0.19f, 34f, 1, 0.55f, 0.95f), FloatSpec(0.07f, 0.31f, 18f, 2, 0.10f, 0.90f),
    FloatSpec(0.92f, 0.40f, 24f, 1, 0.80f, 0.90f), FloatSpec(0.02f, 0.57f, 34f, 1, 0.40f, 0.95f),
    FloatSpec(0.98f, 0.66f, 20f, 2, 0.65f, 0.90f), FloatSpec(0.03f, 0.83f, 24f, 2, 0.20f, 0.95f),
    FloatSpec(0.90f, 0.89f, 34f, 1, 0.95f, 0.95f), FloatSpec(0.50f, 0.96f, 14f, 2, 0.50f, 0.85f), FloatSpec(0.68f, 0.955f, 22f, 1, 0.15f, 0.90f), FloatSpec(0.30f, 0.945f, 28f, 1, 0.60f, 0.92f),
    FloatSpec(0.38f, 0.035f, 12f, 2, 0.70f, 0.85f), FloatSpec(0.60f, 0.10f, 9f, 1, 0.25f, 0.85f)
)

@Composable
fun FloatingBubbles(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "floatingBubbles")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        FLOAT_SPECS.forEach { sp ->
            val a = 2.0 * PI * (phase * sp.cycles + sp.off)
            val x = w * sp.x + (sin(a) * 10.dp.toPx()).toFloat()
            val y = h * sp.y + (cos(a * 0.9 + 1.3) * 16.dp.toPx()).toFloat()
            drawBubble(Offset(x, y), sp.rDp.dp.toPx(), alpha = sp.alpha, spin = (phase * 360f * sp.cycles))
        }
    }
}

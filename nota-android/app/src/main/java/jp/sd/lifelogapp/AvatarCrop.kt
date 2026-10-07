package jp.sd.lifelogapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 4f

// 写真をドラッグ・ピンチして、丸い枠に入れる範囲を決める画面。「決定」で、枠内の範囲を192pxのJPEG（Base64）にして返す。
@Composable
fun AvatarCropDialog(bitmap: android.graphics.Bitmap, onCancel: () -> Unit, onConfirm: (String?) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val bw = bitmap.width
    val bh = bitmap.height

    var zoom by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableIntStateOf(0) }

    fun baseScale(v: Float) = v / min(bw, bh).toFloat()

    fun clamp(v: Float) {
        val s = baseScale(v) * zoom
        val maxX = max(0f, (bw * s - v) / 2f)
        val maxY = max(0f, (bh * s - v) / 2f)
        ox = ox.coerceIn(-maxX, maxX)
        oy = oy.coerceIn(-maxY, maxY)
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "アイコンの範囲を決める", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ドラッグで位置を、2本指で大きさを変えられます。丸の中が、アイコンになります。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.Black)
                        .clipToBounds()
                        .onSizeChanged { viewport = it.width }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val v = size.width.toFloat()
                                zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                ox += pan.x
                                oy += pan.y
                                clamp(v)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val v = size.width
                        val s = baseScale(v) * zoom
                        val dw = bw * s
                        val dh = bh * s
                        val left = (v - dw) / 2f + ox
                        val top = (size.height - dh) / 2f + oy
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bw, bh),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                        // 丸の外側を暗くして、アイコンになる範囲を見せる
                        val circle = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, androidx.compose.ui.geometry.Size(v, size.height)))
                        }
                        clipPath(circle, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.55f))
                        }
                        drawCircle(
                            color = Color.White,
                            radius = v / 2f,
                            center = Offset(v / 2f, size.height / 2f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("キャンセル") }
                    Button(
                        enabled = viewport > 0,
                        onClick = {
                            val v = viewport.toFloat()
                            val s = baseScale(v) * zoom
                            val side = v / s
                            val cx = bw / 2f - ox / s
                            val cy = bh / 2f - oy / s
                            val left = (cx - side / 2f).coerceIn(0f, max(0f, bw - side))
                            val top = (cy - side / 2f).coerceIn(0f, max(0f, bh - side))
                            onConfirm(cropToAvatarBase64(bitmap, left, top, side))
                        }
                    ) { Text("決定") }
                }
            }
        }
    }
}

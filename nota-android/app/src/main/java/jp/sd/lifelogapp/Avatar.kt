package jp.sd.lifelogapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.text.BreakIterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val AVATAR_PX = 192

// 選んだ写真を、画面に出して編集できる大きさ（長辺1600px以下）で読み込む。向きの情報（Exif）も反映する。
fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        bmp
    } catch (e: Exception) {
        Log.e("Avatar", "decode bitmap failed", e)
        null
    }
}

// 指定された正方形の範囲を切り出し、192pxのJPEG（Base64）にする。
fun cropToAvatarBase64(bmp: Bitmap, left: Float, top: Float, side: Float): String? {
    return try {
        val s = side.roundToInt().coerceIn(1, min(bmp.width, bmp.height))
        val x = left.roundToInt().coerceIn(0, bmp.width - s)
        val y = top.roundToInt().coerceIn(0, bmp.height - s)
        val square = Bitmap.createBitmap(bmp, x, y, s, s)
        val scaled = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e("Avatar", "crop avatar failed", e)
        null
    }
}

fun decodeAvatarBase64(base64: String): ImageBitmap? {
    if (base64.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        Log.e("Avatar", "decode avatar failed", e)
        null
    }
}

// 入力された文字列の先頭の「見た目1文字」だけを取り出す（絵文字は複数のコードの組み合わせのため）。
fun firstGrapheme(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val it = BreakIterator.getCharacterInstance()
    it.setText(t)
    val end = it.next()
    return if (end == BreakIterator.DONE) t else t.substring(0, end)
}

// いま見ているグループの「オーナー」（最初にグループを作った人）のユーザーID
object OwnerState {
    var id by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun isOwner(userId: String?): Boolean = !userId.isNullOrBlank() && userId == id
}

val OwnerGold = androidx.compose.ui.graphics.Color(0xFFE0A526)

// 写真 → 絵文字 → 頭文字 の順に表示するアイコン。オーナーは、金色の輪郭と、星印が付く。
@Composable
fun AvatarCircle(avatarSize: Dp, initial: String, emoji: String, image: ImageBitmap?, large: Boolean, owner: Boolean = false) {
    Box(modifier = Modifier.size(avatarSize)) {
    Box(
        modifier = Modifier
            .size(avatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .then(if (owner) Modifier.border(androidx.compose.ui.unit.Dp(maxOf(2f, avatarSize.value * 0.06f)), OwnerGold, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            emoji.isNotBlank() -> Text(text = emoji, fontSize = (avatarSize.value * 0.55f).sp)
            else -> Text(
                text = initial,
                fontSize = (avatarSize.value * 0.42f).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
    if (owner) {
        val badge = Dp(maxOf(12f, avatarSize.value * 0.38f))
        Box(
            modifier = Modifier
                .size(badge)
                .align(Alignment.TopEnd)
                .offset(x = badge * 0.18f, y = -badge * 0.18f)
                .background(OwnerGold, CircleShape)
                .border(1.5.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "★", color = androidx.compose.ui.graphics.Color.White, fontSize = (badge.value * 0.62f).sp)
        }
    }
    }
}

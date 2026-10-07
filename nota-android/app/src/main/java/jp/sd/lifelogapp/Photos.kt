package jp.sd.lifelogapp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val BUCKET = "meetup-photos"

data class MeetupPhoto(val id: String, val meetupId: String, val path: String, val createdBy: String = "", val createdAt: String = "")

// 画面に出した写真を、アプリが動いている間だけ覚えておく（毎回ダウンロードしないため）
private object PhotoCache {
    private val map = HashMap<String, ImageBitmap>()
    @Synchronized fun get(path: String): ImageBitmap? = map[path]
    @Synchronized fun put(path: String, img: ImageBitmap) { map[path] = img }
    @Synchronized fun remove(path: String) { map.remove(path) }
}

// アルバムの小さな表示用（読み込みを軽くするため、縮めて覚える）
private object ThumbCache {
    private val map = HashMap<String, ImageBitmap>()
    @Synchronized fun get(path: String): ImageBitmap? = map[path]
    @Synchronized fun put(path: String, img: ImageBitmap) { map[path] = img }
    @Synchronized fun remove(path: String) { map.remove(path) }
}

suspend fun loadThumb(path: String): ImageBitmap? {
    ThumbCache.get(path)?.let { return it }
    PhotoCache.get(path)?.let { return it }
    return try {
        val bytes = supabase.storage.from(BUCKET).downloadAuthenticated(path)
        val bmp = withContext(Dispatchers.Default) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } ?: return null
        val img = bmp.asImageBitmap()
        ThumbCache.put(path, img)
        img
    } catch (e: Exception) {
        Log.e("Photos", "thumb download failed: $path", e)
        null
    }
}

suspend fun loadPhoto(path: String): ImageBitmap? {
    PhotoCache.get(path)?.let { return it }
    return try {
        val bytes = supabase.storage.from(BUCKET).downloadAuthenticated(path)
        val bmp = withContext(Dispatchers.Default) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } ?: return null
        val img = bmp.asImageBitmap()
        PhotoCache.put(path, img)
        img
    } catch (e: Exception) {
        Log.e("Photos", "download failed: $path", e)
        null
    }
}

// 長辺を maxEdge に縮めて、JPEGにする（通信量と保存容量を減らす）
fun bitmapToJpegBytes(src: Bitmap, maxEdge: Int = 1280, quality: Int = 80): ByteArray {
    val longest = maxOf(src.width, src.height)
    val bmp = if (longest > maxEdge) {
        val scale = maxEdge.toFloat() / longest
        Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
    } else src
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

// 会えた日の写真を、まとめて送る。1枚ずつ縮めて、保存先に上げ、一覧の表にも記録する。
suspend fun uploadMeetupPhotos(context: Context, meetupId: String, uris: List<Uri>): Int {
    var done = 0
    uris.forEach { uri ->
        val bytes = withContext(Dispatchers.Default) { decodeOrientedBitmap(context, uri)?.let { bitmapToJpegBytes(it) } } ?: return@forEach
        val path = "$meetupId/${UUID.randomUUID()}.jpg"
        supabase.storage.from(BUCKET).upload(path, bytes)
        supabase.from("meetup_photos").insert(buildJsonObject {
            put("meetup_id", meetupId)
            put("path", path)
        })
        done++
    }
    return done
}

// 写真を、保存先と、一覧の表の両方から消す
suspend fun deleteMeetupPhoto(path: String) {
    supabase.storage.from(BUCKET).delete(path)
    supabase.from("meetup_photos").delete { filter { eq("path", path) } }
    PhotoCache.remove(path)
    ThumbCache.remove(path)
}

@Composable
fun PhotoTile(path: String, modifier: Modifier, onClick: () -> Unit) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(ThumbCache.get(path)) }
    LaunchedEffect(path) { if (img == null) img = loadThumb(path) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        img?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
fun PhotoFull(path: String, modifier: Modifier) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(PhotoCache.get(path)) }
    LaunchedEffect(path) { if (img == null) img = loadPhoto(path) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        img?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            ?: Text(text = "読み込んでいます…", color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
fun PhotoThumb(path: String, size: Dp, onClick: () -> Unit) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(PhotoCache.get(path)) }
    LaunchedEffect(path) { if (img == null) img = loadPhoto(path) }
    Box(
        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        img?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Text(text = "…")
    }
}

@Composable
fun PhotoViewerDialog(path: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(PhotoCache.get(path)) }
    LaunchedEffect(path) { if (img == null) img = loadPhoto(path) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("閉じる", color = androidx.compose.ui.graphics.Color.White) }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("この写真を消す", color = androidx.compose.ui.graphics.Color(0xFFFFB4B4)) }
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    img?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        ?: Text(text = "読み込んでいます…", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

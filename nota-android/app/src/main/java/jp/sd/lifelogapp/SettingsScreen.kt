package jp.sd.lifelogapp

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import jp.sd.lifelogapp.ui.theme.ThemeOption
import jp.sd.lifelogapp.ui.theme.ThemePrefs
import jp.sd.lifelogapp.ui.theme.ThemeState
import jp.sd.lifelogapp.ui.theme.colorSchemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

data class GroupMember(val email: String, val nickname: String, val status: String, val mine: Boolean = false)
data class IncomingInvite(val id: String, val inviterEmail: String, val suggestedName: String, val groupName: String = "")

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current




    var avatarEmoji by remember { mutableStateOf("") }
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var emojiInput by remember { mutableStateOf("") }
    var avatarBusy by remember { mutableStateOf(false) }
    var avatarMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    suspend fun postAvatar(kind: String, emoji: String, imageBase64: String): Boolean {
        val session = supabase.auth.currentSessionOrNull()
        val userId = supabase.auth.currentUserOrNull()?.id
        if (session == null || userId == null) return false
        val requestBody = JSONObject().apply {
            put("source", "set_avatar")
            put("access_token", session.accessToken)
            put("user_id", userId)
            put("kind", kind)
            put("emoji", emoji)
            put("image_base64", imageBase64)
        }.toString()
        return JSONObject(postToGas(requestBody)).optString("status") == "ok"
    }

    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatarBusy = true
        avatarMessage = null
        scope.launch {
            try {
                val bmp = withContext(Dispatchers.Default) { decodeOrientedBitmap(context, uri) }
                if (bmp == null) {
                    avatarMessage = "写真を読み込めませんでした。別の写真でお試しください。"
                } else {
                    cropSource = bmp
                }
            } finally {
                avatarBusy = false
            }
        }
    }

    fun uploadPhoto(base64: String?) {
        cropSource = null
        if (base64 == null) {
            avatarMessage = "写真を切り出せませんでした。もう一度お試しください。"
            return
        }
        avatarBusy = true
        avatarMessage = null
        scope.launch {
            try {
                if (postAvatar("photo", "", base64)) {
                    avatarImage = decodeAvatarBase64(base64)
                    avatarEmoji = ""
                    avatarMessage = "写真を設定しました。"
                } else {
                    avatarMessage = "設定できませんでした。もう一度お試しください。"
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "set avatar photo failed", e)
                avatarMessage = "通信に失敗しました。電波の良い場所でもう一度お試しください。"
            } finally {
                avatarBusy = false
            }
        }
    }

    cropSource?.let { src ->
        AvatarCropDialog(bitmap = src, onCancel = { cropSource = null }, onConfirm = { uploadPhoto(it) })
    }

    fun applyMyProfile(responseJson: JSONObject) {
                avatarEmoji = responseJson.optString("avatar_emoji")
                avatarImage = decodeAvatarBase64(responseJson.optString("avatar_image"))
                }

    suspend fun loadMyProfile() {
        try {
            val session = supabase.auth.currentSessionOrNull()
            val userId = supabase.auth.currentUserOrNull()?.id
            if (session == null || userId == null) return
            val requestBody = JSONObject().apply {
                put("source", "get_my_profile")
                put("access_token", session.accessToken)
                put("user_id", userId)
            }.toString()
            val responseText = postToGas(requestBody)
            val responseJson = JSONObject(responseText)
            if (responseJson.optString("status") == "ok") {
                UiCache.put(context, userId, "my_profile", responseText)
                applyMyProfile(responseJson)
            }
        } catch (e: Exception) {
            Log.e("SettingsScreen", "load profile failed", e)
        }
    }

    LaunchedEffect(Unit) {
        supabase.auth.currentUserOrNull()?.id?.let { cachedUser ->
            try {
                UiCache.get(context, cachedUser, "my_profile")?.let { applyMyProfile(JSONObject(it)) }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "read cache failed", e)
            }
        }
        loadMyProfile()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(top = 8.dp)
    ) {
        Text(text = "設定", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "テーマ", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ThemeOption.entries.forEach { option ->
                val isSelected = ThemeState.current == option
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val swatch = colorSchemeFor(option)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(swatch.background, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else swatch.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable {
                                ThemeState.current = option
                                ThemePrefs.save(context, option)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(26.dp).background(swatch.primary, CircleShape))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = option.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "のたろん", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Nota(NotaKind.SMILE, 56.dp, always = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "のたろんを、お休みさせる", style = MaterialTheme.typography.bodyLarge)
                Text(text = "案内役の、のたろんを、画面に出さなくします。ログイン画面には、出ます。", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = !NotaState.visible, onCheckedChange = { NotaState.save(context, !it) })
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "あなたのアイコン", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "仲間に表示されます。写真、絵文字、頭文字（標準）から選べます。",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        AvatarCircle(avatarSize = 84.dp, initial = "私", emoji = avatarEmoji, image = avatarImage, large = true)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !avatarBusy,
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) { Text("写真を選ぶ") }
            OutlinedButton(
                enabled = !avatarBusy && (avatarEmoji.isNotBlank() || avatarImage != null),
                onClick = {
                    avatarBusy = true
                    avatarMessage = null
                    scope.launch {
                        try {
                            if (postAvatar("none", "", "")) {
                                avatarEmoji = ""
                                avatarImage = null
                                avatarMessage = "頭文字に戻しました。"
                            } else {
                                avatarMessage = "戻せませんでした。もう一度お試しください。"
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "reset avatar failed", e)
                            avatarMessage = "通信に失敗しました。電波の良い場所でもう一度お試しください。"
                        } finally {
                            avatarBusy = false
                        }
                    }
                }
            ) { Text("頭文字に戻す") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = emojiInput,
            onValueChange = { emojiInput = firstGrapheme(it) },
            label = { Text("絵文字をひとつ入力") },
            supportingText = { Text("キーボードの絵文字から選んで入れてください。") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            enabled = !avatarBusy && emojiInput.isNotBlank(),
            onClick = {
                val chosen = firstGrapheme(emojiInput)
                avatarBusy = true
                avatarMessage = null
                scope.launch {
                    try {
                        if (postAvatar("emoji", chosen, "")) {
                            avatarEmoji = chosen
                            avatarImage = null
                            emojiInput = ""
                            avatarMessage = "絵文字を設定しました。"
                        } else {
                            avatarMessage = "設定できませんでした。もう一度お試しください。"
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsScreen", "set avatar emoji failed", e)
                        avatarMessage = "通信に失敗しました。電波の良い場所でもう一度お試しください。"
                    } finally {
                        avatarBusy = false
                    }
                }
            }
        ) { Text("この絵文字にする") }
        if (avatarBusy) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        avatarMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it)
        }

        Spacer(modifier = Modifier.height(32.dp))
        FamilySection()

        Spacer(modifier = Modifier.height(40.dp))
        OutlinedButton(onClick = { scope.launch { UiCache.clear(context); supabase.auth.signOut() } }) {
            Text("ログアウト")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

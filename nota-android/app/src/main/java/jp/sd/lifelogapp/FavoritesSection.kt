package jp.sd.lifelogapp

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FAV_JST: ZoneId = ZoneId.of("Asia/Tokyo")
private val FAV_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

data class Favorite(val id: String, val name: String, val area: String, val url: String)
data class FavNote(val favoriteId: String, val visitedOn: String, val verdict: String, val note: String, val createdAt: String)

private fun nn(s: String) = if (s == "null") "" else s

// 仲間の行きつけ帳：お店と、訪問のたびのひとこと（また行きたい／いまいち）を、グループのみんなで残す。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesSection(groupId: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { supabase.auth.currentUserOrNull()?.id }

    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var notes by remember { mutableStateOf<List<FavNote>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddShop by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<Favorite?>(null) }

    fun applyData(json: JSONObject) {
        val fa = json.optJSONArray("favorites") ?: JSONArray()
        favorites = (0 until fa.length()).map {
            val o = fa.getJSONObject(it)
            Favorite(o.optString("id"), o.optString("shop_name"), nn(o.optString("area")), nn(o.optString("url")))
        }
        val na = json.optJSONArray("notes") ?: JSONArray()
        notes = (0 until na.length()).map {
            val o = na.getJSONObject(it)
            FavNote(o.optString("favorite_id"), nn(o.optString("visited_on")), nn(o.optString("verdict")), nn(o.optString("note")), o.optString("created_at"))
        }
    }

    suspend fun loadReal() {
        try {
            val fav = JSONArray(supabase.from("group_favorites").select { filter { eq("group_id", groupId) } }.data)
            val ids = (0 until fav.length()).map { fav.getJSONObject(it).optString("id") }
            val noteArr = if (ids.isEmpty()) JSONArray()
            else JSONArray(supabase.from("favorite_notes").select { filter { isIn("favorite_id", ids) } }.data)
            val json = JSONObject().put("favorites", fav).put("notes", noteArr)
            applyData(json)
            userId?.let { UiCache.put(context, it, "favorites:$groupId", json.toString()) }
        } catch (e: Exception) {
            Log.e("FavoritesSection", "load failed", e)
            message = "読み込めませんでした。電波の良い場所で、もう一度お試しください。"
        }
    }

    fun runAction(failMessage: String, block: suspend () -> Unit) {
        loading = true
        message = null
        scope.launch {
            try {
                block()
                loadReal()
            } catch (e: Exception) {
                Log.e("FavoritesSection", failMessage, e)
                message = failMessage
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(groupId) {
        userId?.let { uid ->
            try { UiCache.get(context, uid, "favorites:$groupId")?.let { applyData(JSONObject(it)) } } catch (e: Exception) { Log.e("FavoritesSection", "cache failed", e) }
        }
        loadReal()
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("＜ 戻る") }
            }
        }
        Text(text = "仲間の行きつけ帳", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "行ったお店のことを、ひとこと残します。次にお店を探すとき、仲間の経験が、いちばん頼りになる情報になります。",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { showAddShop = true }) { Text("お店を登録する") }
        Spacer(modifier = Modifier.height(16.dp))

        if (favorites.isEmpty() && !loading) {
            Text(text = "まだお店が登録されていません。", style = MaterialTheme.typography.bodyMedium)
        }
        favorites.forEach { f ->
            val shopNotes = notes.filter { it.favoriteId == f.id }.sortedByDescending { it.visitedOn.ifBlank { it.createdAt } }
            val latest = shopNotes.firstOrNull { it.verdict.isNotBlank() }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        latest?.let {
                            Text(
                                text = if (it.verdict == "again") "また行きたい" else "いまいち",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (f.area.isNotBlank()) Text(text = f.area, style = MaterialTheme.typography.bodySmall)
                    if (f.url.isNotBlank()) Text(text = f.url, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    shopNotes.forEach { n ->
                        val mark = when (n.verdict) { "again" -> "◎ "; "soso" -> "△ "; else -> "" }
                        val date = n.visitedOn.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it).format(FAV_DATE) }.getOrNull() } ?: ""
                        Text(text = "$mark$date　${n.note}".trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { noteTarget = f }) { Text("行った！ひとこと") }
                    }
                }
            }
        }

        if (loading) CircularProgressIndicator()
        message?.let { Text(text = it) }
    }

    if (showAddShop) {
        AddShopDialog(
            onDismiss = { showAddShop = false },
            onSave = { name, area, url ->
                showAddShop = false
                runAction("お店を登録できませんでした。もう一度お試しください。") {
                    supabase.from("group_favorites").insert(buildJsonObject {
                        put("group_id", groupId)
                        put("shop_name", name)
                        put("area", area)
                        put("url", url)
                    })
                }
            }
        )
    }
    noteTarget?.let { f ->
        AddNoteDialog(
            shopName = f.name,
            onDismiss = { noteTarget = null },
            onSave = { verdict, note ->
                noteTarget = null
                runAction("ひとことを保存できませんでした。もう一度お試しください。") {
                    supabase.from("favorite_notes").insert(buildJsonObject {
                        put("favorite_id", f.id)
                        put("visited_on", LocalDate.now(FAV_JST).toString())
                        put("verdict", verdict)
                        put("note", note)
                    })
                }
            }
        )
    }
}

@Composable
private fun AddShopDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("お店を登録する") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("お店の名前") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = area, onValueChange = { area = it.take(40) }, singleLine = true, label = { Text("エリア・最寄り駅（任意）") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it.filter { c -> c.code in 33..126 }.take(300) }, singleLine = true, label = { Text("お店のページのURL（任意）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), area.trim(), url.trim()) }) { Text("登録する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddNoteDialog(shopName: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var verdict by remember { mutableStateOf("again") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「$shopName」に行きました") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrongChip(selected = verdict == "again", onClick = { verdict = "again" }, label = { Text("また行きたい") })
                    StrongChip(selected = verdict == "soso", onClick = { verdict = "soso" }, label = { Text("いまいち") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text("ひとこと（例: 店長がいて接客が良かった）") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(verdict, note.trim()) }) { Text("保存する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

// 下のナビの「行きつけ帳」。グループが複数あるときは、選んで切り替える。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesTab(modifier: Modifier = Modifier) {
    val groups = GroupsState.groups
    var selectedId by remember { mutableStateOf<String?>(null) }
    val current = groups.firstOrNull { it.id == selectedId } ?: groups.firstOrNull()
    Column(modifier = modifier.fillMaxSize()) {
        if (current == null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "まだグループがありません。設定で仲間を招待すると、ここで、みんなの行きつけ帳を作れます。", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (groups.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEachIndexed { i, g ->
                    StrongChip(selected = g.id == current.id, onClick = { selectedId = g.id }, label = { Text(g.name ?: "グループ${i + 1}") })
                }
            }
        }
        FavoritesSection(groupId = current.id, onBack = null, modifier = Modifier.weight(1f))
    }
}

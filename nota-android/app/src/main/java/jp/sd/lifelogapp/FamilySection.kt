package jp.sd.lifelogapp

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_GROUPS = 3
private const val NEW_GROUP = "__new__"

// 設定のアイコンに付ける赤い点のための、届いている招待の数
object InviteBadge {
    var count by mutableIntStateOf(0)
}

// グループの状況（届いている招待を含む）をGASから取得して、端末にも保存する。取得できなければnull。
suspend fun fetchGroupStatus(context: Context): JSONObject? {
    val session = supabase.auth.currentSessionOrNull()
    val user = supabase.auth.currentUserOrNull()
    if (session == null || user == null) return null
    val body = JSONObject().apply {
        put("source", "check_group")
        put("app", "awai")
        put("access_token", session.accessToken)
        put("user_id", user.id)
        put("user_email", user.email)
    }.toString()
    val text = postToGas(body)
    val json = JSONObject(text)
    if (json.optString("status") != "ok") return null
    UiCache.put(context, user.id, "check_group", text)
    InviteBadge.count = json.optJSONArray("pending_invites")?.length() ?: 0
    GroupsState.updateFrom(json)
    return json
}

// mine = 自分が招待した人（このときだけ、メールアドレスが見える）
data class GroupInfo(val id: String, val name: String?, val myNickname: String, val members: List<GroupMember>)

// 設定画面の「見守り合う家族」。複数のグループ（最大3つ）に対応する。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<GroupInfo>>(emptyList()) }
    var incomingInvites by remember { mutableStateOf<List<IncomingInvite>>(emptyList()) }
    var acceptNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var nameEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }   // groupId -> 入力中の自分の名前
    var leaveTarget by remember { mutableStateOf<GroupInfo?>(null) }
    var cancelTarget by remember { mutableStateOf<Pair<GroupInfo, GroupMember>?>(null) }

    var inviteTarget by remember { mutableStateOf<String?>(null) }                // groupId または NEW_GROUP
    var newGroupName by remember { mutableStateOf("") }
    var myNameForNewGroup by remember { mutableStateOf("") }
    var newMemberNickname by remember { mutableStateOf("") }
    var newMemberEmail by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun groupLabel(g: GroupInfo) = g.name?.takeIf { it.isNotBlank() } ?: "グループ"

    fun applyStatus(json: JSONObject) {
        val parsed = mutableListOf<GroupInfo>()
        val arr = json.optJSONArray("groups")
        val objs: List<JSONObject> = when {
            arr != null -> (0 until arr.length()).map { arr.getJSONObject(it) }
            json.optJSONObject("group") != null -> listOf(json.getJSONObject("group"))   // 旧い応答形式との互換
            else -> emptyList()
        }
        objs.forEach { g ->
            val membersArr = g.optJSONArray("members") ?: JSONArray()
            parsed += GroupInfo(
                id = g.optString("id"),
                name = g.optString("name").takeIf { it.isNotBlank() },
                myNickname = g.optString("my_nickname"),
                members = (0 until membersArr.length()).map {
                    val m = membersArr.getJSONObject(it)
                    GroupMember(m.optString("email"), m.optString("nickname"), m.optString("status"), m.optBoolean("mine"))
                }
            )
        }
        groups = parsed
        nameEdits = parsed.associate { it.id to it.myNickname }

        val invitesArr = json.optJSONArray("pending_invites") ?: JSONArray()
        incomingInvites = (0 until invitesArr.length()).map {
            val o = invitesArr.getJSONObject(it)
            IncomingInvite(
                id = o.optString("invite_id"),
                inviterEmail = o.optString("inviter_name").ifBlank { o.optString("inviter_email") }.ifBlank { "仲間の一人" },
                suggestedName = o.optString("suggested_nickname"),
                groupName = o.optString("group_name")
            )
        }
        acceptNames = incomingInvites.associate { inv -> inv.id to (acceptNames[inv.id] ?: inv.suggestedName) }
        InviteBadge.count = incomingInvites.size
        GroupsState.updateFrom(json)

        if (inviteTarget == null || (inviteTarget != NEW_GROUP && parsed.none { it.id == inviteTarget })) {
            inviteTarget = if (parsed.isEmpty()) NEW_GROUP else parsed.first().id
        }
    }

    suspend fun refresh() {
        try {
            fetchGroupStatus(context)?.let { applyStatus(it) }
        } catch (e: Exception) {
            Log.e("FamilySection", "refresh failed", e)
        }
    }

    // GASに操作を送る共通処理。成功したらtrue。
    fun send(source: String, successMessage: String, failMessage: String, build: JSONObject.() -> Unit, onOk: () -> Unit = {}) {
        isLoading = true
        message = null
        scope.launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                val user = supabase.auth.currentUserOrNull()
                if (session == null || user == null) {
                    message = "ログイン状態を確認できませんでした。"
                    return@launch
                }
                val body = JSONObject().apply {
                    put("source", source)
                    put("app", "awai")
                    put("access_token", session.accessToken)
                    put("user_id", user.id)
                    put("user_email", user.email)
                    build()
                }.toString()
                val json = JSONObject(postToGas(body))
                if (json.optString("status") == "ok") {
                    message = successMessage
                    onOk()
                    refresh()
                } else {
                    val reason = json.optString("message")
                    message = if (reason.isNotBlank() && reason.contains("3つまで")) reason else failMessage
                }
            } catch (e: Exception) {
                Log.e("FamilySection", "$source failed", e)
                message = "通信に失敗しました。電波の良い場所でもう一度お試しください。"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        supabase.auth.currentUserOrNull()?.id?.let { uid ->
            try {
                UiCache.get(context, uid, "check_group")?.let { applyStatus(JSONObject(it)) }
            } catch (e: Exception) {
                Log.e("FamilySection", "read cache failed", e)
            }
        }
        refresh()
    }

    Text(text = "仲間のグループ", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "いっしょに会う約束をしたり、記念日や行きつけのお店を分け合ったりする仲間です。2人でも、大勢でも使えます。グループは、${MAX_GROUPS}つまで入れます。",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (groups.isEmpty() && incomingInvites.isEmpty()) {
        Text(text = "まだ仲間がいません。", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ---- 届いている招待（いちばん上に、目立つ色で表示する） ----
    incomingInvites.forEach { inv ->
        val myName = acceptNames[inv.id].orEmpty()
        val full = groups.size >= MAX_GROUPS
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    BubblePair(modifier = Modifier.size(width = 36.dp, height = 28.dp))
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(text = "招待が届いています", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val groupPart = inv.groupName.takeIf { it.isNotBlank() }?.let { "「$it」への" } ?: ""
                Text(text = "${inv.inviterEmail} さんから、${groupPart}招待があります")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = myName,
                    onValueChange = { acceptNames = acceptNames + (inv.id to it.take(20)) },
                    label = { Text("あなたの名前（このグループの仲間に表示されます）") },
                    supportingText = { Text("呼ばれたい名前を入れてください。あとから変えられます。") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (full) {
                    Text(text = "グループは${MAX_GROUPS}つまでです。どれかを抜けると、参加できます。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    enabled = !isLoading && !full && myName.isNotBlank(),
                    onClick = {
                        send(
                            source = "accept_group_invite",
                            successMessage = "仲間に加わりました。",
                            failMessage = "参加できませんでした。もう一度お試しください。",
                            build = { put("invite_id", inv.id); put("nickname", myName.trim()) }
                        )
                    }
                ) { Text("参加する") }
            }
        }
    }

    // ---- 入っているグループ ----
    groups.forEach { g ->
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = groupLabel(g), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (g.members.isEmpty()) {
                    Text(text = "まだほかの仲間はいません。", style = MaterialTheme.typography.bodySmall)
                }
                g.members.forEach { m ->
                    val label = m.nickname.ifBlank { "名前未設定" }
                    val status = if (m.status == "active") "参加中" else "招待中（返事待ち）"
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "・$label（$status）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (m.status != "active" && m.mine) {
                            TextButton(enabled = !isLoading, onClick = { cancelTarget = g to m }) { Text("招待を取り消す") }
                        }
                    }
                    if (m.status != "active" && m.mine && m.email.isNotBlank()) {
                        Text(text = "   招待したメールアドレス：${m.email}（あなたにだけ見えます）", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameEdits[g.id] ?: "",
                    onValueChange = { nameEdits = nameEdits + (g.id to it.take(20)) },
                    label = { Text("あなたの名前") },
                    supportingText = { Text("このグループの仲間に表示される名前です。") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row2(
                    left = {
                        Button(
                            enabled = !isLoading && !nameEdits[g.id].isNullOrBlank(),
                            onClick = {
                                send(
                                    source = "set_my_nickname",
                                    successMessage = "名前を保存しました。",
                                    failMessage = "保存できませんでした。もう一度お試しください。",
                                    build = { put("nickname", nameEdits[g.id].orEmpty().trim()); put("group_id", g.id) }
                                )
                            }
                        ) { Text("名前を保存する") }
                    },
                    right = {
                        OutlinedButton(enabled = !isLoading, onClick = { leaveTarget = g }) { Text("グループを抜ける") }
                    }
                )
            }
        }
    }

    cancelTarget?.let { (g, m) ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("招待を取り消しますか？") },
            text = { Text("「${m.nickname.ifBlank { "名前未設定" }}」（${m.email}）への、「${groupLabel(g)}」の招待を、取り消します。メールアドレスを間違えたときなどに、使えます。取り消したあと、改めて、招待できます。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelTarget = null
                    isLoading = true
                    message = null
                    scope.launch {
                        try {
                            val res = supabase.postgrest.rpc("cancel_group_invite", buildJsonObject {
                                put("p_group_id", g.id)
                                put("p_email", m.email)
                            })
                            val n = res.data.trim().toIntOrNull() ?: 0
                            message = if (n > 0) "招待を取り消しました。" else "取り消せませんでした。招待できるのは、招待した本人だけです。"
                            refresh()
                        } catch (e: Exception) {
                            Log.e("FamilySection", "cancel invite failed", e)
                            message = "取り消せませんでした。もう一度お試しください。"
                        } finally {
                            isLoading = false
                        }
                    }
                }) { Text("取り消す") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("やめる") } }
        )
    }

    leaveTarget?.let { g ->
        AlertDialog(
            onDismissRequest = { leaveTarget = null },
            title = { Text("グループを抜けますか？") },
            text = {
                Text(
                    "「${groupLabel(g)}」を抜けると、このグループの予定や記念日が見えなくなります。" +
                        "ほかのメンバーには、抜けたことがメールで伝わります。" +
                        "もう一度加わるには、改めて招待してもらう必要があります。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    leaveTarget = null
                    send(
                        source = "leave_group",
                        successMessage = "グループを抜けました。",
                        failMessage = "抜けられませんでした。もう一度お試しください。",
                        build = { put("group_id", g.id) }
                    )
                }) { Text("抜ける") }
            },
            dismissButton = { TextButton(onClick = { leaveTarget = null }) { Text("やめる") } }
        )
    }

    // ---- 新しく招待する ----
    if (groups.isNotEmpty() || incomingInvites.isEmpty()) {
        Text(text = "仲間を招待する", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        val canCreate = groups.size < MAX_GROUPS
        if (groups.isNotEmpty()) {
            Text(text = "どのグループに招待しますか？", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    StrongChip(selected = inviteTarget == g.id, onClick = { inviteTarget = g.id }, label = { Text(groupLabel(g)) })
                }
                if (canCreate) {
                    StrongChip(selected = inviteTarget == NEW_GROUP, onClick = { inviteTarget = NEW_GROUP }, label = { Text("新しいグループ") })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val creating = inviteTarget == NEW_GROUP
        if (creating) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = { Text("グループ名（例: 大学の友だち、ヨガ仲間）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = myNameForNewGroup,
                onValueChange = { myNameForNewGroup = it.take(20) },
                label = { Text("あなたの名前（このグループの仲間に表示されます）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = newMemberNickname,
            onValueChange = { newMemberNickname = it },
            label = { Text("相手の呼び名（仮）（例: ゆうこ）") },
            supportingText = { Text("相手が参加するときに、自分の名前へ変えられます。") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newMemberEmail,
            onValueChange = { newMemberEmail = it.filter { c -> c.code in 33..126 } },
            label = { Text("仲間のメールアドレス") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !isLoading && newMemberEmail.isNotBlank() && newMemberNickname.isNotBlank() &&
                (!creating || myNameForNewGroup.isNotBlank()),
            onClick = {
                val target = inviteTarget
                send(
                    source = "invite_to_group",
                    successMessage = "招待を送りました。相手が参加すると、仲間になります。",
                    failMessage = "送信できませんでした。もう一度お試しください。",
                    build = {
                        put("invited_email", newMemberEmail)
                        put("nickname", newMemberNickname)
                        if (target == NEW_GROUP || target == null) {
                            put("group_name", newGroupName)
                            put("my_nickname", myNameForNewGroup.trim())
                        } else {
                            put("group_id", target)
                        }
                    },
                    onOk = {
                        newMemberNickname = ""
                        newMemberEmail = ""
                        newGroupName = ""
                        myNameForNewGroup = ""
                    }
                )
            }
        ) { Text("仲間に誘う") }
    }

    if (isLoading) {
        Spacer(modifier = Modifier.height(8.dp))
        CircularProgressIndicator()
    }
    message?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = it)
    }
}

@Composable
private fun Row2(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        left()
        right()
    }
}

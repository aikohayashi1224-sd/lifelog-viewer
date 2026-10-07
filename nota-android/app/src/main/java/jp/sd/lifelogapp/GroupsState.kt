package jp.sd.lifelogapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class GroupBrief(val id: String, val name: String?)

// 所属しているグループの一覧（アプリ全体で共有）。check_group の応答から更新する。
object GroupsState {
    var groups by mutableStateOf<List<GroupBrief>>(emptyList())
    var selectedId by mutableStateOf<String?>(null)

    fun updateFrom(json: JSONObject) {
        val arr = json.optJSONArray("groups")
        groups = if (arr != null) {
            (0 until arr.length()).map {
                val g = arr.getJSONObject(it)
                GroupBrief(g.optString("id"), g.optString("name").takeIf { n -> n.isNotBlank() })
            }
        } else {
            json.optJSONObject("group")?.let { g ->
                listOf(GroupBrief(g.optString("id"), g.optString("name").takeIf { n -> n.isNotBlank() }))
            } ?: emptyList()
        }
    }
}

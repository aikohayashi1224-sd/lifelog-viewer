package jp.sd.lifelogapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val SOON_DAYS = 60L           // 「そろそろ会いませんか」を出す間隔（日）
private const val SNOOZE_DAYS = 14L         // 「今回はパス」のあと、静かにする日数
private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

data class GroupEvent(val id: String, val title: String, val date: LocalDate, val yearly: Boolean, val note: String, val createdBy: String = "", val createdAt: String = "")
data class Attendee(val meetupId: String, val userId: String, val attended: Boolean)
private data class DayMark(val event: Boolean, val plan: Boolean, val met: Boolean, val missed: Boolean)
data class MeetupArea(val id: String, val meetupId: String, val name: String)
data class AreaAnswer(val areaId: String, val userId: String, val answer: String)
data class Wish(val meetupId: String, val userId: String, val kind: String, val value: String)
data class NoteEvt(val favoriteId: String, val createdBy: String, val createdAt: String)
data class Meetup(val id: String, val title: String, val status: String, val heldOn: LocalDate?, val place: String, val note: String, val shareToken: String = "", val heldTime: String = "", val createdBy: String = "", val createdAt: String = "", val venueName: String = "", val venueLink: String = "", val meetText: String = "", val cancelReason: String = "", val cancelledAt: String = "")
data class GuestAnswer(val dateId: String, val name: String, val answer: String)

data class MeetupDate(val id: String, val meetupId: String, val date: LocalDate, val label: String = "")
data class MeetupShop(val id: String, val meetupId: String, val name: String, val url: String, val note: String)
data class ShopVote(val shopId: String, val userId: String)
data class MeetupAnswer(val dateId: String, val userId: String, val answer: String)

// LINEなどに送る、決まったお知らせの文面（配慮メモは、含めない）
private const val CANCEL_DEFAULT = "今回は都合により取りやめます。次回よろしくお願いします。"

private fun Meetup.cancelText(): String = "【あわい】「${title.ifBlank { "会合" }}」は、取りやめになりました。\n幹事より：${cancelReason.ifBlank { CANCEL_DEFAULT }}"

private fun Meetup.announceText(): String = buildString {
    append("【あわい】「${title.ifBlank { "会合" }}」の日程が決まりました！\n")
    append("日時：${whenText()}\n")
    if (venueName.isNotBlank()) append("お店：${venueName}\n")
    if (venueLink.isNotBlank()) append("場所・URL：${venueLink}\n")
    else if (place.isNotBlank()) append("エリア：${place}\n")
    if (meetText.isNotBlank()) append("集合：${meetText}\n")
    append("\n詳しくは、あわいのアプリで、見てください。")
}

private fun Meetup.whenText(): String = (heldOn?.format(DATE_FMT) ?: "") + if (heldTime.isNotBlank()) "　$heldTime" else ""

private fun JSONObject.optDate(key: String): LocalDate? =
    if (isNull(key) || optString(key).isBlank()) null else LocalDate.parse(optString(key))

private fun parseEvents(arr: JSONArray): List<GroupEvent> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    GroupEvent(o.optString("id"), o.optString("title"), LocalDate.parse(o.getString("event_date")), o.optBoolean("repeat_yearly"), o.optString("note").let { n -> if (n == "null") "" else n }, o.optString("created_by"), o.optString("created_at"))
}

private fun parseDates(arr: JSONArray): List<MeetupDate> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    MeetupDate(o.optString("id"), o.optString("meetup_id"), LocalDate.parse(o.getString("candidate_date")), o.optString("start_time").let { n -> if (n == "null") "" else n })
}

private fun parseShops(arr: JSONArray): List<MeetupShop> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    MeetupShop(o.optString("id"), o.optString("meetup_id"), o.optString("shop_name"),
        o.optString("url").let { n -> if (n == "null") "" else n }, o.optString("note").let { n -> if (n == "null") "" else n })
}

private fun parseVotes(arr: JSONArray): List<ShopVote> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    ShopVote(o.optString("shop_id"), o.optString("user_id"))
}

private fun parseAttendees(arr: JSONArray): List<Attendee> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it); Attendee(o.optString("meetup_id"), o.optString("user_id"), o.optBoolean("attended", true))
}

private fun parseAreas(arr: JSONArray): List<MeetupArea> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it); MeetupArea(o.optString("id"), o.optString("meetup_id"), o.optString("name"))
}

private fun parseAreaAnswers(arr: JSONArray): List<AreaAnswer> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it); AreaAnswer(o.optString("area_id"), o.optString("user_id"), o.optString("answer"))
}

private fun parseWishes(arr: JSONArray): List<Wish> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it); Wish(o.optString("meetup_id"), o.optString("user_id"), o.optString("kind"), o.optString("value"))
}

private fun parseGuests(arr: JSONArray): List<GuestAnswer> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    GuestAnswer(o.optString("date_id"), o.optString("guest_name"), o.optString("answer"))
}

private fun parseAnswers(arr: JSONArray): List<MeetupAnswer> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    MeetupAnswer(o.optString("date_id"), o.optString("user_id"), o.optString("answer"))
}

private fun parseMeetups(arr: JSONArray): List<Meetup> = (0 until arr.length()).map {
    val o = arr.getJSONObject(it)
    Meetup(
        o.optString("id"), o.optString("title").let { n -> if (n == "null") "" else n }, o.optString("status"), o.optDate("held_on"),
        o.optString("place_text").let { n -> if (n == "null") "" else n }, o.optString("note").let { n -> if (n == "null") "" else n },
        o.optString("share_token").let { n -> if (n == "null") "" else n },
        o.optString("held_time").let { n -> if (n == "null") "" else n },
        o.optString("created_by").let { n -> if (n == "null") "" else n },
        o.optString("created_at"),
        o.optString("venue_name").let { n -> if (n == "null") "" else n },
        o.optString("venue_link").let { n -> if (n == "null") "" else n },
        o.optString("meet_text").let { n -> if (n == "null") "" else n },
        o.optString("cancel_reason").let { n -> if (n == "null") "" else n },
        o.optString("cancelled_at").let { n -> if (n == "null") "" else n }
    )
}

// 次にその記念日が来る日（毎年の記念日は、今年か来年。単発は、まだ先なら、その日）
private fun instantOf(s: String): java.time.Instant? = runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull()

private fun agoText(createdAt: String, today: LocalDate): String {
    val inst = instantOf(createdAt) ?: return ""
    val days = ChronoUnit.DAYS.between(inst.atZone(JST).toLocalDate(), today)
    return when {
        days <= 0 -> "今日"
        days == 1L -> "昨日"
        else -> "${days}日前"
    }
}

private fun nextOccurrence(e: GroupEvent, today: LocalDate): LocalDate? {
    if (!e.yearly) return if (e.date.isBefore(today)) null else e.date
    var d = e.date.withYear(today.year)
    if (d.isBefore(today)) d = e.date.withYear(today.year + 1)
    return d
}

private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("community_prefs", Context.MODE_PRIVATE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityScreen(mode: Int, onGo: (Int) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups = GroupsState.groups

    val current = groups.firstOrNull { it.id == GroupsState.selectedId } ?: groups.firstOrNull()

    var events by remember { mutableStateOf<List<GroupEvent>>(emptyList()) }
    var meetups by remember { mutableStateOf<List<Meetup>>(emptyList()) }
    var dates by remember { mutableStateOf<List<MeetupDate>>(emptyList()) }
    var answers by remember { mutableStateOf<List<MeetupAnswer>>(emptyList()) }
    var guests by remember { mutableStateOf<List<GuestAnswer>>(emptyList()) }
    var shops by remember { mutableStateOf<List<MeetupShop>>(emptyList()) }
    var votes by remember { mutableStateOf<List<ShopVote>>(emptyList()) }
    var favs by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var shopFor by remember { mutableStateOf<Meetup?>(null) }
    var noteFor by remember { mutableStateOf<Meetup?>(null) }
    var announceFor by remember { mutableStateOf<Meetup?>(null) }
    var cancelReasonFor by remember { mutableStateOf<Meetup?>(null) }
    var cancelAnnounce by remember { mutableStateOf<Meetup?>(null) }
    var venueFor by remember { mutableStateOf<Meetup?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var decideDraft by remember { mutableStateOf<Triple<Meetup, LocalDate, String>?>(null) }
    var subTab by remember { mutableIntStateOf(0) }
    var albumNewestFirst by remember { mutableStateOf(true) }
    var albumViewIndex by remember { mutableStateOf<Int?>(null) }
    var areas by remember { mutableStateOf<List<MeetupArea>>(emptyList()) }
    var areaAnswers by remember { mutableStateOf<List<AreaAnswer>>(emptyList()) }
    var wishes by remember { mutableStateOf<List<Wish>>(emptyList()) }
    var addAreaFor by remember { mutableStateOf<Meetup?>(null) }
    var attendees by remember { mutableStateOf<List<Attendee>>(emptyList()) }
    var attendeeFor by remember { mutableStateOf<Meetup?>(null) }
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var favNoteEvents by remember { mutableStateOf<List<NoteEvt>>(emptyList()) }
    var photos by remember { mutableStateOf<List<MeetupPhoto>>(emptyList()) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var addPhotosFor by remember { mutableStateOf<Meetup?>(null) }
    var ym by remember { mutableStateOf(YearMonth.now(JST)) }
    var selDay by remember { mutableStateOf<LocalDate?>(LocalDate.now(JST)) }
    var detailFor by remember { mutableStateOf<Meetup?>(null) }
    var groupIcon by remember { mutableStateOf<String?>(null) }
    var groupNameDb by remember { mutableStateOf<String?>(null) }
    var showGroupEdit by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var ringByGroup by remember { mutableStateOf<Map<String, List<RingMember>>>(emptyMap()) }
    var cancelTarget by remember { mutableStateOf<Meetup?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var snoozeTick by remember { mutableIntStateOf(0) }

    var showAddEvent by remember { mutableStateOf(false) }
    var showMet by remember { mutableStateOf(false) }
    var editMetFor by remember { mutableStateOf<Meetup?>(null) }
    var deletePhotoPath by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupEvent?>(null) }

    val userId = remember { supabase.auth.currentUserOrNull()?.id }

    fun applyData(json: JSONObject) {
        events = parseEvents(json.optJSONArray("events") ?: JSONArray())
        meetups = parseMeetups(json.optJSONArray("meetups") ?: JSONArray())
        dates = parseDates(json.optJSONArray("dates") ?: JSONArray())
        answers = parseAnswers(json.optJSONArray("answers") ?: JSONArray())
        guests = parseGuests(json.optJSONArray("guests") ?: JSONArray())
        val pa = json.optJSONArray("photos") ?: JSONArray()
        photos = (0 until pa.length()).map { val o = pa.getJSONObject(it); MeetupPhoto(o.optString("id"), o.optString("meetup_id"), o.optString("path"), o.optString("created_by"), o.optString("created_at")) }
        shops = parseShops(json.optJSONArray("shops") ?: JSONArray())
        votes = parseVotes(json.optJSONArray("votes") ?: JSONArray())
        attendees = parseAttendees(json.optJSONArray("attendees") ?: JSONArray())
        areas = parseAreas(json.optJSONArray("areas") ?: JSONArray())
        areaAnswers = parseAreaAnswers(json.optJSONArray("area_answers") ?: JSONArray())
        wishes = parseWishes(json.optJSONArray("wishes") ?: JSONArray())
        val fn = json.optJSONArray("favnotes") ?: JSONArray()
        favNoteEvents = (0 until fn.length()).map { val o = fn.getJSONObject(it); NoteEvt(o.optString("favorite_id"), o.optString("created_by"), o.optString("created_at")) }
        val fa = json.optJSONArray("favs") ?: JSONArray()
        favs = (0 until fa.length()).map {
            val o = fa.getJSONObject(it)
            Favorite(o.optString("id"), o.optString("shop_name"), "", o.optString("url").let { u -> if (u == "null") "" else u })
        }
    }

    suspend fun load(gid: String) {
        try {
            val ev = supabase.from("group_events").select { filter { eq("group_id", gid) } }
            val mt = supabase.from("meetups").select { filter { eq("group_id", gid) } }
            val meetupArr = JSONArray(mt.data)
            val openIds = parseMeetups(meetupArr).filter { it.status != "done" && it.status != "cancelled" }.map { it.id }
            var dateArr = JSONArray()
            var answerArr = JSONArray()
            var guestArr = JSONArray()
            if (openIds.isNotEmpty()) {
                dateArr = JSONArray(supabase.from("meetup_dates").select { filter { isIn("meetup_id", openIds) } }.data)
                val dateIds = parseDates(dateArr).map { it.id }
                if (dateIds.isNotEmpty()) {
                    answerArr = JSONArray(supabase.from("meetup_answers").select { filter { isIn("date_id", dateIds) } }.data)
                    guestArr = JSONArray(supabase.from("meetup_guest_answers").select { filter { isIn("date_id", dateIds) } }.data)
                }
            }
            var shopArr = JSONArray()
            var voteArr = JSONArray()
            if (openIds.isNotEmpty()) {
                shopArr = JSONArray(supabase.from("meetup_shops").select { filter { isIn("meetup_id", openIds) } }.data)
                val shopIds = parseShops(shopArr).map { it.id }
                if (shopIds.isNotEmpty()) {
                    voteArr = JSONArray(supabase.from("meetup_shop_votes").select { filter { isIn("shop_id", shopIds) } }.data)
                }
            }
            val favArr = JSONArray(supabase.from("group_favorites").select { filter { eq("group_id", gid) } }.data)
            var areaArr = JSONArray()
            var areaAnsArr = JSONArray()
            var wishArr = JSONArray()
            val activeIds = parseMeetups(meetupArr).filter { it.status != "done" && it.status != "cancelled" }.map { it.id }
            if (activeIds.isNotEmpty()) {
                try {
                    areaArr = JSONArray(supabase.from("meetup_areas").select { filter { isIn("meetup_id", activeIds) } }.data)
                    val aids = parseAreas(areaArr).map { it.id }
                    if (aids.isNotEmpty()) areaAnsArr = JSONArray(supabase.from("meetup_area_answers").select { filter { isIn("area_id", aids) } }.data)
                    wishArr = JSONArray(supabase.from("meetup_wishes").select { filter { isIn("meetup_id", activeIds) } }.data)
                } catch (e: Exception) { Log.e("CommunityScreen", "areas/wishes failed", e) }
            }
            val allMeetupIds = parseMeetups(meetupArr).map { it.id }
            val attendeeArr = if (allMeetupIds.isEmpty()) JSONArray()
            else try { JSONArray(supabase.from("meetup_attendees").select { filter { isIn("meetup_id", allMeetupIds) } }.data) } catch (e: Exception) { JSONArray() }
            val favIds = (0 until favArr.length()).map { favArr.getJSONObject(it).optString("id") }
            val favNoteArr = if (favIds.isEmpty()) JSONArray()
            else try { JSONArray(supabase.from("favorite_notes").select { filter { isIn("favorite_id", favIds) } }.data) } catch (e: Exception) { JSONArray() }
            val allIds = parseMeetups(meetupArr).map { it.id }
            val photoArr = if (allIds.isEmpty()) JSONArray()
            else try { JSONArray(supabase.from("meetup_photos").select { filter { isIn("meetup_id", allIds) } }.data) } catch (e: Exception) { Log.e("CommunityScreen", "photos failed", e); JSONArray() }
            val json = JSONObject().put("events", JSONArray(ev.data)).put("meetups", meetupArr)
                .put("dates", dateArr).put("answers", answerArr)
                .put("shops", shopArr).put("votes", voteArr).put("favs", favArr).put("guests", guestArr).put("photos", photoArr).put("favnotes", favNoteArr)
                .put("areas", areaArr).put("area_answers", areaAnsArr).put("wishes", wishArr).put("attendees", attendeeArr)
            applyData(json)
            userId?.let { UiCache.put(context, it, "community:$gid", json.toString()) }
        } catch (e: Exception) {
            Log.e("CommunityScreen", "load failed", e)
            message = "読み込めませんでした。電波の良い場所で、もう一度お試しください。"
        }
    }

    LaunchedEffect(current?.id) {
        val gid = current?.id ?: return@LaunchedEffect
        events = emptyList(); meetups = emptyList()
        groupIcon = null; groupNameDb = null
        try {
            val g = JSONArray(supabase.from("groups").select { filter { eq("id", gid) } }.data)
            if (g.length() > 0) {
                groupIcon = g.getJSONObject(0).optString("icon").takeIf { it.isNotBlank() && it != "null" }
                groupNameDb = g.getJSONObject(0).optString("name").takeIf { it.isNotBlank() && it != "null" }
            }
        } catch (e: Exception) { Log.e("CommunityScreen", "group info failed", e) }
        try {
            val gm = JSONArray(supabase.from("group_members").select { filter { eq("group_id", gid) } }.data)
            OwnerState.id = (0 until gm.length()).map { gm.getJSONObject(it) }
                .filter { it.optString("joined_at").isNotBlank() && it.optString("joined_at") != "null" }
                .minByOrNull { it.optString("joined_at") }?.optString("user_id")
            memberNames = (0 until gm.length()).associate { i ->
                val o = gm.getJSONObject(i)
                o.optString("user_id") to o.optString("nickname").let { n -> if (n == "null") "" else n }
            }.filterValues { it.isNotBlank() }
        } catch (e: Exception) { Log.e("CommunityScreen", "member names failed", e) }
        userId?.let { uid ->
            try { UiCache.get(context, uid, "community:$gid")?.let { applyData(JSONObject(it)) } } catch (e: Exception) { Log.e("CommunityScreen", "cache failed", e) }
        }
        load(gid)
    }

    val addPhotosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        val target = addPhotosFor
        addPhotosFor = null
        if (target != null && uris.isNotEmpty()) {
            loading = true
            message = null
            scope.launch {
                try {
                    uploadMeetupPhotos(context, target.id, uris)
                    current?.id?.let { load(it) }
                } catch (e: Exception) {
                    Log.e("CommunityScreen", "add photos failed", e)
                    message = "写真を送れませんでした。もう一度お試しください。"
                } finally { loading = false }
            }
        }
    }

    fun applyRing(json: JSONObject) {
        val arr = json.optJSONArray("groups") ?: return
        ringByGroup = (0 until arr.length()).associate { gi ->
            val g = arr.getJSONObject(gi)
            val ms = g.optJSONArray("members") ?: JSONArray()
            g.optString("id") to (0 until ms.length()).map { i ->
                val o = ms.getJSONObject(i)
                val isSelf = o.optBoolean("is_self")
                val label = o.optString("nickname").ifBlank { "名前未設定" }
                RingMember(
                    label = label,
                    initial = if (isSelf) "私" else label.take(1).ifBlank { "?" },
                    isSelf = isSelf,
                    loggedToday = false,
                    avatarEmoji = o.optString("avatar_emoji"),
                    avatarImage = decodeAvatarBase64(o.optString("avatar_image")),
                    userId = o.optString("user_id")
                )
            }.sortedByDescending { it.isSelf }
        }
    }

    LaunchedEffect(Unit) {
        userId?.let { uid ->
            try { UiCache.get(context, uid, "group_today")?.let { applyRing(JSONObject(it)) } } catch (e: Exception) { Log.e("CommunityScreen", "ring cache failed", e) }
        }
        try {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null && userId != null) {
                val body = JSONObject().apply {
                    put("source", "get_group_today")
                    put("app", "awai")
                    put("access_token", session.accessToken)
                    put("user_id", userId)
                }.toString()
                val text = postToGas(body)
                val json = JSONObject(text)
                if (json.optString("status") == "ok") {
                    UiCache.put(context, userId, "group_today", text)
                    applyRing(json)
                }
            }
        } catch (e: Exception) {
            Log.e("CommunityScreen", "ring load failed", e)
        }
    }

    var surveySentAt by remember { mutableStateOf<String?>(null) }

    fun fire(failMessage: String, block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: Exception) {
                Log.e("CommunityScreen", failMessage, e)
                message = failMessage
                current?.id?.let { load(it) }
            }
        }
    }

    fun runAction(failMessage: String, block: suspend () -> Unit) {
        loading = true
        message = null
        scope.launch {
            try {
                block()
                current?.id?.let { load(it) }
            } catch (e: Exception) {
                Log.e("CommunityScreen", failMessage, e)
                message = failMessage
            } finally {
                loading = false
            }
        }
    }

    val today = LocalDate.now(JST)
    // あなたが参加した会合か（参加者の記録が無い会合は、参加したものとして扱う）
    fun attendedBy(m: Meetup): Boolean =
        attendees.none { it.meetupId == m.id } || attendees.any { it.meetupId == m.id && it.userId == userId && it.attended }
    val memberList: List<Pair<String, String>> = run {
        val map = LinkedHashMap<String, String>()
        userId?.let { map[it] = memberNames[it] ?: "あなた" }
        memberNames.forEach { (k, v) -> if (k != userId) map[k] = v }
        map.toList()
    }
    suspend fun saveAttendees(meetupId: String, attended: Set<String>) {
        supabase.from("meetup_attendees").delete { filter { eq("meetup_id", meetupId) } }
        if (memberList.isNotEmpty()) {
            supabase.from("meetup_attendees").insert(memberList.map { (uid, _) ->
                buildJsonObject { put("meetup_id", meetupId); put("user_id", uid); put("attended", uid in attended) }
            })
        }
    }
    val lastMet = meetups.filter { it.status == "done" && attendedBy(it) }.mapNotNull { it.heldOn }.maxOrNull()
    val daysSince = lastMet?.let { ChronoUnit.DAYS.between(it, today) }
    val snoozeUntil = remember(current?.id, snoozeTick) { current?.id?.let { prefs(context).getLong("snooze_$it", 0L) } ?: 0L }
    val showSoon = current != null && (daysSince == null || daysSince >= SOON_DAYS) && today.toEpochDay() >= snoozeUntil

    val upcomingEvents = events.mapNotNull { e -> nextOccurrence(e, today)?.let { d -> e to d } }.sortedBy { it.second }
    val recentDone = meetups.filter { it.status == "done" && it.heldOn != null && ChronoUnit.DAYS.between(it.heldOn, today) in 0..45 }
        .sortedByDescending { it.heldOn }
    val planning = meetups.filter { it.status == "planning" }
    val decided = meetups.filter { it.status == "decided" && it.heldOn != null }.sortedBy { it.heldOn }

    val renderExtras: @Composable (Meetup) -> Unit = { m ->
        PlanningExtras(
            meetup = m,
            isOrganizer = m.createdBy == userId,
            myId = userId,
            memberCount = ringByGroup[current?.id ?: ""]?.size ?: memberNames.size,
            names = memberNames,
            areas = areas.filter { it.meetupId == m.id },
            areaAnswers = areaAnswers,
            wishes = wishes.filter { it.meetupId == m.id },
            sentAt = surveySentAt,
            onAddArea = { addAreaFor = m },
            onDeleteArea = { id ->
                areas = areas.filterNot { it.id == id }
                fire("エリアを消せませんでした。") { supabase.from("meetup_areas").delete { filter { eq("id", id) } } }
            },
            onChooseArea = { name ->
                meetups = meetups.map { if (it.id == m.id) it.copy(place = name) else it }
                fire("エリアを決められませんでした。") {
                    supabase.from("meetups").update(buildJsonObject { put("place_text", name) }) { filter { eq("id", m.id) } }
                }
            },
            onSubmit = { d ->
                val uid = userId
                if (uid != null) {
                    val areaIds = areas.filter { it.meetupId == m.id }.map { it.id }
                    val newWishes = buildList {
                        if (d.any) add(Wish(m.id, uid, "area_any", "yes"))
                        d.budget?.let { add(Wish(m.id, uid, "budget", it)) }
                        d.genres.forEach { add(Wish(m.id, uid, "genre", it)) }
                        if (d.areaFree.isNotBlank()) add(Wish(m.id, uid, "area_free", d.areaFree))
                        if (d.genreFree.isNotBlank()) add(Wish(m.id, uid, "genre_free", d.genreFree))
                        if (d.idea.isNotBlank()) add(Wish(m.id, uid, "shop_idea", d.idea))
                    }
                    areaAnswers = areaAnswers.filterNot { it.userId == uid && it.areaId in areaIds } + d.areaAnswers.map { AreaAnswer(it.key, uid, it.value) }
                    wishes = wishes.filterNot { it.meetupId == m.id && it.userId == uid } + newWishes
                    surveySentAt = java.time.LocalTime.now(JST).format(DateTimeFormatter.ofPattern("H:mm"))
                    fire("希望を送れませんでした。もう一度お試しください。") {
                        if (areaIds.isNotEmpty()) {
                            supabase.from("meetup_area_answers").delete { filter { isIn("area_id", areaIds); eq("user_id", uid) } }
                            if (d.areaAnswers.isNotEmpty()) {
                                supabase.from("meetup_area_answers").insert(d.areaAnswers.map { (areaId, ans) ->
                                    buildJsonObject { put("area_id", areaId); put("user_id", uid); put("answer", ans) }
                                })
                            }
                        }
                        supabase.from("meetup_wishes").delete { filter { eq("meetup_id", m.id); eq("user_id", uid) } }
                        if (newWishes.isNotEmpty()) {
                            supabase.from("meetup_wishes").insert(newWishes.map { w ->
                                buildJsonObject { put("meetup_id", m.id); put("user_id", uid); put("kind", w.kind); put("value", w.value) }
                            })
                        }
                    }
                }
            }
        )
    }

    // 幹事向け：まだ答えていない人と、「返事をお願いする」
    val renderNudge: @Composable (Meetup) -> Unit = { m ->
        val ds = dates.filter { it.meetupId == m.id }
        val answered = answers.filter { a -> ds.any { it.id == a.dateId } }.map { it.userId }.toSet()
        val waiting = memberList.filter { (uid, _) -> uid != userId && uid !in answered }.map { it.second }
        if (m.createdBy == userId && m.status == "planning") {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "返事の状況", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (waiting.isEmpty()) {
                        Text(text = "アプリのメンバーは、みんな答えています。", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(text = "まだ答えていない人：" + waiting.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(onClick = {
                            val url = "https://aikohayashi1224-sd.github.io/lifelog-viewer/reply.html?t=${m.shareToken}"
                            val text = "${waiting.joinToString("、") { "${it}さん" }}、「${m.title.ifBlank { "会合" }}」の日程の返事を、お願いできますか？ 行ける日を、教えてください。アプリを入れなくても、ここから答えられます。\n$url"
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(send, "返事をお願いする").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("返事をお願いする（LINEなどで送る）") }
                    }
                }
            }
        }
    }

    val renderPlanning: @Composable (Meetup) -> Unit = { m ->
            PlanningCard(
                meetup = m,
                dates = dates.filter { it.meetupId == m.id }.sortedBy { it.date },
                answers = answers,
                myId = userId,
                onAnswer = { dateId, answer ->
                    answers = answers.filterNot { it.dateId == dateId && it.userId == userId } + MeetupAnswer(dateId, userId ?: "", answer)
                    fire("回答を送れませんでした。もう一度お試しください。") {
                        supabase.from("meetup_answers").delete { filter { eq("date_id", dateId); eq("user_id", userId ?: "") } }
                        supabase.from("meetup_answers").insert(buildJsonObject {
                            put("date_id", dateId)
                            put("user_id", userId ?: "")
                            put("answer", answer)
                        })
                    }
                },
                onDecide = { date ->
                    decideDraft = Triple(m, date, dates.firstOrNull { it.meetupId == m.id && it.date == date }?.label ?: "")
                },
                onCancel = { cancelTarget = m },
                isOrganizer = m.createdBy == userId,
                organizerTag = { OrganizerTag(m.createdBy, userId, memberNames, ringByGroup[current?.id ?: ""]) },
                guests = guests,
                onShare = {
                    val url = "https://aikohayashi1224-sd.github.io/lifelog-viewer/reply.html?t=${m.shareToken}"
                    val text = "${m.title.ifBlank { "会合" }}の日程を相談しています。アプリを入れなくても、ここから答えられます。\n$url"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "返事リンクを送る").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                shops = shops.filter { it.meetupId == m.id },
                votes = votes,
                onVote = { shopId ->
                    val mine = votes.any { it.shopId == shopId && it.userId == userId }
                    votes = if (mine) votes.filterNot { it.shopId == shopId && it.userId == userId } else votes + ShopVote(shopId, userId ?: "")
                    fire("投票できませんでした。もう一度お試しください。") {
                        if (mine) {
                            supabase.from("meetup_shop_votes").delete { filter { eq("shop_id", shopId); eq("user_id", userId ?: "") } }
                        } else {
                            supabase.from("meetup_shop_votes").insert(buildJsonObject {
                                put("shop_id", shopId)
                                put("user_id", userId ?: "")
                            })
                        }
                    }
                },
                onAddShop = { shopFor = m },
                onEditNote = { noteFor = m }
            )
    }

    val who: (String) -> String = { uid -> if (uid == userId) "あなた" else (memberNames[uid]?.let { "${it}さん" } ?: "仲間") }
    val albumItems: List<AlbumItem> = run {
        val inGroup = photos.mapNotNull { ph -> meetups.firstOrNull { it.id == ph.meetupId }?.let { AlbumItem(ph, it) } }
        val asc = inGroup.sortedWith(compareBy<AlbumItem> { it.meetup.heldOn ?: LocalDate.MIN }.thenBy { it.photo.createdAt })
        if (albumNewestFirst) asc.reversed() else asc
    }
    val titleText = when (mode) { 1 -> "プランニング"; 2 -> "カレンダー"; else -> "タイムライン" }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 上部（3つのページ共通）：ページ名、設定、グループ ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = titleText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "設定") }
                if (InviteBadge.count > 0) {
                    Box(modifier = Modifier.size(12.dp).align(Alignment.TopEnd).background(androidx.compose.ui.graphics.Color(0xFFB3262E), androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
        if (current != null) {
            if (groups.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEachIndexed { i, g ->
                        StrongChip(selected = g.id == current.id, onClick = { GroupsState.selectedId = g.id }, label = { Text(g.name ?: "グループ${i + 1}") })
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondary, androidx.compose.foundation.shape.CircleShape)
                        .clickable { showGroupEdit = true },
                    contentAlignment = Alignment.Center
                ) { Text(text = groupIcon ?: "👥", fontSize = 24.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = groupNameDb ?: current.name ?: "グループ", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showMembers = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("メンバー ${ringByGroup[current.id]?.size ?: 0}人 ＞")
                    }
                }
                TextButton(onClick = { showGroupEdit = true }) { Text("変更") }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (current == null) {
            Text(
                text = "まだグループがありません。右上の設定で仲間を招待すると、ここで、会う約束や記念日を、いっしょに管理できます。",
                style = MaterialTheme.typography.bodyMedium
            )
            if (InviteBadge.count > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenSettings) { Text("届いている招待を見る") }
            }
        } else if (mode == 2 && subTab == 2) {
            // ---- カレンダー：アルバム ----
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrongChip(selected = false, onClick = { subTab = 0 }, label = { Text("カレンダー") })
                StrongChip(selected = false, onClick = { subTab = 1 }, label = { Text("記念日の一覧") })
                StrongChip(selected = true, onClick = { subTab = 2 }, label = { Text("アルバム") })
            }
            Spacer(modifier = Modifier.height(4.dp))
            AlbumSection(
                items = albumItems,
                newestFirst = albumNewestFirst,
                onToggleOrder = { albumNewestFirst = !albumNewestFirst },
                onTap = { albumViewIndex = it },
                modifier = Modifier.weight(1f)
            )
        } else if (mode == 1 && subTab == 1) {
            // ---- プランニング：行きつけ帳 ----
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrongChip(selected = false, onClick = { subTab = 0 }, label = { Text("企画") })
                StrongChip(selected = true, onClick = { subTab = 1 }, label = { Text("行きつけ帳") })
            }
            FavoritesSection(groupId = current.id, onBack = null, modifier = Modifier.weight(1f))
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp)) {
                when (mode) {
                    // ================= タイムライン =================
                    0 -> {
                        val myUnanswered = planning.firstOrNull { m ->
                            dates.filter { it.meetupId == m.id }.none { d -> answers.any { it.dateId == d.id && it.userId == userId } }
                        }
                        val nextDecided = decided.firstOrNull { ChronoUnit.DAYS.between(today, it.heldOn) in 0..14 }
                        val soonEvents = upcomingEvents.filter { (_, d) -> ChronoUnit.DAYS.between(today, d) in 0..31 }
                        val memories = meetups.filter { m ->
                            m.status == "done" && m.heldOn != null && m.heldOn.year < today.year &&
                                ChronoUnit.DAYS.between(m.heldOn.withYear(today.year), today) in -3..3
                        }

                        if (InviteBadge.count > 0) {
                            TLCard("招待が届いています", "仲間のグループへの招待が届いています。", true, "招待を見る", onOpenSettings)
                        }
                        meetups.filter { cm ->
                            cm.status == "cancelled" && instantOf(cm.cancelledAt)?.let { t -> ChronoUnit.DAYS.between(t.atZone(JST).toLocalDate(), today) in 0..7 } == true
                        }.forEach { cm ->
                            TLCard(
                                "「${cm.title.ifBlank { "会合" }}」は、取りやめになりました",
                                "${who(cm.createdBy)}（幹事）より：${cm.cancelReason.ifBlank { CANCEL_DEFAULT }}",
                                true, "詳しく見る", { detailFor = cm }
                            )
                        }
                        if (showSoon) {
                            TLCard(
                                "そろそろ会いませんか？",
                                if (daysSince == null) "まだ「会えた」の記録がありません。最近、みんなで会えましたか？"
                                else "最後に会ってから、約${(daysSince / 30).coerceAtLeast(1)}か月（${lastMet?.format(DATE_FMT)}）です。",
                                true, "日にちを相談する（プランニング）", { onGo(1) }, nota = NotaKind.BOW
                            ) {
                                OutlinedButton(onClick = { showMet = true }) { Text("会えた日を記録") }
                                OutlinedButton(onClick = {
                                    prefs(context).edit().putLong("snooze_${current.id}", today.toEpochDay() + SNOOZE_DAYS).apply()
                                    snoozeTick++
                                }) { Text("今回はパス") }
                            }
                        }
                        if (myUnanswered != null) {
                            TLCard("みんなが、あなたの返事を待っています", "「${myUnanswered.title.ifBlank { "会合" }}」の日程を、相談中です。行ける日を、教えてください。", true, "答える", { detailFor = myUnanswered })
                        }
                        if (nextDecided != null) {
                            val left = ChronoUnit.DAYS.between(today, nextDecided.heldOn)
                            TLCard(
                                if (left == 0L) "今日は、会合です" else if (left <= 7) "あと${left}日で、会合です" else "${left}日後に、会合があります",
                                "${nextDecided.title.ifBlank { "会合" }}　${nextDecided.whenText()}", false, "詳しく見る", { detailFor = nextDecided }
                            )
                        }
                        soonEvents.forEach { (e, d) ->
                            val left = ChronoUnit.DAYS.between(today, d)
                            TLCard(
                                when {
                                    left == 0L -> "今日は「${e.title}」です"
                                    left <= 7 -> "${left}日後に「${e.title}」があります"
                                    d.monthValue != today.monthValue -> "来月は「${e.title}」の記念日です"
                                    else -> "今月の${d.dayOfMonth}日は「${e.title}」です"
                                },
                                d.format(DATE_FMT) + if (e.yearly) "（毎年）" else "", false, null, null
                            )
                        }
                        memories.forEach { m ->
                            val years = today.year - m.heldOn!!.year
                            TLCard(
                                "${years}年前の今ごろは、会えた日でした",
                                m.heldOn.format(DATE_FMT) + if (m.note.isNotBlank()) "　${m.note}" else "",
                                false, "写真を振り返る", { detailFor = m }
                            )
                        }

                        // ---- 流れ（グループの出来事） ----
                        val feed = buildList<Triple<java.time.Instant, String, (() -> Unit)?>> {
                            meetups.forEach { m ->
                                val t = instantOf(m.createdAt) ?: return@forEach
                                val title = m.title.ifBlank { "会合" }
                                if (m.status == "done") add(Triple(t, "${who(m.createdBy)}が、会えた日（${m.heldOn?.format(DATE_FMT) ?: ""}）を記録しました", { detailFor = m }))
                                else add(Triple(t, "${who(m.createdBy)}が、「${title}」の日程相談を始めました", { detailFor = m }))
                                if (m.status == "cancelled") {
                                    val ct = instantOf(m.cancelledAt)
                                    if (ct != null) add(Triple(ct, "${who(m.createdBy)}（幹事）が、「${title}」を取りやめました。${m.cancelReason.ifBlank { CANCEL_DEFAULT }}", { detailFor = m }))
                                }
                            }
                            events.forEach { e ->
                                val t = instantOf(e.createdAt) ?: return@forEach
                                add(Triple(t, "${who(e.createdBy)}が、記念日「${e.title}」を追加しました", null))
                            }
                            photos.groupBy { Triple(it.meetupId, it.createdBy, it.createdAt.take(13)) }.forEach { (key, list) ->
                                val t = instantOf(list.first().createdAt) ?: return@forEach
                                val m = meetups.firstOrNull { it.id == key.first }
                                add(Triple(t, "${who(key.second)}が、写真を${list.size}枚、追加しました", if (m != null) ({ detailFor = m }) else null))
                            }
                            favNoteEvents.forEach { n ->
                                val t = instantOf(n.createdAt) ?: return@forEach
                                val shop = favs.firstOrNull { it.id == n.favoriteId }?.name ?: "お店"
                                add(Triple(t, "${who(n.createdBy)}が、行きつけ帳の「${shop}」に、ひとことを書きました", null))
                            }
                        }.sortedByDescending { it.first }
                            .filter { ChronoUnit.DAYS.between(it.first.atZone(JST).toLocalDate(), today) <= 90 }
                            .take(40)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "みんなの出来事", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (feed.isEmpty()) {
                            Text(text = "まだ出来事はありません。プランニングで日にちを相談したり、カレンダーに記念日を足したりすると、ここに、みんなの動きが流れます。", style = MaterialTheme.typography.bodySmall)
                        }
                        feed.forEach { (t, text, click) ->
                            TimelineRow(badge = agoText(t.toString(), today), title = text, sub = "", onClick = click)
                        }
                    }

                    // ================= プランニング =================
                    1 -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StrongChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("企画") })
                            StrongChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("行きつけ帳") })
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showPlan = true }) { Text("日にちを相談する") }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (planning.isEmpty() && decided.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Nota(NotaKind.SLEEP, 150.dp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = if (NotaState.visible) "進めている企画は、ありません。\nのたろんは、そばで、待っています。" else "進めている企画は、ありません。", style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "「日にちを相談する」から、始められます。決まったあとの会合は、カレンダーに残ります。", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        planning.forEach { m ->
                            val ds = dates.filter { it.meetupId == m.id }
                            val answeredUsers = answers.filter { a -> ds.any { it.id == a.dateId } }.map { it.userId }.toSet().size +
                                guests.filter { g -> ds.any { it.id == g.dateId } }.map { it.name }.toSet().size
                            val mine = ds.any { d -> answers.any { it.dateId == d.id && it.userId == userId } }
                            MeetupRow(
                                badge = "相談中",
                                title = m.title.ifBlank { "会合" },
                                sub = "${answeredUsers}人が回答済み" + if (mine) "" else "　（あなたは、まだです）",
                                organizer = { OrganizerTag(m.createdBy, userId, memberNames, ringByGroup[current.id]) },
                                onClick = { detailFor = m }
                            )
                        }
                        decided.forEach { m ->
                            val left = ChronoUnit.DAYS.between(today, m.heldOn)
                            MeetupRow(
                                badge = if (left > 0) "あと${left}日" else if (left == 0L) "今日" else "終了",
                                title = m.title.ifBlank { "会合" } + "（決定）",
                                sub = m.whenText(),
                                organizer = { OrganizerTag(m.createdBy, userId, memberNames, ringByGroup[current.id]) },
                                onClick = { detailFor = m }
                            )
                        }
                    }

                    // ================= カレンダー =================
                    else -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAddEvent = true }) { Text("記念日を足す") }
                            OutlinedButton(onClick = { showMet = true }) { Text("会えた日を記録") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StrongChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("カレンダー") })
                            StrongChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("記念日の一覧") })
                            StrongChip(selected = subTab == 2, onClick = { subTab = 2 }, label = { Text("アルバム") })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (subTab == 0) {
                            GroupCalendar(
                                ym = ym,
                                onPrev = { ym = ym.minusMonths(1) },
                                onNext = { ym = ym.plusMonths(1) },
                                selected = selDay,
                                onSelect = { selDay = it },
                                marks = { d ->
                                    DayMark(
                                        events.any { e -> if (e.yearly) e.date.monthValue == d.monthValue && e.date.dayOfMonth == d.dayOfMonth else e.date == d },
                                        meetups.any { (it.status == "planning" || it.status == "decided") && it.heldOn == d },
                                        meetups.any { it.status == "done" && it.heldOn == d && attendedBy(it) },
                                        meetups.any { it.status == "done" && it.heldOn == d && !attendedBy(it) }
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            selDay?.let { d ->
                                Text(text = d.format(DATE_FMT), style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                val dayEvents = events.filter { e -> if (e.yearly) e.date.monthValue == d.monthValue && e.date.dayOfMonth == d.dayOfMonth else e.date == d }
                                val dayMeetups = meetups.filter { it.heldOn == d && (it.status == "done" || it.status == "decided") }
                                if (dayEvents.isEmpty() && dayMeetups.isEmpty()) Text(text = "この日の予定や記録は、ありません。", style = MaterialTheme.typography.bodySmall)
                                dayEvents.forEach { e -> TimelineRow(badge = "記念日", title = e.title, sub = e.note, onClick = { deleteTarget = e }) }
                                dayMeetups.forEach { m ->
                                    val n = photos.count { it.meetupId == m.id }
                                    TimelineRow(
                                        badge = if (m.status == "done") "会えた" else "予定",
                                        title = m.title.ifBlank { "会合" },
                                        sub = m.whenText() + if (n > 0) "　写真${n}枚" else "",
                                        onClick = { detailFor = m }
                                    )
                                }
                            }
                        } else {
                            if (upcomingEvents.isEmpty()) {
                                Text(
                                    text = "記念日は、まだありません。「記念日を足す」から、卒業式の日、誰かの誕生日、お祭りの日など、会うきっかけになる日を、登録してみましょう。",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            upcomingEvents.forEach { (e, d) ->
                                val left = ChronoUnit.DAYS.between(today, d)
                                TimelineRow(
                                    badge = if (left == 0L) "今日" else "あと${left}日",
                                    title = e.title,
                                    sub = buildString {
                                        append(d.format(DATE_FMT))
                                        if (e.yearly) append("（毎年）")
                                        if (e.note.isNotBlank()) append("　${e.note}")
                                    },
                                    onClick = { deleteTarget = e }
                                )
                            }
                        }
                    }
                }

                if (loading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = it)
                }
            }
        }
    }

    if (showGroupEdit && current != null) {
        GroupEditDialog(
            initialIcon = groupIcon ?: "",
            initialName = groupNameDb ?: current.name ?: "",
            onDismiss = { showGroupEdit = false },
            onSave = { icon, name ->
                showGroupEdit = false
                runAction("グループの情報を保存できませんでした。もう一度お試しください。") {
                    supabase.from("groups").update(buildJsonObject {
                        put("icon", icon)
                        put("name", name)
                    }) { filter { eq("id", current.id) } }
                    groupIcon = icon.ifBlank { null }
                    groupNameDb = name.ifBlank { null }
                    try { fetchGroupStatus(context) } catch (e: Exception) { Log.e("CommunityScreen", "refresh groups failed", e) }
                }
            }
        )
    }
    if (showMembers && current != null) {
        MembersDialog(members = ringByGroup[current.id] ?: emptyList(), onDismiss = { showMembers = false })
    }

    attendeeFor?.let { m ->
        val decidedDateRow = dates.firstOrNull { it.meetupId == m.id && it.date == m.heldOn }
        val yesIds = decidedDateRow?.let { d -> answers.filter { it.dateId == d.id && it.answer == "yes" }.map { it.userId }.toSet() } ?: emptySet()
        var chosen by remember(m.id) { mutableStateOf(if (yesIds.isEmpty()) memberList.map { it.first }.toSet() else yesIds) }
        var placeText by remember(m.id) { mutableStateOf(m.place) }
        AlertDialog(
            onDismissRequest = { attendeeFor = null },
            title = { Text("会えた！ 参加した人は？") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = "◯と答えた人が、最初に「参加」になっています。参加しなかった人にも、「会合があった」ことは、残ります。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = placeText, onValueChange = { placeText = it.take(60) }, singleLine = true,
                        label = { Text("場所（任意）　例：新宿のイタリアン") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AttendanceRows(memberList, chosen) { chosen = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = chosen
                    val placeValue = placeText.trim()
                    attendeeFor = null
                    runAction("記録できませんでした。もう一度お試しください。") {
                        supabase.from("meetups").update(buildJsonObject { put("status", "done"); put("place_text", placeValue) }) { filter { eq("id", m.id) } }
                        saveAttendees(m.id, ids)
                    }
                }) { Text("記録する") }
            },
            dismissButton = { TextButton(onClick = { attendeeFor = null }) { Text("やめる") } }
        )
    }

    addAreaFor?.let { m ->
        var name by remember(m.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addAreaFor = null },
            title = { Text("候補のエリアを足す") },
            text = {
                Column {
                    Text(text = "「新宿界隈」「〇〇線沿線」「渋谷駅の近く」など、自由に書けます。いくつでも足せます。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("エリア") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val v = name.trim()
                    addAreaFor = null
                    runAction("エリアを足せませんでした。もう一度お試しください。") {
                        supabase.from("meetup_areas").insert(buildJsonObject { put("meetup_id", m.id); put("name", v) })
                    }
                }) { Text("足す") }
            },
            dismissButton = { TextButton(onClick = { addAreaFor = null }) { Text("やめる") } }
        )
    }

    albumViewIndex?.let { idx ->
        if (albumItems.isEmpty()) { albumViewIndex = null } else {
            AlbumViewer(
                items = albumItems,
                startIndex = idx,
                infoLines = { item ->
                    val mt = item.meetup
                    val lines = mutableListOf<String>()
                    if (mt.place.isNotBlank()) lines += "場所：${mt.place}"
                    if (mt.note.isNotBlank()) lines += mt.note
                    val att = attendees.filter { it.meetupId == mt.id && it.attended }
                    if (att.isNotEmpty()) lines += "参加：" + att.joinToString("、") { memberNames[it.userId] ?: if (it.userId == userId) "あなた" else "仲間" }
                    lines
                },
                canDelete = { item -> item.photo.createdBy == userId || item.meetup.createdBy == userId },
                onOpenRecord = { mt -> detailFor = mt },
                onDelete = { p -> deletePhotoPath = p },
                onDismiss = { albumViewIndex = null }
            )
        }
    }
    viewerPath?.let { vp ->
        val ph = photos.firstOrNull { it.path == vp }
        val canDelete = ph != null && (ph.createdBy == userId || meetups.firstOrNull { it.id == ph.meetupId }?.createdBy == userId)
        PhotoViewerDialog(vp, onDismiss = { viewerPath = null }, onDelete = if (canDelete) ({ deletePhotoPath = vp }) else null)
    }

    // ---- 会合の詳細（全画面） ----
    detailFor?.let { shown ->
        val m = meetups.firstOrNull { it.id == shown.id }
        if (m == null) { detailFor = null } else {
            Dialog(onDismissRequest = { detailFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        TextButton(onClick = { detailFor = null }) { Text("＜ 戻る") }
                        if (m.status == "planning") {
                            renderPlanning(m)
                            renderNudge(m)
                            renderExtras(m)
                        } else if (m.status == "cancelled") {
                            Text(text = "取りやめになりました", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            Text(text = m.title.ifBlank { "会合" }, style = MaterialTheme.typography.headlineSmall)
                            OrganizerTag(m.createdBy, userId, memberNames, ringByGroup[current?.id ?: ""])
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "幹事からのひとこと", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(text = m.cancelReason.ifBlank { CANCEL_DEFAULT }, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "また、日にちを相談したくなったら、プランニングから、新しく企画できます。", style = MaterialTheme.typography.bodyMedium)
                        } else if (m.status == "done") {
                            Text(text = "会えた日", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(text = m.heldOn?.format(DATE_FMT) ?: "", style = MaterialTheme.typography.headlineSmall)
                            if (m.place.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "場所：${m.place}", style = MaterialTheme.typography.bodyLarge)
                            }
                            if (m.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = m.note, style = MaterialTheme.typography.bodyLarge)
                            }
                            val att = attendees.filter { it.meetupId == m.id }
                            if (att.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "参加：" + att.filter { it.attended }.joinToString("、") { memberNames[it.userId] ?: if (it.userId == userId) "あなた" else "仲間" }.ifBlank { "なし" }, style = MaterialTheme.typography.bodyMedium)
                                val absent = att.filter { !it.attended }
                                if (absent.isNotEmpty()) Text(text = "不参加：" + absent.joinToString("、") { memberNames[it.userId] ?: if (it.userId == userId) "あなた" else "仲間" }, style = MaterialTheme.typography.bodyMedium)
                                Text(text = if (attendedBy(m)) "あなたは、参加しました。" else "あなたは、この日は不参加でした。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (m.createdBy == userId) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { editMetFor = m }) { Text("日付・場所・ひとこと・参加者を直す") }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val mine = photos.filter { it.meetupId == m.id }
                            if (mine.isEmpty()) Text(text = "まだ写真はありません。", style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                mine.forEach { ph -> PhotoThumb(ph.path, 104.dp) { viewerPath = ph.path } }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                addPhotosFor = m
                                addPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("写真を足す（最大6枚ずつ）") }
                            if (mine.isNotEmpty()) Text(text = "写真をタップして開くと、「この写真を消す」が出ます（足した人と、幹事が消せます）。", style = MaterialTheme.typography.bodySmall)
                            if (loading) { Spacer(modifier = Modifier.height(8.dp)); CircularProgressIndicator() }
                            message?.let { Text(text = it) }
                        } else {
                            val left = ChronoUnit.DAYS.between(today, m.heldOn)
                            Text(text = m.title.ifBlank { "会合" }, style = MaterialTheme.typography.headlineSmall)
                            OrganizerTag(m.createdBy, userId, memberNames, ringByGroup[current?.id ?: ""])
                            Text(text = m.whenText() + if (left > 0) "（あと${left}日）" else "", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (m.place.isNotBlank()) Text(text = "エリア：${m.place}", style = MaterialTheme.typography.bodyMedium)
                            if (m.note.isNotBlank()) Text(text = "配慮が必要なこと：${m.note}", style = MaterialTheme.typography.bodyMedium)
                            shops.filter { it.meetupId == m.id }.maxByOrNull { s -> votes.count { it.shopId == s.id } }?.let { top ->
                                Text(text = "お店の候補（いちばん人気）：${top.name}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "幹事からの連絡", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    if (m.venueName.isBlank() && m.venueLink.isBlank() && m.meetText.isBlank()) {
                                        Text(
                                            text = if (m.createdBy == userId) "お店・時間・集合の連絡を入れると、メンバーに、表示されます。時間は、お店に確認してから、直せます。" else "お店と集合の連絡は、幹事が入れると、ここに表示されます。時間が、変わることもあります。",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (m.venueName.isNotBlank()) Text(text = "お店：${m.venueName}", style = MaterialTheme.typography.bodyLarge)
                                    if (m.venueLink.isNotBlank()) Text(text = "場所・URL：${m.venueLink}", style = MaterialTheme.typography.bodyMedium)
                                    if (m.meetText.isNotBlank()) Text(text = "集合：${m.meetText}", style = MaterialTheme.typography.bodyLarge)
                                    if (m.createdBy == userId) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { venueFor = m }) { Text("お店・時間・集合を入れる／直す") }
                                            OutlinedButton(onClick = {
                                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.announceText()) }
                                                context.startActivity(Intent.createChooser(send, "みんなに知らせる").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }) { Text("LINEなどで知らせる") }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (m.createdBy == userId) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = "予約のときに確認すること（幹事用）", style = MaterialTheme.typography.labelLarge)
                                        Text(text = "・席のタイプ（個室・半個室・テーブル・座敷）\n・席の時間制限\n・予約できる時間（決まったら、「幹事からの連絡」で、時間を直せます）\n・キャンセル規定（キャンセル料）", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = if (m.createdBy == userId) "あなたが幹事です。" else "この予定の変更や、取りやめは、幹事ができます。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (m.createdBy == userId) OutlinedButton(onClick = { noteFor = m }) { Text("配慮が必要なことをメモ") }
                                if (left <= 0 && m.createdBy == userId) {
                                    Button(onClick = { detailFor = null; attendeeFor = m }) { Text("会えた！") }
                                }
                                if (m.createdBy == userId) TextButton(onClick = { detailFor = null; cancelTarget = m }) { Text("取りやめる") }
                            }
                            if (m.createdBy == userId) renderExtras(m)
                        }
                    }
                }
            }
        }
    }

    // ---- ダイアログ ----
    if (showPlan && current != null) {
        PlanMeetupDialog(
            onDismiss = { showPlan = false },
            onSave = { title, candidates ->
                showPlan = false
                runAction("会合を作れませんでした。もう一度お試しください。") {
                    val created = supabase.from("meetups").insert(buildJsonObject {
                        put("group_id", current.id)
                        put("title", title)
                        put("status", "planning")
                    }) { select() }
                    val meetupId = JSONArray(created.data).getJSONObject(0).getString("id")
                    supabase.from("meetup_dates").insert(candidates.map { (d, label) ->
                        buildJsonObject {
                            put("meetup_id", meetupId)
                            put("candidate_date", d.toString())
                            put("start_time", label)
                        }
                    })
                }
            }
        )
    }
    decideDraft?.let { (m, date, label) ->
        val areaIdSet = areas.filter { it.meetupId == m.id }.map { it.id }.toSet()
        val answeredIds = areaAnswers.filter { it.areaId in areaIdSet }.map { it.userId }.toSet() + wishes.filter { it.meetupId == m.id }.map { it.userId }.toSet()
        val notAnswered = memberList.filter { it.first !in answeredIds && it.first != m.createdBy }.map { it.second }
        DecideDialog(
            dateText = date.format(DATE_FMT),
            initialTime = label,
            notAnswered = notAnswered,
            onDismiss = { decideDraft = null },
            onSave = { time ->
                decideDraft = null
                runAction("決定できませんでした。もう一度お試しください。") {
                    supabase.from("meetups").update(buildJsonObject {
                        put("status", "decided")
                        put("held_on", date.toString())
                        put("held_time", time)
                    }) { filter { eq("id", m.id) } }
                    announceFor = m.copy(status = "decided", heldOn = date, heldTime = time)
                }
            }
        )
    }
    announceFor?.let { am ->
        AlertDialog(
            onDismissRequest = { announceFor = null },
            title = { Text("日程が決まりました") },
            text = {
                Text("アプリの画面にも、表示されます。ただ、見落とす人もいるので、LINEなどで、ひと声かけておくと、安心です。お店や集合場所が決まったら、詳細の画面から、もう一度、知らせることもできます。")
            },
            confirmButton = {
                TextButton(onClick = {
                    announceFor = null
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, am.announceText()) }
                    context.startActivity(Intent.createChooser(send, "みんなに知らせる").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("LINEなどで知らせる") }
            },
            dismissButton = { TextButton(onClick = { announceFor = null }) { Text("あとで") } }
        )
    }
    venueFor?.let { vm ->
        var vTime by remember(vm.id) { mutableStateOf(vm.heldTime) }
        var vName by remember(vm.id) { mutableStateOf(vm.venueName.ifBlank { shops.filter { it.meetupId == vm.id }.maxByOrNull { s -> votes.count { it.shopId == s.id } }?.name ?: "" }) }
        var vLink by remember(vm.id) { mutableStateOf(vm.venueLink) }
        var vMeet by remember(vm.id) { mutableStateOf(vm.meetText) }
        fun save(thenAnnounce: Boolean) {
            val n = vName.trim(); val l = vLink.trim(); val mt = vMeet.trim(); val tm = vTime.trim()
            venueFor = null
            val updated = vm.copy(venueName = n, venueLink = l, meetText = mt, heldTime = tm)
            meetups = meetups.map { if (it.id == vm.id) updated else it }
            if (thenAnnounce) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, updated.announceText()) }
                context.startActivity(Intent.createChooser(send, "みんなに知らせる").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            fire("保存できませんでした。もう一度お試しください。") {
                supabase.from("meetups").update(buildJsonObject {
                    put("venue_name", n); put("venue_link", l); put("meet_text", mt); put("held_time", tm)
                }) { filter { eq("id", vm.id) } }
            }
        }
        AlertDialog(
            onDismissRequest = { venueFor = null },
            title = { Text("お店・時間・集合の連絡") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = "ここに入れると、メンバーの詳細画面に、表示されます。時間は、お店に確認してから、決め直せます。「保存して知らせる」で、LINEなどにも、送れます。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = vTime, onValueChange = { vTime = it.take(20) }, singleLine = true, label = { Text("時間（例：17:00、夕方）") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = vName, onValueChange = { vName = it.take(60) }, singleLine = true, label = { Text("お店の名前") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = vLink, onValueChange = { vLink = it.take(300) }, label = { Text("お店の場所（住所、地図やお店のURL）") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = vMeet, onValueChange = { vMeet = it.take(120) }, label = { Text("集合（例：18:50 新宿駅東口の改札前）") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { save(false) }) { Text("保存") }
                    TextButton(onClick = { save(true) }) { Text("保存して知らせる") }
                }
            },
            dismissButton = { TextButton(onClick = { venueFor = null }) { Text("やめる") } }
        )
    }
    shopFor?.let { m ->
        AddShopForMeetupDialog(
            favorites = favs,
            onDismiss = { shopFor = null },
            onSave = { name, url, note ->
                shopFor = null
                runAction("お店の候補を足せませんでした。もう一度お試しください。") {
                    supabase.from("meetup_shops").insert(buildJsonObject {
                        put("meetup_id", m.id)
                        put("shop_name", name)
                        put("url", url)
                        put("note", note)
                    })
                }
            }
        )
    }
    noteFor?.let { m ->
        EditNoteDialog(
            initial = m.note,
            onDismiss = { noteFor = null },
            onSave = { text ->
                noteFor = null
                runAction("メモを保存できませんでした。もう一度お試しください。") {
                    supabase.from("meetups").update(buildJsonObject { put("note", text) }) { filter { eq("id", m.id) } }
                }
            }
        )
    }
    cancelTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("本当に取りやめますか？") },
            text = { Text("「${m.title.ifBlank { "会合" }}」を取りやめると、メンバー全員のタイムラインに、取りやめたことが、表示されます。次の画面で、みんなへのひとことを、添えられます。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelTarget = null
                    cancelReasonFor = m
                }) { Text("はい、取りやめる") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("やめる") } }
        )
    }
    cancelReasonFor?.let { cm ->
        var useFree by remember(cm.id) { mutableStateOf(false) }
        var free by remember(cm.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelReasonFor = null },
            title = { Text("みんなへのひとこと") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Nota(NotaKind.BOW, 84.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(text = "取りやめの理由を、ひとこと添えると、みんなが、安心します。タイムラインに、表示されます。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StrongChip(selected = !useFree, onClick = { useFree = false }, label = { Text("定型文") })
                        StrongChip(selected = useFree, onClick = { useFree = true }, label = { Text("自分の言葉で書く") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!useFree) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text(text = CANCEL_DEFAULT, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
                        }
                    } else {
                        OutlinedTextField(
                            value = free, onValueChange = { free = it.take(200) }, minLines = 3,
                            label = { Text("みんなへのひとこと") }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !useFree || free.isNotBlank(), onClick = {
                    val reason = if (useFree) free.trim() else CANCEL_DEFAULT
                    cancelReasonFor = null
                    runAction("取りやめられませんでした。もう一度お試しください。") {
                        supabase.from("meetups").update(buildJsonObject {
                            put("status", "cancelled")
                            put("cancel_reason", reason)
                            put("cancelled_at", java.time.Instant.now().toString())
                        }) { filter { eq("id", cm.id) } }
                        cancelAnnounce = cm.copy(status = "cancelled", cancelReason = reason)
                    }
                }) { Text("取りやめを知らせる") }
            },
            dismissButton = { TextButton(onClick = { cancelReasonFor = null }) { Text("やめる") } }
        )
    }
    cancelAnnounce?.let { ca ->
        AlertDialog(
            onDismissRequest = { cancelAnnounce = null },
            title = { Text("取りやめを、記録しました") },
            text = { Text("メンバーのタイムラインに、表示されます。LINEなどでも、ひと声かけておくと、安心です。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelAnnounce = null
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ca.cancelText()) }
                    context.startActivity(Intent.createChooser(send, "みんなに知らせる").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("LINEなどで知らせる") }
            },
            dismissButton = { TextButton(onClick = { cancelAnnounce = null }) { Text("あとで") } }
        )
    }
    if (showAddEvent && current != null) {
        AddEventDialog(
            onDismiss = { showAddEvent = false },
            onSave = { title, date, yearly, note ->
                showAddEvent = false
                runAction("記念日を保存できませんでした。もう一度お試しください。") {
                    supabase.from("group_events").insert(buildJsonObject {
                        put("group_id", current.id)
                        put("title", title)
                        put("event_date", date.toString())
                        put("repeat_yearly", yearly)
                        put("note", note)
                    })
                }
            }
        )
    }
    if (showMet && current != null) {
        MetDialog(
            members = memberList,
            onDismiss = { showMet = false },
            onSave = { date, place, note, uris, attendedIds ->
                showMet = false
                runAction("記録できませんでした。もう一度お試しください。") {
                    val created = supabase.from("meetups").insert(buildJsonObject {
                        put("group_id", current.id)
                        put("title", "会えた")
                        put("status", "done")
                        put("held_on", date.toString())
                        put("place_text", place)
                        put("note", note)
                    }) { select() }
                    val meetupId = JSONArray(created.data).getJSONObject(0).getString("id")
                    saveAttendees(meetupId, attendedIds)
                    if (uris.isNotEmpty()) uploadMeetupPhotos(context, meetupId, uris)
                }
            }
        )
    }
    editMetFor?.let { em ->
        val cur = attendees.filter { it.meetupId == em.id }
        MetDialog(
            members = memberList,
            title = "会えた日の記録を直す",
            initialDate = em.heldOn ?: LocalDate.now(JST),
            initialNote = em.note,
            initialPlace = em.place,
            initialAttended = if (cur.isEmpty()) null else cur.filter { it.attended }.map { it.userId }.toSet(),
            allowPhotos = false,
            confirmLabel = "直す",
            onDismiss = { editMetFor = null },
            onSave = { date, place, note, _, attendedIds ->
                editMetFor = null
                runAction("直せませんでした。もう一度お試しください。") {
                    supabase.from("meetups").update(buildJsonObject {
                        put("held_on", date.toString())
                        put("place_text", place)
                        put("note", note)
                    }) { filter { eq("id", em.id) } }
                    saveAttendees(em.id, attendedIds)
                }
            }
        )
    }
    deletePhotoPath?.let { dp ->
        AlertDialog(
            onDismissRequest = { deletePhotoPath = null },
            title = { Text("この写真を消しますか？") },
            text = { Text("グループのみんなの画面から、消えます。元には戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    deletePhotoPath = null
                    viewerPath = null
                    albumViewIndex = null
                    runAction("消せませんでした。もう一度お試しください。") { deleteMeetupPhoto(dp) }
                }) { Text("消す") }
            },
            dismissButton = { TextButton(onClick = { deletePhotoPath = null }) { Text("やめる") } }
        )
    }
    deleteTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("この記念日を削除しますか？") },
            text = { Text("「${e.title}」を、グループのみんなの画面から消します。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    runAction("削除できませんでした。もう一度お試しください。") {
                        supabase.from("group_events").delete { filter { eq("id", e.id) } }
                    }
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("やめる") } }
        )
    }
}

@Composable
private fun TimelineRow(badge: String, title: String, sub: String, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(64.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (sub.isNotBlank()) Text(text = sub, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label：${date.format(DATE_FMT)}") }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("やめる") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun AddEventDialog(onDismiss: () -> Unit, onSave: (String, LocalDate, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now(JST)) }
    var yearly by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("記念日を足す") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                    label = { Text("名前（例: 卒業式の日、〇〇さんの誕生日）") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                DatePickField("日付", date) { date = it }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("毎年くり返す")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, singleLine = true,
                    label = { Text("メモ（任意）") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), date, yearly, note.trim()) }) { Text("保存する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

// 参加した人・しなかった人を、ひと目で分かるように、1人ずつ、「参加」「不参加」のボタンで選ぶ
@Composable
private fun AttendanceRows(members: List<Pair<String, String>>, attended: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(text = "参加した人・しなかった人を、名前の右のボタンで、選んでください。", style = MaterialTheme.typography.bodySmall)
    Text(
        text = "参加 ${members.count { it.first in attended }}人　／　不参加 ${members.count { it.first !in attended }}人",
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    members.forEach { (uid, name) ->
        val on = uid in attended
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (on) Button(onClick = {}, contentPadding = pad) { Text("参加") }
            else OutlinedButton(onClick = { onChange(attended + uid) }, contentPadding = pad) { Text("参加") }
            Spacer(modifier = Modifier.width(6.dp))
            if (!on) Button(onClick = {}, contentPadding = pad, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)) { Text("不参加") }
            else OutlinedButton(onClick = { onChange(attended - uid) }, contentPadding = pad) { Text("不参加") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetDialog(
    members: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, List<Uri>, Set<String>) -> Unit,
    title: String = "会えた日を記録する",
    initialDate: LocalDate = LocalDate.now(JST),
    initialNote: String = "",
    initialPlace: String = "",
    initialAttended: Set<String>? = null,
    allowPhotos: Boolean = true,
    confirmLabel: String = "記録する"
) {
    var attended by remember { mutableStateOf(initialAttended ?: members.map { it.first }.toSet()) }
    var date by remember { mutableStateOf(initialDate) }
    var note by remember { mutableStateOf(initialNote) }
    var place by remember { mutableStateOf(initialPlace) }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { picked -> if (picked.isNotEmpty()) uris = picked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DatePickField("会えた日", date) { date = it }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = place, onValueChange = { place = it.take(60) }, singleLine = true,
                    label = { Text("場所（任意）　例：新宿のイタリアン") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(120) },
                    label = { Text("ひとこと（任意）") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (members.size > 1) {
                    AttendanceRows(members, attended) { attended = it }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (allowPhotos) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (uris.isEmpty()) "写真を選ぶ（最大6枚）" else "写真を選び直す（${uris.size}枚）")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, place.trim(), note.trim(), uris, attended) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningCard(
    meetup: Meetup,
    dates: List<MeetupDate>,
    answers: List<MeetupAnswer>,
    myId: String?,
    onAnswer: (String, String) -> Unit,
    onDecide: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    isOrganizer: Boolean,
    organizerTag: @Composable () -> Unit,
    guests: List<GuestAnswer>,
    onShare: () -> Unit,
    shops: List<MeetupShop>,
    votes: List<ShopVote>,
    onVote: (String) -> Unit,
    onAddShop: () -> Unit,
    onEditNote: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "日程を相談中", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = meetup.title.ifBlank { "会合" }, style = MaterialTheme.typography.titleSmall)
            organizerTag()
            Text(text = "行ける日に ◯、行けるかも なら △、難しければ × を押してください。あとから、変えられます。", style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (isOrganizer) "あなたが幹事です。日にちを決めたり、取りやめたりできます。" else "日にちを決めるのは、幹事です。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            dates.forEach { d ->
                val forDate = answers.filter { it.dateId == d.id }
                val mine = forDate.firstOrNull { it.userId == myId }?.answer
                val gFor = guests.filter { it.dateId == d.id }
                val yes = forDate.count { it.answer == "yes" } + gFor.count { it.answer == "yes" }
                val maybe = forDate.count { it.answer == "maybe" } + gFor.count { it.answer == "maybe" }
                val no = forDate.count { it.answer == "no" } + gFor.count { it.answer == "no" }
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(text = d.date.format(DATE_FMT) + if (d.label.isNotBlank()) "　${d.label}" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "◯ ${yes}人　△ ${maybe}人　× ${no}人", style = MaterialTheme.typography.bodySmall)
                    if (gFor.isNotEmpty()) {
                        Text(
                            text = "アプリを使わない人：" + gFor.joinToString("、") { g -> g.name + when (g.answer) { "yes" -> "◯"; "maybe" -> "△"; else -> "×" } },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("yes" to "◯", "maybe" to "△", "no" to "×").forEach { (key, label) ->
                            StrongChip(selected = mine == key, onClick = { onAnswer(d.id, key) }, label = { Text(label) })
                        }
                        if (isOrganizer) TextButton(onClick = { onDecide(d.date) }) { Text("この日に決める") }
                    }
                }
            }
            Text(text = "お店の候補", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (shops.isEmpty()) Text(text = "まだ候補がありません。行ってみたいお店を足してみましょう。", style = MaterialTheme.typography.bodySmall)
            shops.forEach { s ->
                val count = votes.count { it.shopId == s.id }
                val mine = votes.any { it.shopId == s.id && it.userId == myId }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = s.name, style = MaterialTheme.typography.bodyLarge)
                        if (s.url.isNotBlank()) Text(text = s.url, style = MaterialTheme.typography.bodySmall)
                        if (s.note.isNotBlank()) Text(text = s.note, style = MaterialTheme.typography.bodySmall)
                    }
                    StrongChip(selected = mine, onClick = { onVote(s.id) }, label = { Text("いいね ${count}") })
                }
            }
            if (meetup.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "配慮が必要なこと：${meetup.note}", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare) { Text("返事リンクを送る") }
                OutlinedButton(onClick = onAddShop) { Text("お店の候補を足す") }
                if (isOrganizer) OutlinedButton(onClick = onEditNote) { Text("配慮が必要なことをメモ") }
                if (isOrganizer) TextButton(onClick = onCancel) { Text("取りやめる") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddShopForMeetupDialog(favorites: List<Favorite>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("お店の候補を足す") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (favorites.isNotEmpty()) {
                    Text(text = "行きつけ帳から選ぶ", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        favorites.forEach { f ->
                            StrongChip(selected = name == f.name, onClick = { name = f.name; url = f.url }, label = { Text(f.name) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("お店の名前") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it.filter { c -> c.code in 33..126 }.take(300) }, singleLine = true, label = { Text("お店のページのURL（任意）") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it.take(120) }, label = { Text("ひとこと（任意）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), url.trim(), note.trim()) }) { Text("足す") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

@Composable
private fun EditNoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配慮が必要なこと") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(200) },
                label = { Text("例: 骨折中の人がいるので、エレベーターとテーブル席") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("保存する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanMeetupDialog(onDismiss: () -> Unit, onSave: (String, List<Pair<LocalDate, String>>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(listOf(LocalDate.now(JST).plusDays(14) to "")) }
    val slots = listOf("午前", "昼", "午後", "夕方", "夜")
    val scroll = rememberScrollState()
    // 候補日を足したときは、いちばん下まで自動でスクロールして、足した欄が見えるようにする
    LaunchedEffect(candidates.size) { scroll.animateScrollTo(scroll.maxValue) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
                Text(text = "日にちを相談する", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                        label = { Text("名前（例: 秋の飲み会）") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "候補の日（最大5つ）。同じ日の、午前と午後を、別の候補にもできます。時間帯は、ざっくりで大丈夫です。", style = MaterialTheme.typography.bodySmall)
                    candidates.forEachIndexed { i, (d, label) ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                DatePickField("候補${i + 1}", d) { picked -> candidates = candidates.toMutableList().also { it[i] = picked to label } }
                            }
                            if (candidates.size > 1) {
                                TextButton(onClick = { candidates = candidates.toMutableList().also { it.removeAt(i) } }) { Text("削除") }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            slots.forEach { s ->
                                StrongChip(selected = label == s, onClick = { candidates = candidates.toMutableList().also { it[i] = d to if (label == s) "" else s } }, label = { Text(s) })
                            }
                        }
                        OutlinedTextField(
                            value = label, onValueChange = { v -> candidates = candidates.toMutableList().also { it[i] = d to v.take(20) } }, singleLine = true,
                            label = { Text("時間帯（任意）例: 18時前後、11:00〜13:00") }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (candidates.size < 5) {
                        TextButton(onClick = { candidates = candidates + (candidates.last().first to "") }) { Text("候補日を足す") }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("やめる") }
                    Button(onClick = { onSave(title.trim(), candidates.distinctBy { it.first to it.second }) }) { Text("みんなに相談する") }
                }
            }
        }
    }
}


@Composable
private fun DecideDialog(dateText: String, initialTime: String, notAnswered: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var time by remember { mutableStateOf(initialTime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${dateText}に決める") },
        text = {
            Column {
                Text(text = "集合の時間を入れてください（お店が決まるまでは、「夕方」などざっくりでも大丈夫です。あとから、「幹事からの連絡」で、直せます）。", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Nota(NotaKind.THINK, 58.dp)
                            if (NotaState.visible) Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ご注意", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(text = "日程を決めると、事前アンケートは、締め切られて、変更できなくなります。", style = MaterialTheme.typography.bodySmall)
                        if (notAnswered.isNotEmpty()) {
                            Text(text = "まだ、事前アンケートに答えていない人：" + notAnswered.joinToString("、"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = time, onValueChange = { time = it.take(20) }, singleLine = true,
                    label = { Text("時間（例: 19:00、夜）") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(time.trim()) }) { Text("この日に決める") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupEditDialog(initialIcon: String, initialName: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var icon by remember { mutableStateOf(initialIcon) }
    var name by remember { mutableStateOf(initialName) }
    val picks = listOf("🎓", "🍶", "🍻", "🎣", "🎸", "⚽", "🏔", "📚", "🍽", "☕", "🎨", "🌸", "🏠", "🚶")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("グループのアイコンと名前") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = "どんな仲間かが、ひと目で分かる絵文字を選べます（同窓会、飲み仲間、釣り仲間など）。", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picks.forEach { e -> StrongChip(selected = icon == e, onClick = { icon = e }, label = { Text(e, fontSize = 22.sp) }) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon, onValueChange = { icon = firstGrapheme(it) }, singleLine = true,
                    label = { Text("ほかの絵文字（キーボードから）") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                    label = { Text("グループの名前") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(icon.trim(), name.trim()) }) { Text("保存する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } }
    )
}

@Composable
private fun MembersDialog(members: List<RingMember>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("メンバー（${members.size}人）") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (members.isEmpty()) Text("メンバーを読み込んでいます…")
                members.sortedByDescending { it.isSelf }.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        AvatarCircle(avatarSize = 44.dp, initial = m.initial, emoji = m.avatarEmoji, image = m.avatarImage, large = false, owner = OwnerState.isOwner(m.userId))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = m.label + if (m.isSelf) "（あなた）" else "", style = MaterialTheme.typography.bodyLarge)
                            if (OwnerState.isOwner(m.userId)) Text(text = "★ オーナー", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF8A6200))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "★は、オーナー（最初にグループを作った人）のしるしです。幹事は、会合を企画した人のことです。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun GroupCalendar(
    ym: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    marks: (LocalDate) -> DayMark
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onPrev) { Text("＜ 前月") }
        Text(text = "${ym.year}年${ym.monthValue}月", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onNext) { Text("翌月 ＞") }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("月", "火", "水", "木", "金", "土", "日").forEach {
            Text(text = it, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
    val first = ym.atDay(1)
    val offset = first.dayOfWeek.value - 1       // 月曜=0
    val total = ym.lengthOfMonth()
    val rows = (offset + total + 6) / 7
    for (r in 0 until rows) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - offset + 1
                Box(modifier = Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..total) {
                        val d = ym.atDay(dayNum)
                        val mk = marks(d)
                        val ev = mk.event
                        val plan = mk.plan
                        val met = mk.met
                        val missed = mk.missed
                        val isSel = d == selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable { onSelect(d) }
                        ) {
                            Text(text = "$dayNum", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (met) BubblePair(modifier = Modifier.size(width = 32.dp, height = 22.dp))
                                if (missed) BubbleHollow(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                if (plan) Text(text = "◯", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (ev) Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "重なる泡＝参加した会合　淡い輪＝不参加だった会合　◯＝決まった予定　●＝記念日", style = MaterialTheme.typography.bodySmall)
}

// タイムラインの、お知らせカード（重要なものは、色を付ける）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TLCard(
    title: String,
    body: String,
    important: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    nota: NotaKind? = null,
    extra: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = if (important) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = if (important) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nota != null && NotaState.visible) {
                    Nota(nota, 46.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = if (important) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
            if (body.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
            if (actionLabel != null && onAction != null || extra != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
                    extra?.invoke()
                }
            }
        }
    }
}

data class SurveyDraft(
    val areaAnswers: Map<String, String>,
    val any: Boolean,
    val budget: String?,
    val genres: Set<String>,
    val areaFree: String,
    val genreFree: String,
    val idea: String
)

@Composable
private fun OrganizerTag(createdBy: String, myId: String?, names: Map<String, String>, ring: List<RingMember>?) {
    val isMe = createdBy == myId
    val name = if (isMe) (names[createdBy] ?: "あなた") else (names[createdBy] ?: "仲間")
    val member = ring?.firstOrNull { it.userId == createdBy } ?: if (isMe) ring?.firstOrNull { it.isSelf } else null
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        AvatarCircle(avatarSize = 28.dp, initial = member?.initial ?: name.take(1), emoji = member?.avatarEmoji ?: "", image = member?.avatarImage, large = false, owner = OwnerState.isOwner(createdBy))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "幹事：" + name + if (isMe) "（あなた）" else "", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MeetupRow(badge: String, title: String, sub: String, organizer: @Composable () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = badge, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (sub.isNotBlank()) Text(text = sub, style = MaterialTheme.typography.bodySmall)
                organizer()
            }
        }
    }
}

// 事前アンケート（エリアとお店の希望を、1枚のカードにまとめ、いちばん下の1つのボタンで、まとめて送る）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningExtras(
    meetup: Meetup,
    isOrganizer: Boolean,
    myId: String?,
    memberCount: Int,
    names: Map<String, String>,
    areas: List<MeetupArea>,
    areaAnswers: List<AreaAnswer>,
    wishes: List<Wish>,
    sentAt: String?,
    onAddArea: () -> Unit,
    onDeleteArea: (String) -> Unit,
    onChooseArea: (String) -> Unit,
    onSubmit: (SurveyDraft) -> Unit
) {
    fun mineW(kind: String) = wishes.filter { it.userId == myId && it.kind == kind }.map { it.value }
    fun count(kind: String, value: String) = wishes.count { it.kind == kind && it.value == value }
    fun who(uid: String) = if (uid == myId) "あなた" else (names[uid] ?: "仲間")
    val areaIds = areas.map { it.id }.toSet()
    val mySig = (areaAnswers.filter { it.userId == myId && it.areaId in areaIds }.map { it.areaId + it.answer } + wishes.filter { it.userId == myId }.map { it.kind + it.value }).sorted().joinToString("|") + "#" + areaIds.size

    var areaDraft by remember(meetup.id, mySig) { mutableStateOf(areaAnswers.filter { it.userId == myId && it.areaId in areaIds }.associate { it.areaId to it.answer }) }
    var anyDraft by remember(meetup.id, mySig) { mutableStateOf("yes" in mineW("area_any")) }
    var budgetDraft by remember(meetup.id, mySig) { mutableStateOf(mineW("budget").firstOrNull()) }
    var genreDraft by remember(meetup.id, mySig) { mutableStateOf(mineW("genre").toSet()) }
    var areaFree by remember(meetup.id, mySig) { mutableStateOf(mineW("area_free").firstOrNull() ?: "") }
    var genreFree by remember(meetup.id, mySig) { mutableStateOf(mineW("genre_free").firstOrNull() ?: "") }
    var idea by remember(meetup.id, mySig) { mutableStateOf(mineW("shop_idea").firstOrNull() ?: "") }
    val locked = meetup.status != "planning"

    Spacer(modifier = Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "事前アンケート", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (locked) {
                Text(text = "日程が決まったため、事前アンケートは、締め切りました（変更できません）。幹事は、集まった希望を見て、お店を決めます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(text = "エリアとお店の希望が、あれば、答えてください。答えなくても、大丈夫です。日にちが決まったら、幹事が、具体的なお店を提案します。", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "※ 日程が決まると、事前アンケートは締め切られ、変更できなくなります。希望は、決まる前に、送ってください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // ---------- エリア ----------
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "エリアの希望", style = MaterialTheme.typography.titleSmall)
            if (meetup.place.isNotBlank()) Text(text = "決まったエリア：${meetup.place}", style = MaterialTheme.typography.bodyLarge)
            if (areas.isEmpty()) Text(text = if (isOrganizer) "候補のエリアを出すと、みんなが答えられます。" else "幹事が、候補のエリアを出します。", style = MaterialTheme.typography.bodySmall)
            areas.forEach { a ->
                val ans = areaAnswers.filter { it.areaId == a.id }
                val my = areaDraft[a.id]
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    Text(text = a.name, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "◎ ${ans.count { it.answer == "best" }}人　◯ ${ans.count { it.answer == "ok" }}人　△ ${ans.count { it.answer == "far" }}人", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("best" to "◎ ここがいい", "ok" to "◯ どこでも", "far" to "△ 遠い").forEach { (k, label) ->
                            StrongChip(selected = my == k, onClick = { areaDraft = if (my == k) areaDraft - a.id else areaDraft + (a.id to k) }, label = { Text(label) }, enabled = !locked)
                        }
                        if (isOrganizer) {
                            TextButton(onClick = { onChooseArea(a.name) }) { Text("このエリアにする") }
                            if (!locked) TextButton(onClick = { onDeleteArea(a.id) }) { Text("削除") }
                        }
                    }
                }
            }
            if (isOrganizer && !locked) OutlinedButton(onClick = onAddArea) { Text("候補のエリアを足す") }
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StrongChip(selected = anyDraft, onClick = { anyDraft = !anyDraft }, label = { Text("エリアは、幹事におまかせ（${count("area_any", "yes")}人）") }, enabled = !locked)
            }
            OutlinedTextField(
                value = areaFree, onValueChange = { areaFree = it.take(60) }, singleLine = true, enabled = !locked,
                label = { Text("ほかの希望（任意）例: 〇〇線沿線、新宿界隈") }, modifier = Modifier.fillMaxWidth()
            )
            wishes.filter { it.kind == "area_free" }.forEach { w -> Text(text = "・${w.value}（${who(w.userId)}）", style = MaterialTheme.typography.bodySmall) }

            // ---------- お店 ----------
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "お店の希望", style = MaterialTheme.typography.titleSmall)
            Text(text = "予算の上限（1人）", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("3000" to "3000円以下", "5000" to "5000円以下", "any" to "幹事に任せる").forEach { (k, label) ->
                    StrongChip(selected = budgetDraft == k, onClick = { budgetDraft = if (budgetDraft == k) null else k }, label = { Text("${label}（${count("budget", k)}人）") }, enabled = !locked)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "ジャンル（いくつでも）", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("和食", "洋食", "中華", "カフェ").forEach { g ->
                    StrongChip(selected = g in genreDraft, onClick = { genreDraft = if (g in genreDraft) genreDraft - g else genreDraft + g }, label = { Text("${g}（${count("genre", g)}人）") }, enabled = !locked)
                }
            }
            OutlinedTextField(
                value = genreFree, onValueChange = { genreFree = it.take(40) }, singleLine = true, enabled = !locked,
                label = { Text("そのほかのジャンル（任意）例: 焼き鳥、イタリアン") }, modifier = Modifier.fillMaxWidth()
            )
            wishes.filter { it.kind == "genre_free" }.forEach { w -> Text(text = "・${w.value}（${who(w.userId)}）", style = MaterialTheme.typography.bodySmall) }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = idea, onValueChange = { idea = it.take(80) }, singleLine = true, enabled = !locked,
                label = { Text("行きたいお店（任意）名前やURL") }, modifier = Modifier.fillMaxWidth()
            )
            wishes.filter { it.kind == "shop_idea" }.forEach { w -> Text(text = "・${w.value}（${who(w.userId)}）", style = MaterialTheme.typography.bodySmall) }

            if (memberCount >= 6) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "お店選びのヒント", style = MaterialTheme.typography.labelLarge)
                        if (memberCount >= 10) Text(text = "・${memberCount}人いるので、個室を検討すると、話しやすくなります。", style = MaterialTheme.typography.bodySmall)
                        Text(text = "・6人以上のときは、飲み放題つきのコースにすると、割り勘が楽です。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---------- 送信（カードのいちばん下に、1つだけ） ----------
            Spacer(modifier = Modifier.height(14.dp))
            if (!locked) Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSubmit(SurveyDraft(areaDraft, anyDraft, budgetDraft, genreDraft, areaFree.trim(), genreFree.trim(), idea.trim())) }
            ) { Text("希望を送る") }
            if (!locked) sentAt?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "✓ 送信しました（${it}）。あとから、変えて、送り直せます。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

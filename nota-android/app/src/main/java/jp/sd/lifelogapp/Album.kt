package jp.sd.lifelogapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AlbumItem(val photo: MeetupPhoto, val meetup: Meetup)

private val ALBUM_DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN)
private val ALBUM_MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)

fun albumDayText(d: LocalDate?): String = d?.format(ALBUM_DAY_FMT) ?: "日付なし"

private sealed class AlbumRow {
    class Header(val text: String, val count: Int) : AlbumRow()
    class Tiles(val items: List<Pair<Int, AlbumItem>>) : AlbumRow()
}

// 写真を、月ごとにまとめて、3列で並べる。items は、並べたい順（新しい順／古い順）で渡す。
@Composable
fun AlbumSection(items: List<AlbumItem>, newestFirst: Boolean, onToggleOrder: () -> Unit, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
            Nota(NotaKind.SLEEP, 130.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(text = "まだ、写真がありません。", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "「会えた日を記録」で、写真を添えると、ここに、思い出が、並んでいきます。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val rows = ArrayList<AlbumRow>()
    val indexed = items.withIndex().map { it.index to it.value }
    indexed.groupBy { (_, it) -> it.meetup.heldOn?.let { d -> d.year * 100 + d.monthValue } ?: 0 }.forEach { (_, group) ->
        val d = group.first().second.meetup.heldOn
        rows.add(AlbumRow.Header(d?.format(ALBUM_MONTH_FMT) ?: "日付なし", group.size))
        group.chunked(3).forEach { rows.add(AlbumRow.Tiles(it)) }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "写真 ${items.size}枚", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleOrder) { Text(if (newestFirst) "新しい順 ⇅" else "古い順 ⇅") }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows) { row ->
                when (row) {
                    is AlbumRow.Header -> Text(
                        text = "${row.text}（${row.count}枚）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    is AlbumRow.Tiles -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.items.forEach { (index, item) ->
                            PhotoTile(item.photo.path, Modifier.weight(1f).aspectRatio(1f)) { onTap(index) }
                        }
                        repeat(3 - row.items.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// 1枚ずつ、大きく見る。横にめくれて、写真の下に、その日の情報が出る。
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumViewer(
    items: List<AlbumItem>,
    startIndex: Int,
    infoLines: (AlbumItem) -> List<String>,
    canDelete: (AlbumItem) -> Boolean,
    onOpenRecord: (Meetup) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = onDismiss) { Text("閉じる", color = Color.White) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "${pager.currentPage + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.padding(end = 8.dp))
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    PhotoFull(items[page].photo.path, Modifier.fillMaxSize())
                }
                val cur = items.getOrNull(pager.currentPage)
                if (cur != null) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text(text = albumDayText(cur.meetup.heldOn), style = MaterialTheme.typography.titleMedium)
                            infoLines(cur).forEach { line ->
                                Text(text = line, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onDismiss(); onOpenRecord(cur.meetup) }) { Text("この日の記録を見る") }
                                if (canDelete(cur)) {
                                    TextButton(onClick = { onDelete(cur.photo.path) }) { Text("この写真を消す", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

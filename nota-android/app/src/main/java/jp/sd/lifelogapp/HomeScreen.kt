package jp.sd.lifelogapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

private enum class HomeTab { CALENDAR, TIMELINE, PLANNING, SETTINGS }

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(HomeTab.TIMELINE) }
    var lastTab by remember { mutableStateOf(HomeTab.TIMELINE) }

    LaunchedEffect(Unit) {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId != null) {
            // 届いている招待の数を、前回の保存結果ですぐ反映し、そのあと最新を確認する（設定のアイコンの赤い点）
            try {
                UiCache.get(context, userId, "check_group")?.let {
                    val cached = org.json.JSONObject(it)
                    InviteBadge.count = cached.optJSONArray("pending_invites")?.length() ?: 0
                    GroupsState.updateFrom(cached)
                }
            } catch (e: Exception) { /* 保存結果が読めなくても、続行する */ }
            launch {
                try { fetchGroupStatus(context) } catch (e: Exception) { android.util.Log.e("HomeScreen", "invite check failed", e) }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (currentTab) {
                HomeTab.CALENDAR -> CommunityScreen(mode = 2, onGo = { currentTab = if (it == 1) HomeTab.PLANNING else HomeTab.TIMELINE }, onOpenSettings = { lastTab = currentTab; currentTab = HomeTab.SETTINGS }, modifier = Modifier.fillMaxSize())
                HomeTab.TIMELINE -> CommunityScreen(mode = 0, onGo = { currentTab = if (it == 1) HomeTab.PLANNING else HomeTab.CALENDAR }, onOpenSettings = { lastTab = currentTab; currentTab = HomeTab.SETTINGS }, modifier = Modifier.fillMaxSize())
                HomeTab.PLANNING -> CommunityScreen(mode = 1, onGo = { currentTab = HomeTab.TIMELINE }, onOpenSettings = { lastTab = currentTab; currentTab = HomeTab.SETTINGS }, modifier = Modifier.fillMaxSize())
                HomeTab.SETTINGS -> Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.TextButton(onClick = { currentTab = lastTab }) { Text("＜ 戻る") }
                    SettingsScreen(modifier = Modifier.weight(1f))
                }
            }
        }

        BottomNavBar(current = currentTab, onSelect = { currentTab = it })
    }
}

@Composable
private fun BottomNavBar(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SideNavItem(
                icon = Icons.Filled.DateRange,
                label = "カレンダー",
                selected = current == HomeTab.CALENDAR,
                onClick = { onSelect(HomeTab.CALENDAR) }
            )
            RecordNavButton(selected = current == HomeTab.TIMELINE, onClick = { onSelect(HomeTab.TIMELINE) })
            SideNavItem(
                icon = Icons.Filled.Edit,
                label = "プランニング",
                selected = current == HomeTab.PLANNING,
                onClick = { onSelect(HomeTab.PLANNING) }
            )
        }
    }
}

@Composable
private fun SideNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, badge: Boolean = false) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
            if (badge) {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-4).dp)
                        .background(Color(0xFFB3262E), CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun RecordNavButton(selected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(targetValue = if (selected) 68.dp else 58.dp, label = "recordButtonSize")
    // 開いているときだけ色を反転（塗りつぶし＋輪）。開いていないときは、ほかのボタンと同じ控えめな色にする。
    val inactiveTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
                .border(
                    width = if (selected) 4.dp else 1.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Face,
                contentDescription = "タイムライン",
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else inactiveTint,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "タイムライン",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else inactiveTint
        )
    }
}

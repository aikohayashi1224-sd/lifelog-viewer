package jp.sd.lifelogapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RingGroup(val name: String?, val members: List<RingMember>)

data class RingMember(
    val label: String,
    val initial: String,
    val isSelf: Boolean,
    val loggedToday: Boolean,
    val avatarEmoji: String = "",
    val avatarImage: ImageBitmap? = null,
    val userId: String = ""
)

// 家族の「今日」を並べる。自分のアイコンだけ大きく、ほかの人は同じ大きさで並び、
// 一列に収まらなければ自動で次の行へ折り返す。線でつなぐ表現は使わない（誰も特別扱いしないため）。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionRing(members: List<RingMember>, modifier: Modifier = Modifier) {
    if (members.size < 2) return

    val ordered = members.sortedByDescending { it.isSelf }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ordered.forEach { member ->
            if (member.isSelf) {
                MemberBadge(member = member, avatarSize = 76.dp, slotWidth = 92.dp, knotSize = 32.dp, large = true)
            } else {
                MemberBadge(member = member, avatarSize = 52.dp, slotWidth = 68.dp, knotSize = 26.dp, large = false)
            }
        }
    }
}

@Composable
private fun MemberBadge(member: RingMember, avatarSize: Dp, slotWidth: Dp, knotSize: Dp, large: Boolean) {
    Column(
        modifier = Modifier
            .width(slotWidth)
            .padding(top = if (large) 6.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(avatarSize), contentAlignment = Alignment.Center) {
            AvatarCircle(
                avatarSize = avatarSize,
                initial = member.initial,
                emoji = member.avatarEmoji,
                image = member.avatarImage,
                large = large,
                owner = OwnerState.isOwner(member.userId)
            )
            if (member.loggedToday) {
                BubblePair(
                    modifier = Modifier
                        .size(width = knotSize * 1.3f, height = knotSize)
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-8).dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (member.isSelf) "あなた" else member.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

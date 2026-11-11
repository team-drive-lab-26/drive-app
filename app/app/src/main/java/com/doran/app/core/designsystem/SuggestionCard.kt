package com.doran.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doran.app.core.data.AgentCard

private val CATEGORY_NAMES = mapOf(
    "attraction" to "관광지",
    "culture" to "문화시설",
    "leisure" to "레저",
    "shopping" to "쇼핑",
    "food" to "식당",
    "cafe" to "카페",
    "event" to "행사",
    "rest_stop" to "휴게소",
)

/** 카드 맨 위 한 줄. 무엇인지와 경로에서 얼마나 벗어나는지 */
fun AgentCard.headline(): String {
    if (isTrack) return artist.ifBlank { "추천 곡" }
    val kindName = CATEGORY_NAMES[category] ?: "장소"
    val distance = distanceKm ?: return kindName
    return if (distance < 0.5) "경로 위 $kindName" else "경로에서 ${"%.1f".format(distance)}km 떨어진 $kindName"
}

/**
 * 정차 중에만 보이는 제안 카드 한 장. 사진이 있으면 왼쪽에 크게 둔다.
 * 말("응", "아니")로도 답할 수 있어 버튼은 두 개만 둔다.
 */
@Composable
fun SuggestionCard(
    card: AgentCard,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(shape)
            .background(DoranPalette.Surface)
            .border(1.dp, DoranPalette.Line, shape),
    ) {
        if (card.imageUrl.isNotBlank()) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().width(260.dp).background(DoranPalette.Line),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(card.headline(), style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
                Spacer(Modifier.height(6.dp))
                Text(
                    card.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = DoranPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        card.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DoranPalette.InkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DoranPrimaryButton(text = if (card.isTrack) "틀기" else "들르기", onClick = onAccept)
                DoranSecondaryButton(text = "괜찮아", onClick = onDismiss)
            }
        }
    }
}

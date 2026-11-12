package com.doran.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** 무엇인지와 경로에서 얼마나 벗어나는지 */
fun AgentCard.headline(): String {
    if (isTrack) return artist.ifBlank { "추천 곡" }
    val kindName = CATEGORY_NAMES[category] ?: "장소"
    val distance = distanceKm ?: return kindName
    return if (distance < 0.5) "$kindName · 경로 위" else "$kindName · 경로에서 ${"%.1f".format(distance)}km"
}

/** 안내 문구에 전기차 충전소가 적혀 있는 곳 */
val AgentCard.canCharge: Boolean get() = "전기차" in summary

private val PanelShape = RoundedCornerShape(22.dp)

/**
 * 정차 중 제안 패널. 길가에 솟은 주황 표지판과 짝을 이룬다.
 * [compact]가 아니면 위에 사진을 크게 싣는다. 말("응", "아니")로도 답할 수 있어 버튼은 두 개만 둔다.
 */
@Composable
fun SuggestionCard(
    card: AgentCard,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    batteryPercent: Int? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .panel(PanelShape),
    ) {
        if (!compact && card.imageUrl.isNotBlank()) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp).background(DoranPalette.SurfaceHigh),
            )
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(card.name, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(card.headline(), card.summary).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = DoranPalette.InkSoft,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 배터리가 낮을 때 휴게소 안내에 전기차 충전소가 있으면 그 사실을 앞에 꺼낸다
            if (batteryPercent != null && batteryPercent <= LOW_BATTERY && card.canCharge) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryIcon(batteryPercent, DoranPalette.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("배터리 $batteryPercent%. 여기서 충전할 수 있어", style = MaterialTheme.typography.titleMedium, color = DoranPalette.Accent)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoranPrimaryButton(text = if (card.isTrack) "틀기" else "들르기", onClick = onAccept, modifier = Modifier.weight(1.3f))
                DoranSecondaryButton(text = "괜찮아", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

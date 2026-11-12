package com.doran.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.doran.app.core.model.PlaceText

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

/** 업종마다 그리는 아이콘. 사진이 없는 곳은 이 아이콘을 사진 자리에 둔다 */
fun AgentCard.icon(): DoranIcon = when {
    isTrack -> DoranIcon.NOTE
    else -> when (category) {
        "food" -> DoranIcon.FOOD
        "cafe" -> DoranIcon.CAFE
        "rest_stop" -> DoranIcon.REST
        "event" -> DoranIcon.EVENT
        "culture" -> DoranIcon.CULTURE
        "leisure" -> DoranIcon.LEISURE
        "shopping" -> DoranIcon.SHOPPING
        else -> DoranIcon.SIGHT
    }
}

// 관광공사 사진 주소는 http로 오는데 같은 주소가 https로도 열린다. 정식 빌드는 http를 막으므로 https로 바꿔 부른다
private fun photoUrl(url: String): String = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

/**
 * 정차 중 제안 패널. 길가에 솟은 주황 표지판과 짝을 이룬다.
 * [compact]가 아니고 사진이 있으면 위에 사진을 크게 싣는다. 사진이 없거나 못 불러오면 업종 아이콘 칸을 이름 옆에 둔다.
 * 말("응", "아니")로도 답할 수 있어 버튼은 두 개만 둔다.
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
    var photoFailed by remember(card.imageUrl) { mutableStateOf(false) }
    val showPhoto = !compact && card.imageUrl.isNotBlank() && !photoFailed
    Column(
        modifier
            .fillMaxWidth()
            .panel(PanelShape),
    ) {
        if (showPhoto) {
            AsyncImage(
                model = photoUrl(card.imageUrl),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true },
                modifier = Modifier.fillMaxWidth().height(200.dp).background(DoranPalette.SurfaceHigh),
            )
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!showPhoto) {
                    val tile = if (compact) 56.dp else 72.dp
                    Box(
                        Modifier.size(tile).background(DoranPalette.Line, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(card.icon(), DoranPalette.Ink, size = tile * 0.5f) }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.headline(), style = MaterialTheme.typography.titleMedium, color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val detail = PlaceText.summary(card.summary)
            if (detail.isNotBlank() && !compact) {
                Spacer(Modifier.height(10.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DoranPalette.InkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

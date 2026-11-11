package com.doran.app.feature.character

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranStage
import com.doran.app.core.designsystem.SuggestionCard

/** 정차 화면에서 바로 갈 수 있는 곳 */
class HomeLinks(val onAlbum: () -> Unit, val onMusic: () -> Unit, val onSettings: () -> Unit)

/**
 * 정차 중 첫 화면. 평소에는 도란 혼자 있고, 도란이 제안하면 도란이 왼쪽으로 비켜서고 카드가 들어온다.
 * 위쪽에는 길 정보 한 줄과 앨범·음악·설정으로 가는 글자 링크만 둔다.
 */
@Composable
fun CharacterScreen(
    face: DoranFace,
    caption: String,
    routeLine: String,
    cards: List<AgentCard>,
    links: HomeLinks,
    onCall: () -> Unit,
    onLongPress: () -> Unit,
    onChoose: (AgentCard) -> Unit,
    onDismissCards: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                routeLine,
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.InkSoft,
                modifier = Modifier.weight(1f),
            )
            TopLink("앨범", links.onAlbum)
            TopLink("음악", links.onMusic)
            TopLink("설정", links.onSettings)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = maxHeight
            val hasCards = cards.isNotEmpty()
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    DoranStage(
                        face = face,
                        caption = caption,
                        onCall = onCall,
                        onLongPress = onLongPress,
                        characterSize = if (hasCards) minOf(height * 0.5f, 300.dp) else minOf(height * 0.62f, 420.dp),
                    )
                }
                AnimatedVisibility(
                    visible = hasCards,
                    enter = fadeIn() + slideInHorizontally { it / 3 },
                    exit = fadeOut(),
                    modifier = Modifier.weight(1.25f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        cards.take(2).forEach { card ->
                            SuggestionCard(card = card, onAccept = { onChoose(card) }, onDismiss = onDismissCards)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
    }
}

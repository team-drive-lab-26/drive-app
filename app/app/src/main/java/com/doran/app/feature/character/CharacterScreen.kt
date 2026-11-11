package com.doran.app.feature.character

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.CALL_HINT
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.SuggestionCard
import com.doran.app.core.designsystem.TapeEntry
import com.doran.app.core.designsystem.TapeStrip
import com.doran.app.core.designsystem.TappableDoran
import com.doran.app.core.designsystem.TopBar
import com.doran.app.core.designsystem.clockText
import com.doran.app.core.designsystem.doranFeetOffset

/** 정차 화면에서 바로 갈 수 있는 곳 */
class HomeLinks(val onAlbum: () -> Unit, val onMusic: () -> Unit, val onSettings: () -> Unit)

/** 테이프 끝에 적는 목적지와 남은 시간 */
class TapeEnd(val destination: String, val eta: String)

/**
 * 정차 중 첫 화면. 이번 여행이 테이프로 펼쳐진다.
 * 왼쪽은 지나온 곳과 남긴 목소리, 귤색 재생 헤드가 지금, 오른쪽 빈 테이프 끝이 목적지다. 도란은 헤드 위에 앉는다.
 * 도란이 제안하면 헤드가 왼쪽으로 물러나고, 빈 테이프 위로 카드(최대 2장)가 내려와 끼울 자리를 가리킨다.
 */
@Composable
fun CharacterScreen(
    face: DoranFace,
    caption: String,
    routeLine: String,
    tape: List<TapeEntry>,
    end: TapeEnd,
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
            .background(DoranPalette.Background),
    ) {
        TopBar(
            title = routeLine.ifBlank { "이번 여행" },
            links = listOf("앨범" to links.onAlbum, "음악" to links.onMusic, "설정" to links.onSettings),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val hasCards = cards.isNotEmpty()
            val band = 156.dp
            val bandTop = maxHeight - band - 44.dp - 36.dp
            val playhead by animateFloatAsState(if (hasCards) 0.30f else 0.40f, tween(520), label = "playhead")
            val doran by animateDpAsState(if (hasCards) minOf(bandTop * 0.55f, 240.dp) else minOf(bandTop * 0.7f, 300.dp), tween(520), label = "doran")
            val headX = maxWidth * playhead

            // 도란의 말. 화면의 제목처럼 맨 위에 둔다
            Text(
                caption.ifBlank { CALL_HINT },
                style = if (hasCards) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                color = if (caption.isBlank()) DoranPalette.InkSoft else DoranPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 40.dp, top = 8.dp)
                    .width(if (hasCards) headX + 170.dp - 64.dp else maxWidth * 0.6f),
            )

            TapeStrip(
                entries = tape,
                playhead = playhead,
                bandHeight = band,
                nowLabel = "지금 ${clockText(System.currentTimeMillis())}",
                endTitle = end.destination,
                endSub = end.eta.ifBlank { if (end.destination.isBlank()) "목적지를 정하면 여기까지 이어져" else "" },
                modifier = Modifier.offset(y = bandTop),
            )

            Box(Modifier.offset(x = headX - doran / 2, y = bandTop - doranFeetOffset(doran))) {
                TappableDoran(face = face, onCall = onCall, onLongPress = onLongPress, modifier = Modifier.size(doran))
            }

            val cardsLeft = headX + 170.dp
            // 바깥 Column의 ColumnScope 판을 피해 일반 판을 부른다
            androidx.compose.animation.AnimatedVisibility(
                visible = hasCards,
                enter = fadeIn(tween(240, delayMillis = 200)) + slideInVertically(tween(420, delayMillis = 200)) { -it / 6 },
                exit = fadeOut(tween(150)),
                modifier = Modifier.offset(x = cardsLeft),
            ) {
                val shown = cards.take(2)
                val cardHeight = 196.dp
                val stack = cardHeight * shown.size + 16.dp * (shown.size - 1).coerceAtLeast(0)
                Box(Modifier.width(maxWidth - cardsLeft - 40.dp).height(bandTop)) {
                    // 카드마다 빈 테이프에 끼울 칸 하나와 내려가는 점선. 칸에는 그곳 이름을 펜으로 적는다
                    val slot = 168.dp
                    Canvas(Modifier.fillMaxSize()) {
                        shown.indices.forEach { i ->
                            val x = (24.dp + slot * i + 24.dp).toPx()
                            drawLine(
                                DoranPalette.Head,
                                Offset(x, (bandTop - 28.dp).toPx()),
                                Offset(x, size.height + 16.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                            )
                            drawRoundRect(
                                DoranPalette.Head,
                                topLeft = Offset((24.dp + slot * i).toPx(), size.height + 16.dp.toPx()),
                                size = Size((slot - 16.dp).toPx(), band.toPx() - 32.dp.toPx()),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
                            )
                        }
                    }
                    shown.forEachIndexed { i, card ->
                        Text(
                            card.name,
                            style = DoranText.labelSmall,
                            color = DoranPalette.Head,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(x = 24.dp + slot * i + 14.dp, y = bandTop + 30.dp)
                                .width(slot - 44.dp),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = bandTop - 28.dp - stack),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        shown.forEach { card ->
                            SuggestionCard(
                                card = card,
                                onAccept = { onChoose(card) },
                                onDismiss = onDismissCards,
                                modifier = Modifier.height(cardHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

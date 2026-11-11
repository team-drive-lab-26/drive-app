package com.doran.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 누르면 듣고, 길게 누르면 디버그 패널을 여는 도란. 주행 중에는 [still]로 혼자 두리번거리지 않게 한다 */
@Composable
fun TappableDoran(face: DoranFace, onCall: () -> Unit, modifier: Modifier = Modifier, onLongPress: () -> Unit = {}, still: Boolean = false) {
    DoranCharacter(
        mood = face.mood,
        level = face.level,
        cheerKey = face.cheerKey,
        still = still,
        modifier = modifier
            .semantics { contentDescription = "도란. 누르면 말을 들어요" }
            .pointerInput(onCall, onLongPress) {
                detectTapGestures(onTap = { onCall() }, onLongPress = { onLongPress() })
            },
    )
}

/** 테이프 면 표시. 가는 길은 A면 */
@Composable
fun SideMark(side: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .border(BorderStroke(2.5.dp, DoranPalette.Ink), DoranButtonShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(side, style = DoranText.trackNo, color = DoranPalette.Ink)
    }
}

/** 정차 화면 위 막대. 왼쪽은 테이프 면과 길 정보, 오른쪽은 다른 화면으로 가는 글자 탭 */
@Composable
fun TopBar(title: String, links: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(start = 40.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideMark("A")
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = DoranPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        links.forEach { (text, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 64.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                Text(text, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
            }
        }
    }
}

/** 뒤로 가는 꺾쇠. 글리프 대신 직접 그린다 */
@Composable
private fun BackChevron(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.64f, size.height * 0.18f)
            lineTo(size.width * 0.30f, size.height * 0.5f)
            lineTo(size.width * 0.64f, size.height * 0.82f)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 하위 화면 위 막대. 뒤로 가기와 화면 이름 */
@Composable
fun BackBar(back: String, title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(start = 20.dp, end = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "$back(으)로 돌아가기" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(DoranPalette.Ink)
            Spacer(Modifier.width(4.dp))
            Text(back, style = MaterialTheme.typography.titleLarge, color = DoranPalette.InkSoft)
        }
        Spacer(Modifier.width(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 가족 이름 버튼. 그 사람 리본 색 칸이 앞에 붙는다 */
@Composable
fun RibbonButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String = "") {
    OutlinedButton(
        onClick = onClick,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = 72.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 28.dp),
        border = BorderStroke(2.dp, DoranPalette.Ink.copy(alpha = 0.85f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DoranPalette.Ink),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TapeSwatch(FamilyRibbon.colorFor(name), Modifier.size(28.dp))
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
    }
}

/** 띠 위아래 여백 없이 한 줄 구분선 */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = DoranPalette.Line) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}

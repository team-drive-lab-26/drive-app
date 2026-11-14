package com.doran.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.doran.app.core.model.VehicleState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.border
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

/** 누르면 듣고, 길게 누르면 디버그 패널을 여는 도란. 주행 중에는 [still]로 혼자 움직이지 않게 한다 */
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

/**
 * 정차 화면 위 막대. 왼쪽은 목적지와 남은 시간(가장 크게), 도착 시각, 차 상태(배터리·탑승 인원).
 * 오른쪽은 다른 화면으로 가는 탭(아이콘과 이름, 높이 72dp).
 */
@Composable
fun TopBar(
    destination: String,
    eta: String,
    arrival: String,
    vehicle: VehicleState,
    links: List<NavLink>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 32.dp, top = 28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (destination.isBlank()) {
                Text("목적지를 말해 주면 길을 펼쳐 둘게", style = DoranText.etaSide, color = DoranPalette.InkSoft)
            } else {
                Text("${destination}까지", style = DoranText.etaSide, color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(eta.ifBlank { "--" }, style = DoranText.eta, color = DoranPalette.Ink, maxLines = 1)
                    if (arrival.isNotBlank()) {
                        Spacer(Modifier.width(18.dp))
                        Text("$arrival 도착", style = DoranText.etaSide, color = DoranPalette.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            CarStatusRow(vehicle, Modifier.padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            links.forEach { NavChip(it) }
        }
    }
}

/** 위 막대 탭 하나 */
class NavLink(val label: String, val icon: DoranIcon, val onClick: () -> Unit)

@Composable
private fun NavChip(link: NavLink) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(DoranPalette.Surface)
            .border(1.dp, DoranPalette.Line, shape)
            .clickable(onClick = link.onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(link.icon, DoranPalette.InkSoft, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(link.label, style = DoranText.tab, color = DoranPalette.Ink)
    }
}

/** 배터리가 이 아래면 위 막대와 휴게소 패널에서 충전 이야기를 꺼낸다 */
const val LOW_BATTERY = 20

/**
 * 차에서 받은 배터리 잔량과 탑승 인원. 값을 주지 않는 차에서는 아무것도 보이지 않는다.
 * 배터리가 낮으면 주황으로 바뀐다.
 */
@Composable
fun CarStatusRow(vehicle: VehicleState, modifier: Modifier = Modifier) {
    val battery = vehicle.batteryPercent
    val seats = vehicle.buckledSeats
    if (battery == null && (seats == null || seats == 0)) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (battery != null) {
            val low = battery <= LOW_BATTERY
            val color = if (low) DoranPalette.Accent else DoranPalette.InkSoft
            BatteryIcon(battery, color)
            Spacer(Modifier.width(10.dp))
            Text(if (low) "배터리 $battery%, 충전이 필요해" else "$battery%", style = DoranText.number.copy(fontSize = 22.sp), color = color)
        }
        if (seats != null && seats > 0) {
            if (battery != null) Spacer(Modifier.width(28.dp))
            Glyph(DoranIcon.PERSON, DoranPalette.InkSoft, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text("${seats}명 탑승", style = DoranText.number.copy(fontSize = 22.sp), color = DoranPalette.InkSoft)
        }
    }
}

/** 도란이 하는 말. 앞의 주황 점이 도란의 말이라는 표시다. 비어 있으면 부르는 법을 흐리게 */
@Composable
fun SayLine(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val hint = text.isBlank()
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 21.dp)
                .size(16.dp)
                .background(if (hint) DoranPalette.InkMute else DoranPalette.Accent, CircleShape),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text.ifBlank { CALL_HINT },
            style = if (hint) DoranText.say.copy(fontWeight = FontWeight.Bold) else DoranText.say,
            color = if (hint) DoranPalette.InkSoft else DoranPalette.Ink,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 길 위에 붙는 패널. 표지판과 같은 바탕에 부드러운 그림자 */
fun Modifier.panel(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)): Modifier =
    this
        .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(DoranPalette.Surface)
        .border(1.dp, DoranPalette.Line, shape)

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
fun BackBar(back: String, title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .padding(start = 28.dp, end = 40.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DoranPalette.Surface)
                .border(1.dp, DoranPalette.Line, RoundedCornerShape(20.dp))
                .clickable(onClick = onBack)
                .semantics { contentDescription = "$back(으)로 돌아가기" }
                .padding(start = 14.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(DoranPalette.InkSoft)
            Spacer(Modifier.width(6.dp))
            Text(back, style = DoranText.tab, color = DoranPalette.InkSoft)
        }
        Spacer(Modifier.width(20.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = DoranPalette.Ink, modifier = Modifier.weight(1f))
    }
}

/** 그 사람 색 점 */
@Composable
fun PersonDot(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(18.dp).background(FamilyRibbon.colorFor(name), CircleShape))
}

/** 가족 이름 버튼. 그 사람 색 점이 앞에 붙는다 */
@Composable
fun RibbonButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String = "", large: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = if (large) 96.dp else 72.dp),
        contentPadding = if (large) PaddingValues(start = 30.dp, end = 38.dp) else PaddingValues(start = 22.dp, end = 28.dp),
        border = BorderStroke(2.dp, DoranPalette.LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DoranPalette.Ink),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonDot(name, Modifier.size(if (large) 28.dp else 22.dp))
            Text(name, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
            if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
    }
}

/** 한 줄 구분선 */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = DoranPalette.Line) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}

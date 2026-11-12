package com.doran.app.core.model

/**
 * 서버 카드 설명(summary)을 차 화면에 맞게 다듬는다.
 * 장소 운영 정보는 관광공사 원문이 "운영 07:00~23:00 (휴무: 연중무휴)", "운영 휴무: 연중무휴 24시개방"처럼 섞여 오고,
 * 서버가 60자에서 자르기도 한다. 영업 시간 하나와 쉬는 날만 남겨 "영업 07:00~23:00 · 쉬는 날 없음"으로 보인다.
 * 운영 정보가 아닌 설명(휴게소 시설, 소개 문장)은 그대로 둔다.
 */
object PlaceText {
    private val RANGE = Regex("""(\d{1,2}):(\d{2})\s*[~\-–]\s*(\d{1,2}):(\d{2})""")
    private val CLOSED = Regex("""\(?\s*휴무\s*:\s*([^)]*)\)?""")
    private val ALWAYS_OPEN = listOf("24시", "상시", "00:00~24:00", "연중개방")
    private val NO_CLOSED_DAY = listOf("연중무", "없음", "무휴")
    private const val MAX_CLOSED = 16

    fun summary(raw: String): String {
        val text = raw.trim()
        if (!text.startsWith("운영")) return text
        val body = text.removePrefix("운영").trim()
        val compact = body.replace(" ", "")
        val closedMatch = CLOSED.find(body)
        val hoursPart = closedMatch?.let { body.removeRange(it.range) } ?: body
        val hours = when {
            ALWAYS_OPEN.any { it in compact } -> "24시간 영업"
            else -> hours(hoursPart)
        }
        val closed = closedMatch?.groupValues?.get(1)?.let(::closedDay).orEmpty()
        return listOf(hours, closed).filter(String::isNotBlank).joinToString(" · ").ifBlank { text }
    }

    private fun hours(text: String): String {
        val range = RANGE.find(text) ?: return ""
        val (h1, m1, h2, m2) = range.destructured
        val weekday = if ("평일" in text.substring(0, range.range.first)) "평일 " else ""
        return "영업 $weekday${h1.padStart(2, '0')}:$m1~${h2.padStart(2, '0')}:$m2"
    }

    private fun closedDay(text: String): String {
        val first = text.substringBefore("/").substringBefore("※").trim()
        if (first.isBlank()) return ""
        if (NO_CLOSED_DAY.any { first.startsWith(it) || first == it }) return "쉬는 날 없음"
        // "24시개방"처럼 휴무 칸에 붙어 온 영업 안내는 떼어 낸다
        val day = first.replace(Regex("""\s*24시\S*"""), "")
            .removeSuffix("정기휴무").removeSuffix("휴무").trim()
        if (day.isBlank()) return ""
        if (NO_CLOSED_DAY.any { day.startsWith(it) }) return "쉬는 날 없음"
        val short = if (day.length > MAX_CLOSED) day.take(MAX_CLOSED).trimEnd(',', ' ') + "…" else day
        return "$short 휴무"
    }
}

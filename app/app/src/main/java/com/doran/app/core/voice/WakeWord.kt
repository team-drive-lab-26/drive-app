package com.doran.app.core.voice

sealed interface WakeMatch {
    /** 호출어가 없다. 대기 중에 들은 말은 버린다 */
    data object None : WakeMatch

    /** "도란아"만 불렀다. 대답한 뒤 다음 한 문장을 듣는다 */
    data object CallOnly : WakeMatch

    /** "도란아, 휴게소 어디야"처럼 호출어 뒤에 바로 요청이 붙었다 */
    data class Command(val text: String) : WakeMatch
}

/**
 * 계속 듣는 중에 받아 적힌 문장에서 호출어 "도란"을 찾는다.
 * 전용 호출어 엔진 없이 일반 음성 인식 결과를 보므로, 자주 잘못 받아 적히는 표기도 같이 인정한다.
 */
object WakeWord {
    // 받아 적기 결과에 따라 추가한다. 일반 단어와 겹치는 표기(토란 등)는 넣지 않는다
    private val NAMES = listOf("도란", "또란", "도랑", "두란")
    private val SUFFIXES = listOf("아", "야", "이", "님")

    // 호출어 앞에 "어", "저기" 같은 말이 붙어도 인정하되, 문장 중간의 "도란"은 부른 것으로 보지 않는다
    private const val MAX_LEADING_CHARS = 4
    private val SEPARATORS = Regex("^[\\s,.!?~·…]+")

    fun match(text: String): WakeMatch {
        val heard = text.trim()
        val (start, name) = NAMES
            .mapNotNull { name -> heard.indexOf(name).takeIf { it >= 0 }?.let { it to name } }
            .minByOrNull { it.first } ?: return WakeMatch.None
        if (heard.substring(0, start).count { !it.isWhitespace() } > MAX_LEADING_CHARS) return WakeMatch.None

        var rest = heard.substring(start + name.length)
        // "도란도란"처럼 겹쳐 받아 적힌 경우
        while (true) {
            rest = rest.replace(SEPARATORS, "")
            val repeated = NAMES.firstOrNull { rest.startsWith(it) } ?: break
            rest = rest.removePrefix(repeated)
        }
        // "도란아" 뒤가 끝이거나 띄어 쓴 경우에만 호칭으로 본다("도란 이거 틀어 줘"의 "이"는 남긴다)
        SUFFIXES.firstOrNull { suffix ->
            rest.startsWith(suffix) && (rest.length == suffix.length || SEPARATORS.containsMatchIn(rest.substring(suffix.length)))
        }?.let { rest = rest.removePrefix(it) }
        rest = rest.replace(SEPARATORS, "").trim()
        return if (rest.isEmpty()) WakeMatch.CallOnly else WakeMatch.Command(rest)
    }
}

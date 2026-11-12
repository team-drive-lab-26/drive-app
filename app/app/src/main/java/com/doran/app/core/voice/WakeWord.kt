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
    private const val CALL = "도란아"

    // 호출어 앞에 "어", "저기" 같은 말이 붙어도 인정하되, 문장 중간의 "도란"은 부른 것으로 보지 않는다
    private const val MAX_LEADING_CHARS = 4
    private val SEPARATORS = Regex("^[\\s,.!?~·…]+")

    // Pleos 음성 인식은 "도란아"를 "도라나", "또래나"처럼 소리 나는 대로 받아 적는다(에뮬레이터에서 확인).
    // 표기가 맞지 않으면 앞쪽 낱말을 자음·모음으로 풀어 소리가 가까운지 본다. 낱말 단위로 보므로 "돌아나가자"는 부른 것이 아니다
    private val SPOKEN_TARGETS = mapOf("도란아" to 1.0, "도란야" to 1.0, "도란" to 0.25)
    private val WORD = Regex("[^\\s,.!?~·…]+")

    // 세 글자 낱말 하나만 말했으면 더 느슨하게 본다(에뮬레이터에서 "도란아"가 "드라마", "그러나"로 적혔다).
    // 잘못 깨어나도 "응?" 하고 끝나므로, 뒤에 말이 이어질 때보다 덜 엄격해도 된다
    private const val LONE_CALL_LIMIT = 2.5

    fun match(text: String): WakeMatch {
        val heard = text.trim()
        return matchWritten(heard) ?: matchSpoken(heard) ?: WakeMatch.None
    }

    /** 화면에 보일 문장. 소리 나는 대로 적힌 호출어("돌아나")를 "도란아"로 바꾼다 */
    fun display(text: String): String = when (val match = match(text)) {
        WakeMatch.None -> text
        WakeMatch.CallOnly -> CALL
        is WakeMatch.Command -> "$CALL ${match.text}"
    }

    private fun matchWritten(heard: String): WakeMatch? {
        val (start, name) = NAMES
            .mapNotNull { name -> heard.indexOf(name).takeIf { it >= 0 }?.let { it to name } }
            .minByOrNull { it.first } ?: return null
        if (leadingChars(heard, start) > MAX_LEADING_CHARS) return null

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
        return afterCall(rest)
    }

    // 앞쪽 낱말 하나 또는 붙은 두 낱말("돌 아나")을 호출어와 견줘 가장 가까운 것을 고른다
    private fun matchSpoken(heard: String): WakeMatch? {
        val words = WORD.findAll(heard).toList()
        val lone = words.singleOrNull()?.value
        if (lone != null && lone.count { it in '가'..'힣' } == CALL.length && JamoDistance.between(lone, CALL) <= LONE_CALL_LIMIT) {
            return WakeMatch.CallOnly
        }
        val best = words.indices
            .takeWhile { leadingChars(heard, words[it].range.first) <= MAX_LEADING_CHARS }
            .flatMap { i -> listOfNotNull(i..i, (i..i + 1).takeIf { i + 1 < words.size }) }
            .mapNotNull { span ->
                val spoken = span.joinToString("") { words[it].value }
                val score = SPOKEN_TARGETS.minOf { (target, limit) -> JamoDistance.between(spoken, target) - limit }
                (words[span.last].range.last + 1 to score).takeIf { score <= 0.0 }
            }
            .minByOrNull { it.second } ?: return null
        return afterCall(heard.substring(best.first))
    }

    private fun afterCall(rest: String): WakeMatch {
        val command = rest.replace(SEPARATORS, "").trim()
        return if (command.isEmpty()) WakeMatch.CallOnly else WakeMatch.Command(command)
    }

    private fun leadingChars(heard: String, end: Int) = heard.substring(0, end).count { !it.isWhitespace() }
}

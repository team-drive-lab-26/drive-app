package com.doran.app.core.voice

/**
 * 한글을 자음·모음으로 풀어 소리가 얼마나 다른지 잰다. 0이면 같은 소리다.
 * 받아 적기가 "도란아"를 "도라나", "또래나"처럼 소리 나는 대로 바꿔 적어도 같은 말로 보려고 쓴다.
 */
internal object JamoDistance {
    private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val MEDIALS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val FINALS = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    // 연음으로 다음 글자 첫소리로 넘어가는 받침. 겹받침과 ㅇ은 넘기지 않는다
    private const val MOVABLE_FINALS = "ㄱㄲㄴㄷㄹㅁㅂㅅㅆㅈㅊㅋㅌㅍㅎ"

    // 된소리는 거의 같은 소리, 거센소리는 조금 다른 소리로 본다
    private val TENSE = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')
    private val ASPIRATED = mapOf('ㅋ' to 'ㄱ', 'ㅌ' to 'ㄷ', 'ㅍ' to 'ㅂ', 'ㅊ' to 'ㅈ')
    private val SAME_SOUND = setOf(pair('ㅐ', 'ㅔ'))
    private val NEAR_SOUND = setOf(pair('ㄴ', 'ㄹ'), pair('ㅏ', 'ㅐ'), pair('ㅏ', 'ㅓ'), pair('ㅗ', 'ㅜ'), pair('ㅓ', 'ㅗ'))

    private const val INSERT_OR_DELETE = 1.0
    private const val DIFFERENT = 1.0

    /** 소리 나는 대로 푼다. 첫소리 ㅇ은 소리가 없어 뺀다. 예: "도란아", "돌아나" → ㄷㅗㄹㅏㄴㅏ */
    fun spell(text: String): String {
        val syllables = text.filter { it in '가'..'힣' }.map { char ->
            val code = char - '가'
            Syllable(INITIALS[code / 588], MEDIALS[code % 588 / 28], FINALS[code % 28].takeIf { it != ' ' })
        }.toMutableList()
        for (i in 0 until syllables.size - 1) {
            val final = syllables[i].final ?: continue
            if (syllables[i + 1].initial == 'ㅇ' && final in MOVABLE_FINALS) {
                syllables[i] = syllables[i].copy(final = null)
                syllables[i + 1] = syllables[i + 1].copy(initial = final)
            }
        }
        return buildString {
            syllables.forEach { s ->
                if (s.initial != 'ㅇ') append(s.initial)
                append(s.medial)
                s.final?.let(::append)
            }
        }
    }

    /** 두 말의 소리 차이. 비슷한 소리로 바뀐 것은 덜 센다 */
    fun between(heard: String, target: String): Double {
        val a = spell(heard)
        val b = spell(target)
        var previous = DoubleArray(b.length + 1) { it * INSERT_OR_DELETE }
        for (i in 1..a.length) {
            val current = DoubleArray(b.length + 1)
            current[0] = i * INSERT_OR_DELETE
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j] + INSERT_OR_DELETE,
                    current[j - 1] + INSERT_OR_DELETE,
                    previous[j - 1] + substitution(a[i - 1], b[j - 1]),
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun substitution(a: Char, b: Char): Double {
        if (a == b || pair(a, b) in SAME_SOUND) return 0.0
        val baseA = TENSE[a] ?: ASPIRATED[a] ?: a
        val baseB = TENSE[b] ?: ASPIRATED[b] ?: b
        if (baseA == baseB) return if (a in ASPIRATED || b in ASPIRATED) 0.5 else 0.25
        if (pair(a, b) in NEAR_SOUND) return 0.5
        return DIFFERENT
    }

    private fun pair(a: Char, b: Char) = if (a < b) "$a$b" else "$b$a"

    private data class Syllable(val initial: Char, val medial: Char, val final: Char?)
}

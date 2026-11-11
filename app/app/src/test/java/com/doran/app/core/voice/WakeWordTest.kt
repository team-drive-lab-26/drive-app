package com.doran.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordTest {
    @Test
    fun callOnly() {
        assertEquals(WakeMatch.CallOnly, WakeWord.match("도란아"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match(" 도란아! "))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("도란"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("도란도란"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("또란아"))
    }

    @Test
    fun commandAfterName() {
        assertEquals(WakeMatch.Command("휴게소 어디야"), WakeWord.match("도란아, 휴게소 어디야"))
        assertEquals(WakeMatch.Command("근처 국밥집"), WakeWord.match("도란아 근처 국밥집"))
        assertEquals(WakeMatch.Command("노래 틀어 줘"), WakeWord.match("어 도란아 노래 틀어 줘"))
    }

    @Test
    fun keepsWordStartingLikeSuffix() {
        assertEquals(WakeMatch.Command("이거 틀어 줘"), WakeWord.match("도란 이거 틀어 줘"))
    }

    @Test
    fun ignoresSpeechWithoutCall() {
        assertEquals(WakeMatch.None, WakeWord.match("휴게소 어디야"))
        assertEquals(WakeMatch.None, WakeWord.match(""))
        assertEquals(WakeMatch.None, WakeWord.match("아까 아빠가 도란 앱 얘기했잖아"))
    }
}

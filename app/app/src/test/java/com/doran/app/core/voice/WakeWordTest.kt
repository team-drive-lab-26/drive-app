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
    fun acceptsCallWrittenAsPronounced() {
        assertEquals(WakeMatch.CallOnly, WakeWord.match("도라나"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("또래나"))
        assertEquals(WakeMatch.Command("근처 휴게소 있어"), WakeWord.match("돌아나 근처 휴게소 있어"))
        assertEquals(WakeMatch.None, WakeWord.match("돌아나가자"))
    }

    @Test
    fun acceptsSimilarSoundByJamo() {
        assertEquals(WakeMatch.CallOnly, WakeWord.match("도라마"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("돌아다"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("돌 아나"))
        assertEquals(WakeMatch.Command("노래 틀어 줘"), WakeWord.match("어 또래나 노래 틀어 줘"))
    }

    @Test
    fun acceptsLoneWordMoreLoosely() {
        assertEquals(WakeMatch.CallOnly, WakeWord.match("드라마"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("그러나"))
        assertEquals(WakeMatch.CallOnly, WakeWord.match("올아나"))
        assertEquals(WakeMatch.None, WakeWord.match("너나"))
        assertEquals(WakeMatch.None, WakeWord.match("돌아가자"))
        assertEquals(WakeMatch.None, WakeWord.match("휴게소"))
    }

    @Test
    fun rejectsOtherWordsBySound() {
        assertEquals(WakeMatch.None, WakeWord.match("드라마 틀어 줘"))
        assertEquals(WakeMatch.None, WakeWord.match("토란 먹자"))
        assertEquals(WakeMatch.None, WakeWord.match("도라지 무침"))
        assertEquals(WakeMatch.None, WakeWord.match("그러나 저기"))
        assertEquals(WakeMatch.None, WakeWord.match("돌아가자"))
    }

    @Test
    fun showsCallAsDoran() {
        assertEquals("도란아", WakeWord.display("돌아나"))
        assertEquals("도란아 근처 휴게소 있어", WakeWord.display("돌아나 근처 휴게소 있어"))
        assertEquals("돌아나가자", WakeWord.display("돌아나가자"))
        assertEquals("휴게소 어디야", WakeWord.display("휴게소 어디야"))
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

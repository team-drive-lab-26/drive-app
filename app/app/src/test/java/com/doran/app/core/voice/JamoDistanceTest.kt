package com.doran.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class JamoDistanceTest {
    @Test
    fun spellsAsPronounced() {
        assertEquals("ㄷㅗㄹㅏㄴㅏ", JamoDistance.spell("도란아"))
        assertEquals("ㄷㅗㄹㅏㄴㅏ", JamoDistance.spell("돌아나"))
        assertEquals("ㄷㅗㄹㅏㄴㅏ", JamoDistance.spell("도라나"))
    }

    @Test
    fun similarSoundsCostLess() {
        assertEquals(0.0, JamoDistance.between("도라나", "도란아"), 0.0)
        assertEquals(0.75, JamoDistance.between("또래나", "도란아"), 1e-9)
        assertEquals(2.0, JamoDistance.between("드라마", "도란아"), 1e-9)
    }
}

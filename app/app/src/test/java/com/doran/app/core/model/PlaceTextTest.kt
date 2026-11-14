package com.doran.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceTextTest {
    @Test
    fun hoursOnly() {
        assertEquals("영업 11:00~22:30", PlaceText.summary("운영 11:00~22:30"))
    }

    @Test
    fun closedDayInParentheses() {
        assertEquals("영업 07:00~23:00 · 쉬는 날 없음", PlaceText.summary("운영 07:00~23:00 (휴무: 연중무휴)"))
        assertEquals("영업 10:00~22:00 · 매주 수요일 휴무", PlaceText.summary("운영 10:00~22:00 (휴무: 매주 수요일)"))
    }

    @Test
    fun alwaysOpenMixedIntoClosedDay() {
        assertEquals("24시간 영업 · 쉬는 날 없음", PlaceText.summary("운영 휴무: 연중무휴 24시개방"))
        assertEquals("24시간 영업 · 쉬는 날 없음", PlaceText.summary("운영 00:00~24:00 (휴무: 연중무휴)"))
    }

    @Test
    fun truncatedClosedDay() {
        assertEquals("영업 11:30~20:30 · 쉬는 날 없음", PlaceText.summary("운영 11:30~20:30 (휴무: 연중무"))
    }

    @Test
    fun weekdayHoursFirst() {
        assertEquals(
            "영업 평일 18:00~03:00 · 쉬는 날 없음",
            PlaceText.summary("운영 [평일] 18:00 - 03:00 [주말] 18:00 - 04:00 (휴무: 연중무휴)"),
        )
    }

    @Test
    fun padsSingleDigitHour() {
        assertEquals("영업 09:30~20:00 · 1, 3번째 일요일 휴무", PlaceText.summary("운영 9:30~20:00 (휴무: 1, 3번째 일요일 휴무)"))
    }

    @Test
    fun leavesOtherSummaries() {
        assertEquals("주유소, LPG 충전소, 전기차 충전소", PlaceText.summary("주유소, LPG 충전소, 전기차 충전소"))
        assertEquals("강릉 바다를 따라 걷는 길", PlaceText.summary("강릉 바다를 따라 걷는 길"))
    }
}

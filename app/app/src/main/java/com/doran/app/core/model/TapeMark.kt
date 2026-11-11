package com.doran.app.core.model

/** 여행 테이프에 남는 것. 출발, 들른 곳, 남긴 목소리 */
enum class TapeKind { START, STOP, VOICE }

/**
 * 여행 테이프의 한 지점. [label]은 장소 이름이나 남긴 사람 이름.
 * 목소리면 [seconds]가 길이다. 테이프에서 구간 길이가 실제 초에 비례한다.
 */
data class TapeMark(
    val id: String,
    val kind: TapeKind,
    val label: String,
    val atMillis: Long,
    val seconds: Int = 0,
)

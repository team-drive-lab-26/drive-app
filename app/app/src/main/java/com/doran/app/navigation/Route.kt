package com.doran.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Album : Route

    /** 여행 하나의 3D 지도 앨범 */
    @Serializable
    data class AlbumMap(val sessionId: String) : Route

    @Serializable
    data object Character : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Record : Route

    @Serializable
    data object Music : Route

    @Serializable
    data object Driving : Route

    companion object {
        /** 디버그 인텐트에서 쓰는 이름 → 화면 */
        fun byName(name: String, currentSessionId: String): Route? = when {
            // "albummap:<session_id>"로 다른 여행의 지도를 연다
            name.startsWith("albummap:") -> AlbumMap(name.substringAfter(':'))
            else -> byPlainName(name.lowercase(), currentSessionId)
        }

        private fun byPlainName(name: String, currentSessionId: String): Route? = when (name) {
            "onboarding" -> Onboarding
            "album" -> Album
            "albummap" -> AlbumMap(currentSessionId)
            "character", "home" -> Character
            "settings" -> Settings
            "record" -> Record
            "music" -> Music
            else -> null
        }
    }
}

package com.doran.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Album : Route

    @Serializable
    data object AlbumMap : Route

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
        fun byName(name: String): Route? = when (name.lowercase()) {
            "onboarding" -> Onboarding
            "album" -> Album
            "albummap" -> AlbumMap
            "character", "home" -> Character
            "settings" -> Settings
            "record" -> Record
            "music" -> Music
            else -> null
        }
    }
}

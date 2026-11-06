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
    data object SpeakerSelect : Route

    @Serializable
    data object Music : Route

    @Serializable
    data object Driving : Route
}

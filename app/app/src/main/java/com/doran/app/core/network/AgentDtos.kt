package com.doran.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 형식은 docs/api, docs/ai를 따른다

@Serializable
data class AgentRequest(
    @SerialName("session_id") val sessionId: String,
    val kind: String,
    val vehicle: VehicleDto,
    val navigation: NavigationDto = NavigationDto(),
    val utterance: String? = null,
    val event: String? = null,
    val now: String? = null,
    @SerialName("selected_place_id") val selectedPlaceId: String? = null,
    @SerialName("selected_track_id") val selectedTrackId: String? = null,
) {
    companion object {
        const val KIND_UTTERANCE = "user_utterance"
        const val KIND_EVENT = "vehicle_event"
    }
}

@Serializable
data class VehicleDto(
    val gear: String,
    @SerialName("doors_closed") val doorsClosed: Boolean? = null,
    @SerialName("last_stop_minutes") val lastStopMinutes: Int? = null,
    @SerialName("continuous_driving_minutes") val continuousDrivingMinutes: Int? = null,
)

@Serializable
data class NavigationDto(
    @SerialName("distance_from_home_km") val distanceFromHomeKm: Double? = null,
    @SerialName("eta_minutes") val etaMinutes: Int? = null,
    @SerialName("arrival_local_time") val arrivalLocalTime: String? = null,
    @SerialName("destination_is_home") val destinationIsHome: Boolean? = null,
    @SerialName("minutes_since_departure") val minutesSinceDeparture: Int? = null,
)

@Serializable
data class AgentResponse(
    val status: String,
    val speech: String = "",
    val caption: String = "",
    @SerialName("proposal_type") val proposalType: String? = null,
    @SerialName("selected_place_id") val selectedPlaceId: String? = null,
    @SerialName("selected_track_id") val selectedTrackId: String? = null,
    val cards: List<CardDto> = emptyList(),
    val effects: List<EffectDto> = emptyList(),
)

@Serializable
data class CardDto(
    @SerialName("place_id") val placeId: String? = null,
    @SerialName("track_id") val trackId: String? = null,
    val name: String = "",
    /** place, rest_stop. 곡 카드는 비어 있다 */
    val kind: String = "",
    val category: String = "",
    val address: String = "",
    val summary: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("distance_km") val distanceKm: Double? = null,
    val artist: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class EffectDto(
    val type: String,
    val place: PlaceDto? = null,
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("capsule_id") val capsuleId: String? = null,
) {
    companion object {
        const val ADD_WAYPOINT = "ADD_WAYPOINT"
        const val PLAY_MUSIC = "PLAY_MUSIC"
        const val PLAY_CAPSULE = "PLAY_CAPSULE"
    }
}

@Serializable
data class PlaceDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("navi_poi_id") val naviPoiId: String? = null,
    @SerialName("navi_poi_sub_id") val naviPoiSubId: String? = null,
    val address: String = "",
)

@Serializable
data class TripRecordRequest(
    @SerialName("session_id") val sessionId: String,
    val type: String,
    val id: String,
    val name: String? = null,
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class StatusResponse(val status: String)

@Serializable
data class TripSummaryDto(
    @SerialName("session_id") val sessionId: String,
    val region: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("event_count") val eventCount: Int = 0,
)

@Serializable
data class TripListDto(val status: String, val trips: List<TripSummaryDto> = emptyList())

@Serializable
data class TripEventDto(
    val type: String,
    val id: String,
    val name: String = "",
    val category: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("recorded_at") val recordedAt: String = "",
)

@Serializable
data class TripEventsDto(
    val status: String,
    @SerialName("session_id") val sessionId: String = "",
    val events: List<TripEventDto> = emptyList(),
    /** [위도, 경도] 목록. 앨범 지도에 그리는 길 */
    val route: List<List<Double>> = emptyList(),
)

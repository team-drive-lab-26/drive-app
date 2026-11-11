package com.doran.app.core.data

import com.doran.app.core.network.AgentRequest
import com.doran.app.core.network.AgentResponse
import com.doran.app.core.network.DoranApi
import com.doran.app.core.network.EffectDto
import com.doran.app.core.network.NavigationDto
import com.doran.app.core.network.NetworkModule
import com.doran.app.core.network.TripRecordRequest
import com.doran.app.core.network.VehicleDto

data class AgentReply(
    val status: String,
    val speech: String,
    val caption: String,
    val cards: List<AgentCard>,
    val waypoint: WaypointRequest?,
    val requestedTrackId: String?,
    val requestedCapsuleId: String?,
)

data class AgentCard(
    val placeId: String?,
    val trackId: String?,
    val name: String,
    val kind: String = "",
    val category: String = "",
    val summary: String = "",
    val imageUrl: String = "",
    val distanceKm: Double? = null,
    val artist: String = "",
    val tags: List<String> = emptyList(),
) {
    val isTrack: Boolean get() = trackId != null
}

data class WaypointRequest(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val naviPoiId: String,
    val naviPoiSubId: String,
    val address: String,
)

class AgentRepository(baseUrl: String) {
    private val api: DoranApi? = baseUrl.takeIf { it.isNotBlank() }?.let(NetworkModule::doranApi)

    val isConfigured: Boolean get() = api != null

    suspend fun send(
        sessionId: String,
        vehicle: VehicleDto,
        utterance: String? = null,
        event: String? = null,
        navigation: NavigationDto = NavigationDto(),
        selectedPlaceId: String? = null,
        selectedTrackId: String? = null,
    ): AgentReply {
        val request = AgentRequest(
            sessionId = sessionId,
            kind = if (event == null) AgentRequest.KIND_UTTERANCE else AgentRequest.KIND_EVENT,
            vehicle = vehicle,
            navigation = navigation,
            utterance = utterance,
            event = event,
            selectedPlaceId = selectedPlaceId,
            selectedTrackId = selectedTrackId,
        )
        return requireApi().respond(request).toReply()
    }

    suspend fun recordTrip(sessionId: String, type: String, id: String, name: String? = null, category: String? = null) {
        // 정차 기록의 이름·좌표는 서버가 후보 목록에서 채운다. 목록에 없는 곳은 이름만 남는다
        requireApi().recordTrip(TripRecordRequest(sessionId, type, id, name, category))
    }

    private fun requireApi(): DoranApi = checkNotNull(api) { "AI 서버 주소가 없어" }
}

private fun AgentResponse.toReply(): AgentReply {
    val waypoint = effects.firstOrNull { it.type == EffectDto.ADD_WAYPOINT }?.place?.let { place ->
        // Pleos 내비는 POI ID 없이 좌표만으로도 경유지를 받는다(에뮬레이터에서 확인)
        WaypointRequest(
            id = place.id,
            name = place.name,
            latitude = place.latitude,
            longitude = place.longitude,
            naviPoiId = place.naviPoiId.orEmpty(),
            naviPoiSubId = place.naviPoiSubId.orEmpty(),
            address = place.address,
        )
    }
    return AgentReply(
        status = status,
        speech = speech,
        caption = caption,
        cards = cards.map {
            AgentCard(it.placeId, it.trackId, it.name, it.kind, it.category, it.summary, it.imageUrl, it.distanceKm, it.artist, it.tags)
        },
        waypoint = waypoint,
        requestedTrackId = effects.firstOrNull { it.type == EffectDto.PLAY_MUSIC }?.trackId,
        requestedCapsuleId = effects.firstOrNull { it.type == EffectDto.PLAY_CAPSULE }?.capsuleId,
    )
}

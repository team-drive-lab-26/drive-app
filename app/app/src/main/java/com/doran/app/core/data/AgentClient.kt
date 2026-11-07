package com.doran.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AgentReply(
    val status: String,
    val speech: String,
    val caption: String,
    val waypoint: WaypointRequest?,
    val requestedTrackId: String?,
    val requestedCapsuleId: String?,
)

data class WaypointRequest(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val naviPoiId: String,
    val naviPoiSubId: String,
    val address: String,
)

class AgentClient(private val baseUrl: String) {
    suspend fun send(
        sessionId: String,
        gear: String,
        utterance: String? = null,
        event: String? = null,
        vehicleExtras: JSONObject = JSONObject(),
        navigation: JSONObject = JSONObject(),
    ): AgentReply = withContext(Dispatchers.IO) {
        val vehicle = JSONObject(vehicleExtras.toString()).put("gear", gear)
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("kind", if (event == null) "user_utterance" else "vehicle_event")
            .put("vehicle", vehicle)
            .put("navigation", navigation)
        if (utterance != null) body.put("utterance", utterance)
        if (event != null) body.put("event", event)
        val connection = URL("$baseUrl/v1/agent/respond").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw IllegalStateException("AI 서버 응답 ${connection.responseCode}")
            val result = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val effects = result.optJSONArray("effects")
            var waypoint: WaypointRequest? = null
            var trackId: String? = null
            var capsuleId: String? = null
            if (effects != null) {
                for (index in 0 until effects.length()) {
                    val effect = effects.optJSONObject(index) ?: continue
                    when (effect.optString("type")) {
                        "ADD_WAYPOINT" -> {
                            val place = effect.optJSONObject("place") ?: continue
                            waypoint = WaypointRequest(
                                id = place.getString("id"),
                                name = place.getString("name"),
                                latitude = place.getDouble("latitude"),
                                longitude = place.getDouble("longitude"),
                                naviPoiId = place.getString("navi_poi_id"),
                                naviPoiSubId = place.optString("navi_poi_sub_id", "0"),
                                address = place.optString("address"),
                            )
                        }
                        "PLAY_MUSIC" -> trackId = effect.optString("track_id")
                        "PLAY_CAPSULE" -> capsuleId = effect.optString("capsule_id")
                    }
                }
            }
            AgentReply(
                status = result.optString("status"),
                speech = result.optString("speech"),
                caption = result.optString("caption"),
                waypoint = waypoint,
                requestedTrackId = trackId,
                requestedCapsuleId = capsuleId,
            )
        } finally {
            connection.disconnect()
        }
    }
}

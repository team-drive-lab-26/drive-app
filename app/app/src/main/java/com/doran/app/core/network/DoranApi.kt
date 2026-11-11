package com.doran.app.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DoranApi {
    @POST("v1/agent/respond")
    suspend fun respond(@Body request: AgentRequest): AgentResponse

    @POST("v1/trip/record")
    suspend fun recordTrip(@Body request: TripRecordRequest): StatusResponse

    @GET("v1/trip/record")
    suspend fun trips(): TripListDto

    @GET("v1/trip/record")
    suspend fun tripEvents(@Query("session_id") sessionId: String): TripEventsDto

    @GET("health")
    suspend fun health(): StatusResponse
}

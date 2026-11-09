package com.doran.app.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DoranApi {
    @POST("v1/agent/respond")
    suspend fun respond(@Body request: AgentRequest): AgentResponse

    @POST("v1/trip/record")
    suspend fun recordTrip(@Body request: TripRecordRequest): StatusResponse

    @GET("health")
    suspend fun health(): StatusResponse
}

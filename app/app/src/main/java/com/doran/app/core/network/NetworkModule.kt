package com.doran.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // 주행 중 끊김을 빨리 알리도록 연결은 짧게, LLM 응답은 느릴 수 있어 읽기는 길게 둔다
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun doranApi(baseUrl: String): DoranApi = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
        .build()
        .create(DoranApi::class.java)
}

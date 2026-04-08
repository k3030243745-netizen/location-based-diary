package com.example.myapplication

import retrofit2.http.GET
import retrofit2.http.Query

interface DatahubApi {

    @GET("health")
    suspend fun health(): Map<String, Any>

    @GET("intent/classify")
    suspend fun classifyIntent(
        @Query("purpose") purpose: String
    ): IntentClassifyResponse

    @GET("pois/near")
    suspend fun nearPois(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int,
        @Query("limit") limit: Int = 20,
        @Query("classesPrefix") classesPrefix: String? = null,
        @Query("classIn") classIn: String? = null,
        @Query("categoryId") categoryId: String? = null,
    ): List<RemotePoi>
}
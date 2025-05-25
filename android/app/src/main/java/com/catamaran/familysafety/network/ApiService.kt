package com.catamaran.familysafety.network

import retrofit2.Response
import retrofit2.http.*

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val user: User?,
    val tokens: Tokens?,
    val message: String?
)

data class User(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String
)

data class Tokens(
    val accessToken: String,
    val refreshToken: String
)

data class SyncRequest(
    val calls: List<CallData>,
    val sms: List<SmsData>
)

data class CallData(
    val phoneNumber: String,
    val contactName: String?,
    val duration: Long,
    val timestamp: String,
    val callType: String
)

data class SmsData(
    val senderNumber: String,
    val contactName: String?,
    val messageType: String,
    val timestamp: String
)

data class SyncResponse(
    val success: Boolean,
    val message: String?,
    val processed: ProcessedData?
)

data class ProcessedData(
    val calls: Int,
    val sms: Int
)

interface ApiService {
    
    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>
    
    @POST("api/activity/sync")
    suspend fun syncActivity(
        @Header("Authorization") authorization: String,
        @Body syncRequest: SyncRequest
    ): Response<SyncResponse>
    
    @GET("api/auth/me")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): Response<User>
} 
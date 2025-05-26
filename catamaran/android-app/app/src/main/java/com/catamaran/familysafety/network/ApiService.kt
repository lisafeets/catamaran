package com.catamaran.familysafety.network

import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    
    @GET("api/health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    @POST("api/monitoring/call-logs")
    suspend fun uploadCallLogs(@Body callLogs: List<CallLogEntry>): Response<UploadResponse>
    
    @POST("api/monitoring/sms")
    suspend fun uploadSMSMessages(@Body smsMessages: List<SMSEntry>): Response<UploadResponse>
    
    @GET("api/monitoring/status")
    suspend fun getMonitoringStatus(): Response<MonitoringStatusResponse>
    
    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>
    
    companion object {
        private const val BASE_URL = "https://catamaran-production-3422.up.railway.app/"
        
        fun create(): ApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

// Data classes for API responses
data class HealthResponse(
    val status: String,
    val timestamp: String
)

data class UploadResponse(
    val success: Boolean,
    val message: String,
    val recordsProcessed: Int
)

data class MonitoringStatusResponse(
    val isActive: Boolean,
    val lastSync: String?,
    val deviceCount: Int
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val phone: String?
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val user: User?,
    val tokens: Tokens?
)

data class User(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val phone: String?,
    val createdAt: String
)

data class Tokens(
    val accessToken: String,
    val refreshToken: String
) 
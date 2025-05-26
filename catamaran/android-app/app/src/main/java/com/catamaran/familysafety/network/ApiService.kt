package com.catamaran.familysafety.network

import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry
import com.catamaran.familysafety.utils.PreferenceManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    @POST("api/activity/sync")
    suspend fun syncActivity(@Body request: ActivitySyncRequest): Response<UploadResponse>
    
    @GET("api/logs/summary")
    suspend fun getMonitoringStatus(): Response<MonitoringStatusResponse>
    
    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>
    
    companion object {
        private const val BASE_URL = "https://catamaran-production-3422.up.railway.app/"
        
        fun create(preferenceManager: PreferenceManager): ApiService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            // Auth interceptor to add Bearer token to requests
            // TEMPORARILY USING HARDCODED TOKEN FOR TESTING
            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                // Hardcoded test token for testing
                val testToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6InRlc3QtdXNlci0xMjMiLCJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJyb2xlIjoiU0VOSU9SIiwiaWF0IjoxNzQ4MjI3NDA1LCJleHAiOjE3NDgzMTM4MDV9.K_Lr6ohLGbPPze-WlZRBR6m6qPsLy6VHXmXL7xJQgw4"
                
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $testToken")
                    .build()
                
                chain.proceed(newRequest)
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor) // RE-ENABLED WITH TEST TOKEN
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

// Request wrapper classes to match Railway backend API
data class ActivitySyncRequest(
    val callLogs: List<CallLogEntryAPI> = emptyList(),
    val smsLogs: List<SMSEntryAPI> = emptyList()
)

// API-compatible data classes for Railway backend
data class CallLogEntryAPI(
    val phoneNumber: String,
    val duration: Int,
    val callType: String, // "incoming", "outgoing", "missed"
    val timestamp: String,
    val isKnownContact: Boolean,
    val contactName: String?
)

data class SMSEntryAPI(
    val senderNumber: String, // Railway backend expects 'senderNumber' not 'phoneNumber'
    val messageCount: Int,
    val messageType: String, // Railway backend expects 'messageType' not 'smsType' - "received" or "sent"
    val timestamp: String,
    val isKnownContact: Boolean,
    val contactName: String?,
    val hasLink: Boolean = false
)

// Data classes for API responses
data class HealthResponse(
    val status: String,
    val timestamp: String
)

data class UploadResponse(
    val success: Boolean,
    val message: String,
    val processed: Int
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
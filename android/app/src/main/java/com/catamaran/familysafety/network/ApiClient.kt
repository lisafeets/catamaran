package com.catamaran.familysafety.network

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    
    private val preferences = context.getSharedPreferences("catamaran_auth", Context.MODE_PRIVATE)
    private val baseUrl = "http://10.0.2.2:3001/" // Android emulator localhost
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                if (loginResponse.success && loginResponse.tokens != null) {
                    // Store tokens
                    saveTokens(loginResponse.tokens)
                    Log.d("ApiClient", "Login successful for user: $email")
                }
                Result.success(loginResponse)
            } else {
                Log.e("ApiClient", "Login failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Login error", e)
            Result.failure(e)
        }
    }
    
    suspend fun syncActivity(calls: List<com.catamaran.familysafety.data.CallLogEntry>, 
                           sms: List<com.catamaran.familysafety.data.SmsLogEntry>): Result<SyncResponse> {
        return try {
            val accessToken = getAccessToken()
            if (accessToken.isNullOrEmpty()) {
                return Result.failure(Exception("No access token available"))
            }
            
            val callData = calls.map { call ->
                CallData(
                    phoneNumber = call.phoneNumber,
                    contactName = call.contactName,
                    duration = call.duration,
                    timestamp = formatTimestamp(call.timestamp),
                    callType = call.callType
                )
            }
            
            val smsData = sms.map { sms ->
                SmsData(
                    senderNumber = sms.senderNumber,
                    contactName = sms.contactName,
                    messageType = sms.messageType,
                    timestamp = formatTimestamp(sms.timestamp)
                )
            }
            
            val syncRequest = SyncRequest(callData, smsData)
            val response = apiService.syncActivity("Bearer $accessToken", syncRequest)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d("ApiClient", "Sync successful: ${calls.size} calls, ${sms.size} SMS")
                Result.success(response.body()!!)
            } else {
                Log.e("ApiClient", "Sync failed: ${response.code()} ${response.message()}")
                Result.failure(Exception("Sync failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Sync error", e)
            Result.failure(e)
        }
    }
    
    private fun saveTokens(tokens: Tokens) {
        preferences.edit()
            .putString(ACCESS_TOKEN_KEY, tokens.accessToken)
            .putString(REFRESH_TOKEN_KEY, tokens.refreshToken)
            .apply()
    }
    
    fun getAccessToken(): String? {
        return preferences.getString(ACCESS_TOKEN_KEY, null)
    }
    
    fun getRefreshToken(): String? {
        return preferences.getString(REFRESH_TOKEN_KEY, null)
    }
    
    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrEmpty()
    }
    
    fun logout() {
        preferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .apply()
        Log.d("ApiClient", "User logged out")
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }
    
    companion object {
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
    }
} 
package com.halimos.halimos

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/Security/authenticate")
    fun login(@Body body: RequestBody): Call<ResponseBody>
}

data class LoginRequest(
    val User: String,
    val Password: String
)

data class ApiResponse<T>(
    val Data: T?,
    val Success: Boolean,
    val Title: String?,
    val Message: String?
)

data class LoginData(
    val Id: String,
    val UserName: String,
    val Email: String,
    val Roles: List<String>,
    val IsVerified: Boolean,
    val JWToken: String
)

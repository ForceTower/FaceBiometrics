package dev.forcetower.fullfacepoc.service

import dev.forcetower.fullfacepoc.model.AuthModel
import dev.forcetower.fullfacepoc.model.BiometricResponse
import dev.forcetower.fullfacepoc.model.RegisterModel
import retrofit2.http.Body
import retrofit2.http.POST

interface FullfaceService {
    @POST("cadapi/users/register")
    suspend fun register(@Body model: RegisterModel): BiometricResponse

    @POST("autapi/users/authenticate")
    suspend fun authenticate(@Body model: AuthModel): BiometricResponse
}
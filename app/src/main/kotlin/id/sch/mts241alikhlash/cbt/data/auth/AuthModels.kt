package id.sch.mts241alikhlash.cbt.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val ROLE_STUDENT = "STUDENT"
const val ROLE_TEACHER = "TEACHER"

@Serializable
data class AuthUser(val id: String, val name: String, val roles: Set<String>)

val AuthUser.isStudent: Boolean get() = ROLE_STUDENT in roles
val AuthUser.isTeacher: Boolean get() = ROLE_TEACHER in roles

@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)

@Serializable
data class StoredAuth(val tokens: AuthTokens, val user: AuthUser)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class AuthUserDto(val id: String, val name: String, val roles: List<String>)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUserDto
)

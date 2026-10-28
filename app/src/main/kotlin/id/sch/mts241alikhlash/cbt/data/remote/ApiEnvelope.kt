package id.sch.mts241alikhlash.cbt.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

const val GENERIC_ERROR_MESSAGE = "Terjadi kesalahan. Coba lagi."
const val NETWORK_ERROR_MESSAGE = "Tidak dapat terhubung ke server."

@Serializable
data class ApiEnvelope<T>(
    val statusCode: Int,
    val message: String = "",
    val data: T? = null
)

fun Throwable.userMessage(): String = (this as? ApiException)?.message ?: GENERIC_ERROR_MESSAGE

class ApiException(val statusCode: Int, override val message: String) : Exception(message)

fun <T> ApiEnvelope<T>.unwrap(): T =
    data ?: throw ApiException(statusCode, message.ifBlank { GENERIC_ERROR_MESSAGE })

suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T = guarded { block().unwrap() }

suspend fun <T> guarded(block: suspend () -> T): T =
    try {
        block()
    } catch (e: ApiException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw ApiException(e.code(), e.serverMessage() ?: GENERIC_ERROR_MESSAGE)
    } catch (e: IOException) {
        throw ApiException(0, NETWORK_ERROR_MESSAGE)
    } catch (e: SerializationException) {
        throw ApiException(0, GENERIC_ERROR_MESSAGE)
    }

private val errorJson = Json { ignoreUnknownKeys = true }

private fun HttpException.serverMessage(): String? =
    runCatching {
        val body = response()?.errorBody()?.string().orEmpty()
        errorJson.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

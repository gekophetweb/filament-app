package nl.jaap.filamentspools.api

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** Every way an API call can fail, mapped from the documented responses (spec section 7). */
sealed class ApiError : Exception() {
    data object Unauthorized : ApiError() { private fun readResolve(): Any = Unauthorized }
    data object MissingAbility : ApiError() { private fun readResolve(): Any = MissingAbility }
    data object IpBlocked : ApiError() { private fun readResolve(): Any = IpBlocked }
    data object NotFound : ApiError() { private fun readResolve(): Any = NotFound }
    data class Validation(override val message: String, val fields: Map<String, List<String>>) : ApiError()
    data class RateLimited(val retryAfterSeconds: Int?) : ApiError()
    data class Network(override val cause: IOException) : ApiError()
    data class Http(val code: Int) : ApiError()
}

/** Runs an API call and turns every failure into an [ApiError]. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        Result.failure(e)
    } catch (e: HttpException) {
        Result.failure(mapHttp(e))
    } catch (e: SerializationException) {
        Log.w("Api", "Response could not be parsed", e)
        Result.failure(ApiError.Http(-1))
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization reports missing fields as IllegalArgumentException subclasses.
        Log.w("Api", "Response could not be parsed", e)
        Result.failure(ApiError.Http(-1))
    } catch (e: IOException) {
        Result.failure(ApiError.Network(e))
    }

/** Maps a non-2xx response to an [ApiError]. Also used for calls that return a raw `Response`. */
fun mapHttp(e: HttpException): ApiError {
    val response = e.response()
    val contentType = response?.errorBody()?.contentType()?.toString().orEmpty()
    val body = runCatching { response?.errorBody()?.string() }.getOrNull().orEmpty()
    return when (e.code()) {
        401 -> ApiError.Unauthorized
        403 -> if (contentType.contains("json", ignoreCase = true)) ApiError.MissingAbility else ApiError.IpBlocked
        404 -> ApiError.NotFound
        422 -> parseValidation(body)
        429 -> ApiError.RateLimited(response?.headers()?.get("Retry-After")?.trim()?.toIntOrNull())
        else -> ApiError.Http(e.code())
    }
}

private fun parseValidation(body: String): ApiError.Validation {
    val obj = runCatching { ApiJson.parseToJsonElement(body).jsonObject }.getOrNull()
    val message = obj?.get("message")?.jsonPrimitive?.contentOrNull ?: "The input is not valid."
    val fields = (obj?.get("errors") as? JsonObject).orEmpty().mapValues { (_, v) ->
        (v as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    }
    return ApiError.Validation(message, fields)
}

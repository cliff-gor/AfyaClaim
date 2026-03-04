package ke.afyaclaim.pos.data.remote

import ke.afyaclaim.pos.data.remote.dto.ErrorEnvelope
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Turns transport failures into short, human-readable messages. */
object ApiErrors {
    private val json = Json { ignoreUnknownKeys = true }

    fun message(error: Throwable): String = when (error) {
        is HttpException -> {
            val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
            val parsed = body?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
            parsed?.error?.message ?: when (error.code()) {
                401 -> "Email or password is incorrect."
                403 -> "This account cannot use the POS app."
                in 500..599 -> "AfyaClaim is having trouble. Try again shortly."
                else -> "Request failed (${error.code()})."
            }
        }
        is IOException -> "Can't reach AfyaClaim. Check your connection."
        else -> error.message ?: "Something went wrong."
    }
}

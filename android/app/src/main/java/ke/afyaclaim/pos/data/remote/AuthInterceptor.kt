package ke.afyaclaim.pos.data.remote

import ke.afyaclaim.pos.data.prefs.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Adds the bearer token and drops the session when the server says it has expired. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.cachedToken()
        val request = chain.request().newBuilder().apply {
            header("Accept", "application/json")
            if (token != null && chain.request().url.encodedPath.endsWith("auth/login").not()) {
                header("Authorization", "Bearer $token")
            }
        }.build()
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            sessionStore.markExpired()
        }
        return response
    }
}

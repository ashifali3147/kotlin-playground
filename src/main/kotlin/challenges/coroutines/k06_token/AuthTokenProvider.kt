package challenges.coroutines.k06_token

data class Token(val value: String, val expiresAtMs: Long)

interface AuthApi {
    /** Exchanges the refresh token for a new access token. Slow network call; may throw. */
    suspend fun refresh(): Token
}

/**
 * Supplies the access token for every API request.
 *
 * The HTTP layer calls [validToken] before each request, from many coroutines on many threads at once.
 * When a request comes back 401 Unauthorized, the HTTP layer calls [invalidate] with the token
 * that was rejected, then retries with [validToken].
 */
class AuthTokenProvider(
    private val authApi: AuthApi,
    private val clock: () -> Long,
) {
    private var token: Token? = null

    suspend fun validToken(): String {
        val current = token
        if (current != null && clock() < current.expiresAtMs) return current.value

        val fresh = authApi.refresh()
        token = fresh
        return fresh.value
    }

    /** The server rejected [rejected]. The next [validToken] call must not return it. */
    fun invalidate(rejected: String) {
        token = null
    }
}

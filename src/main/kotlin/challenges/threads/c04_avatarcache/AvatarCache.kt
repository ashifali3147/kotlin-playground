package challenges.threads.c04_avatarcache

class Avatar(val userId: String, val bytes: ByteArray)

class AvatarLoadException(message: String) : RuntimeException(message)

/**
 * In-memory cache of decoded user avatars, shared by every screen: chat list, order history,
 * rider profile, restaurant reviews.
 *
 * [loader] downloads and decodes an avatar: slow (100ms–2s), costs mobile data, and may throw
 * [AvatarLoadException]. List rows bind on a background pool, so many threads call [get] at once,
 * very often for the same user.
 */
class AvatarCache(private val loader: (userId: String) -> Avatar) {

    private val cache = HashMap<String, Avatar>()

    fun get(userId: String): Avatar {
        val cached = cache[userId]
        if (cached != null) return cached

        val avatar = loader(userId)
        cache[userId] = avatar
        return avatar
    }
}

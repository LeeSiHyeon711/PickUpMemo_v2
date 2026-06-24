package com.itmakesome.pickupmemo2.route

import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object RouteService {
    const val TIMEOUT_MS = 5000L

    @Volatile
    private var provider: RouteProvider? = null
    private val cache = ConcurrentHashMap<String, RouteInfo>()

    fun init(apiKey: String) {
        if (provider == null) {
            synchronized(this) {
                if (provider == null) provider = KakaoRouteProvider(apiKey)
            }
        }
    }

    suspend fun resolve(pickup: String, dest: String): RouteInfo? {
        val key = "$pickup|$dest"
        cache[key]?.let { return it }
        val p = provider ?: return null
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            val origin = p.geocode(pickup, GeoQueryType.KEYWORD) ?: return@withTimeoutOrNull null
            val destPt = geocodeDestWithFallback(p, dest) ?: return@withTimeoutOrNull null
            p.getRoute(origin, destPt)
        }
        if (result != null) cache[key] = result
        return result
    }

    private suspend fun geocodeDestWithFallback(p: RouteProvider, dest: String): GeoPoint? {
        p.geocode(dest, GeoQueryType.ADDRESS)?.let { return it }
        p.geocode(maskRemoved(dest), GeoQueryType.ADDRESS)?.let { return it }
        p.geocode(roadHead(dest), GeoQueryType.ADDRESS)?.let { return it }
        p.geocode(dest, GeoQueryType.KEYWORD)?.let { return it }
        return null
    }

    private fun maskRemoved(dest: String): String =
        dest.replace("****", " ")
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun roadHead(dest: String): String {
        val cleaned = maskRemoved(dest)
        val m = Regex("^(.*?[가-힣]+[로길])").find(cleaned)
        return (m?.groupValues?.get(1) ?: cleaned).trim()
    }
}

package com.itmakesome.pickupmemo2.route

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object RouteService {
    const val TIMEOUT_MS = 5000L
    private const val TAG = "RouteService"

    @Volatile
    private var provider: RouteProvider? = null

    @Volatile
    private var apiKeyBlank = true

    private val cache = ConcurrentHashMap<String, RouteInfo>()

    fun init(apiKey: String) {
        apiKeyBlank = apiKey.isBlank()
        Log.d(TAG, "init: apiKey isBlank=$apiKeyBlank, length=${apiKey.length}")
        if (provider == null) {
            synchronized(this) {
                if (provider == null) provider = KakaoRouteProvider(apiKey)
            }
        }
    }

    suspend fun resolve(pickup: String, dest: String): RouteResolveOutcome {
        val cacheKey = "$pickup|$dest"
        cache[cacheKey]?.let {
            Log.d(TAG, "resolve cache hit: pickup=$pickup")
            return RouteResolveOutcome(it)
        }

        if (provider == null) {
            Log.w(TAG, "resolve fail: provider not initialized")
            return RouteResolveOutcome(null, RouteFailure.PROVIDER_NOT_INIT)
        }
        if (apiKeyBlank) {
            Log.w(TAG, "resolve fail: API key blank (isBlank=true, length=0)")
            return RouteResolveOutcome(null, RouteFailure.API_KEY_BLANK)
        }

        var failure: RouteFailure? = null
        var detail: String? = null

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            val p = provider!!
            val origin = p.geocode(pickup, GeoQueryType.KEYWORD)
            if (origin == null) {
                failure = RouteFailure.PICKUP_GEOCODE_FAILED
                detail = "keyword query=$pickup"
                Log.w(TAG, "resolve fail: pickup geocode (KEYWORD) failed, query=$pickup")
                return@withTimeoutOrNull null
            }
            Log.d(TAG, "pickup geocode ok: $pickup -> (${origin.lng}, ${origin.lat})")

            val destPt = geocodeDestWithFallback(p, dest)
            if (destPt == null) {
                failure = RouteFailure.DEST_GEOCODE_FAILED
                detail = "all 4 fallback steps failed, dest=$dest"
                Log.w(TAG, "resolve fail: dest geocode fallback 1~4 all failed, dest=$dest")
                return@withTimeoutOrNull null
            }
            Log.d(TAG, "dest geocode ok: $dest -> (${destPt.lng}, ${destPt.lat})")

            val route = p.getRoute(origin, destPt)
            if (route == null) {
                failure = RouteFailure.DIRECTIONS_FAILED
                detail = "directions API returned no route"
                Log.w(TAG, "resolve fail: directions API failed")
                return@withTimeoutOrNull null
            }
            route
        }

        if (result == null && failure == null) {
            failure = RouteFailure.TIMEOUT
            detail = "exceeded ${TIMEOUT_MS}ms"
            Log.w(TAG, "resolve fail: timeout after ${TIMEOUT_MS}ms")
        }

        if (result != null) {
            cache[cacheKey] = result
            Log.d(TAG, "resolve ok: ${result.summaryText}")
            return RouteResolveOutcome(result)
        }

        return RouteResolveOutcome(null, failure, detail)
    }

    private suspend fun geocodeDestWithFallback(p: RouteProvider, dest: String): GeoPoint? {
        p.geocode(dest, GeoQueryType.ADDRESS)?.also {
            Log.d(TAG, "dest geocode step1 ok (원문 ADDRESS)")
            return it
        }
        Log.w(TAG, "dest geocode step1 fail (원문 ADDRESS): $dest")

        val masked = maskRemoved(dest)
        p.geocode(masked, GeoQueryType.ADDRESS)?.also {
            Log.d(TAG, "dest geocode step2 ok (마스킹 제거 ADDRESS): $masked")
            return it
        }
        Log.w(TAG, "dest geocode step2 fail (마스킹 제거 ADDRESS): $masked")

        val road = roadHead(dest)
        p.geocode(road, GeoQueryType.ADDRESS)?.also {
            Log.d(TAG, "dest geocode step3 ok (도로명 앞부분 ADDRESS): $road")
            return it
        }
        Log.w(TAG, "dest geocode step3 fail (도로명 앞부분 ADDRESS): $road")

        p.geocode(dest, GeoQueryType.KEYWORD)?.also {
            Log.d(TAG, "dest geocode step4 ok (KEYWORD)")
            return it
        }
        Log.w(TAG, "dest geocode step4 fail (KEYWORD): $dest")
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

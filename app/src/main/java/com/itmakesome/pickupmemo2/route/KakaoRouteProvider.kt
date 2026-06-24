package com.itmakesome.pickupmemo2.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KakaoRouteProvider(private val restApiKey: String) : RouteProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    override suspend fun geocode(query: String, type: GeoQueryType): GeoPoint? =
        withContext(Dispatchers.IO) {
            if (restApiKey.isBlank() || query.isBlank()) return@withContext null
            val base = when (type) {
                GeoQueryType.ADDRESS -> LOCAL_ADDRESS
                GeoQueryType.KEYWORD -> LOCAL_KEYWORD
            }
            val url = "$base?query=${URLEncoder.encode(query, "UTF-8")}"
            val body = get(url) ?: return@withContext null
            try {
                val docs = JSONObject(body).optJSONArray("documents") ?: return@withContext null
                if (docs.length() == 0) return@withContext null
                val d = docs.getJSONObject(0)
                val lng = d.getString("x").toDouble()
                val lat = d.getString("y").toDouble()
                GeoPoint(lng, lat)
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun getRoute(origin: GeoPoint, dest: GeoPoint): RouteInfo? =
        withContext(Dispatchers.IO) {
            if (restApiKey.isBlank()) return@withContext null
            val url = "$DIRECTIONS_BASE?origin=${origin.lng},${origin.lat}" +
                "&destination=${dest.lng},${dest.lat}"
            val body = get(url) ?: return@withContext null
            try {
                val routes = JSONObject(body).optJSONArray("routes") ?: return@withContext null
                if (routes.length() == 0) return@withContext null
                val r0 = routes.getJSONObject(0)
                if (r0.optInt("result_code", 0) != 0) return@withContext null
                val summary = r0.getJSONObject("summary")
                val distance = summary.getInt("distance")
                val duration = summary.getInt("duration")
                RouteInfo(distance, duration, RouteInfo.summaryOf(distance, duration), "kakao")
            } catch (e: Exception) {
                null
            }
        }

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "KakaoAK $restApiKey").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val LOCAL_ADDRESS = "https://dapi.kakao.com/v2/local/search/address.json"
        private const val LOCAL_KEYWORD = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val DIRECTIONS_BASE = "https://apis-navi.kakaomobility.com/v1/directions"
    }
}

package com.itmakesome.pickupmemo2.route

import android.util.Log
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
            if (restApiKey.isBlank()) {
                Log.w(TAG, "geocode skip: API key blank")
                return@withContext null
            }
            if (query.isBlank()) {
                Log.w(TAG, "geocode skip: blank query (type=$type)")
                return@withContext null
            }
            val base = when (type) {
                GeoQueryType.ADDRESS -> LOCAL_ADDRESS
                GeoQueryType.KEYWORD -> LOCAL_KEYWORD
            }
            val url = "$base?query=${URLEncoder.encode(query, "UTF-8")}"
            val label = "geocode($type)"
            val body = get(url, label) ?: return@withContext null
            try {
                val docs = JSONObject(body).optJSONArray("documents")
                if (docs == null || docs.length() == 0) {
                    Log.w(TAG, "$label empty documents, query=$query body=${body.take(BODY_LOG_MAX)}")
                    return@withContext null
                }
                val d = docs.getJSONObject(0)
                val lng = d.getString("x").toDouble()
                val lat = d.getString("y").toDouble()
                GeoPoint(lng, lat)
            } catch (e: Exception) {
                Log.w(TAG, "$label parse error: ${e.message}, body=${body.take(BODY_LOG_MAX)}")
                null
            }
        }

    override suspend fun getRoute(origin: GeoPoint, dest: GeoPoint): RouteInfo? =
        withContext(Dispatchers.IO) {
            if (restApiKey.isBlank()) {
                Log.w(TAG, "getRoute skip: API key blank")
                return@withContext null
            }
            val url = "$DIRECTIONS_BASE?origin=${origin.lng},${origin.lat}" +
                "&destination=${dest.lng},${dest.lat}"
            val body = get(url, "getRoute") ?: return@withContext null
            try {
                val routes = JSONObject(body).optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    Log.w(TAG, "getRoute empty routes, body=${body.take(BODY_LOG_MAX)}")
                    return@withContext null
                }
                val r0 = routes.getJSONObject(0)
                val resultCode = r0.optInt("result_code", 0)
                if (resultCode != 0) {
                    Log.w(TAG, "getRoute result_code=$resultCode, body=${body.take(BODY_LOG_MAX)}")
                    return@withContext null
                }
                val summary = r0.getJSONObject("summary")
                val distance = summary.getInt("distance")
                val duration = summary.getInt("duration")
                RouteInfo(distance, duration, RouteInfo.summaryOf(distance, duration), "kakao")
            } catch (e: Exception) {
                Log.w(TAG, "getRoute parse error: ${e.message}, body=${body.take(BODY_LOG_MAX)}")
                null
            }
        }

    private fun get(url: String, label: String): String? = try {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "KakaoAK $restApiKey").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful) {
                Log.w(
                    TAG,
                    "$label HTTP ${resp.code}, body=${body?.take(BODY_LOG_MAX) ?: "(empty)"}"
                )
                null
            } else {
                body
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "$label network error: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "KakaoRouteProvider"
        private const val BODY_LOG_MAX = 200

        private const val LOCAL_ADDRESS = "https://dapi.kakao.com/v2/local/search/address.json"
        private const val LOCAL_KEYWORD = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val DIRECTIONS_BASE = "https://apis-navi.kakaomobility.com/v1/directions"
    }
}

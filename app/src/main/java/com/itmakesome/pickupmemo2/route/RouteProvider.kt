package com.itmakesome.pickupmemo2.route

enum class GeoQueryType { ADDRESS, KEYWORD }

interface RouteProvider {
    /** 주소/키워드 문자열을 좌표로 변환. 실패 시 null 반환(예외 전파 금지). */
    suspend fun geocode(query: String, type: GeoQueryType): GeoPoint?

    /** 두 좌표 사이 경로의 거리/소요시간. 실패 시 null 반환(예외 전파 금지). */
    suspend fun getRoute(origin: GeoPoint, dest: GeoPoint): RouteInfo?
}

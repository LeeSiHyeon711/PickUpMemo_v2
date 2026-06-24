package com.itmakesome.pickupmemo2.route

data class RouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val summaryText: String,   // "약 X.Xkm · 약 N분"
    val rawProvider: String    // 예: "kakao"
) {
    companion object {
        /** distanceMeters/durationSeconds로 표시 문자열을 만든다 (PRD 9장). */
        fun summaryOf(distanceMeters: Int, durationSeconds: Int): String {
            val km = distanceMeters / 1000.0
            val min = Math.round(durationSeconds / 60.0)
            return "약 ${String.format("%.1f", km)}km · 약 ${min}분"
        }
    }
}

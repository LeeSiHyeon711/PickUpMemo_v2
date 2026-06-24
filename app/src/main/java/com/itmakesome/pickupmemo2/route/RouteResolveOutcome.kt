package com.itmakesome.pickupmemo2.route

enum class RouteFailure {
    API_KEY_BLANK,
    PROVIDER_NOT_INIT,
    PICKUP_GEOCODE_FAILED,
    DEST_GEOCODE_FAILED,
    DIRECTIONS_FAILED,
    TIMEOUT,
}

data class RouteResolveOutcome(
    val route: RouteInfo?,
    val failure: RouteFailure? = null,
    val detail: String? = null,
)

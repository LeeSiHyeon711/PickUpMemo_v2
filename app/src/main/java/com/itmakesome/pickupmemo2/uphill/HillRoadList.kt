package com.itmakesome.pickupmemo2.uphill

data class HillRoad(val roadName: String, val note: String)

object HillRoadList {
    /** v3.1에서 위험 도로명을 채운다. v3에서는 빈 리스트. */
    val roads: List<HillRoad> = emptyList()
}

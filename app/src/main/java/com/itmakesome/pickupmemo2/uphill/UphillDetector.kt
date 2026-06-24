package com.itmakesome.pickupmemo2.uphill

import com.itmakesome.pickupmemo2.route.GeoPoint

data class HillAlert(val roadName: String, val message: String)

interface UphillDetector {
    /** v3.1에서 구현. v3 골격에서는 항상 null. */
    fun detect(pickup: GeoPoint?, dest: GeoPoint?): HillAlert?
}

object NoopUphillDetector : UphillDetector {
    override fun detect(pickup: GeoPoint?, dest: GeoPoint?): HillAlert? = null
}

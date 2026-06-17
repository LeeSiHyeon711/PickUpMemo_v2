package com.itmakesome.pickupmemo2.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PickupAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* FEAT-07에서 구현 */ }
    override fun onInterrupt() {}
}

package com.za869765.imagine.data.usage

import com.za869765.imagine.data.prefs.SecurePrefs

object Pricing {
    const val PER_IMAGE_USD: Double = 0.05
    const val PER_VIDEO_SEC_USD: Double = 0.05
}

// Tracks budget consumption with optimistic ledger:
// 1. Call tentativeImage()/tentativeVideo() before API call.
// 2. On success → confirmImage()/confirmVideo() (no-op, charge already applied).
// 3. On policy-violation refund → refundImage()/refundVideo().
class UsageTracker(private val prefs: SecurePrefs) {

    fun estimateImage(count: Int = 1): Double = count * Pricing.PER_IMAGE_USD
    fun estimateVideo(seconds: Int): Double = seconds * Pricing.PER_VIDEO_SEC_USD

    fun canAffordImage(count: Int = 1): Boolean = canAfford(estimateImage(count))
    fun canAffordVideo(seconds: Int): Boolean = canAfford(estimateVideo(seconds))

    fun canAfford(cost: Double): Boolean {
        if (!prefs.lockOnLimit) return true
        return prefs.spent + cost <= prefs.budgetCap
    }

    fun tentativeImage(count: Int = 1) {
        prefs.imageCount += count
        prefs.spent += estimateImage(count)
    }

    fun tentativeVideo(seconds: Int) {
        prefs.videoSeconds += seconds
        prefs.spent += estimateVideo(seconds)
    }

    fun refundImage(count: Int = 1) {
        prefs.imageCount = (prefs.imageCount - count).coerceAtLeast(0)
        prefs.spent = (prefs.spent - estimateImage(count)).coerceAtLeast(0.0)
    }

    fun refundVideo(seconds: Int) {
        prefs.videoSeconds = (prefs.videoSeconds - seconds).coerceAtLeast(0)
        prefs.spent = (prefs.spent - estimateVideo(seconds)).coerceAtLeast(0.0)
    }
}

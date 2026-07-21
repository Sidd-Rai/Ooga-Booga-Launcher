package com.siddrai.oogaboogalauncher

import android.app.PendingIntent
import android.os.SystemClock

object NotificationLaunchBridge {
    private const val MAX_AGE_MS = 5 * 60_000L
    private data class ClickedIntent(val intent: PendingIntent, val capturedAt: Long)
    private val clicked = mutableMapOf<String, ClickedIntent>()

    @Synchronized
    fun capture(packageName: String, intent: PendingIntent) {
        prune()
        clicked[packageName] = ClickedIntent(intent, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun consume(packageName: String): PendingIntent? {
        prune()
        return clicked.remove(packageName)?.intent
    }

    @Synchronized
    fun discard(packageName: String) {
        clicked.remove(packageName)
    }

    private fun prune() {
        val oldestAllowed = SystemClock.elapsedRealtime() - MAX_AGE_MS
        clicked.entries.removeAll { it.value.capturedAt < oldestAllowed }
    }
}

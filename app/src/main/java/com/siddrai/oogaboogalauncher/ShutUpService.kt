package com.siddrai.oogaboogalauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.SharedPreferences

class ShutUpService : NotificationListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onListenerConnected() {
        getSharedPreferences(AppStore.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)
        cancelMatching()
    }
    override fun onNotificationPosted(notification: StatusBarNotification?) {
        notification ?: return
        if (notification.packageName in AppStore(this).shutUp()) cancelNotification(notification.key)
    }
    override fun onNotificationRemoved(
        notification: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        if (reason != REASON_CLICK || notification == null) return
        if (notification.packageName !in AppStore(this).distracting()) return
        notification.notification.contentIntent?.let {
            NotificationLaunchBridge.capture(notification.packageName, it)
        }
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "shut_up_apps") cancelMatching()
    }
    private fun cancelMatching() {
        val blocked = AppStore(this).shutUp()
        activeNotifications?.filter { it.packageName in blocked }?.forEach { cancelNotification(it.key) }
    }
    override fun onDestroy() {
        getSharedPreferences(AppStore.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }
}

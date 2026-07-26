package com.mrm.pgmanager.data.model

/** تنظیمات بروزرسانی و هشدارها؛ در EncryptedSharedPreferences ذخیره می‌شود. */
data class MonitoringSettings(
    val autoRefreshEnabled: Boolean = true,
    val refreshIntervalSeconds: Int = 10,
    /** false = فقط Dashboard، true = هنگام بازبودن کل اپ */
    val refreshWhileAppOpen: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyUserActions: Boolean = true,
    val notifyLimited: Boolean = true,
    val notifyExpired: Boolean = true,
    val notifyNearLimit: Boolean = true,
    val nearLimitPercent: Int = 80,
    val notifyNearExpiry: Boolean = true,
    val nearExpiryDays: Int = 1,
    val notifySystemHealth: Boolean = true,
    val cpuThreshold: Int = 85,
    val ramThreshold: Int = 85,
    val diskThreshold: Int = 90,
    val notifyPanelOffline: Boolean = true,
    val notifyNodeOffline: Boolean = true,
    /** هنگام قطع اتصال، آخرین داده‌های دریافت‌شده با برچسب «آفلاین» نمایش داده می‌شود. */
    val offlineCacheEnabled: Boolean = true,
    /** هشدار زمانی که کاربران آنلاین هم‌زمان از حد مجاز عبور کند. */
    val notifyCapacity: Boolean = false,
    val capacityOnlineLimit: Int = 500
)

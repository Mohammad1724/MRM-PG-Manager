package com.mrm.pgmanager.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.storage.SessionStore

/**
 * اکشنِ «بی‌صدا کردن» روی اعلان‌های هشدار — فاز ۸.۳.
 *
 * چرا BroadcastReceiver و نه باز کردنِ اپ: هدفِ این اکشن دقیقاً این است که کاربر
 * **بدونِ** باز کردنِ اپ، سروصدا را بخواباند. با یک broadcast، اعلان از سایهٔ
 * اعلان‌ها می‌رود و همان کلیدِ تنظیمات خاموش می‌شود؛ پس وقتی بعداً اپ را باز کند،
 * سوییچِ «تنظیمات ← اعلان‌ها» هم خاموش است و کاربر دچارِ «چرا دیگر نمی‌آید؟» نمی‌شود.
 *
 * خواندنِ EncryptedSharedPreferences روی رشتهٔ اصلی انجام نمی‌شود (`goAsync` +
 * تردِ کوتاه‌عمر)؛ Toast هم به‌خاطرِ نیازِ Looper به رشتهٔ اصلی پست می‌شود.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_MUTE) return
        val kind = intent.getStringExtra(NotificationHelper.EXTRA_MUTE_KIND) ?: return
        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIF_ID, -1)
        val muter = NotificationHelper.MUTE_TARGETS[kind] ?: return
        val appContext = context.applicationContext

        val pending = goAsync()
        Thread {
            try {
                val store = SessionStore(appContext)
                store.saveMonitoringSettings(muter(store.readMonitoringSettings()))
                // اعلانِ فعلی هم از سایه برود؛ وگرنه تا وقتی کاربر آن را نپاکاند،
                // به نظر می‌رسد «بی‌صدا کردن» چیزی را عوض نکرده است.
                if (notifId >= 0) NotificationManagerCompat.from(appContext).cancel(notifId)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, R.string.notif_mute_done, Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                // بی‌صدا کردن نباید هیچ‌وقت اپ را بیندازد؛ بدترین حالت = انجام‌نشدن.
            } finally {
                pending.finish()
            }
        }.start()
    }
}

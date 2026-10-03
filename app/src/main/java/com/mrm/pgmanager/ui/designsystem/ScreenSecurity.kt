package com.mrm.pgmanager.ui.designsystem

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * محافظتِ **هدفمند** از صفحه — جایگزینِ پوششِ سراسریِ فاز ۷.
 *
 * پیش از این، تنظیماتِ «حریمِ خصوصی» کلِ پنجرهٔ Activity را با `FLAG_SECURE`
 * می‌پوشاند و پیش‌فرضش هم روشن بود؛ نتیجه: کاربر نمی‌توانست از هیچ‌جای اپ
 * اسکرین‌شات بگیرد (و همین جلوی گزارشِ باگ‌دادنِ خودش را می‌گرفت).
 *
 * حالا:
 *  • پیش‌فرضِ اپ اسکرین‌شات‌پذیر است.
 *  • فقط سطح‌هایی که **آگاهانه یک راز را نشان می‌دهند** (لینکِ اشتراک/QR) خودشان
 *    درخواستِ محافظت می‌کنند — همان لحظه‌ای که روی صفحه‌اند.
 *  • اگر کاربر خودش بخواهد، سوییچِ تنظیمات هنوز کلِ اپ را محافظت می‌کند.
 *
 * نکتهٔ فنیِ مهمی که همین‌جا حل شد: `FLAG_SECURE` روی پنجرهٔ Activity، شاملِ
 * **پنجرهٔ دیالوگ‌های Compose نمی‌شود** (هر `Dialog` یک پنجرهٔ جدا دارد). پس
 * محافظت باید صریحاً روی پنجرهٔ همان دیالوگ هم گذاشته شود؛ وگرنه توکنِ اشتراک
 * با اینکه «محافظت‌شده» به‌نظر می‌رسد، در عمل قابلِ عکس‌برداری بود.
 */
object ScreenSecurity {
    /** تعدادِ سطح‌های حساسی که همین حالا روی صفحه‌اند (خواندنی برای MainActivity). */
    var holds by mutableStateOf(0)
        private set

    internal fun acquire() {
        holds += 1
    }

    internal fun release() {
        if (holds > 0) holds -= 1
    }
}

/**
 * محتوایی که تا وقتی روی صفحه است، پنجره‌اش از عکس‌برداری/ضبط محافظت می‌شود.
 *
 * دو کار انجام می‌دهد:
 *  ۱. شمارندهٔ [ScreenSecurity.holds] را بالا می‌برد — MainActivity این را
 *     می‌خواند و در صورتِ لزوم پنجرهٔ Activity را هم می‌پوشاند.
 *  ۲. اگر این محتوا داخلِ یک `Dialog` باشد، `FLAG_SECURE` را روی **پنجرهٔ همان
 *     دیالوگ** می‌گذارد (چون پوششِ پنجرهٔ Activity شاملش نمی‌شود).
 */
@Composable
fun SecureContent(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        ScreenSecurity.acquire()
        val dialogWindow = findDialogWindow(view)
        dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            // فقط پنجرهٔ دیالوگ را آزاد می‌کنیم؛ پنجرهٔ Activity در اختیارِ MainActivity
            // است و اگر آن را اینجا پاک کنیم، سوییچِ روشنِ کاربر بی‌اثر می‌شود.
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            ScreenSecurity.release()
        }
    }
    content()
}

/**
 * پنجرهٔ دیالوگی که این view داخلش است (اگر باشد).
 *
 * در Compose، والدِ ریشهٔ محتوای هر `Dialog` یک `DialogWindowProvider` است؛ ولی
 * لایه‌های میانی بسته به نسخه تفاوت دارند، پس چند سطح بالا می‌رویم.
 */
private fun findDialogWindow(view: android.view.View): android.view.Window? {
    var node: android.view.ViewParent? = view.parent
    var depth = 0
    while (node != null && depth < 6) {
        (node as? DialogWindowProvider)?.let { return it.window }
        node = node.parent
        depth++
    }
    return null
}

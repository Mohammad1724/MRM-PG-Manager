# قواعدِ R8 برای بیلدِ ریلیز.
#
# سیاست: کوچک‌سازی و بهینه‌سازی بله، مبهم‌سازیِ نام‌ها نه. اپ گزارشِ کرشِ خودکار
# ندارد و لاگ‌ها دستی خوانده می‌شوند؛ نامِ اصلیِ کلاس/متد در stack trace بیشتر
# می‌ارزد تا چند صد کیلوبایتِ کمتر. ضمناً هر چیزی که با نام و reflection ساخته
# می‌شود (Workerهای WorkManager، کلاس‌های مانیفست، …) بدونِ نگرانی کار می‌کند.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# ── Tink (پشتِ androidx.security:security-crypto)
# protobuf-lite فیلدهای پیام‌ها را با *نامشان* و از راهِ reflection می‌خواند؛ اگر
# R8 فیلدی را «بی‌استفاده» تشخیص دهد و حذف کند، EncryptedSharedPreferences در
# اولین ساخت با «Field version_ ... not found» می‌شکند. خودِ Tink از 1.4 این قاعده
# را همراه دارد؛ اینجا برای اطمینان تکرار شده است.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**

# ── OkHttp/Okio قواعدِ خودشان را دارند؛ این‌ها فقط هشدارهای پلتفرم‌های غیرِ اندروید را خاموش می‌کنند.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── WorkManager: Workerها با نامِ کلاس (از دیتابیسِ WorkManager) ساخته می‌شوند.
# work-runtime خودش سازنده‌ها را نگه می‌دارد؛ اینجا صریح می‌کنیم که با تغییرِ نسخه هم بماند.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── مدل‌ها با org.json و دستی پارس می‌شوند (نه Gson/Moshi/kotlinx-serialization)؛ قاعده‌ای لازم ندارند.
# ── zxing کدِ خالصِ جاواست و reflection ندارد.

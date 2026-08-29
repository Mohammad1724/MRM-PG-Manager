# فیکس ۳ باگ گزارش شده

## ۱. فاکتور - تاریخ شروع اشتباه (امروز به جای تاریخ ساخت)
**مشکل:** در `PdfInvoiceGenerator` و `InvoiceDialog` تاریخ شروع همیشه `LocalDate.now()` بود. یعنی حتی برای کاربر قدیمی، فاکتور میزد شروع امروز.
**علت:** `startJalali = isoToShamsi(now)` و `durationDays = daysRemaining` (فقط باقی‌مانده تا انقضا)
**فیکس:**
- `startDate` از `user.createdAt` پارس می‌شود (با پشتیبانی Instant و yyyy-MM-dd)
- اگر `createdAt` موجود نباشد fallback به `now`
- `durationDays = between(startDate, endDate)` به جای `between(now, endDate)` - یعنی مدت کل اشتراک
- `startJalali` از `startDate` واقعی

فایل‌ها:
- `utils/PdfInvoiceGenerator.kt` خط 140-180
- `ui/dialogs/InvoiceDialog.kt` خط 60-90

## ۲. وضعیت کاربر - expired/limited هم فعال نشان داده می‌شد
**مشکل:** در `UserDetailsDialog`:
```kotlin
val isActive = status != "disabled"
```
یعنی expired و limited هم فعال حساب می‌شدند و نقطه سبز + متن "فعال" نشان داده می‌شد.

**فیکس:**
- `isDisabled = status == "disabled"`
- `isActive = status == "active"` فقط برای active واقعی
- `statusColor` بر اساس status واقعی:
  - active -> سبز
  - expired -> قرمز
  - limited -> نارنجی
  - disabled -> خاکستری
  - on_hold -> بنفش
- `statusLabel` هم از همین map
- هدر دیالوگ حالا رنگ و متن واقعی را نشان می‌دهد
- دکمه toggle بر اساس isDisabled نه isActive
- `QuickActionSheet` هم فیکس شد تا بج وضعیت رنگی واقعی را نشان دهد

فایل‌ها:
- `ui/dialogs/UserDetailsDialog.kt` خط 279-380
- `ui/dialogs/QuickActionSheet.kt` خط 60

## ۳. ویرایش کاربر - ۳۰ روز می‌زنی نامحدود می‌شود
**علت اصلی:** ارقام فارسی!
- فیلتر `it.filter { c -> c.isDigit() }` ارقام فارسی `۰۱۲۳` را نگه می‌دارد چون `isDigit()` برای یونیکد true است
- ولی `"۳۰".toIntOrNull()` در Kotlin null برمی‌گرداند (فقط ارقام انگلیسی را می‌فهمد)
- پس `days.toIntOrNull() == null` -> `expire = ""` -> `expireValue("") == 0` -> نامحدود

**فیکس:**
- تابع `normalizePersianDigits` اضافه شد در `utils/FormatBytes.kt` که `۰-۹` و `٠-٩` را به `0-9` تبدیل می‌کند
- همه جا قبل از `toIntOrNull` / `toDoubleOrNull` / `toLongOrNull` نرمال‌سازی می‌شود:
  - `UserEditorDialog`: limitGb, days, hwid, autoDeleteDays + چیپ‌های +7 روز
  - `BulkCreateUsersDialog`: days, limitGb
  - `InvoiceDialog`: currentPrice, previousDebt, paidAmount
  - `UsersScreen`: DebtorEditDialog amount, bulkAmount days/data
  - `ResetExpiryDurationDialog`: days

**تست:**
اگر کاربر با کیبورد فارسی `۳۰` تایپ کند:
- قبل: `"۳۰".toIntOrNull() == null` -> نامحدود
- بعد: `normalize("۳۰") = "30"` -> `30.toIntOrNull() = 30` -> درست

## فایل‌های تغییر یافته
1. `app/src/main/java/com/mrm/pgmanager/utils/FormatBytes.kt` - افزودن normalizePersianDigits
2. `app/src/main/java/com/mrm/pgmanager/utils/PdfInvoiceGenerator.kt` - فیکس تاریخ شروع
3. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/InvoiceDialog.kt` - فیکس تاریخ شروع + نرمال‌سازی ارقام
4. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/UserDetailsDialog.kt` - فیکس وضعیت
5. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/QuickActionSheet.kt` - فیکس بج وضعیت
6. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/UserEditorDialog.kt` - فیکس ارقام فارسی
7. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/BulkCreateUsersDialog.kt` - فیکس ارقام فارسی
8. `app/src/main/java/com/mrm/pgmanager/ui/screens/UsersScreen.kt` - فیکس ارقام فارسی
9. `app/src/main/java/com/mrm/pgmanager/ui/dialogs/ResetExpiryDurationDialog.kt` - فیکس ارقام فارسی

## پیشنهاد تست دستی
- یک کاربر با createdAt قدیمی بساز، فاکتور بگیر، چک کن شروع = تاریخ ساخت نه امروز
- یک کاربر expired/limited را باز کن، باید قرمز/نارنجی ببینی نه سبز "فعال"
- ویرایش کاربر: با کیبورد فارسی `۳۰` روز بزن، باید 30 روزه شود نه نامحدود
- همین تست با کیبورد انگلیسی هم

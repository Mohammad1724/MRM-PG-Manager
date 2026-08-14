package com.mrm.pgmanager.data.model

/**
 * یک فروشِ ثبت‌شده — پایهٔ گزارشِ درآمد.
 *
 * هر بار که مدیر کاربری را تمدید می‌کند و مبلغ وارد می‌کند، یک [SaleRecord]
 * ساخته و به‌صورت محلی ذخیره می‌شود. پنلِ PasarGuard چیزی دربارهٔ پول نمی‌داند،
 * پس این تنها منبعِ حقیقتِ مالی است.
 *
 * @param id شناسهٔ یکتا (زمانِ ثبت + نامِ کاربر) تا حذف/ویرایش ممکن باشد
 * @param baseUrl پنلی که این فروش به آن مربوط است — گزارش‌ها بین پنل‌ها قاطی نشوند
 * @param soldAt زمانِ ثبت (epoch millis)
 * @param days مدتی که فروخته شد
 * @param amount مبلغ؛ صفر یعنی «تمدیدِ رایگان/هدیه» و در جمع اثری ندارد
 */
data class SaleRecord(
    val id: String,
    val username: String,
    val baseUrl: String,
    val amount: Long,
    val currency: String = "تومان",
    val days: Int,
    val soldAt: Long,
    val note: String = ""
)

package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DsFont {
    val Micro = 9.sp
    val Small = 10.sp
    val Caption = 11.sp
    val Body = 12.sp
    val BodyLg = 13.sp
    val Base = 14.sp
    val Headline = 16.sp
    val Title = 18.sp
    val Display = 20.sp
    val Large = 22.sp

    val Regular = FontWeight.Normal
    val Medium = FontWeight.Medium
    val Semibold = FontWeight.SemiBold
    val Bold = FontWeight.Bold
    val ExtraBold = FontWeight.ExtraBold
}

object DsTypeRole {
    val DisplaySize = DsFont.Display
    val TitleSize = DsFont.Title
    val HeadlineSize = DsFont.Headline
    val BodySize = DsFont.Body
    val CaptionSize = DsFont.BodyLg
    val MicroSize = DsFont.Small
    val TagSize = DsFont.Micro
}

/**
 * سبک‌های متنیِ **معنایی** — لایهٔ بالایِ [DsFont].
 *
 * کامپوننت‌های جدید به‌جایِ `fontSize = 13.sp` هاردکد، از این سبک‌ها
 * استفاده می‌کنند تا مقیاس تایپوگرافی سراسری بماند. مهاجرتِ تدریجیِ
 * کامپوننت‌های قدیمی در فاز ۲ نقشه راه UX انجام می‌شود.
 */
object DsTextStyle {
    /** پیامِ بازخوردِ سراسری (Snackbar). */
    val SnackMessage = TextStyle(fontSize = DsFont.BodyLg, fontWeight = DsFont.Medium)
    /** عنوانِ کارت. */
    val CardTitle = TextStyle(fontSize = DsFont.BodyLg, fontWeight = DsFont.Semibold)
    /** متنِ فرعیِ کارت (متادیتا). */
    val CardMeta = TextStyle(fontSize = DsFont.Caption, fontWeight = DsFont.Regular)
    /** عنوانِ بخش. */
    val SectionTitle = TextStyle(fontSize = DsFont.Headline, fontWeight = DsFont.Bold)
    /** بدنهٔ استاندارد. */
    val Body = TextStyle(fontSize = DsFont.BodyLg, fontWeight = DsFont.Regular)
}

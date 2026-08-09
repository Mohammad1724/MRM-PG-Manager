package com.mrm.pgmanager.ui.designsystem

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

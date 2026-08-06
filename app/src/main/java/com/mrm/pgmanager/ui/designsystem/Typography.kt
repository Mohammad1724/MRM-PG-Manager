package com.mrm.pgmanager.ui.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale — consistent sizes, weights and roles for both
 * Persian and English. Keep the role tokens in use across the app so
 * hierarchy is instantly readable:
 *   Display -> page hero titles
 *   Title   -> card / section headers
 *   Headline -> sub-header, stat values
 *   Body    -> primary text
 *   Caption -> supporting text
 *   Micro   -> dense meta lines (badges, chips, timestamps)
 */
object DsFont {
    // Sizes
    val Micro = 8.sp
    val Small = 10.sp
    val Body = 12.sp
    val BodyLg = 13.sp
    val Base = 15.sp
    val Headline = 17.sp
    val Title = 20.sp
    val Display = 24.sp

    // Weights (usage-driven, not ad-hoc)
    val Regular = FontWeight.Normal
    val Medium = FontWeight.Medium
    val Semibold = FontWeight.SemiBold
    val Bold = FontWeight.Bold
    val ExtraBold = FontWeight.ExtraBold
}

/** Named typographic roles for readability. */
object DsTypeRole {
    val DisplaySize = DsFont.Display
    val TitleSize = DsFont.Title
    val HeadlineSize = DsFont.Headline
    val BodySize = DsFont.Body
    val CaptionSize = DsFont.BodyLg
    val MicroSize = DsFont.Small
    val TagSize = DsFont.Micro
}

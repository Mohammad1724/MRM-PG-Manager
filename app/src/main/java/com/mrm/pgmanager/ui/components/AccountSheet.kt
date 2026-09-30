package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * ورقهٔ هویت/حساب — جایگزین کشوی کناری (فاز ۱ نقشه راه UX).
 *
 * فقط محتوای «هویت» دارد: نام ادمین + آدرس پنل فعال، سوییچ بین حساب‌های
 * ذخیره‌شده، افزودن حساب، تنظیمات و خروج. **بدون تکرار تب‌ها** (ناوبری با
 * نوار پایین) و **بدون دیتای هاردکد** (ترافیک فیک حذف شد).
 *
 * در ریشهٔ `MRMApp` و بالای محتوای صفحات نمایش داده می‌شود تا با اسنکِ
 * بازخورد سراسری تداخل نکند (اسنک بالاتر می‌نشیند).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    adminName: String,
    activeBaseUrl: String,
    accounts: List<Session>,
    onSwitch: (Session) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    val closeLabel = stringResource(R.string.cd_close)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.dialogBgColor,
        contentColor = theme.inkColor,
        tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor))
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── سربرگ: آواتار + نام ادمین + آدرس پنل فعال + دکمهٔ بستن
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(DsRadius.Full)
                        .background(theme.accentPrimary.copy(0.16f))
                        .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.35f)), DsRadius.Full),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (adminName.trim().firstOrNull() ?: "?").uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.inkColor
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        adminName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        activeBaseUrl,
                        fontSize = 11.sp,
                        color = theme.mutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(DsRadius.Sm)
                        .semantics { contentDescription = closeLabel }
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 15.sp, color = theme.mutedColor)
                }
            }
            Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))

            AccountRow(icon = AppIcon.Settings, label = stringResource(R.string.app_settings)) { onOpenSettings() }
            AccountRow(icon = AppIcon.UserAdd, label = stringResource(R.string.acct_add)) { onAddAccount() }

            if (accounts.size > 1) {
                Text(
                    stringResource(R.string.acct_title),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.mutedColor,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                )
                val activeHint = stringResource(R.string.acct_active)
                accounts.forEach { acc ->
                    val isActive = acc.baseUrl == activeBaseUrl && acc.username == adminName
                    AccountRow(
                        icon = AppIcon.Users,
                        label = "${acc.username} · ${acc.baseUrl}",
                        highlight = isActive,
                        trailing = if (isActive) AppIcon.Check else null,
                        activeHint = if (isActive) activeHint else null
                    ) { if (!isActive) onSwitch(acc) }
                }
            }

            Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
            AccountRow(icon = AppIcon.Logout, label = stringResource(R.string.logout), tint = GlassRed) { onLogout() }
        }
    }
}

/** ردیفِ داخلِ ورقهٔ حساب — هدفِ لمسی ۴۴dp. */
@Composable
private fun AccountRow(
    icon: AppIcon,
    label: String,
    tint: Color? = null,
    highlight: Boolean = false,
    trailing: AppIcon? = null,
    activeHint: String? = null,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val rowTint = tint ?: theme.mutedColor
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(DsRadius.Sm)
            .background(if (highlight) theme.searchBgColor else Color.Transparent)
            .then(
                if (highlight) Modifier.border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm)
                else Modifier
            )
            .then(if (activeHint != null) Modifier.semantics { contentDescription = "$label، $activeHint" } else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoundedAppIcon(icon, tint = rowTint, size = 16.dp)
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) theme.inkColor else rowTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.let { RoundedAppIcon(it, tint = theme.accentPrimary, size = 16.dp) }
    }
}

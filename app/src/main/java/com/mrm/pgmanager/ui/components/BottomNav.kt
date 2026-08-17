package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * ناوبریِ پایینِ صفحه — جایگزینِ کشوی کناری برای کارِ تک‌دستی.
 *
 * چرا پایین: دکمهٔ همبرگری در بالای صفحه بود و روی گوشی‌های بلند با یک دست
 * عملاً دور از دسترس؛ ضمن اینکه هر جابه‌جایی سه حرکت می‌خواست
 * (رسیدن به بالا → باز کردن کشو → انتخاب). حالا یک تپ در ناحیهٔ شست کافی است.
 *
 * سه بخشِ پرکاربرد مستقیم اینجا هستند و بقیه در شیتِ «بیشتر»:
 * ایندکس‌ها عمداً با `ImplementedDrawerIds` و شاخه‌های `when(selectedTab)` در
 * MainActivity یکی است (۰ داشبورد، ۱ کاربران، ۲ آمار، ۳ گروه‌ها، ۴ تمپلت‌ها).
 */
const val TAB_DASHBOARD = 0
const val TAB_USERS = 1
const val TAB_STATISTICS = 2
const val TAB_GROUPS = 3
const val TAB_TEMPLATES = 4

@Composable
fun MrmBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    onMore: () -> Unit
) {
    val theme = LocalThemeState.current
    // وقتی کاربر در گروه‌ها/تمپلت‌هاست، آیتمِ «بیشتر» فعال نشان داده می‌شود تا
    // معلوم باشد کجاست (وگرنه هیچ آیتمی روشن نبود و کاربر گم می‌شد).
    val moreSelected = selectedTab == TAB_GROUPS || selectedTab == TAB_TEMPLATES
    Column(Modifier.fillMaxWidth().background(theme.cardSurfaceColor)) {
        // خطِ جداکنندهٔ بالای نوار — border کامل دورِ نوار می‌کشید، اینجا فقط
        // یک خطِ مویی لازم است.
        Box(Modifier.fillMaxWidth().height(DsBorder.Hairline).background(theme.borderColor))
        Row(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomBarItem(
                icon = AppIcon.Gauge,
                label = stringResource(R.string.dashboard),
                selected = selectedTab == TAB_DASHBOARD,
                modifier = Modifier.weight(1f)
            ) { onSelect(TAB_DASHBOARD) }
            BottomBarItem(
                icon = AppIcon.Users,
                label = stringResource(R.string.users),
                selected = selectedTab == TAB_USERS,
                modifier = Modifier.weight(1f)
            ) { onSelect(TAB_USERS) }
            BottomBarItem(
                icon = AppIcon.Timer,
                label = stringResource(R.string.statistics),
                selected = selectedTab == TAB_STATISTICS,
                modifier = Modifier.weight(1f)
            ) { onSelect(TAB_STATISTICS) }
            BottomBarItem(
                icon = AppIcon.Menu,
                label = stringResource(R.string.more),
                selected = moreSelected,
                modifier = Modifier.weight(1f),
                onClick = onMore
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: AppIcon,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val tint = if (selected) theme.accentPrimary else theme.mutedColor
    Column(
        modifier
            .clip(DsRadius.Md)
            .background(if (selected) theme.accentPrimary.copy(.12f) else Color.Transparent)
            // ارتفاع ۵۴ + عرضِ تقسیم‌شده: هر هدف بزرگ‌تر از ۴۸dp توصیه‌شدهٔ
            // اندروید است تا با شست هم خطای کلیک ندهد.
            .height(54.dp)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RoundedAppIcon(icon, tint = tint, size = 19.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** یک ردیفِ بزرگِ قابلِ لمس داخلِ شیتِ «بیشتر». */
@Composable
fun MoreSheetRow(
    icon: AppIcon,
    title: String,
    subtitle: String? = null,
    accent: Color? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val ac = accent ?: theme.accentPrimary
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(DsRadius.Xl)
            .background(if (selected) ac.copy(.12f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) ac.copy(.35f) else theme.borderColor),
                DsRadius.Xl
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(DsRadius.Md).background(ac.copy(.14f)),
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(icon, tint = ac, size = 17.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = theme.mutedColor)
        }
        if (selected) Box(
            Modifier.size(8.dp).clip(DsRadius.Full).background(ac)
        )
    }
}

/** دستگیرهٔ کوچکِ بالای شیت. */
@Composable
fun SheetHandle() {
    val theme = LocalThemeState.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(38.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor))
    }
}

/**
 * شیتِ «بیشتر» — از پایین بالا می‌آید، پس کاملاً در دسترسِ شست است.
 *
 * بخش‌های کم‌کاربردتر (گروه‌ها/تمپلت‌ها) به‌علاوهٔ تنظیمات و خروج اینجا هستند؛
 * سه بخشِ پرکاربرد مستقیماً روی نوار پایین‌اند.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun MoreSheet(
    selectedTab: Int,
    adminName: String,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.dialogBgColor,
        dragHandle = { Box(Modifier.padding(top = 10.dp)) { SheetHandle() } }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (adminName.isNotBlank()) {
                Text(
                    adminName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.mutedColor,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            MoreSheetRow(
                icon = AppIcon.Folder,
                title = stringResource(R.string.groups_title),
                selected = selectedTab == TAB_GROUPS
            ) { onSelect(TAB_GROUPS) }
            MoreSheetRow(
                icon = AppIcon.Template,
                title = stringResource(R.string.templates_title),
                selected = selectedTab == TAB_TEMPLATES
            ) { onSelect(TAB_TEMPLATES) }
            MoreSheetRow(
                icon = AppIcon.Settings,
                title = stringResource(R.string.app_settings)
            ) { onOpenSettings() }
            MoreSheetRow(
                icon = AppIcon.Logout,
                title = stringResource(R.string.logout),
                accent = com.mrm.pgmanager.ui.theme.GlassRed
            ) { onLogout() }
        }
    }
}

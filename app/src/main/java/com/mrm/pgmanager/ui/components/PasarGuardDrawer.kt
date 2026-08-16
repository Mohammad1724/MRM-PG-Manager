package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.theme.LocalThemeState

data class DrawerItem(val id: String, val label: String, val icon: AppIcon, val hasSub: Boolean = false)

/**
 * بخش‌هایی که واقعاً پیاده‌سازی شده‌اند و باید در منو قابل انتخاب باشند.
 * ترتیبِ این فهرست با ترتیبِ تب‌ها در MainActivity یکی است، پس ایندکسِ هر id
 * همان مقدارِ selectedTab است. تنها مرجعِ این اطلاعات همین‌جاست تا با
 * افزودنِ بخشِ جدید، منو و تب‌بار از هم جدا نیفتند.
 */
val ImplementedDrawerIds = listOf("dashboard", "users", "statistics", "groups")

val PasarGuardDrawerItems = listOf(
    DrawerItem("dashboard","Dashboard", AppIcon.Gauge),
    DrawerItem("users","Users", AppIcon.Users),
    DrawerItem("statistics","Statistics", AppIcon.Gauge),
    DrawerItem("hosts","Hosts", AppIcon.Storage),
    DrawerItem("groups","Groups", AppIcon.Folder),
    DrawerItem("admins","Admins", AppIcon.User),
    DrawerItem("apikeys","API Keys", AppIcon.Link),
    DrawerItem("nodes","Nodes", AppIcon.Storage, hasSub = true),
    DrawerItem("templates","Templates", AppIcon.Template, hasSub = true),
    DrawerItem("bulk","Bulk", AppIcon.Users, hasSub = true),
    DrawerItem("settings","Settings", AppIcon.Settings, hasSub = true),
)

@Composable
fun PasarGuardDrawer(
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = {},
    adminName: String = "mrm",
    traffic: String = "12.43 TB"
) {
    val theme = LocalThemeState.current
    Column(
        // statusBarsPadding/navigationBarsPadding لازم است، وگرنه سربرگ کشو
        // زیر نوار اعلان گوشی می‌رود و بخش پایینی زیر نوار ناوبری گم می‌شود.
        Modifier.fillMaxHeight().width(268.dp).background(theme.cardSurfaceColor)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Brand header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold.copy(0.18f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold.copy(0.30f) else Color(0xFFFDE68A)), DsRadius.Sm), contentAlignment = Alignment.Center) {
                        Text("PG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold else Color(0xFF92400E))
                    }
                    Column { Text("PasarGuard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor); Text("v${com.mrm.pgmanager.BuildConfig.VERSION_NAME} • " + stringResource(R.string.up_to_date), fontSize = 10.sp, color = Color(0xFF22C55E)) }
                }
                Box(Modifier.size(32.dp).clip(DsRadius.Sm).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("×", fontSize = 18.sp, color = theme.mutedColor) }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.platform), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            PasarGuardDrawerItems.forEach { item ->
                val sel = item.id == selectedId
                val isImplemented = item.id in ImplementedDrawerIds
                Row(
                    Modifier.fillMaxWidth().height(38.dp).clip(DsRadius.Sm)
                        .background(if (sel) theme.searchBgColor else Color.Transparent)
                        .then(if (sel) Modifier.border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm) else Modifier)
                        .clickable(enabled = isImplemented) { onSelect(item.id); onClose() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(item.icon, tint = if (!isImplemented) theme.mutedColor else if (sel) theme.inkColor else theme.mutedColor, size = 16.dp)
                        val labelRes = when(item.id) {
                            "dashboard" -> stringResource(R.string.dashboard)
                            "users" -> stringResource(R.string.users)
                            "statistics" -> stringResource(R.string.statistics)
                            "hosts" -> "Hosts"
                            "groups" -> stringResource(R.string.groups_title)
                            "admins" -> "Admins"
                            "apikeys" -> "API Keys"
                            "nodes" -> stringResource(R.string.nodes)
                            "templates" -> "Templates"
                            "bulk" -> "Bulk"
                            "settings" -> "Settings"
                            else -> item.label
                        }
                        Text(labelRes, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (!isImplemented) theme.mutedColor else if (sel) theme.inkColor else theme.mutedColor)
                        if (!isImplemented) Text(stringResource(R.string.coming_soon), fontSize = 9.sp, color = theme.mutedColor, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(theme.borderSubtle).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    if (item.hasSub) Text("›", fontSize = 12.sp, color = theme.mutedColor)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            // تنظیمات برنامه: از نوار بالای همهٔ صفحه‌ها برداشته شد و اینجا نشست.
            Row(
                Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm)
                    .clickable { onOpenSettings(); onClose() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 14.dp)
                Text(stringResource(R.string.app_settings), fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedAppIcon(AppIcon.Bell, tint = theme.mutedColor, size = 14.dp)
                Text(stringResource(R.string.support_us), fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.star_count), fontSize = 11.sp, color = theme.inkColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(AppIcon.Palette, tint = theme.mutedColor, size = 14.dp) }
                    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm), contentAlignment = Alignment.Center) { RoundedAppIcon(if (theme.isDark) AppIcon.DarkMode else AppIcon.LightMode, tint = theme.mutedColor, size = 14.dp) }
                }
            }
            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(adminName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text(traffic, fontSize = 10.sp, color = theme.mutedColor) }
                RoundedAppIcon(AppIcon.Prev, tint = theme.mutedColor, size = 12.dp)
            }
        }
    }
}

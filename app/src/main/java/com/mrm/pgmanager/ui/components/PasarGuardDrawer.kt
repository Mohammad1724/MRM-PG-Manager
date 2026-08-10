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
import com.mrm.pgmanager.ui.theme.LocalThemeState

data class DrawerItem(val id: String, val label: String, val icon: AppIcon, val hasSub: Boolean = false)

val PasarGuardDrawerItems = listOf(
    DrawerItem("dashboard","Dashboard", AppIcon.Gauge),
    DrawerItem("users","Users", AppIcon.Users),
    DrawerItem("statistics","Statistics", AppIcon.Gauge),
    DrawerItem("hosts","Hosts", AppIcon.Storage),
    DrawerItem("groups","Groups", AppIcon.Users),
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
    adminName: String = "mrm",
    traffic: String = "12.43 TB"
) {
    val theme = LocalThemeState.current
    Column(
        Modifier.fillMaxHeight().width(268.dp).background(theme.cardSurfaceColor).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Brand header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(DsRadius.Sm).background(if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold.copy(0.18f) else Color(0xFFFFFBEB)).border(BorderStroke(DsBorder.Hairline, if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold.copy(0.30f) else Color(0xFFFDE68A)), DsRadius.Sm), contentAlignment = Alignment.Center) {
                        Text("PG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (theme.isDark) com.mrm.pgmanager.ui.designsystem.DsAccent.Gold else Color(0xFF92400E))
                    }
                    Column { Text("PasarGuard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor); Text("v${com.mrm.pgmanager.BuildConfig.VERSION_NAME} • به‌روز", fontSize = 10.sp, color = Color(0xFF22C55E)) }
                }
                Box(Modifier.size(32.dp).clip(DsRadius.Sm).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("×", fontSize = 18.sp, color = theme.mutedColor) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Platform", fontSize = 10.sp, color = theme.mutedLightColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            PasarGuardDrawerItems.forEach { item ->
                val sel = item.id == selectedId
                val isImplemented = item.id in listOf("dashboard","users","statistics")
                Row(
                    Modifier.fillMaxWidth().height(38.dp).clip(DsRadius.Sm)
                        .background(if (sel) theme.searchBgColor else Color.Transparent)
                        .then(if (sel) Modifier.border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm) else Modifier)
                        .clickable(enabled = isImplemented) { onSelect(item.id); onClose() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(item.icon, tint = if (!isImplemented) theme.mutedLightColor else if (sel) theme.inkColor else theme.mutedColor, size = 16.dp)
                        Text(item.label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (!isImplemented) theme.mutedLightColor else if (sel) theme.inkColor else theme.mutedColor)
                        if (!isImplemented) Text("به‌زودی", fontSize = 9.sp, color = theme.mutedLightColor, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(theme.borderSubtle).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    if (item.hasSub) Text("›", fontSize = 12.sp, color = theme.mutedLightColor)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedAppIcon(AppIcon.Bell, tint = theme.mutedColor, size = 14.dp)
                Text("حمایت از ما", fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("★ Star  2,484", fontSize = 11.sp, color = theme.inkColor)
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

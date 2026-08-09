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
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFFFBEB)).border(BorderStroke(0.7.dp, Color(0xFFFDE68A)), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                        Text("PG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                    }
                    Column { Text("PasarGuard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor); Text("v5.2.1 • Up to date", fontSize = 9.sp, color = Color(0xFF22C55E)) }
                }
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("×", fontSize = 16.sp, color = theme.mutedColor) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Platform", fontSize = 9.sp, color = theme.mutedLightColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            PasarGuardDrawerItems.forEach { item ->
                val sel = item.id == selectedId
                Row(
                    Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Color(0xFFF3F4F6) else Color.Transparent)
                        .border(if (sel) BorderStroke(0.7.dp, theme.borderSubtle) else null, RoundedCornerShape(8.dp))
                        .clickable { onSelect(item.id); onClose() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(item.icon, tint = if (sel) theme.inkColor else theme.mutedColor, size = 16.dp)
                        Text(item.label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) theme.inkColor else theme.mutedColor)
                    }
                    if (item.hasSub) Text("›", fontSize = 12.sp, color = theme.mutedLightColor)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("◎", fontSize = 12.sp, color = theme.mutedColor)
                Text("Support Us", fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("★ Star  2,484", fontSize = 10.sp, color = theme.inkColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("A", fontSize = 10.sp, color = theme.mutedColor) }
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("☀", fontSize = 10.sp, color = theme.mutedColor) }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(adminName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor); Text(traffic, fontSize = 9.sp, color = theme.mutedColor) }
                Text("⌃", fontSize = 10.sp, color = theme.mutedColor)
            }
        }
    }
}

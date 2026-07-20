package com.mrm.pgmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.model.AppTab
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.screens.HostsScreen
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MRMApp() }
    }
}

@Composable
fun MRMApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }
    var themeState by remember { mutableStateOf(store.readTheme()) }

    LiquidGlassTheme(themeState = themeState) {
        if (session == null) {
            LoginScreen(
                onLoggedIn = { v -> store.save(v); session = v },
                themeState = themeState,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }
            )
        } else {
            MainContainerScreen(
                session = session!!,
                onLogout = { store.clear(); session = null },
                themeState = themeState,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }
            )
        }
    }
}

@Composable
fun MainContainerScreen(
    session: Session,
    onLogout: () -> Unit,
    themeState: com.mrm.pgmanager.ui.theme.ThemeState,
    onThemeChange: (com.mrm.pgmanager.ui.theme.ThemeState) -> Unit
) {
    var currentTab by remember { mutableStateOf(AppTab.USERS) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when (currentTab) {
            AppTab.USERS -> UsersScreen(
                session = session,
                onLogout = onLogout,
                themeState = themeState,
                onThemeChange = onThemeChange
            )
            AppTab.HOSTS -> HostsScreen(
                session = session,
                onLogout = onLogout,
                onOpenThemeDialog = { showThemeDialog = true }
            )
        }

        // Floating Glass Bottom Navigation Bar
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 40.dp, end = 40.dp)
        ) {
            GlassBottomNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    }

    if (showThemeDialog) {
        com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog(
            themeState = themeState,
            onDismiss = { showThemeDialog = false },
            onThemeChange = onThemeChange
        )
    }
}

@Composable
fun GlassBottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val theme = LocalThemeState.current
    val isDark = theme.isDark
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (isDark) Color(0xFF1E1E26).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f))
            .border(BorderStroke(1.2.dp, if (isDark) Color.White.copy(0.24f) else Color.White.copy(0.60f)), RoundedCornerShape(26.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.values().forEach { tab ->
            val sel = currentTab == tab
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) theme.lamp.primary else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tab.icon, fontSize = 14.sp)
                    Text(tab.labelFa, fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium, color = if (sel) Color.White else theme.inkColor)
                }
            }
        }
    }
}

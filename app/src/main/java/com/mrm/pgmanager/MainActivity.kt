package com.mrm.pgmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme

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
            UsersScreen(
                session = session!!,
                onLogout = { store.clear(); session = null },
                themeState = themeState,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }
            )
        }
    }
}

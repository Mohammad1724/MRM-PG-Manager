package com.mrm.pgmanager

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { MRMApp(this) } }
    }
}

data class Session(val baseUrl: String, val token: String, val username: String)
data class PanelUser(val username: String, val status: String, val usedTraffic: Long, val dataLimit: Long)

@Composable
private fun MRMApp(context: Context) {
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }
    if (session == null) LoginScreen(onLoggedIn = { value -> store.save(value); session = value })
    else UsersScreen(session = session!!, onLogout = { store.clear(); session = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(onLoggedIn: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://psrg1.iranshop21.monster:7431/dashboard/#/login") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.manage_panel), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.panel_address)) }, placeholder = { Text(stringResource(R.string.panel_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !loading, onClick = {
                loading = true; error = null
                scope.launch {
                    runCatching { PanelApi.login(url, username, password) }
                        .onSuccess(onLoggedIn)
                        .onFailure { error = "Unable to connect. Check panel address, SSL, username and password." }
                    loading = false
                }
            }, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sign_in)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersScreen(session: Session, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    fun load() { scope.launch { loading = true; error = null; runCatching { PanelApi.users(session) }.onSuccess { users = it }.onFailure { error = it.message ?: "Unable to load users" }; loading = false } }
    LaunchedEffect(Unit) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.users)) }, actions = { TextButton(onClick = { load() }) { Text(stringResource(R.string.refresh)) }; TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.search_users)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users.filter { it.username.contains(query, true) }) { user -> UserCard(user) }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: PanelUser) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.username, style = MaterialTheme.typography.titleMedium)
            Text("${user.status}  •  Used: ${formatBytes(user.usedTraffic)} / ${if (user.dataLimit == 0L) "Unlimited" else formatBytes(user.dataLimit)}")
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return "${DecimalFormat("#.##").format(value / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
}

private object PanelApi {
    private val client = OkHttpClient()
    private fun baseUrl(input: String): String {
        val prepared = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        val uri = URI(prepared)
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        return buildString { append(uri.scheme); append("://"); append(uri.host); if (uri.port != -1) append(":${uri.port}") }
    }
    suspend fun login(address: String, username: String, password: String): Session = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "Credentials required" }
        val base = baseUrl(address)
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url("$base/api/admin/token").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login failed: ${response.code}")
            val token = JSONObject(response.body?.string() ?: error("Empty login response")).getString("access_token")
            Session(base, token, username)
        }
    }
    suspend fun users(session: Session): List<PanelUser> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${session.baseUrl}/api/user/s?offset=0&limit=100").header("Authorization", "Bearer ${session.token}").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val data = JSONObject(response.body?.string() ?: error("Empty users response")).getJSONArray("users")
            List(data.length()) { index ->
                val user = data.getJSONObject(index)
                PanelUser(user.getString("username"), user.optString("status", "unknown"), user.optLong("used_traffic", 0), user.optLong("data_limit", 0))
            }
        }
    }
}

private class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mrm_pg_manager",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun read(): Session? {
        val base = prefs.getString("base", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(base, token, prefs.getString("username", "") ?: "")
    }
    fun save(value: Session) = prefs.edit().putString("base", value.baseUrl).putString("token", value.token).putString("username", value.username).apply()
    fun clear() = prefs.edit().clear().apply()
}

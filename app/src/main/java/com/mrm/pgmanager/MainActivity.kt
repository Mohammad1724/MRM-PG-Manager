package com.mrm.pgmanager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MRMApp(this)
            }
        }
    }
}

data class Session(val baseUrl: String, val token: String, val username: String)
data class PanelUser(
    val username: String,
    val status: String,
    val usedTraffic: Long,
    val dataLimit: Long,
    val expire: String?
)

@Composable
private fun MRMApp(context: Context) {
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }

    if (session == null) {
        LoginScreen(onLoggedIn = { value ->
            store.save(value)
            session = value
        })
    } else {
        UsersScreen(session = session!!, onLogout = {
            store.clear()
            session = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(onLoggedIn: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("MRM PG Manager") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Manage your PasarGuard panel", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Full panel address") },
                placeholder = { Text("https://panel.example.com:7431/dashboard/#/login") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching { PanelApi.login(url, username, password) }
                            .onSuccess(onLoggedIn)
                            .onFailure {
                                error = "Unable to connect. Check panel address, SSL, username and password."
                            }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Sign in")
            }
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
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { PanelApi.users(session) }
                .onSuccess { users = it }
                .onFailure { error = it.message ?: "Unable to load users" }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }
                .onFailure { error = it.message ?: "Action failed" }
                .onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                actions = {
                    TextButton(onClick = { load() }) { Text("Refresh") }
                    TextButton(onClick = onLogout) { Text("Log out") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createUser = true }) { Text("+") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search users") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users.filter { it.username.contains(query, ignoreCase = true) }) { user ->
                        UserCard(user, onClick = { selectedUser = user })
                    }
                }
            }
        }
    }

    selectedUser?.let { user ->
        UserEditorDialog(
            initial = user,
            onDismiss = { selectedUser = null },
            onSave = { limitGb, expire ->
                selectedUser = null
                runAction { PanelApi.modifyUser(session, user.username, limitGb.value, expire) }
            },
            onToggle = {
                selectedUser = null
                runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") }
            },
            onDelete = { deleteUser = user; selectedUser = null }
        )
    }

    if (createUser) {
        UserEditorDialog(
            initial = null,
            onDismiss = { createUser = false },
            onSave = { limitGb, expire ->
                createUser = false
                runAction { PanelApi.createUser(session, limitGb.username, limitGb.value, expire) }
            },
            onToggle = null,
            onDelete = null
        )
    }

    deleteUser?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteUser = null },
            title = { Text("Delete ${user.username}?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteUser = null
                    runAction { PanelApi.deleteUser(session, user.username) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteUser = null }) { Text("Cancel") } }
        )
    }
}

private data class UserEditorValues(val username: String, val value: Double)

@Composable
private fun UserEditorDialog(
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    var expireDate by remember { mutableStateOf(initial?.expire?.take(10) ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create user" else "Edit ${initial.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initial == null) {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                }
                OutlinedTextField(
                    limitGb,
                    { limitGb = it },
                    label = { Text("Data limit (GB, blank = unlimited)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    expireDate,
                    { expireDate = it },
                    label = { Text("Expiry date (YYYY-MM-DD, blank = unlimited)") },
                    singleLine = true
                )
                initial?.let {
                    Text("Used: ${formatBytes(it.usedTraffic)} • Status: ${it.status}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onToggle?.let { toggle ->
                            OutlinedButton(onClick = toggle) {
                                Text(if (it.status == "disabled") "Activate" else "Disable")
                            }
                        }
                        onDelete?.let { delete -> OutlinedButton(onClick = delete) { Text("Delete") } }
                    }
                }
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limit = if (limitGb.isBlank()) 0.0 else limitGb.toDoubleOrNull()
                if (username.length !in 3..32 && initial == null) {
                    formError = "Username must be 3 to 32 characters."
                } else if (limit == null || limit < 0) {
                    formError = "Data limit must be a valid number."
                } else if (expireDate.isNotBlank() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(expireDate)) {
                    formError = "Use YYYY-MM-DD for expiry date."
                } else {
                    onSave(UserEditorValues(username, limit), expireDate)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UserCard(user: PanelUser, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.username, style = MaterialTheme.typography.titleMedium)
            Text("${user.status} • Used: ${formatBytes(user.usedTraffic)} / ${if (user.dataLimit == 0L) "Unlimited" else formatBytes(user.dataLimit)}")
            user.expire?.takeIf { it != "0" }?.let { Text("Expiry: ${it.take(10)}") }
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
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(input: String): String {
        val prepared = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        val uri = URI(prepared)
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        return buildString {
            append(uri.scheme)
            append("://")
            append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
        }
    }

    private fun userUrl(session: Session, username: String): String =
        "${session.baseUrl}/api/user/${URLEncoder.encode(username, "UTF-8")}" 

    private fun requestBuilder(session: Session, url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")

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
        val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=0&limit=100").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val data = JSONObject(response.body?.string() ?: error("Empty users response")).getJSONArray("users")
            List(data.length()) { index -> parseUser(data.getJSONObject(index)) }
        }
    }

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireDate: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("status", "active")
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireDate))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireDate: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireDate))
        executeJson(requestBuilder(session, userUrl(session, username)).put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun setDisabled(session: Session, username: String, disabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("disabled", disabled)
        executeJson(requestBuilder(session, "${userUrl(session, username)}/disabled").put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun deleteUser(session: Session, username: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, username)).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Delete failed: ${response.code}")
        }
    }

    private fun executeJson(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string()?.take(250).orEmpty()
                error("Request failed: ${response.code} $details")
            }
        }
    }

    private fun parseUser(user: JSONObject) = PanelUser(
        username = user.getString("username"),
        status = user.optString("status", "unknown"),
        usedTraffic = user.optLong("used_traffic", 0),
        dataLimit = user.optLong("data_limit", 0),
        expire = user.opt("expire")?.toString()
    )

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    private fun expireValue(date: String): Any = if (date.isBlank()) 0 else "${date}T23:59:59Z"
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

    fun save(value: Session) = prefs.edit()
        .putString("base", value.baseUrl)
        .putString("token", value.token)
        .putString("username", value.username)
        .apply()

    fun clear() = prefs.edit().clear().apply()
}

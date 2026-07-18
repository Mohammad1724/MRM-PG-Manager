package com.mrm.pgmanager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiquidGlassTheme { MRMApp(this) } }
    }
}

private val GlassYellow = Color(0xFFFFC94A)
private val GlassCream = Color(0xFFFFFBF1)
private val GlassInk = Color(0xFF25231E)
private val GlassMuted = Color(0xFF6D685D)
private val GlassGreen = Color(0xFF2E9B66)
private val GlassRed = Color(0xFFD33F3F)
private val GlassShape = RoundedCornerShape(28.dp)

@Composable
private fun LiquidGlassTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFFB87800),
        onPrimary = Color.White,
        secondary = GlassYellow,
        background = GlassCream,
        surface = Color.White.copy(alpha = 0.55f),
        onSurface = GlassInk,
        onBackground = GlassInk,
        error = GlassRed
    )
    MaterialTheme(colorScheme = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFFDF8),
                            Color(0xFFFFF4CB),
                            Color(0xFFFFFCF4)
                        )
                    )
                )
        ) {
            // Lamp light shining from the LEFT side on main pages (نور لامپ از سمت چپ صفحه اصلی)
            Box(
                Modifier
                    .size(380.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-90).dp, y = (-30).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xAAFFC94A), // Vibrant golden lamp light
                                Color(0x44FFB300),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(200.dp)
                    )
            )
            Box(
                Modifier
                    .size(300.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (-100).dp, y = 160.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0x66FFC94A),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(200.dp)
                    )
            )
            content()
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Built with pure Box instead of M3 Card to prevent rectangular shadow boxes under transparent glass
    val boxModifier = modifier
        .clip(GlassShape)
        .background(Color.White.copy(alpha = 0.38f))
        .border(
            BorderStroke(
                1.2.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.92f),
                        Color.White.copy(alpha = 0.25f)
                    )
                )
            ),
            GlassShape
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(18.dp)

    Column(
        modifier = boxModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
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
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.35f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.22f),
            focusedBorderColor = GlassYellow,
            unfocusedBorderColor = Color.White.copy(alpha = 0.85f),
            focusedLabelColor = Color(0xFF9C6700),
            cursorColor = Color(0xFF9C6700)
        ),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.58f))
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color(0xFFFFD34E).copy(alpha = 0.5f),
                            Color.White.copy(alpha = 0.35f)
                        )
                    )
                ),
                RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔍", fontSize = 16.sp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search users by username...",
                        color = GlassMuted.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = GlassInk, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.65f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "×",
                        color = GlassInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassTopHeader(
    userCount: Int,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    loading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Users",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = GlassInk
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFE9A8).copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "$userCount",
                    color = Color(0xFF9C6700),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Refresh Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.65f))
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White,
                                    Color(0xFFFFD34E).copy(alpha = 0.4f)
                                )
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(enabled = !loading, onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            color = GlassInk,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("🔄", fontSize = 13.sp)
                    }
                    Text(
                        "Refresh",
                        color = GlassInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // Logout Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFF0F0).copy(alpha = 0.75f))
                    .border(
                        BorderStroke(1.dp, Color(0xFFF2BABA).copy(alpha = 0.8f)),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⎋", fontSize = 14.sp, color = GlassRed)
                    Text(
                        "Exit",
                        color = GlassRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onLoggedIn: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 72.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("MRM", style = MaterialTheme.typography.displayLarge, color = GlassInk)
                Text(
                    "PG Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF9C6700)
                )
                Text("Manage your PasarGuard panel securely", color = GlassMuted)
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Sign in",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassInk
                    )
                    GlassTextField(
                        url,
                        { url = it },
                        "Full panel address",
                        keyboardType = KeyboardType.Uri
                    )
                    GlassTextField(username, { username = it }, "Username")
                    GlassTextField(
                        password,
                        { password = it },
                        "Password",
                        password = true
                    )
                    error?.let { Text(it, color = GlassRed) }
                    Button(
                        enabled = !loading,
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                runCatching { PanelApi.login(url, username, password) }
                                    .onSuccess(onLoggedIn)
                                    .onFailure {
                                        error =
                                            "Unable to connect. Check panel address and login details."
                                    }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF0B800),
                            contentColor = GlassInk
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (loading) CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = GlassInk,
                            strokeWidth = 2.dp
                        ) else Text("Sign in")
                    }
                }
            }
        }
    }
}

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
                .onFailure {
                    error = it.message ?: "Unable to load users"
                    if (it.message?.contains("401") == true) onLogout()
                }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }
                .onFailure {
                    error = it.message ?: "Action failed"
                    if (it.message?.contains("401") == true) onLogout()
                }
                .onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            // Sleek Glass Floating Action Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFFD34E), Color(0xFFE5A800)))
                    )
                    .border(
                        BorderStroke(1.2.dp, Color.White.copy(alpha = 0.85f)),
                        RoundedCornerShape(26.dp)
                    )
                    .clickable { createUser = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlassInk)
                    Text("Create User", fontWeight = FontWeight.Bold, color = GlassInk, fontSize = 14.sp)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            GlassTopHeader(
                userCount = users.size,
                onRefresh = { load() },
                onLogout = onLogout,
                loading = loading
            )
            GlassSearchBar(
                query = query,
                onQueryChange = { query = it }
            )
            Spacer(Modifier.height(14.dp))
            when {
                loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFB87800))
                }
                error != null -> GlassCard(Modifier.fillMaxWidth()) {
                    Text("Error: $error", color = GlassRed, fontWeight = FontWeight.Medium)
                }
                else -> {
                    val filteredUsers = users.filter {
                        it.username.contains(query, ignoreCase = true)
                    }
                    if (filteredUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No users found.", color = GlassMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            items(filteredUsers) { user ->
                                UserCard(user, onClick = { selectedUser = user })
                            }
                        }
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
                runAction {
                    PanelApi.modifyUser(
                        session,
                        user.username,
                        limitGb.value,
                        expire
                    )
                }
            },
            onToggle = {
                selectedUser = null
                runAction {
                    PanelApi.setDisabled(
                        session,
                        user.username,
                        user.status != "disabled"
                    )
                }
            },
            onDelete = {
                deleteUser = user
                selectedUser = null
            }
        )
    }

    if (createUser) {
        UserEditorDialog(
            initial = null,
            onDismiss = { createUser = false },
            onSave = { limitGb, expire ->
                createUser = false
                runAction {
                    PanelApi.createUser(
                        session,
                        limitGb.username,
                        limitGb.value,
                        expire
                    )
                }
            },
            onToggle = null,
            onDelete = null
        )
    }

    deleteUser?.let { user ->
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(GlassShape)
                    .background(Color(0xFFFFFBF1).copy(alpha = 0.88f))
                    .border(BorderStroke(1.2.dp, Color.White), GlassShape)
            ) {
                // Lamp light from RIGHT side on Delete Dialog (نور لامپ از سمت راست)
                Box(
                    Modifier
                        .size(220.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 60.dp, y = (-50).dp)
                        .background(
                            Brush.radialGradient(listOf(Color(0xAAFFC94A), Color.Transparent)),
                            shape = RoundedCornerShape(200.dp)
                        )
                )
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Delete ${user.username}?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GlassInk
                    )
                    Text(
                        "This action will permanently remove the user and cannot be undone.",
                        color = GlassMuted,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { deleteUser = null }) {
                            Text("Cancel", color = GlassMuted)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                deleteUser = null
                                runAction { PanelApi.deleteUser(session, user.username) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlassRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
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
    var limitGb by remember {
        mutableStateOf(
            if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(
                Locale.US,
                initial.dataLimit / 1073741824.0
            ).trimEnd('0').trimEnd('.')
        )
    }
    var expireDate by remember { mutableStateOf(initial?.expire?.take(10) ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFFFFBF1).copy(alpha = 0.88f))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFFFD34E).copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.25f)
                            )
                        )
                    ),
                    RoundedCornerShape(32.dp)
                )
        ) {
            // Spotlight / Lamp light shining specifically from the RIGHT side (نور لامپ از سمت راست در پنجره باز شده)
            Box(
                Modifier
                    .size(260.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-50).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xAAFFC94A), // Bright golden lamp light from right
                                Color(0x44FFB300),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(200.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "Create New User" else "Edit ${initial.username}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GlassInk
                )

                if (initial == null) {
                    GlassTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username"
                    )
                }

                GlassTextField(
                    value = limitGb,
                    onValueChange = { limitGb = it },
                    label = "Data limit (GB, blank = unlimited)",
                    keyboardType = KeyboardType.Decimal
                )

                GlassTextField(
                    value = expireDate,
                    onValueChange = { expireDate = it },
                    label = "Expiry date (YYYY-MM-DD, blank = unlimited)"
                )

                initial?.let {
                    Text(
                        "Used: ${formatBytes(it.usedTraffic)} • Status: ${it.status.uppercase()}",
                        color = GlassMuted,
                        fontSize = 13.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        onToggle?.let { toggle ->
                            val isDisabled = it.status == "disabled"
                            Button(
                                onClick = toggle,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDisabled) GlassGreen else Color(0xFFE5A800),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (isDisabled) "Activate" else "Disable")
                            }
                        }
                        onDelete?.let { delete ->
                            OutlinedButton(
                                onClick = delete,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassRed),
                                border = BorderStroke(1.dp, GlassRed.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }

                formError?.let {
                    Text(it, color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = GlassMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanLimitStr = limitGb.replace(',', '.').trim()
                            val limit = if (cleanLimitStr.isBlank()) 0.0 else cleanLimitStr.toDoubleOrNull()
                            if (username.length !in 3..32 && initial == null) {
                                formError = "Username must be 3 to 32 characters."
                            } else if (limit == null || limit < 0) {
                                formError = "Data limit must be a valid number."
                            } else if (expireDate.isNotBlank() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(expireDate)) {
                                formError = "Use YYYY-MM-DD for expiry date."
                            } else {
                                onSave(UserEditorValues(username, limit), expireDate)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF0B800),
                            contentColor = GlassInk
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: PanelUser, onClick: () -> Unit) {
    val limitText = if (user.dataLimit == 0L) "Unlimited" else formatBytes(user.dataLimit)
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    
    // Color coding based on usage percentage
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 75 -> GlassGreen
        progressPercent in 75..99 -> Color(0xFFE5A800) // Gold/Yellow
        else -> GlassRed
    }
    
    val statusColor = if (user.status == "active") GlassGreen else GlassRed

    GlassCard(Modifier.fillMaxWidth(), onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    user.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassInk
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor)
                    )
                    Text(user.status.uppercase(), color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                if (user.dataLimit == 0L) "∞" else "${progressPercent}%",
                color = GlassInk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = progressColor,
            trackColor = Color.White.copy(alpha = 0.85f) // White track as requested
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Used: ${formatBytes(user.usedTraffic)} / $limitText", color = GlassMuted, fontSize = 12.sp)
            user.expire?.takeIf { it != "0" && it != "null" }?.let {
                Text("Expiry: ${it.take(10)}", color = GlassMuted, fontSize = 12.sp)
            }
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
        val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=0&limit=1000").get().build()
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
        expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" }
    )

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    private fun expireValue(date: String): Any = if (date.isBlank() || date == "null" || date == "0") 0 else "${date}T23:59:59Z"
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

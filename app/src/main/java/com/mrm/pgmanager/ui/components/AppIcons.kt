package com.mrm.pgmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** آیکون‌های یکدست Rounded برای جایگزینی کامل emojiهای رابط. */
enum class AppIcon { Lock, User, UserAdd, Users, Copy, Qr, Refresh, Delete, Settings, Palette, Search, Calendar, Template, Reset, Note, Logout, Warning }

private fun AppIcon.vector(): ImageVector = when (this) {
    AppIcon.Lock -> Icons.Rounded.Lock
    AppIcon.User -> Icons.Rounded.Person
    AppIcon.UserAdd -> Icons.Rounded.PersonAdd
    AppIcon.Users -> Icons.Rounded.Group
    AppIcon.Copy -> Icons.Rounded.ContentCopy
    AppIcon.Qr -> Icons.Rounded.QrCode2
    AppIcon.Refresh -> Icons.Rounded.Refresh
    AppIcon.Delete -> Icons.Rounded.DeleteOutline
    AppIcon.Settings -> Icons.Rounded.Settings
    AppIcon.Palette -> Icons.Rounded.Palette
    AppIcon.Search -> Icons.Rounded.Search
    AppIcon.Calendar -> Icons.Rounded.CalendarMonth
    AppIcon.Template -> Icons.Rounded.Inventory2
    AppIcon.Reset -> Icons.Rounded.RestartAlt
    AppIcon.Note -> Icons.Rounded.NoteAlt
    AppIcon.Logout -> Icons.Rounded.Logout
    AppIcon.Warning -> Icons.Rounded.WarningAmber
}

@Composable
fun RoundedAppIcon(icon: AppIcon, contentDescription: String? = null, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(icon.vector(), contentDescription = contentDescription, tint = tint, modifier = modifier.then(Modifier.size(size)))
}

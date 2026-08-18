package com.mrm.pgmanager.ui.dialogs

import androidx.compose.ui.res.stringResource

import com.mrm.pgmanager.R

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.ActionIconButton
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsAnim
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsRadius

/** رنگ خاکستریِ واضح برای کادرِ کاشی‌ها (تمایز بهتر در حالت روشن/تیره). */
fun tileBorderColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF606068) else Color(0xFF9C978C)

/** ردیف سوئیچ استاندارد تنظیمات: عنوان + توضیح اختیاری + Switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (enabled) theme.inkColor else theme.mutedColor)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = theme.mutedColor)
        }
        Switch(checked = checked, onCheckedChange = { if (enabled) onChange(it) }, enabled = enabled)
    }
}

/** استپر عددی (− / +) به‌جای فیلدهای متنی کوچک؛ سریع و بدون خطای تایپ. */
@Composable
fun SettingsStepper(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    onChange: (Int) -> Unit
) {
    val theme = LocalThemeState.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enabled) theme.inkColor else theme.mutedColor)
        Box(
            Modifier.size(30.dp).clip(DsRadius.Sm)
                .background(if (enabled) theme.accentPrimary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value - step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
        Box(
            Modifier.width(66.dp).height(30.dp).clip(DsRadius.Sm)
                .background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm),
            contentAlignment = Alignment.Center
        ) { Text("$value $unit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(
            Modifier.size(30.dp).clip(DsRadius.Sm)
                .background(if (enabled) theme.accentPrimary.copy(.18f) else theme.searchBgColor)
                .clickable(enabled = enabled) { onChange((value + step).coerceIn(range)) },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
    }
}

/** کنترل سگمنت‌شدهٔ هم‌سبک با تب‌های شناور پایین برنامه (accent + متن تیره روی گزینهٔ فعال). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    icons: List<AppIcon> = emptyList(),
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Xl)
            .background(theme.searchBgColor.copy(alpha = 0.6f))
            .border(BorderStroke(1.2.dp, theme.borderColor), DsRadius.Xl)
            .padding(4.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.55f),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(DsRadius.Md)
                    .background(if (selected) theme.accentPrimary.copy(.85f) else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (icons.getOrNull(index) != null) RoundedAppIcon(icons[index], tint = if (selected) Color(0xFF1A1A1A) else theme.mutedColor, size = 16.dp)
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color(0xFF1A1A1A) else theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** کارت استاندارد هر بخش تنظیمات؛ همان surface خنثی + border ظریفِ کارت‌های داشبورد. */
@Composable
fun SettingsCard(
    title: String,
    icon: AppIcon,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalThemeState.current
    val ac = accent ?: theme.accentPrimary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(DsRadius.Md).background(ac.copy(.12f)), contentAlignment = Alignment.Center) {
                RoundedAppIcon(icon, tint = ac, size = 16.dp)
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
        }
        content()
    }
}

/** ردیف اکشن رنگی با آیکون (خروج از حساب، بازنشانی و ...). */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    icon: AppIcon,
    accent: Color,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Xl)
            .background(accent.copy(.08f))
            .border(BorderStroke(1.dp, accent.copy(.22f)), DsRadius.Xl)
            .pressScale(0.97f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(DsRadius.Md).background(accent.copy(.12f)), contentAlignment = Alignment.Center) {
            RoundedAppIcon(icon, tint = accent, size = 18.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = theme.mutedColor)
        }
    }
}

/** ردیف اطلاعات فقط‌خواندنی با قابلیت کپی (آدرس پنل / نام کاربری). */
@Composable
fun SettingsInfoRow(label: String, value: String, copyable: Boolean = false) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Xl)
            .background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            MrmText(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
        }
        if (copyable) {
            ActionIconButton(
                icon = { RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 16.dp) },
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                    android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                },
                size = 36.dp
            )
        }
    }
}

/** فیلد متنی کوچک کپسولی مخصوص دیالوگ‌ها. */
@Composable
fun CompactGlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    leading: String = "",
    leadingAppIcon: AppIcon? = null,
    fieldHeight: androidx.compose.ui.unit.Dp = 42.dp
) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier.fillMaxWidth().height(fieldHeight).clip(DsRadius.Md)
            .background(if (theme.isDark) Color.White.copy(.10f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, theme.borderColor), DsRadius.Md)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (leadingAppIcon != null) RoundedAppIcon(leadingAppIcon, tint = theme.mutedColor, size = 16.dp) else if (leading.isNotEmpty()) Text(leading, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = theme.mutedColor.copy(0.55f), fontSize = 12.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) Box(
                Modifier.size(20.dp).clip(DsRadius.Md).background(Color.Black.copy(0.06f)).clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) { Text("×", fontSize = 12.sp, color = theme.mutedColor) }
        }
    }
}

@Composable
fun CheckboxIcon(selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val selectLabel = stringResource(R.string.cd_select)
    val unselectLabel = stringResource(R.string.cd_unselect)
    val isDark = theme.isDark
    val bg = if (selected) theme.accentPrimary else if (isDark) Color(0xFF383842) else Color.White
    val borderCol = if (selected) theme.accentPrimary else if (isDark) Color(0xFF8E8C98) else Color(0xFFB8BBC2)
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(DsRadius.Xs)
            .background(bg)
            .border(BorderStroke(DsBorder.Hairline, borderCol), DsRadius.Xs)
            .semantics { contentDescription = if (selected) unselectLabel else selectLabel }
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = androidx.compose.animation.scaleIn(DsAnim.bouncy()) + androidx.compose.animation.fadeIn(DsAnim.fast()),
            exit = androidx.compose.animation.scaleOut(DsAnim.exit()) + androidx.compose.animation.fadeOut(DsAnim.exit())
        ) {
            // تیک با Canvas رسم می‌شود تا به baseline فونت وابسته نباشد و دقیقاً وسط مربع بماند.
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = size.minDimension * .14f, cap = StrokeCap.Round)
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .23f, size.height * .52f),
                    end = Offset(size.width * .43f, size.height * .71f),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .43f, size.height * .71f),
                    end = Offset(size.width * .78f, size.height * .30f),
                    strokeWidth = stroke.width,
                    cap = stroke.cap
                )
            }
        }
    }
}

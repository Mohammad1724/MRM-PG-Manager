package com.mrm.pgmanager.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.LocalThemeState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.material3.ripple
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsAnim
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsGlass
import com.mrm.pgmanager.ui.designsystem.DsRadius

@Composable
fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = LocalContext.current
    val resId = remember(context) {
        var id = context.resources.getIdentifier("ic_launcher", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("logo_mrm", "drawable", context.packageName)
        id
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "MRM Logo",
            contentScale = ContentScale.Fit,
            modifier = modifier.height(height).widthIn(max = height * 3.2f)
        )
    } else {
        val theme = LocalThemeState.current
        Box(
            modifier = modifier.height(height).widthIn(max = height * 2.8f)
                .clip(RoundedCornerShape(height / 3.2f))
                .background(Brush.linearGradient(listOf(theme.accentPrimary, theme.accentLight)))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(height / 3.2f))
                .padding(horizontal = DsSpacing.Mid),
            contentAlignment = Alignment.Center
        ) {
            Text("MRM", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.45f).sp)
        }
    }
}

@Composable
fun PasswordEyeIcon(visible: Boolean) {
    val theme = LocalThemeState.current
    val eyeLabel = stringResource(if (visible) R.string.cc_hide_password else R.string.cc_show_password)
    Canvas(modifier = Modifier.size(20.dp).semantics { contentDescription = eyeLabel }) {
        val w = size.width; val h = size.height
        drawOval(color = theme.inkColor, topLeft = Offset(1f, h * 0.22f), size = Size(w - 2f, h * 0.56f), style = Stroke(width = 2.2f))
        drawCircle(color = if (visible) theme.accentPrimary else theme.inkColor, radius = if (visible) w * 0.20f else w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        if (!visible) drawLine(color = theme.accentPrimary, start = Offset(w * 0.10f, h * 0.90f), end = Offset(w * 0.90f, h * 0.10f), strokeWidth = 2.8f)
    }
}

// === MRM Premium UI Components (2026 Edition) ===

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Primary
    )
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Secondary
    )
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Danger
    )
}

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRed: Boolean = false
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier.height(36.dp),
        enabled = enabled,
        style = if (isRed) MrmButtonStyle.Danger else MrmButtonStyle.Secondary,
        compact = true
    )
}

@Composable
fun ActionIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRed: Boolean = false,
    size: Dp = 44.dp,
    contentDescription: String? = null
) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1.0f,
        animationSpec = DsAnim.snappy(),
        label = "iconScale"
    )
    val shape = DsRadius.Md
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = if (enabled) 1f else DsGlass.DisabledAlpha)
            .shadow(
                elevation = if (!isPressed) DsElevation.Low.ambient.dp else 0.dp,
                shape = shape,
                ambientColor = if (isRed) GlassRed.copy(0.25f) else theme.accentPrimary.copy(0.18f),
                spotColor = if (isRed) GlassRed.copy(0.30f) else theme.accentPrimary.copy(0.22f)
            )
            .clip(shape)
            .background(if (isRed) GlassRed.copy(0.14f) else theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, if (isRed) GlassRed.copy(0.38f) else theme.borderColor), shape)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = ripple(bounded = true, radius = size), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
fun PrimarySaveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    PrimaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, loading = loading, icon = AppIcon.Check)
}

@Composable
fun MutedCancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(text = text, onClick = onClick, modifier = modifier)
}

@Composable
fun MiniGlassButton(text: String, modifier: Modifier = Modifier, isRed: Boolean = false, onClick: () -> Unit) {
    SmallButton(text = text, onClick = onClick, modifier = modifier, isRed = isRed)
}

/**
 * دکمهٔ شناورِ مشترکِ صفحه‌ها (کاربران، گروه‌ها، قالب‌ها).
 *
 * دایرهٔ ۵۶dp با همان زبانِ دکمهٔ اصلی: گرادیانِ سطحِ اصلی، لبهٔ سفیدِ نیمه‌شفاف،
 * برقِ بالا و هالهٔ نور. با [visible] هنگامِ اسکرول محو می‌شود و در اولین
 * نمایش با یک جهشِ کوچک می‌آید.
 */
@Composable
fun MrmFab(
    icon: AppIcon,
    contentDescription: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressFactor by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = DsAnim.snappy(),
        label = "fabPress"
    )
    // ورودِ اول با فنرِ سرزنده؛ محو/ظهورِ هنگامِ اسکرول با tween کوتاه تا با هر
    // اسکرول «نپرد».
    var introduced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { introduced = true }
    val shown by animateFloatAsState(
        targetValue = if (visible && introduced) 1f else 0f,
        animationSpec = when {
            !visible -> DsAnim.exit()
            !introduced -> DsAnim.bouncy()
            else -> DsAnim.enter()
        },
        label = "fabShown"
    )
    val glow = theme.primaryGlow
    Box(
        modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = pressFactor * shown
                scaleY = pressFactor * shown
                alpha = shown
            }
            .shadow(
                elevation = if (pressed) 6.dp else 12.dp, shape = CircleShape, clip = false,
                ambientColor = glow.copy(alpha = 0.40f),
                spotColor = glow.copy(alpha = 0.70f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(theme.primaryStart, theme.primaryEnd)))
            .glassGloss(CircleShape, pressed)
            .border(BorderStroke(1.dp, theme.primaryEdge), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            // وقتی دکمه محو است نباید لمس را بگیرد.
            .clickable(enabled = visible, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RoundedAppIcon(icon, tint = theme.onPrimary, size = 24.dp)
    }
}

/**
 * برقِ شیشه‌ای روی سطحِ اصلی: نیمهٔ بالا کمی روشن‌تر (نورِ محیط)، و هنگامِ فشار
 * یک لایهٔ سفیدِ کم‌رنگ روی کل سطح. بعد از background و پیشِ محتوا کشیده می‌شود
 * (drawBehind) تا متن و آیکون شسته نشوند؛ فقط فازِ draw — بدونِ recomposition.
 */
private fun Modifier.glassGloss(shape: androidx.compose.ui.graphics.Shape, pressed: Boolean): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = DsGlass.GlossTopAlpha),
                0.55f to Color.White.copy(alpha = DsGlass.GlossBottomAlpha),
                1f to Color.Transparent
            )
        )
        if (pressed) drawOutline(outline = outline, color = Color.White.copy(alpha = DsGlass.PressedOverlayAlpha))
    }

sealed class MrmButtonStyle {
    object Primary : MrmButtonStyle()
    object Secondary : MrmButtonStyle()
    object Danger : MrmButtonStyle()
    object Glass : MrmButtonStyle()
}

/**
 * دکمهٔ استانداردِ اپ — کپسولِ کامل.
 *
 * سبکِ Primary همان «دکمهٔ شیشه‌ای»ِ مرجعِ کاربر است: گرادیانِ سطحِ اصلی
 * ([ThemeState.primaryBrush])، لبهٔ سفیدِ نیمه‌شفاف، برقِ بالای سطح، هالهٔ نورِ
 * هم‌رنگ، و اگر [icon] داده شود یک «سکه»ی گرد در ابتدا و فلشِ کوچک در انتها.
 * در حالتِ [loading] اسپینر داخلِ همان سکه می‌چرخد. سبک‌های دیگر (Secondary/
 * Danger/Glass) تخت‌اند ولی هم‌شکل، تا کنارِ هم بنشینند.
 */
@Composable
fun MrmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null,
    style: MrmButtonStyle = MrmButtonStyle.Primary,
    compact: Boolean = false
) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = enabled && !loading

    val scale by animateFloatAsState(
        targetValue = if (isPressed && active) 0.96f else 1.0f,
        animationSpec = DsAnim.snappy(),
        label = "btnScale"
    )
    val contentAlpha by animateFloatAsState(targetValue = if (enabled) 1f else DsGlass.DisabledAlpha, label = "btnAlpha")

    val shape = DsRadius.Full
    val isPrimary = style == MrmButtonStyle.Primary
    val (backgroundBrush, contentColor, borderStroke) = when (style) {
        MrmButtonStyle.Primary -> Triple(
            theme.primaryBrush,
            theme.onPrimary,
            BorderStroke(1.dp, theme.primaryEdge)
        )
        MrmButtonStyle.Secondary -> Triple(
            Brush.verticalGradient(listOf(theme.searchBgColor.copy(0.7f), theme.searchBgColor.copy(0.4f))),
            theme.inkColor,
            BorderStroke(DsBorder.Hairline, theme.borderColor)
        )
        MrmButtonStyle.Danger -> Triple(
            Brush.verticalGradient(listOf(GlassRed.copy(0.16f), GlassRed.copy(0.07f))),
            GlassRed,
            BorderStroke(DsBorder.Default, GlassRed.copy(0.38f))
        )
        MrmButtonStyle.Glass -> Triple(
            Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.White.copy(0.04f))),
            theme.inkColor,
            BorderStroke(DsBorder.Default, Color.White.copy(0.22f))
        )
    }

    val height = if (compact) DsComponent.ButtonCompact else DsComponent.Button
    val coinSize = if (compact) 22.dp else 28.dp
    val chevronSize = if (compact) 14.dp else 18.dp
    val showCoin = isPrimary && (icon != null || loading)
    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = contentAlpha)
            .height(height)
            .let {
                if (isPrimary && enabled) {
                    // هالهٔ نورِ هم‌رنگ؛ هنگامِ فشار پررنگ‌تر (حالتِ «فعال» در مرجع).
                    it.shadow(
                        elevation = if (isPressed) 12.dp else 8.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = theme.primaryGlow.copy(alpha = 0.35f),
                        spotColor = theme.primaryGlow.copy(alpha = 0.60f)
                    )
                } else it
            }
            .clip(shape)
            .background(backgroundBrush)
            .let { if (isPrimary) it.glassGloss(shape, isPressed && active) else it }
            .border(borderStroke, shape)
            .clickable(interactionSource = interactionSource, indication = ripple(color = contentColor.copy(DsGlass.RippleContentAlpha), bounded = true), enabled = active, onClick = onClick)
            .padding(horizontal = if (showCoin) (DsSpacing.Sm) else (if (compact) DsSpacing.Screen else DsSpacing.Xxl)),
        contentAlignment = Alignment.Center
    ) {
        if (showCoin) {
            // چیدمانِ مرجع: سکه چسبیده به ابتدا، فلش چسبیده به انتها، متن وسط.
            // عمداً Row+weight نیست: در دکمه‌ای که عرضِ ثابت ندارد (wrap) weight به
            // صفر می‌رسد و متن ناپدید می‌شود؛ Box با padding هم wrap را درست
            // درمی‌آورد و هم لبه‌ها را می‌چسباند.
            Box(
                Modifier.align(Alignment.CenterStart).size(coinSize).clip(CircleShape)
                    .background(Color.White.copy(alpha = DsGlass.CoinFillAlpha))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = DsGlass.CoinEdgeAlpha)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(coinSize - 9.dp),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                } else if (icon != null) {
                    RoundedAppIcon(icon, tint = contentColor, size = if (compact) DsComponent.IconXs else DsComponent.IconSm)
                }
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = DsFont.Bold,
                fontSize = if (compact) 12.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                // متن بینِ سکه و فلش وسط‌چین می‌شود (نه وسطِ کلِ کپسول) — مثلِ مرجع.
                modifier = Modifier.align(Alignment.Center).padding(start = coinSize + DsSpacing.Sm, end = chevronSize + DsSpacing.Md)
            )
            // فلشِ انتهای کپسول (آیکونِ AutoMirrored — در RTL خودکار برعکس می‌شود).
            RoundedAppIcon(
                AppIcon.Next,
                tint = contentColor.copy(alpha = DsGlass.ChevronAlpha),
                size = chevronSize,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (compact) DsSpacing.Xs else DsSpacing.Sm)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(DsComponent.IconMd),
                        color = contentColor,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    if (icon != null) {
                        RoundedAppIcon(icon, tint = contentColor, size = if (compact) DsComponent.IconSm else DsComponent.IconMd)
                        Spacer(Modifier.width(DsSpacing.Md))
                    }
                    Text(
                        text = text,
                        color = contentColor,
                        fontWeight = DsFont.Bold,
                        fontSize = if (compact) 12.sp else 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// === Text handling for RTL/LTR consistency ===

@Composable
fun MrmText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    isTechnical: Boolean = false // Usernames, URLs, Tokens, etc.
) {
    val theme = LocalThemeState.current
    val finalColor = if (color == Color.Unspecified) theme.inkColor else color
    
    val direction = if (isTechnical) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content
    
    Text(
        text = text,
        modifier = modifier,
        color = finalColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = androidx.compose.ui.text.TextStyle(
            textDirection = direction
        )
    )
}

@Composable
fun TechnicalContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

@Composable
fun BulkActionsBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onResetUsage: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onApplyTemplate: () -> Unit,
    onSelectAll: () -> Unit = {},
    onExport: () -> Unit = {},
    onGroupAdd: () -> Unit = {},
    onGroupRemove: () -> Unit = {},
    onAddDays: () -> Unit = {},
    onAddData: () -> Unit = {},
    onRevokeSubs: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var expanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(50.dp))
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(50.dp))
            .padding(horizontal = DsSpacing.Screen, vertical = DsSpacing.Sm),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
        ) {
            // Target count text
            Text(
                text = if (isFa) stringResource(R.string.cc_n_users, selectedCount) else "$selectedCount Targets",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.inkColor
            )

            // Vertical divider
            Box(Modifier.width(1.dp).height(16.dp).background(theme.borderColor))

            // Trash action (Delete)
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                RoundedAppIcon(AppIcon.Delete, tint = GlassRed, size = 15.dp)
            }

            // More actions (...)
            Box(contentAlignment = Alignment.TopStart) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(26.dp)) {
                    Text("•••", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, textAlign = TextAlign.Center)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(theme.cardSurfaceColor)
                ) {
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Check, tint = GlassGreen, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_enable) else "Enable", color = theme.inkColor) } },
                        onClick = { onEnable(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.User, tint = theme.mutedColor, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_disable) else "Disable", color = theme.inkColor) } },
                        onClick = { onDisable(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Reset, tint = theme.accentPrimary, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_reset_data) else "Reset Usage", color = theme.inkColor) } },
                        onClick = { onResetUsage(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Template, tint = theme.accentPrimary, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_apply_template) else "Apply Template", color = theme.inkColor) } },
                        onClick = { onApplyTemplate(); expanded = false }
                    )
                    // تمدید و افزودنِ حجم به‌صورت گروهی — پنل این‌ها را دارد
                    // (`bulk/expire` و `bulk/data_limit`) و اپ نداشت.
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Calendar, tint = theme.accentPrimary, size = 14.dp); Text(stringResource(R.string.us_bulk_days), color = theme.inkColor) } },
                        onClick = { onAddDays(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Storage, tint = theme.accentPrimary, size = 14.dp); Text(stringResource(R.string.us_bulk_data), color = theme.inkColor) } },
                        onClick = { onAddData(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Reset, tint = GlassAmber, size = 14.dp); Text(stringResource(R.string.us_bulk_revoke), color = theme.inkColor) } },
                        onClick = { onRevokeSubs(); expanded = false }
                    )
                    // افزودن/برداشتنِ گروه برای چند کاربر یک‌جا — قبلاً باید
                    // تک‌تکِ کاربرها را ویرایش می‌کردی.
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Folder, tint = theme.accentPrimary, size = 14.dp); Text(stringResource(R.string.us_bulk_group_add), color = theme.inkColor) } },
                        onClick = { onGroupAdd(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Folder, tint = theme.mutedColor, size = 14.dp); Text(stringResource(R.string.us_bulk_group_remove), color = theme.inkColor) } },
                        onClick = { onGroupRemove(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Download, tint = GlassGreen, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_export) else "Export", color = theme.inkColor) } },
                        onClick = { onExport(); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) { RoundedAppIcon(AppIcon.Users, tint = theme.inkColor, size = 14.dp); Text(if (isFa) stringResource(R.string.cc_select_all) else "Select All", color = theme.inkColor) } },
                        onClick = { onSelectAll(); expanded = false }
                    )
                }
            }

            // Vertical divider
            Box(Modifier.width(1.dp).height(16.dp).background(theme.borderColor))

            // Close button (Clear selection)
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Text("×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
            }
        }
    }
}

@Composable
private fun BulkActionChip(label: String, icon: AppIcon, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(DsRadius.Sm)
            .background(color.copy(alpha = 0.10f))
            .border(BorderStroke(DsBorder.Hairline, color.copy(alpha = 0.26f)), DsRadius.Sm)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.Lg),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) { RoundedAppIcon(icon, tint = color, size = 14.dp); Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color) }
    }
}

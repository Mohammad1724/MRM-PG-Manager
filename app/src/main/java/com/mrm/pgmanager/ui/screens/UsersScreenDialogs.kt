package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.UserFilter
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.data.model.UserEditorValues
import com.mrm.pgmanager.data.model.UserSort
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.*
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassShape
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState
import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.lastSeenText
import com.mrm.pgmanager.utils.lastSeenShort
import com.mrm.pgmanager.utils.formatBytes
import com.mrm.pgmanager.utils.NotificationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.DsElevation
import com.mrm.pgmanager.ui.designsystem.DsFont
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.DsTileRadius


/* ──────────────────────────────────────────────────────────────────────────
 *  دیالوگ‌های صفحهٔ کاربران — فاز ۱.۳ نقشه راه UX (تفکیک `UsersScreen.kt`).
 *
 *  stateها و منطق در `UsersUiState`؛ اینجا فقطِ ترکیبِ پنجره‌ها/ورقه‌ها مانده.
 *  رفتار عیناً همانِ قبلِ تفکیک است.
 * ────────────────────────────────────────────────────────────────────────── */

@Composable
fun DebtorEditDialog(
    user: PanelUser,
    existing: DebtorInfo?,
    currency: String = stringResource(R.string.us_currency),
    onDismiss: () -> Unit,
    onSave: (amount: Long, notes: String) -> Unit,
    onClear: () -> Unit
) {
    val theme = LocalThemeState.current
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val amountLong = com.mrm.pgmanager.utils.normalizePersianDigits(amountText).filter { it.isDigit() }.toLongOrNull() ?: 0L
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(DsSpacing.Dialog)) {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
                Text(if (existing != null) stringResource(R.string.us_debt_edit_title, user.username) else stringResource(R.string.us_debt_add_title, user.username), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                if (existing != null) {
                    Text(stringResource(R.string.us_debt_marked_at, java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(existing.markedAt))), fontSize = 11.sp, color = theme.mutedColor)
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = DsSpacing.FieldHorizontal), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), modifier = Modifier.fillMaxWidth()) {
                        Text(currency, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                        androidx.compose.foundation.text.BasicTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                val n = com.mrm.pgmanager.utils.normalizePersianDigits(raw)
                                amountText = n.filter { c -> c.isDigit() }
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            textStyle = TextStyle(color = theme.inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (amountText.isEmpty()) Text(stringResource(R.string.us_debt_amount_hint), color = theme.mutedColor.copy(0.6f), fontSize = 12.sp)
                                inner()
                            }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).padding(horizontal = DsSpacing.FieldHorizontal), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = notes,
                        onValueChange = { notes = it.take(200) },
                        singleLine = false,
                        textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (notes.isEmpty()) Text(stringResource(R.string.us_debt_note_hint), color = theme.mutedColor.copy(0.6f), fontSize = 11.sp)
                            inner()
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                    PGSecondaryButton(stringResource(R.string.us_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    if (existing != null) {
                        PGPrimaryButton(stringResource(R.string.us_debt_settle), onClick = { onClear() }, modifier = Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
                PGPrimaryButton(
                    text = if (existing != null) stringResource(R.string.us_debt_save) else stringResource(R.string.us_debt_mark),
                    enabled = amountLong > 0L,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(amountLong, notes) }
                )
            }
        }
    }
}

/** دیالوگ‌های/ورقه‌های صفحهٔ کاربران؛ state از [UsersUiState] می‌آید. */
@Composable
internal fun UsersScreenDialogs(
    ui: UsersUiState,
    session: Session,
    monitoringSettings: com.mrm.pgmanager.data.model.MonitoringSettings,
    defaultCurrency: String,
    themeState: ThemeState,
    onBeginExport: (String) -> Unit
) {
    val context = ui.context
    val store = ui.store
    val scope = ui.scope

    ui.quickTemplateUser?.let { u ->
        LaunchedEffect(u) {
            ui.quickTemplatesLoading = true; ui.quickTemplatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { ui.quickTemplates = it } ?: run { ui.quickTemplatesFailed = true }
            ui.quickTemplatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = ui.quickTemplates,
            selectedCount = 1,
            onDismiss = { ui.quickTemplateUser = null },
            onApply = { templateId, note ->
                val id = u.id
                ui.quickTemplateUser = null
                ui.runAction { PanelApi.bulkApplyTemplate(session, setOf(id), templateId, note) }
            },
            isLoading = ui.quickTemplatesLoading,
            loadFailed = ui.quickTemplatesFailed
        )
    }

    ui.bulkAmountKind?.let { kind ->
        val ids = ui.selectedUserIds.toSet()
        val theme = LocalThemeState.current
        // پیش‌نمایشِ dry-run پنل: «چند نفر واقعاً تغییر می‌کنند؟»
        // کاربرِ بدونِ تاریخِ انقضا در «افزودن روز» و کاربرِ بی‌سقف در «افزودن حجم»
        // هدفِ معتبر نیستند؛ عددِ واقعی جلوی اشتباهِ ادمین را می‌گیرد.
        LaunchedEffect(kind, ids) { ui.loadBulkPreview(kind, ids) }
        Dialog(onDismissRequest = { ui.bulkAmountKind = null }) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(DsSpacing.Dialog),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
            ) {
                Text(
                    stringResource(if (kind == "days") R.string.us_bulk_days else R.string.us_bulk_data),
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.us_bulk_group_title, ids.size) + " · " +
                        stringResource(if (kind == "days") R.string.us_bulk_amount_days else R.string.us_bulk_amount_gb),
                    fontSize = 11.sp, color = theme.mutedColor
                )
                val preview = ui.bulkPreviewCount
                when {
                    ui.bulkPreviewLoading -> Text(
                        stringResource(R.string.us_preview_checking),
                        fontSize = 11.sp, color = theme.mutedColor
                    )
                    preview != null && preview >= 0 -> Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                        Text(
                            stringResource(R.string.us_preview_affected, preview, ids.size),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor
                        )
                        // فقط وقتی تفاوت هست توضیح بده — وگرنه فیلدِ بی‌مصرف می‌شود.
                        if (preview < ids.size) Text(
                            stringResource(if (kind == "days") R.string.us_preview_note_expire else R.string.us_preview_note_data),
                            fontSize = 11.sp, color = theme.mutedColor
                        )
                    }
                    preview != null -> Text(
                        stringResource(R.string.us_preview_unavailable),
                        fontSize = 11.sp, color = theme.mutedColor
                    )
                }
                GlassSearchBar(query = ui.bulkAmountText, onQueryChange = { text ->
                    // فقط عدد و یک منفیِ ابتدایی؛ منفی یعنی «کم کن». + نرمال‌سازی فارسی
                    val normalized = com.mrm.pgmanager.utils.normalizePersianDigits(text)
                    ui.bulkAmountText = normalized.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) || (c == '.' && kind == "data") }
                })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                    PGSecondaryButton(stringResource(R.string.us_cancel), onClick = { ui.bulkAmountKind = null }, modifier = Modifier.weight(1f))
                    PGPrimaryButton(
                        text = stringResource(R.string.us_bulk_apply),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val normalized = com.mrm.pgmanager.utils.normalizePersianDigits(ui.bulkAmountText)
                            val amount = normalized.toDoubleOrNull()
                            ui.bulkAmountKind = null
                            if (amount == null || amount == 0.0) return@PGPrimaryButton
                            ui.selectedUserIds = emptySet()
                            // معکوسِ دقیق همان عدد: +۳۰ روز ⇄ −۳۰ روز (و همین برای حجم).
                            // وصلهٔ فوری هم **همان معیارِ پنل** را دارد (سنجیده شد):
                            //  • زمان: فقط کاربری که تاریخِ انقضا دارد جابه‌جا می‌شود.
                            //  • حجم: فقط کاربری که سقفش صفر نیست.
                            // پس عددی که کاربر روی ردیف می‌بیند با «N از M» دیالوگ یکی است.
                            ui.runOptimistic(
                                ids = ids,
                                undo = {
                                    if (kind == "days") PanelApi.bulkAddDays(session, ids, -amount.toInt())
                                    else PanelApi.bulkAddData(session, ids, -amount)
                                },
                                patch = { row ->
                                    if (kind == "days") {
                                        com.mrm.pgmanager.utils.DateLogic.shiftIsoDays(row.expire, amount.toLong())
                                            ?.let { row.copy(expire = it) } ?: row
                                    } else {
                                        if (row.dataLimit <= 0L) row
                                        else row.copy(dataLimit = row.dataLimit + (amount * 1_073_741_824.0).toLong())
                                    }
                                }
                            ) {
                                if (kind == "days") PanelApi.bulkAddDays(session, ids, amount.toInt())
                                else PanelApi.bulkAddData(session, ids, amount)
                            }
                        }
                    )
                }
            }
        }
    }

    if (ui.bulkRevokeConfirm) {
        val ids = ui.selectedUserIds.toSet()
        ConfirmActionDialog(
            title = stringResource(R.string.us_bulk_revoke_title, ids.size),
            message = stringResource(R.string.us_bulk_revoke_msg),
            onDismiss = { ui.bulkRevokeConfirm = false },
            onConfirm = {
                ui.bulkRevokeConfirm = false
                ui.selectedUserIds = emptySet()
                ui.runAction { PanelApi.bulkRevokeSubs(session, ids) }
            }
        )
    }

    ui.cleanupNames?.let { names ->
        ConfirmActionDialog(
            title = stringResource(R.string.us_cleanup_title, names.size),
            message = stringResource(R.string.us_cleanup_msg) + "\n\n" + names.take(12).joinToString("، ") +
                if (names.size > 12) " …" else "",
            onDismiss = { ui.cleanupNames = null },
            onConfirm = {
                val count = names.size
                ui.cleanupNames = null
                ui.runAction(notification = null) { PanelApi.deleteCleanupCandidates(session) }
                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.us_cleanup_done, count))
            }
        )
    }

    if (ui.bulkGroupPicker) {
        val ids = ui.selectedUserIds.toSet()
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { ui.bulkGroupPicker = false }) {
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(DsSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
            ) {
                Text(
                    stringResource(if (ui.bulkGroupAdd) R.string.us_bulk_group_add else R.string.us_bulk_group_remove),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.us_bulk_group_title, ids.size) + " · " + stringResource(R.string.us_bulk_group_pick),
                    fontSize = 11.sp, color = theme.mutedColor
                )
                if (ui.groupOptions.isEmpty()) {
                    Text(stringResource(R.string.ue_no_groups), fontSize = 11.sp, color = theme.mutedColor)
                }
                ui.groupOptions.forEach { g ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(DsRadius.Sm)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                            .pressScale(0.98f)
                            .clickable {
                                ui.bulkGroupPicker = false
                                ui.selectedUserIds = emptySet()
                                val add = ui.bulkGroupAdd
                                ui.runAction(
                                    notification = null
                                ) { PanelApi.bulkGroupMembership(session, setOf(g.id), ids, add) }
                                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(
                                        if (add) R.string.us_bulk_group_added else R.string.us_bulk_group_removed,
                                        ids.size
                                    ))
                            }
                            .padding(horizontal = DsSpacing.Lg, vertical = DsSpacing.Md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                            RoundedAppIcon(AppIcon.Folder, tint = theme.accentPrimary, size = 14.dp)
                            Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = theme.inkColor)
                        }
                    }
                }
                PGSecondaryButton(stringResource(R.string.us_cancel), onClick = { ui.bulkGroupPicker = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (ui.showBulkTemplateDialog) {
        var templates by remember { mutableStateOf<List<UserTemplateItem>>(emptyList()) }
        var templatesLoading by remember { mutableStateOf(true) }
        var templatesFailed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            templatesLoading = true; templatesFailed = false
            var list: List<UserTemplateItem>? = null
            for (i in 1..3) {
                val r = runCatching { PanelApi.userTemplates(session) }
                if (r.isSuccess) { list = r.getOrNull(); break }
                kotlinx.coroutines.delay(400L)
            }
            list?.let { templates = it } ?: run { templatesFailed = true }
            templatesLoading = false
        }
        com.mrm.pgmanager.ui.dialogs.BulkApplyTemplateDialog(
            templates = templates,
            selectedCount = ui.selectedUserIds.size,
            onDismiss = { ui.showBulkTemplateDialog = false },
            onApply = { templateId, note ->
                val ids = ui.selectedUserIds.toSet()
                ui.selectedUserIds = emptySet()
                ui.showBulkTemplateDialog = false
                ui.runAction { PanelApi.bulkApplyTemplate(session, ids, templateId, note) }
            },
            isLoading = templatesLoading,
            loadFailed = templatesFailed
        )
    }

    ui.pendingBulk?.let { p ->
        ConfirmActionDialog(
            title = p.title,
            message = p.message,
            confirmLabel = p.confirmLabel,
            danger = p.danger,
            confirmWord = p.confirmWord,
            onDismiss = { ui.pendingBulk = null },
            onConfirm = { p.action(); ui.pendingBulk = null }
        )
    }

    ui.quickActionUser?.let { u ->
        val isDebtor = ui.debtorByUsername.containsKey(u.username)
        QuickActionSheet(
            user = u,
            onDismiss = { ui.quickActionUser = null },
            onUseTemplate = { ui.quickTemplateUser = u },
            onToggle = {
                // فاز ۶.۱ — ردیف همین لحظه عوض می‌شود؛ برچسبِ وضعیتِ ورقه هم که از
                // همان آبجکت می‌خواند بلافاصله به‌روز می‌شود (قبلش کاربر یک سکوتِ
                // کوتاه می‌دید تا وقتی اسنکِ موفقیت برسد).
                val nowDisabled = u.status != "disabled"
                ui.runOptimistic(
                    ids = setOf(u.id),
                    notification = context.getString(R.string.us_n_status) to context.getString(R.string.us_n_status_body, u.username),
                    // معکوسِ وضعیت: اگر از disabled درآوردیم، برگشتش غیرفعال‌کردن است.
                    undo = { PanelApi.setDisabled(session, u, !nowDisabled) },
                    patch = { row -> row.copy(status = if (nowDisabled) "disabled" else "active") }
                ) { PanelApi.setDisabled(session, u, nowDisabled) }
            },
            onCopySub = { ui.copySubWithFetch(u) },
            onQr = { ui.qrUser = u },
            onEdit = { ui.selectedUser = u },
            onResetUsage = { ui.runOptimistic(
                ids = setOf(u.id),
                notification = context.getString(R.string.us_n_reset_usage) to context.getString(R.string.us_n_reset_usage_body, u.username),
                // نکته: `lifetimeUsedTraffic` عمداً دست‌نخورده می‌ماند — ریستِ مصرف
                // تاریخِ مصرفِ کل را پاک نمی‌کند و ردیف هم نباید دروغ بگوید.
                patch = { row -> row.copy(usedTraffic = 0L) }
            ) { PanelApi.resetUsage(session, u) } },
            onResetExpiry = { ui.resetExpiryTarget = u },
            onDelete = { ui.deleteUser = u },
            onDebtor = { ui.debtorDialogUser = u },
            isDebtor = isDebtor,
            onInvoice = { ui.invoiceDialogUser = u }
        )
    }

    ui.selectedUser?.let { user ->
        val dInfo = ui.debtorByUsername[user.username]
        UserDetailsDialog(
            user = user,
            onDismiss = { ui.selectedUser = null },
            onSave = { limitGb, expireShamsi ->
                ui.selectedUser = null; ui.runAction { val iso = if (limitGb.keepExpire) null else JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds, limitGb.nextPlan, limitGb.resetStrategy, limitGb.autoDeleteDays, limitGb.status, limitGb.onHoldExpireSeconds, limitGb.onHoldTimeoutSeconds) }
            },
            onToggle = {
                ui.selectedUser = null
                // همان الگوی اکشنِ سریع + پیش‌نمایشِ فوری (فاز ۶.۱).
                val nowDisabled = user.status != "disabled"
                ui.runOptimistic(
                    ids = setOf(user.id),
                    notification = context.getString(R.string.us_n_status) to context.getString(R.string.us_n_status_body, user.username),
                    undo = { PanelApi.setDisabled(session, user, !nowDisabled) },
                    patch = { row -> row.copy(status = if (nowDisabled) "disabled" else "active") }
                ) { PanelApi.setDisabled(session, user, nowDisabled) }
            },
            onDelete = { ui.deleteUser = user; ui.selectedUser = null },
            onResetUsage = {
                ui.selectedUser = null; ui.runOptimistic(
                    ids = setOf(user.id),
                    notification = context.getString(R.string.us_n_reset_usage) to context.getString(R.string.us_n_reset_usage_body, user.username),
                    patch = { row -> row.copy(usedTraffic = 0L) }
                ) { PanelApi.resetUsage(session, user) }
            },
            onResetExpiry = { days ->
                ui.selectedUser = null; ui.runAction(notification = context.getString(R.string.us_n_reset_time) to context.getString(R.string.us_n_reset_time_body, user.username, days)) {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, user, user.dataLimit.toDouble() / 1073741824.0, newExpire, user.note ?: "", user.hwidLimit, user.groupIds)
                }
            },
            onApplyTemplate = { templateId, note ->
                ui.selectedUser = null; ui.runAction { PanelApi.bulkApplyTemplate(session, setOf(user.id), templateId, note) }
            },
            session = session,
            debtorInfo = dInfo,
            onMarkDebtor = { ui.selectedUser = null; ui.debtorDialogUser = user },
            onClearDebt = {
                val wasAutoDisabled = dInfo?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, user.username)
                ui.reloadDebtors()
                ui.selectedUser = null
                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.us_debt_cleared))
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, user, false) }
                            .onSuccess { ui.load(resetHeader = false, silent = true) }
                    }
                }
            },
            // دادهٔ تازهٔ ورقه به ردیفِ پشتش هم می‌رسد (فاز ۶.۲: وصلهٔ ردیف، نه رفرشِ کلِ لیست).
            onRefreshed = { fresh -> ui.patchRows(setOf(fresh.id)) { fresh } },
            onInvoice = {
                ui.invoiceDialogUser = user
                ui.selectedUser = null
            }
        )
    }
    if (ui.createMenuOpen) {
        Dialog(onDismissRequest = { ui.createMenuOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(DsSpacing.Xl), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                Text(stringResource(R.string.us_create_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                SettingsActionRow(stringResource(R.string.us_create_single), stringResource(R.string.us_create_single_desc), AppIcon.UserAdd, themeState.accentPrimary) { ui.createMenuOpen = false; ui.createUser = true }
                SettingsActionRow(stringResource(R.string.us_create_bulk), stringResource(R.string.us_create_bulk_desc), AppIcon.Users, GlassGreen) { ui.createMenuOpen = false; ui.bulkCreateOpen = true }
                PGSecondaryButton(stringResource(R.string.us_cancel), onClick = { ui.createMenuOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (ui.bulkCreateOpen) {
        BulkCreateUsersDialog(session = session, onDismiss = { ui.bulkCreateOpen = false }, onFinished = { n -> ui.bulkCreateOpen = false; if (n > 0) ui.load(resetHeader = false, silent = true) })
    }
    if (ui.exportChooserOpen) {
        Dialog(onDismissRequest = { ui.exportChooserOpen = false }) {
            Column(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(themeState.dialogBgColor).border(BorderStroke(DsBorder.Hairline, themeState.borderColor), DsRadius.Xxl).padding(DsSpacing.Xl), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                Text(stringResource(R.string.us_export_title, ui.selectedUserIds.size), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text(stringResource(R.string.us_export_desc), fontSize = 11.sp, color = themeState.mutedColor)
                SettingsActionRow(stringResource(R.string.us_export_csv), stringResource(R.string.us_export_csv_desc), AppIcon.Download, GlassGreen) { ui.exportChooserOpen = false; onBeginExport("csv") }
                SettingsActionRow(stringResource(R.string.us_export_json), stringResource(R.string.us_export_json_desc), AppIcon.Download, themeState.accentPrimary) { ui.exportChooserOpen = false; onBeginExport("json") }
                PGSecondaryButton(stringResource(R.string.us_cancel), onClick = { ui.exportChooserOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (ui.createUser) UserEditorDialog(initial = null, onDismiss = { ui.createUser = false }, onSave = { limitGb, expireShamsi ->
        ui.createUser = false; ui.runAction(notification = context.getString(R.string.us_n_created) to context.getString(R.string.us_n_created_body, limitGb.username)) { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso, limitGb.note, limitGb.hwidLimit, limitGb.groupIds, limitGb.nextPlan, limitGb.resetStrategy, limitGb.autoDeleteDays, limitGb.status, limitGb.onHoldExpireSeconds, limitGb.onHoldTimeoutSeconds) }
    }, onToggle = null, onSaveWithTemplate = { username, templateId, note ->
        ui.createUser = false; ui.runAction(notification = context.getString(R.string.us_n_created) to context.getString(R.string.us_n_created_tpl_body, username)) { PanelApi.createUserFromTemplate(session, username, templateId, note) }
    }, session = session)
    ui.deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { ui.deleteUser = null }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.Lg).clip(DsRadius.Lg).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Xxxl)) {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Screen)) {
                    Text(stringResource(R.string.us_delete_user_title, user.username), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text(stringResource(R.string.us_delete_user_msg), color = theme.mutedColor, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PGSecondaryButton(stringResource(R.string.us_cancel), onClick = { ui.deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(DsSpacing.Mid))
                        PGDangerButton(stringResource(R.string.us_delete), onClick = {
                            // ردیف بلافاصله برود؛ اگر پنل نپذیرد، رفرشِ بی‌صدا برمی‌گرداندش.
                            ui.removeRows(setOf(user.id))
                            ui.runAction(notification = context.getString(R.string.us_n_deleted) to context.getString(R.string.us_n_deleted_body, user.username)) { PanelApi.deleteUser(session, user) }
                            ui.deleteUser = null
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    ui.qrUser?.let { user ->
        SubscriptionQrDialog(user = user, onDismiss = { ui.qrUser = null })
    }
    ui.invoiceDialogUser?.let { u ->
        InvoiceDialog(
            user = u,
            debtorInfo = ui.debtorByUsername[u.username],
            currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
            onDismiss = { ui.invoiceDialogUser = null }
        )
    }
    ui.debtorDialogUser?.let { u ->
        val existing = ui.debtorByUsername[u.username]
        DebtorEditDialog(
            user = u,
            existing = existing,
            currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
            onDismiss = { ui.debtorDialogUser = null },
            onSave = { amount, notes ->
                val info = DebtorInfo(
                    username = u.username,
                    baseUrl = session.baseUrl,
                    amount = amount,
                    currency = monitoringSettings.debtorCurrency.ifBlank { defaultCurrency },
                    markedAt = existing?.markedAt ?: System.currentTimeMillis(),
                    notes = notes,
                    autoDisabled = existing?.autoDisabled ?: false,
                    userId = u.id
                )
                store.setDebtor(info)
                ui.reloadDebtors()
                ui.debtorDialogUser = null
                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(if (existing == null) R.string.us_debt_added else R.string.us_debt_updated))
                if (monitoringSettings.debtorAutoDisableEnabled) {
                    val over = info.isOverdue(monitoringSettings.debtorAutoDisableAfterHours)
                    if (over && u.status != "disabled") {
                        scope.launch {
                            runCatching { PanelApi.setDisabled(session, u, true) }.onSuccess {
                                val updated = info.copy(autoDisabled = true)
                                store.setDebtor(updated)
                                ui.reloadDebtors()
                                com.mrm.pgmanager.ui.feedback.AppFeedback.info(context.getString(R.string.us_debt_auto_disabled))
                            }
                        }
                    }
                }
            },
            onClear = {
                val wasAutoDisabled = ui.debtorByUsername[u.username]?.autoDisabled ?: false
                store.removeDebtor(session.baseUrl, u.username)
                ui.reloadDebtors()
                ui.debtorDialogUser = null
                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.us_debt_cleared))
                if (wasAutoDisabled) {
                    scope.launch {
                        runCatching { PanelApi.setDisabled(session, u, false) }.onSuccess {
                            // وضعیتِ ردیف فوراً عوض می‌شود؛ رفرشِ بی‌صدا هم‌ترازی می‌آورد (فاز ۶.۲).
                            ui.patchRows(setOf(u.id)) { it.copy(status = "active") }
                            ui.load(resetHeader = false, silent = true)
                            com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.us_user_enabled))
                        }
                    }
                }
            }
        )
    }
    ui.resetExpiryTarget?.let { u ->
        ResetExpiryDurationDialog(
            onDismiss = { ui.resetExpiryTarget = null },
            onConfirm = { days ->
                val targetUser = u; ui.resetExpiryTarget = null
                ui.runAction(notification = context.getString(R.string.us_n_reset_time) to context.getString(R.string.us_n_reset_time_body, targetUser.username, days)) {
                    val newExpire = LocalDate.now().plusDays(days.toLong()).toString()
                    PanelApi.modifyUser(session, targetUser, targetUser.dataLimit.toDouble() / 1073741824.0, newExpire, targetUser.note ?: "", targetUser.hwidLimit, targetUser.groupIds)
                }
            }
        )
    }
}

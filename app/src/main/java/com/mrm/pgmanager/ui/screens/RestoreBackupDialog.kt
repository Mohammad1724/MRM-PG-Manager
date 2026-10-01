package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.dialogs.CheckboxIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * دیالوگ بازیابی پشتیبان.
 *
 * از دیالوگِ تنظیماتِ قدیمی (که حذف شد) جدا شد تا هم در صفحهٔ تنظیمات و هم در صفحهٔ ورود
 * (پیش از لاگین) قابل استفاده باشد. فایل قبلاً توسط BackupSection انتخاب
 * شده و اینجا فقط بازرسی و بازیابی می‌شود.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun RestoreBackupDialog(
    uri: android.net.Uri,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var preview by remember { mutableStateOf<BackupManager.BackupInfo?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var restoreAccounts by remember { mutableStateOf(true) }
    var restoreDebtors by remember { mutableStateOf(true) }
    var restoreSettings by remember { mutableStateOf(true) }
    var restoreInvoice by remember { mutableStateOf(true) }
    var restoring by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val pickOneMsg = stringResource(R.string.set_rs_pick_one)
    val okToast = stringResource(R.string.set_rs_ok_toast)
    val doneTemplate = stringResource(R.string.set_rs_done)
    val errorTemplate = stringResource(R.string.set_bk_error)
    val cantOpen = stringResource(R.string.set_bk_cant_open)

    // بازرسیِ فایل: اگر شکست بخورد یعنی احتمالاً رمزدار است.
    LaunchedEffect(uri) {
        runCatching {
            val ins = context.contentResolver.openInputStream(uri) ?: throw Exception(cantOpen)
            ins.use { s -> BackupManager.inspect(context, s) }
        }.onSuccess { info ->
            preview = info
            needsPassword = info.encrypted
        }.onFailure {
            preview = null
            needsPassword = true
        }
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { if (!restoring) onDismiss() }, sheetState = sheetState,
        containerColor = theme.dialogBgColor, contentColor = theme.inkColor, tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = DsSpacing.Mid, bottom = DsSpacing.Sm), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor)) } }
    ) {
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(DsSpacing.Dialog)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)) {
                Text(
                    stringResource(R.string.set_rs_title), fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold, color = theme.inkColor
                )

                val info = preview
                if (info != null) {
                    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                    Column(
                        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                            .padding(DsSpacing.Mid),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
                    ) {
                        Text(
                            stringResource(R.string.set_rs_file_info), fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = theme.mutedColor
                        )
                        Text(
                            stringResource(R.string.set_rs_created, sdf.format(java.util.Date(info.createdAt))),
                            fontSize = 11.sp, color = theme.inkColor
                        )
                        Text(
                            stringResource(R.string.set_rs_appver, info.appVersion.ifBlank { "-" }),
                            fontSize = 11.sp, color = theme.inkColor
                        )
                        Text(
                            stringResource(R.string.set_rs_accounts_n, info.accountsCount.toString()),
                            fontSize = 11.sp, color = theme.inkColor
                        )
                        Text(
                            stringResource(R.string.set_rs_debtors_n, info.debtorsCount.toString()),
                            fontSize = 11.sp, color = theme.inkColor
                        )
                        if (info.sellerName.isNotBlank()) {
                            Text(
                                stringResource(R.string.set_rs_seller, info.sellerName),
                                fontSize = 11.sp, color = theme.inkColor
                            )
                        }
                        if (info.hasLogo) {
                            Text(stringResource(R.string.set_rs_has_logo), fontSize = 11.sp, color = theme.inkColor)
                        }
                        if (info.encrypted) {
                            Text(
                                stringResource(R.string.set_rs_encrypted), fontSize = 11.sp,
                                color = GlassGreen, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.set_rs_selected), fontSize = 11.sp, color = theme.mutedColor)
                }

                if (needsPassword) {
                    CompactGlassField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = stringResource(R.string.set_rs_password),
                        leadingAppIcon = AppIcon.Lock,
                        keyboardType = KeyboardType.Password
                    )
                }

                Text(
                    stringResource(R.string.set_rs_choose), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                    RestoreCheckRow(stringResource(R.string.set_rs_accounts), restoreAccounts) { restoreAccounts = it }
                    RestoreCheckRow(stringResource(R.string.set_rs_debtors), restoreDebtors) { restoreDebtors = it }
                    RestoreCheckRow(stringResource(R.string.set_rs_settings), restoreSettings) { restoreSettings = it }
                    RestoreCheckRow(stringResource(R.string.set_rs_invoice), restoreInvoice) { restoreInvoice = it }
                }

                result?.let {
                    Text(it, fontSize = 11.sp, color = GlassGreen, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (!restoring) {
                            PGSecondaryButton(
                                stringResource(R.string.set_rs_cancel),
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(52.dp).clip(DsRadius.Xxl)
                                    .background(theme.searchBgColor.copy(0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.set_rs_cancel),
                                    color = theme.mutedColor.copy(0.5f),
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PGPrimaryButton(
                            text = stringResource(R.string.set_rs_restore),
                            enabled = !restoring,
                            loading = restoring,
                            icon = AppIcon.Restore,
                            onClick = {
                                if (!restoreAccounts && !restoreDebtors && !restoreSettings && !restoreInvoice) {
                                    com.mrm.pgmanager.ui.feedback.AppFeedback.info(pickOneMsg)
                                    return@PGPrimaryButton
                                }
                                restoring = true
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use { ins ->
                                            BackupManager.restoreBackup(
                                                context, ins, passwordInput,
                                                restoreAccounts = restoreAccounts,
                                                restoreDebtors = restoreDebtors,
                                                restoreSettings = restoreSettings,
                                                restoreInvoice = restoreInvoice
                                            )
                                        } ?: throw Exception(cantOpen)
                                    }.onSuccess { (_, msg) ->
                                        withContext(Dispatchers.Main) {
                                            result = String.format(doneTemplate, msg)
                                            preview = null
                                            restoring = false
                                            com.mrm.pgmanager.ui.feedback.AppFeedback.success(okToast)
                                        }
                                    }.onFailure { e ->
                                        withContext(Dispatchers.Main) {
                                            result = String.format(errorTemplate, e.message ?: "")
                                            restoring = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** ردیفِ تیک‌دار برای انتخابِ بخش‌های بازیابی. */
@Composable
private fun RestoreCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
    ) {
        CheckboxIcon(selected = checked, onToggle = { onCheckedChange(!checked) })
        Text(label, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
    }
}

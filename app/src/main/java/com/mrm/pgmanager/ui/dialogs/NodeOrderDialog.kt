package com.mrm.pgmanager.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGIconButton
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * ترتیبِ نمایشِ نودها — `PUT /api/nodes/order` (**PasarGuard v5.5**، مجوزِ `nodes.update`).
 *
 * چرا در اپ لازم است: ترتیبِ نودها در پنل **ماندگار** شده و همان ترتیبی است که
 * داشبورد و کانفیگ‌های تولیدشده برای مشتری استفاده می‌کنند. روی موبایل مرتب‌کردن با
 * درگ در لیستی که داخلِ اسکرول است قابلِ اتکا نیست، پس هر ردیف دو دکمهٔ «بالا/پایین»
 * دارد (هدفِ لمسیِ ۴۰dp، مطابقِ قاعدهٔ دسترس‌پذیریِ پروژه).
 *
 * ذخیره فقط وقتی فعال است که ترتیب واقعاً عوض شده باشد و حداقل دو نود وجود داشته
 * باشد — همان قاعدهٔ `ReorderRequest` پنل (`min_length=2`, بدونِ تکرار).
 */
@Composable
fun NodeOrderDialog(
    nodes: List<PanelNode>,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    val theme = LocalThemeState.current
    // کپیِ محلی: تغییرها تا لحظهٔ «ذخیره» روی پنل اثری ندارند.
    val order = remember(nodes) { mutableStateListOf(*nodes.toList().toTypedArray()) }
    val dirty = remember(nodes) { mutableStateOf(false) }

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        val item = order.removeAt(from)
        order.add(to, item)
        dirty.value = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(DsRadius.Xxl)
                .background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            Text(stringResource(R.string.st_node_order_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
            Text(stringResource(R.string.st_node_order_desc), fontSize = 11.sp, color = theme.mutedColor)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
            ) {
                order.forEachIndexed { index, node ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(DsRadius.Sm)
                            .background(theme.searchBgColor)
                            .border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Sm)
                            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                    ) {
                        // شمارهٔ ترتیب: «چندم» را بدونِ شمردنِ ردیف‌ها نشان می‌دهد.
                        MrmText(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.mutedColor,
                            isTechnical = true
                        )
                        MrmText(
                            text = node.name,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.inkColor,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        val upLabel = stringResource(R.string.st_node_move_up)
                        val downLabel = stringResource(R.string.st_node_move_down)
                        PGIconButton(
                            icon = { RoundedAppIcon(AppIcon.Prev, contentDescription = upLabel, size = 14.dp) },
                            contentDescription = upLabel,
                            enabled = index > 0,
                            onClick = { move(index, index - 1) }
                        )
                        PGIconButton(
                            icon = { RoundedAppIcon(AppIcon.Next, contentDescription = downLabel, size = 14.dp) },
                            contentDescription = downLabel,
                            enabled = index < order.lastIndex,
                            onClick = { move(index, index + 1) }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                PGSecondaryButton(stringResource(R.string.re_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PGPrimaryButton(
                    text = stringResource(R.string.st_node_order_save),
                    icon = AppIcon.Check,
                    enabled = order.size >= 2 && dirty.value,
                    onClick = { onSave(order.map { it.id }) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** دکمهٔ ورود به این دیالوگ در سربرگِ نودها (هم‌خانوادهٔ PG). */
@Composable
fun NodeOrderEntry(onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val label = stringResource(R.string.st_node_order)
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(DsRadius.Sm)
            .clickable(onClick = onClick)
            .pressScale(0.96f)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .padding(horizontal = DsSpacing.Md, vertical = DsSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
    ) {
        RoundedAppIcon(AppIcon.Tune, contentDescription = label, tint = theme.accentPrimary, size = 12.dp)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.accentTextColor, maxLines = 1)
    }
}

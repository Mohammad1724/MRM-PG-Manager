package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.OutboundUsage
import com.mrm.pgmanager.data.model.StatsRange
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.UsageChart
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.formatBytes

internal sealed interface OutboundLoadState {
    data object Loading : OutboundLoadState
    data object Unsupported : OutboundLoadState
    data class Failed(val message: String) : OutboundLoadState
    data class Ready(val items: List<OutboundUsage>) : OutboundLoadState
}

@Composable
internal fun OutboundUsageCard(state: OutboundLoadState, range: StatsRange, scopeLabel: String, onRefresh: () -> Unit) {
    val theme = LocalThemeState.current
    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically) {
            RoundedAppIcon(AppIcon.Gauge, tint = theme.accentPrimary, size = 14.dp)
            Text(stringResource(R.string.st_outbound_traffic), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = theme.inkColor, modifier = Modifier.weight(1f))
            Text("${range.label} · $scopeLabel", fontSize = 11.sp, color = theme.accentTextColor, maxLines = 1)
        }
        Text(stringResource(R.string.st_outbound_desc), fontSize = 11.sp, color = theme.mutedColor)
        when (state) {
            OutboundLoadState.Loading -> MrmBusyIndicator(color = theme.accentPrimary, size = 20.dp, strokeWidth = 2.dp)
            OutboundLoadState.Unsupported -> Text(stringResource(R.string.st_outbound_unsupported), fontSize = 11.sp, color = theme.mutedColor)
            is OutboundLoadState.Failed -> {
                InfraBanner(text = state.message, success = false)
                PGSecondaryButton(text = stringResource(R.string.refresh), onClick = onRefresh)
            }
            is OutboundLoadState.Ready -> {
                val items = state.items
                if (items.isEmpty()) {
                    Text(stringResource(R.string.st_outbound_empty), fontSize = 11.sp, color = theme.mutedColor)
                } else {
                    val points = remember(items) { OutboundUsage.merge(items) }
                    UsageChart(points = points, accent = DsSemantic.Success, themeIsDark = theme.isDark,
                        emptyActionLabel = stringResource(R.string.chart_empty_refresh), onEmptyAction = onRefresh,
                        valueFormatter = ::formatBytes)
                    items.forEach { item ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                            MrmText(item.tag.ifBlank { stringResource(R.string.st_outbound_untagged) },
                                isTechnical = true, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            MrmText("↓ ${formatBytes(item.downlink)}  ↑ ${formatBytes(item.uplink)}  ·  ${formatBytes(item.total)}",
                                isTechnical = true, fontSize = 11.sp, color = theme.mutedColor)
                        }
                    }
                }
            }
        }
    }
}

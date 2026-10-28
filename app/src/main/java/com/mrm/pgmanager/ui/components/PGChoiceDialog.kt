package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.LocalThemeState

internal data class PGChoiceOption(val value: String, val label: String)

/** Keeps the choice content independent of its platform window for previews and host tests. */
internal interface PGChoiceWindow {
    @Composable
    fun Show(onDismiss: () -> Unit, content: @Composable () -> Unit)
}

private object PlatformChoiceWindow : PGChoiceWindow {
    @Composable
    override fun Show(onDismiss: () -> Unit, content: @Composable () -> Unit) {
        AppDialog(onDismissRequest = onDismiss, content = content)
    }
}

internal val LocalChoiceWindow = staticCompositionLocalOf<PGChoiceWindow> { PlatformChoiceWindow }

internal fun filterChoiceOptions(choices: List<PGChoiceOption>, query: String): List<PGChoiceOption> =
    choices.distinctBy { it.value }.filter { it.label.contains(query.trim(), ignoreCase = true) }

/** Shared surface for searchable single- and multi-choice dialogs. */
@Composable
internal fun PGChoiceDialogFrame(
    title: String,
    searchLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalThemeState.current
    LocalChoiceWindow.current.Show(onDismiss = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Column(
                Modifier.fillMaxWidth().imePadding().heightIn(max = 500.dp).clip(DsRadius.Xxl)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                    .padding(16.dp).semantics { paneTitle = title }.testTag(tag),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title, modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = theme.inkColor
                )
                PGSearchBar(query = query, onQueryChange = onQueryChange, placeholder = searchLabel)
                content()
            }
        }
    }
}

/** A selection is applied immediately; cancel or back leaves the value unchanged. */
@Composable
internal fun PGSingleChoiceDialog(
    title: String,
    searchLabel: String,
    choices: List<PGChoiceOption>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    tag: String = "choice_dialog"
) {
    val theme = LocalThemeState.current
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered = filterChoiceOptions(choices, query)
    PGChoiceDialogFrame(title, searchLabel, query, { query = it }, onDismiss, tag) {
        if (filtered.isEmpty()) {
            Text(stringResource(R.string.no_results), fontSize = 13.sp, color = theme.mutedColor, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).selectableGroup().testTag("${tag}_list")) {
                items(filtered, key = { it.value }) { choice ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = choice.value == selected, role = Role.RadioButton) { onSelect(choice.value) }
                            .testTag("${tag}_option_${choice.value}")
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(choice.label, Modifier.weight(1f), fontSize = 14.sp, color = theme.inkColor, textAlign = PGStartAlign())
                        if (choice.value == selected) RoundedAppIcon(AppIcon.Check, tint = theme.accentTextColor, size = 18.dp)
                    }
                }
            }
        }
        PGSecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("${tag}_cancel"))
    }
}

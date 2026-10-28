package com.mrm.pgmanager.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.mrm.pgmanager.ui.designsystem.DsComponent
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pgFieldSurface
import com.mrm.pgmanager.ui.theme.LocalThemeState

/** A labelled input whose surface matches search fields and other form controls. */
@Composable
internal fun PGFormInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leading: AppIcon? = null,
    textStyle: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
) {
    val theme = LocalThemeState.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = textStyle.copy(color = if (enabled) theme.inkColor else theme.mutedColor)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        interactionSource = interaction,
        textStyle = style,
        cursorBrush = SolidColor(theme.inkColor),
        modifier = modifier.fillMaxWidth().heightIn(min = DsComponent.Field)
            .pgFieldSurface(theme, focused)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = DsSpacing.FieldHorizontal, vertical = DsSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
            ) {
                if (leading != null) RoundedAppIcon(leading, tint = theme.mutedColor, size = 12.dp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder, style = style.copy(color = theme.tertiaryColor),
                            maxLines = if (singleLine) 1 else 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        }
    )
}

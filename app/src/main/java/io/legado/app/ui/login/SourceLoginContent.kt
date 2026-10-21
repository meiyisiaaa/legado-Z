package io.legado.app.ui.login

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.entities.rule.FlexChildStyle
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.ui.widget.compose.LocalLegadoMotion

internal data class SourceLoginField(
    val key: String,
    val label: String,
    val type: String,
    val action: String? = null,
    val options: List<String> = emptyList(),
    val hint: String? = null,
    val error: String? = null,
    val style: FlexChildStyle? = null,
    val countdownLabel: String? = null,
    val enabled: Boolean = true,
    val booleanToggle: Boolean = false,
    val isV2: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SourceLoginContent(
    toolbar: View,
    fields: List<SourceLoginField>,
    values: Map<String, String>,
    globalError: String?,
    onValueChange: (SourceLoginField, String) -> Unit,
    onSelect: (SourceLoginField, String) -> Unit,
    onToggle: (SourceLoginField, String) -> Unit,
    onAction: (SourceLoginField, Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.94f),
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column {
                AndroidView(
                    factory = { toolbar },
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 3.dp, vertical = 8.dp)
                ) {
                    globalError?.takeIf(String::isNotBlank)?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fields.forEach { field ->
                            if (field.style?.layout_wrapBefore == true) {
                                Spacer(Modifier.fillMaxWidth().height(0.dp))
                            }
                            val style = field.style
                            val widthModifier = when {
                                style?.layout_flexBasisPercent != null && style.layout_flexBasisPercent > 0f ->
                                    Modifier.fillMaxWidth(style.layout_flexBasisPercent.coerceIn(0.1f, 1f))
                                style != null && style.layout_flexGrow > 0f -> Modifier.weight(style.layout_flexGrow)
                                field.type == RowUi.Type.text || field.type == RowUi.Type.password -> Modifier.fillMaxWidth()
                                else -> Modifier.wrapContentWidth()
                            }
                            val alignModifier = when (style?.layout_alignSelf) {
                                "center" -> Modifier.align(Alignment.CenterVertically)
                                "flex_end" -> Modifier.align(Alignment.Bottom)
                                else -> Modifier
                            }
                            LoginFieldContent(
                                field = field,
                                value = values[field.key].orEmpty(),
                                modifier = widthModifier.then(alignModifier),
                                onValueChange = { onValueChange(field, it) },
                                onSelect = { onSelect(field, it) },
                                onToggle = { onToggle(field, it) },
                                onAction = { longClick -> onAction(field, longClick) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoginFieldContent(
    field: SourceLoginField,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onToggle: (String) -> Unit,
    onAction: (Boolean) -> Unit
) {
    val motion = LocalLegadoMotion.current
    when {
        field.type == RowUi.Type.text || field.type == RowUi.Type.password -> {
            val hint = field.hint?.takeIf(String::isNotBlank)
            val error = field.error
            val placeholderContent: (@Composable () -> Unit)? = hint?.let { text -> { Text(text) } }
            val supportingContent: (@Composable () -> Unit)? = error?.let { text ->
                {
                    Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                label = { Text(field.label) },
                placeholder = placeholderContent,
                singleLine = false,
                isError = field.error != null,
                supportingText = supportingContent,
                visualTransformation = if (field.type == RowUi.Type.password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = if (field.type == RowUi.Type.password) {
                    KeyboardOptions(keyboardType = KeyboardType.Password)
                } else KeyboardOptions.Default,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = when (field.style?.layout_justifySelf) {
                        "center" -> TextAlign.Center
                        "flex_end" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                )
            )
        }

        field.type == RowUi.Type.select -> {
            var expanded by remember(field.key) { mutableStateOf(false) }
            Column(modifier = modifier) {
                Text(field.label, style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = field.enabled && field.options.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            contentDescription = "${field.label}, $value"
                        }
                    ) {
                        Text(
                            text = value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        field.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    expanded = false
                                    onSelect(option)
                                }
                            )
                        }
                    }
                }
                field.error?.let {
                    Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        field.type == RowUi.Type.toggle && field.booleanToggle -> {
            androidx.compose.foundation.layout.Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(field.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = value.equals("true", ignoreCase = true),
                    onCheckedChange = { onToggle(it.toString()) },
                    enabled = field.enabled,
                    modifier = Modifier.semantics { contentDescription = field.label }
                )
            }
            field.error?.let {
                Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        field.type == RowUi.Type.toggle || field.type == RowUi.Type.button -> {
            val label = if (field.type == RowUi.Type.toggle) {
                val name = field.label
                val selected = value.ifBlank { field.options.firstOrNull().orEmpty() }
                if (field.style?.layout_justifySelf == "right") name + selected else selected + name
            } else field.countdownLabel ?: field.label
            val activeColor = MaterialTheme.colorScheme.secondaryContainer
            val color = if (field.enabled) activeColor else MaterialTheme.colorScheme.surfaceVariant
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        enabled = field.enabled,
                        role = Role.Button,
                        onClick = { onAction(false) },
                        onLongClick = if (field.isV2) null else ({ onAction(true) })
                    ),
                shape = shape,
                color = color,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = if (motion.enabled) 1.dp else 0.dp
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = when (field.style?.layout_justifySelf) {
                        "center" -> TextAlign.Center
                        "flex_end", "right" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                )
            }
        }

        field.type == "label" -> Text(field.label, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
    }
}

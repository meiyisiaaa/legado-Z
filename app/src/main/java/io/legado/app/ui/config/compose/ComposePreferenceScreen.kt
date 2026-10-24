package io.legado.app.ui.config.compose

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import io.legado.app.R
import io.legado.app.lib.prefs.SeekBarPreference
import io.legado.app.lib.prefs.PreferenceCategory as LegadoPreferenceCategory
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import kotlin.math.roundToInt

/**
 * Compose rendering for the existing preference model. The Preference objects stay authoritative,
 * so XML-defined persistence, dependency rules and app-specific click/dialog behavior continue to
 * run through AndroidX PreferenceManager.
 */
@Composable
fun ComposePreferenceScreen(
    screen: PreferenceScreen,
    modifier: Modifier = Modifier,
    revision: Int = 0,
    header: @Composable () -> Unit = {},
    onPreferenceClick: (Preference) -> Unit = { it.performClick() },
    onPreferenceLongClick: ((Preference) -> Boolean)? = null
) {
    val sections = remember(screen, revision) { collectSections(screen) }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 28.dp
        )
    ) {
        item(key = "screen-header") {
            header()
        }
        sections.forEachIndexed { sectionIndex, section ->
            section.title?.takeIf { it.isNotBlank() }?.let { title ->
                item(key = "section-header-$sectionIndex") {
                    PreferenceCategoryHeader(
                        title = title.toString(),
                        showDividerAbove = section.dividerAbove,
                        showDividerBelow = section.dividerBelow
                    )
                }
            }
            section.preferences.forEachIndexed { preferenceIndex, preference ->
                item(key = "preference-$sectionIndex-$preferenceIndex-${preference.key.orEmpty()}") {
                    PreferenceRow(
                        preference = preference,
                        revision = revision,
                        onClick = { onPreferenceClick(preference) },
                        onLongClick = onPreferenceLongClick?.let { { it(preference) } }
                    )
                }
            }
        }
    }
}

private data class PreferenceSectionModel(
    val title: CharSequence?,
    val preferences: List<Preference>,
    val dividerAbove: Boolean = false,
    val dividerBelow: Boolean = false
)

private fun collectSections(screen: PreferenceGroup): List<PreferenceSectionModel> {
    val sections = mutableListOf<PreferenceSectionModel>()
    val loose = mutableListOf<Preference>()

    fun flushLoose() {
        if (loose.isNotEmpty()) {
            sections += PreferenceSectionModel(null, loose.toList())
            loose.clear()
        }
    }

    fun appendChildren(group: PreferenceGroup, categoryTitle: CharSequence?) {
        val rows = mutableListOf<Preference>()
        fun flushRows() {
            if (rows.isNotEmpty()) {
                val category = group as? LegadoPreferenceCategory
                sections += PreferenceSectionModel(
                    title = categoryTitle,
                    preferences = rows.toList(),
                    dividerAbove = category?.allowDividerAbove == true,
                    dividerBelow = category?.allowDividerBelow == true
                )
                rows.clear()
            }
        }
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (!preference.isVisible) continue
            if (preference is PreferenceCategory) {
                flushRows()
                appendChildren(preference, preference.title)
            } else if (preference is PreferenceGroup) {
                flushRows()
                appendChildren(preference, preference.title ?: categoryTitle)
            } else {
                rows += preference
            }
        }
        flushRows()
    }

    for (index in 0 until screen.preferenceCount) {
        val preference = screen.getPreference(index)
        if (!preference.isVisible) continue
        if (preference is PreferenceCategory || preference is PreferenceGroup) {
            flushLoose()
            appendChildren(preference as PreferenceGroup, preference.title)
        } else {
            loose += preference
        }
    }
    flushLoose()
    return sections
}

@Composable
private fun PreferenceCategoryHeader(
    title: String,
    showDividerAbove: Boolean,
    showDividerBelow: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (showDividerAbove) {
            Spacer(Modifier.fillMaxWidth().height(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        if (showDividerBelow) {
            Spacer(Modifier.fillMaxWidth().height(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreferenceRow(
    preference: Preference,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Boolean)?
) {
    if (preference is SeekBarPreference) {
        SeekBarPreferenceRow(preference, revision)
        return
    }

    val enabled = preference.isEnabled
    val iconPainter = preference.icon?.let { rememberDrawablePainter(it) }
    val statePreference = preference as? TwoStatePreference
    val motion = LocalLegadoMotion.current
    val accessibilityLabel = listOfNotNull(
        preference.title?.toString()?.takeIf { it.isNotBlank() },
        preference.summary?.toString()?.takeIf { it.isNotBlank() },
        (preference as? ListPreference)?.entry?.toString()?.takeIf { it.isNotBlank() }
    ).joinToString(". ")
    val clickModifier = Modifier.combinedClickable(
        enabled = enabled && preference.isSelectable,
        onClickLabel = preference.title?.toString(),
        onClick = onClick,
        onLongClickLabel = preference.title?.toString()?.let { "$it（长按）" },
        onLongClick = onLongClick?.let { callback -> { callback(); Unit } }
    )

    Row(
        modifier = clickModifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .animateContentSize(animationSpec = tween(durationMillis = motion.state))
            .alpha(if (enabled) 1f else 0.48f)
            .semantics(mergeDescendants = true) {
                if (accessibilityLabel.isNotBlank()) contentDescription = accessibilityLabel
                role = when {
                    statePreference is CheckBoxPreference -> Role.Checkbox
                    statePreference != null -> Role.Switch
                    else -> Role.Button
                }
                statePreference?.let {
                    toggleableState = if (it.isChecked) {
                        androidx.compose.ui.state.ToggleableState.On
                    } else {
                        androidx.compose.ui.state.ToggleableState.Off
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!preference.title.isNullOrBlank()) {
                Text(
                    text = preference.title.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val summary = preference.summary?.takeIf { it.isNotBlank() }
            if (summary != null) {
                Text(
                    text = summary.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        when {
            statePreference is CheckBoxPreference -> {
                Checkbox(
                    checked = statePreference.isChecked,
                    enabled = enabled,
                    onCheckedChange = null
                )
            }
            statePreference != null -> {
                Switch(
                    checked = statePreference.isChecked,
                    enabled = enabled,
                    onCheckedChange = null
                )
            }
            preference is io.legado.app.lib.prefs.ColorPreference -> {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = Color(preference.selectedColor),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {}
            }
            preference is ListPreference -> {
                val value = preference.entry
                if (!value.isNullOrBlank()) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.42f, fill = false)
                    )
                }
            }
            preference is MultiSelectListPreference -> {
                val value = preference.values.joinToString("、")
                if (value.isNotBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.42f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBarPreferenceRow(
    preference: SeekBarPreference,
    @Suppress("UNUSED_PARAMETER") revision: Int
) {
    var sliderValue by remember(preference) { mutableFloatStateOf(preference.value.toFloat()) }
    var isDragging by remember(preference) { mutableStateOf(false) }
    LaunchedEffect(preference.value) {
        if (!isDragging) sliderValue = preference.value.toFloat()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (preference.isEnabled) 1f else 0.48f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.9f, fill = false).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!preference.title.isNullOrBlank()) {
                Text(
                    text = preference.title.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preference.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val next = (preference.value - 1).coerceAtLeast(preference.minValue)
                    sliderValue = next.toFloat()
                    preference.updateValue(next)
                },
                enabled = preference.isEnabled && preference.value > preference.minValue,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_reduce),
                    contentDescription = stringResource(R.string.reduce),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    isDragging = true
                    val roundedValue = value.roundToInt()
                    sliderValue = roundedValue.toFloat()
                    preference.updateValue(roundedValue)
                },
                onValueChangeFinished = {
                    preference.finishValueChange()
                    isDragging = false
                    sliderValue = preference.value.toFloat()
                },
                valueRange = preference.minValue.toFloat()..preference.maxValue.toFloat(),
                enabled = preference.isEnabled && preference.maxValue > preference.minValue,
                modifier = Modifier.weight(1f).semantics {
                    preference.title?.toString()?.takeIf { it.isNotBlank() }?.let {
                        contentDescription = it
                    }
                }
            )
            IconButton(
                onClick = {
                    val next = (preference.value + 1).coerceAtMost(preference.maxValue)
                    sliderValue = next.toFloat()
                    preference.updateValue(next)
                },
                enabled = preference.isEnabled && preference.value < preference.maxValue,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.plus),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = sliderValue.toInt().toString(),
                modifier = Modifier.padding(start = 2.dp).size(30.dp, 24.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable): Painter? {
    val image = remember(drawable) {
        runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
    }
    return image?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) }
}

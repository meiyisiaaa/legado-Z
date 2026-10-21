package io.legado.app.ui.code.config

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.setLayout

class ChangeThemeDialog() : BaseDialogFragment(0) {

    private var callBack: CallBack? = null
    private var editTemeAuto by mutableStateOf(AppConfig.editTemeAuto)
    private var themeIndex by mutableStateOf(currentThemeIndex())

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CallBack) callBack = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                ChangeThemeContent(
                    editTemeAuto = editTemeAuto,
                    selectedIndex = themeIndex,
                    onAutoChange = ::setAutoTheme,
                    onThemeSelected = ::selectTheme
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        themeIndex = currentThemeIndex()
        callBack?.upTheme(themeIndex)
    }

    private fun currentThemeIndex(): Int {
        return if (editTemeAuto && ThemeConfig.isDarkTheme()) {
            AppConfig.editThemeDark
        } else {
            AppConfig.editTheme
        }
    }

    private fun setAutoTheme(enabled: Boolean) {
        putPrefBoolean(PreferKey.editTemeAuto, enabled)
        editTemeAuto = enabled
        themeIndex = currentThemeIndex()
        callBack?.upTheme(themeIndex)
    }

    private fun selectTheme(index: Int) {
        themeIndex = index
        if (editTemeAuto && ThemeConfig.isDarkTheme()) {
            putPrefInt(PreferKey.editThemeDark, index)
        } else {
            putPrefInt(PreferKey.editTheme, index)
        }
        callBack?.upTheme(index)
    }

    interface CallBack {
        fun upTheme(index: Int)
    }
}

private val editorThemeOptions = listOf(
    R.string.edit_theme_monokai_dimmed,
    R.string.edit_theme_monokai,
    R.string.edit_theme_modern_dark,
    R.string.edit_theme_modern_light,
    R.string.edit_theme_solarized_dark,
    R.string.edit_theme_solarized_light,
    R.string.edit_theme_abyss,
    R.string.edit_theme_quiet_light
)

@Composable
private fun ChangeThemeContent(
    editTemeAuto: Boolean,
    selectedIndex: Int,
    onAutoChange: (Boolean) -> Unit,
    onThemeSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.change_theme),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = editTemeAuto,
                        role = Role.Switch,
                        onValueChange = onAutoChange
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.system_auto),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = editTemeAuto, onCheckedChange = null)
            }
        }
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                editorThemeOptions.filterIndexed { index, _ -> index % 2 == 0 }
                    .forEachIndexed { position, titleRes ->
                        ThemeOption(
                            titleRes = titleRes,
                            selected = selectedIndex == position * 2,
                            onClick = { onThemeSelected(position * 2) }
                        )
                    }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                editorThemeOptions.filterIndexed { index, _ -> index % 2 == 1 }
                    .forEachIndexed { position, titleRes ->
                        val index = position * 2 + 1
                        ThemeOption(
                            titleRes = titleRes,
                            selected = selectedIndex == index,
                            onClick = { onThemeSelected(index) }
                        )
                    }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChangeThemeContentPreview() {
    LegadoTheme {
        ChangeThemeContent(
            editTemeAuto = true,
            selectedIndex = 0,
            onAutoChange = {},
            onThemeSelected = {}
        )
    }
}

@Composable
private fun ThemeOption(titleRes: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = androidx.compose.ui.res.stringResource(titleRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

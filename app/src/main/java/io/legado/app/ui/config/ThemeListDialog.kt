package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.compose.*
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.setLayout
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

class ThemeListDialog : BaseDialogFragment(0) {
    private val configs = mutableStateListOf<ThemeConfig.Config>()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent { LegadoTheme {
                ThemeListContent(configs, { ThemeConfig.applyConfig(requireContext(), it) },
                    ::share, ::delete, ::importTheme, ::dismiss)
            } }
        }
    override fun onStart() { super.onStart(); setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT) }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) { initData() }
    fun initData() { configs.clear(); configs.addAll(ThemeConfig.configList) }
    private fun importTheme() {
        requireContext().getClipText()?.let {
            if (ThemeConfig.addConfig(it)) initData() else toastOnUi("格式不对，添加失败")
        }
    }
    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton { ThemeConfig.delConfig(index); initData() }
            noButton()
        }
    }
    fun share(index: Int) {
        requireContext().share(GSON.toJson(ThemeConfig.configList[index]), "主题分享")
    }
}

@Composable
internal fun ThemeListContent(configs: List<ThemeConfig.Config>, onApply: (ThemeConfig.Config) -> Unit,
    onShare: (Int) -> Unit, onDelete: (Int) -> Unit, onImport: () -> Unit, onClose: () -> Unit) {
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    Surface(Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
        Column(Modifier.navigationBarsPadding()) {
            ConfigDialogToolbar(stringResource(R.string.theme_list), onClose) {
                TextButton(
                    onClick = onImport,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
                ) { Text(stringResource(R.string.import_str)) }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                item(key = "mineral-presets") {
                    listOf(ThemeConfig.mineralDay, ThemeConfig.mineralNight).forEach { preset ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable { onApply(preset) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(Modifier.size(28.dp), shape = MaterialTheme.shapes.extraSmall,
                                color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(preset.primaryColor))) {}
                            Text(preset.themeName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                itemsIndexed(configs, key = { index, item -> "${item.themeName}:$index" }) { index, config ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable { onApply(config) }
                            .padding(start = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = config.themeName,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onShare(index) }) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share))
                        }
                        IconButton(onClick = { onDelete(index) }) {
                            Icon(painterResource(R.drawable.ic_clear_all), stringResource(R.string.delete))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

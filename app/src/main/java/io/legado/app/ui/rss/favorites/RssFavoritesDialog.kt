package io.legado.app.ui.rss.favorites

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssStar
import io.legado.app.databinding.DialogRssFavoriteConfigBinding
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class RssFavoritesDialog() : BaseDialogFragment(R.layout.dialog_rss_favorite_config, true) {

    constructor(rssArticle: RssArticle) : this() {
        arguments = Bundle().apply {
            putString("title", rssArticle.title)
            putString("group", rssArticle.group)
        }
    }

    constructor(rssStar: RssStar) : this() {
        arguments = Bundle().apply {
            putString("title", rssStar.title)
            putString("group", rssStar.group)
        }
    }

    private val binding by viewBinding(DialogRssFavoriteConfigBinding::bind)
    val callback get() = (parentFragment as? Callback) ?: (activity as? Callback)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments
        if (args == null) {
            dismiss()
            return
        }
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                var title by rememberSaveable { mutableStateOf(args.getString("title").orEmpty()) }
                var group by rememberSaveable { mutableStateOf(args.getString("group").orEmpty()) }
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.favorite)) })
                    },
                    bottomBar = {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                callback?.deleteFavorite()
                                dismiss()
                            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.cancel)) }
                                Button(onClick = {
                                    val finalTitle = title.takeIf(String::isNotBlank) ?: args.getString("title")
                                    val finalGroup = group.takeIf(String::isNotBlank) ?: args.getString("group")
                                    callback?.updateFavorite(finalTitle, finalGroup)
                                    dismiss()
                                }) { Text(stringResource(R.string.ok)) }
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.title)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = group,
                            onValueChange = { group = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.group)) },
                            singleLine = true
                        )
                    }
                }
            }
        }
    }

    interface Callback {
        fun updateFavorite(title: String?, group: String?)
        fun deleteFavorite()
    }
}

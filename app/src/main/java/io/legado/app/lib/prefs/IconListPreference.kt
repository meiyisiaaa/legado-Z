package io.legado.app.lib.prefs

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposeDialogView
import io.legado.app.utils.getCompatDrawable
import io.legado.app.utils.setLayout


class IconListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {
    private var iconNames: Array<CharSequence>
    private val mEntryDrawables = arrayListOf<Drawable?>()

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_icon

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.IconListPreference, 0, 0)

        iconNames = try {
            a.getTextArray(R.styleable.IconListPreference_icons)
        } finally {
            a.recycle()
        }

        for (iconName in iconNames) {
            val resId = context.resources
                .getIdentifier(iconName.toString(), "mipmap", context.packageName)
            var d: Drawable? = null
            kotlin.runCatching {
                d = context.getCompatDrawable(resId)
            }
            mEntryDrawables.add(d)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val v = Preference.bindView<ImageView>(
            context,
            holder,
            icon,
            title,
            summary,
            widgetLayoutResource,
            R.id.preview,
            50,
            50
        )
        if (v is ImageView) {
            val selectedIndex = findIndexOfValue(value)
            if (selectedIndex >= 0) {
                val drawable = mEntryDrawables[selectedIndex]
                v.setImageDrawable(drawable)
            }
        }
    }

    override fun onClick() {
        getActivity()?.let {
            val dialog = IconDialog().apply {
                val args = Bundle()
                args.putString("value", value)
                args.putCharSequenceArray("entries", entries)
                args.putCharSequenceArray("entryValues", entryValues)
                args.putCharSequenceArray("iconNames", iconNames)
                arguments = args
                onChanged = { value ->
                    this@IconListPreference.value = value
                }
            }
            it.supportFragmentManager
                .beginTransaction()
                .add(dialog, getFragmentTag())
                .commitAllowingStateLoss()
        }
    }

    override fun onAttached() {
        super.onAttached()
        val fragment =
            getActivity()?.supportFragmentManager?.findFragmentByTag(getFragmentTag()) as IconDialog?
        fragment?.onChanged = { value ->
            this@IconListPreference.value = value
        }
    }

    private fun getActivity(): FragmentActivity? {
        val context = context
        if (context is FragmentActivity) {
            return context
        } else if (context is ContextWrapper) {
            val baseContext = context.baseContext
            if (baseContext is FragmentActivity) {
                return baseContext
            }
        }
        return null
    }

    private fun getFragmentTag(): String {
        return "icon_$key"
    }

    class IconDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

        var onChanged: ((value: String) -> Unit)? = null
        var dialogValue: String? = null
        var dialogEntries: Array<CharSequence>? = null
        var dialogEntryValues: Array<CharSequence>? = null
        var dialogIconNames: Array<CharSequence>? = null
        override fun onStart() {
            super.onStart()
            setLayout(0.8f, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            arguments?.let {
                dialogValue = it.getString("value")
                dialogEntries = it.getCharSequenceArray("entries")
                dialogEntryValues = it.getCharSequenceArray("entryValues")
                dialogIconNames = it.getCharSequenceArray("iconNames")
            }
            return ComposeDialogView(requireContext()).apply {
                setContent {
                    LegadoTheme {
                        IconPreferenceDialogContent(
                            values = dialogEntryValues.orEmpty().map(CharSequence::toString),
                            labels = dialogEntries.orEmpty().map(CharSequence::toString),
                            iconNames = dialogIconNames.orEmpty().map(CharSequence::toString),
                            selectedValue = dialogValue,
                            resolveIcon = { name ->
                                resources.getIdentifier(name, "mipmap", requireContext().packageName)
                            },
                            onSelect = { selected ->
                                onChanged?.invoke(selected)
                                dismissAllowingStateLoss()
                            },
                            onClose = { dismissAllowingStateLoss() }
                        )
                    }
                }
            }
        }

        override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
            // The dialog content is entirely composed in onCreateView.
        }
    }
}

@Composable
private fun IconPreferenceDialogContent(
    values: List<String>,
    labels: List<String>,
    iconNames: List<String>,
    selectedValue: String?,
    resolveIcon: (String) -> Int,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    PreferenceDialogSurface(title = stringResource(R.string.change_icon), onClose = onClose) {
        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            itemsIndexed(values, key = { index, value -> "$index:$value" }) { index, value ->
                PreferenceSingleChoiceRow(
                    title = labels.getOrNull(index) ?: value,
                    selected = value == selectedValue,
                    onSelect = { onSelect(value) },
                    leading = {
                        val iconName = iconNames.getOrNull(index)
                        val iconRes = iconName?.let(resolveIcon) ?: 0
                        if (iconRes != 0) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = labels.getOrNull(index) ?: value,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Box(Modifier.size(48.dp))
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IconPreferenceDialogPreview() {
    LegadoTheme {
        IconPreferenceDialogContent(
            values = listOf("default", "alternate"),
            labels = listOf("Default", "Alternate"),
            iconNames = emptyList(),
            selectedValue = "default",
            resolveIcon = { 0 },
            onSelect = {},
            onClose = {}
        )
    }
}

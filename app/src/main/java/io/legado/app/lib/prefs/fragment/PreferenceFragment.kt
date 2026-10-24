package io.legado.app.lib.prefs.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.lib.prefs.EditTextPreferenceDialog
import io.legado.app.lib.prefs.ListPreferenceDialog
import io.legado.app.lib.prefs.MultiSelectListPreferenceDialog
import io.legado.app.lib.prefs.Preference as LegadoPreference
import io.legado.app.lib.prefs.SwitchPreference as LegadoSwitchPreference
import io.legado.app.ui.config.compose.ComposePreferenceScreen
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.applyNavigationBarPadding

abstract class PreferenceFragment : PreferenceFragmentCompat() {

    @Composable
    protected open fun PreferenceHeader() = Unit

    private val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
    private val preferenceRevision = mutableIntStateOf(0)
    private var preferenceAdapter: RecyclerView.Adapter<*>? = null
    private var preferenceAdapterObserver: RecyclerView.AdapterDataObserver? = null
    private var preferenceComposeView: ComposeView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val legacyList = super.onCreateView(inflater, container, savedInstanceState)
        val composeView = LegadoComposeView(requireContext())
        preferenceComposeView = composeView
        return FrameLayout(requireContext()).apply {
            addView(
                legacyList,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            legacyList.visibility = View.GONE
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        listView.applyNavigationBarPadding()
        preferenceAdapterObserver?.let { observer ->
            preferenceAdapter?.unregisterAdapterDataObserver(observer)
        }
        val adapter = listView.adapter
        val observer = object : RecyclerView.AdapterDataObserver() {
            private fun invalidateCompose() {
                listView.post { preferenceRevision.intValue++ }
            }

            override fun onChanged() = invalidateCompose()

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = invalidateCompose()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidateCompose()

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidateCompose()

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                invalidateCompose()
        }
        preferenceAdapter = adapter
        preferenceAdapterObserver = observer
        adapter?.registerAdapterDataObserver(observer)
        val screen = preferenceScreen ?: return
        preferenceComposeView?.setContent {
            LegadoTheme {
                ComposePreferenceScreen(
                    screen = screen,
                    modifier = Modifier.fillMaxSize(),
                    revision = preferenceRevision.intValue,
                    header = { PreferenceHeader() },
                    onPreferenceClick = { it.performClick() },
                    onPreferenceLongClick = { preference ->
                        when (preference) {
                            is LegadoPreference -> preference.performPreferenceLongClick()
                            is LegadoSwitchPreference -> preference.performPreferenceLongClick()
                            else -> false
                        }
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        preferenceAdapterObserver?.let { observer ->
            preferenceAdapter?.unregisterAdapterDataObserver(observer)
        }
        preferenceAdapter = null
        preferenceAdapterObserver = null
        preferenceComposeView = null
        super.onDestroyView()
    }

    @SuppressLint("RestrictedApi")
    override fun onDisplayPreferenceDialog(preference: Preference) {

        var handled = false
        if (callbackFragment is OnPreferenceDisplayDialogCallback) {
            handled =
                (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
        }
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }

        if (handled) {
            return
        }

        // check if dialog is already showing
        if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
            return
        }

        val dialogFragment: DialogFragment = when (preference) {
            is EditTextPreference -> {
                EditTextPreferenceDialog.newInstance(preference.getKey())
            }
            is ListPreference -> {
                ListPreferenceDialog.newInstance(preference.getKey())
            }
            is MultiSelectListPreference -> {
                MultiSelectListPreferenceDialog.newInstance(preference.getKey())
            }
            else -> {
                throw IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                            + preference.javaClass.simpleName
                            + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                            + "displaying a custom dialog for this Preference."
                )
            }
        }
        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)

        dialogFragment.show(parentFragmentManager, dialogFragmentTag)
    }

}

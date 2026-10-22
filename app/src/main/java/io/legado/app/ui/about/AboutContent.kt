package io.legado.app.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal data class AboutUiState(val versionName: String)

internal enum class AboutAction {
    OpenRepository,
    OpenHelp,
    OpenApiDocumentation,
    OpenUpdateHistory,
    ShowCrashLogs,
    SaveLogs,
    CreateHeapDump,
    ShowPrivacyPolicy,
    ShowLicense,
    ShowDisclaimer
}

internal const val ABOUT_CONTENT_LIST_TEST_TAG = "about_content_list"

private data class AboutEntry(
    @StringRes val title: Int,
    @StringRes val summary: Int?,
    val action: AboutAction
)

private data class AboutSection(
    @StringRes val title: Int,
    val entries: List<AboutEntry>
)

private val aboutSections = listOf(
    AboutSection(
        title = R.string.about_project_section,
        entries = listOf(
            AboutEntry(R.string.about_repository, R.string.about_repository_summary, AboutAction.OpenRepository),
            AboutEntry(R.string.about_user_help, null, AboutAction.OpenHelp),
            AboutEntry(R.string.about_api_documentation, null, AboutAction.OpenApiDocumentation),
            AboutEntry(R.string.about_update_history, R.string.about_update_history_summary, AboutAction.OpenUpdateHistory)
        )
    ),
    AboutSection(
        title = R.string.about_diagnostics_section,
        entries = listOf(
            AboutEntry(R.string.crash_log, null, AboutAction.ShowCrashLogs),
            AboutEntry(R.string.save_log, R.string.about_save_logs_summary, AboutAction.SaveLogs),
            AboutEntry(R.string.create_heap_dump, R.string.about_heap_dump_summary, AboutAction.CreateHeapDump)
        )
    ),
    AboutSection(
        title = R.string.about_legal_section,
        entries = listOf(
            AboutEntry(R.string.privacy_policy, null, AboutAction.ShowPrivacyPolicy),
            AboutEntry(R.string.license, R.string.about_license_summary, AboutAction.ShowLicense),
            AboutEntry(R.string.disclaimer, null, AboutAction.ShowDisclaimer)
        )
    )
)

@Composable
internal fun AboutContent(
    state: AboutUiState,
    onAction: (AboutAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag(ABOUT_CONTENT_LIST_TEST_TAG),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "identity") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_product_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.version_name, state.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.about_project_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.about_project_import_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        aboutSections.forEach { section ->
            item(key = "section-${section.title}") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(section.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .semantics { heading() }
                    )
                    section.entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        val summaryResource = entry.summary
                        val summary = if (summaryResource == null) null else stringResource(summaryResource)
                        AboutActionRow(
                            title = stringResource(entry.title),
                            summary = summary,
                            onClick = { onAction(entry.action) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    summary: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

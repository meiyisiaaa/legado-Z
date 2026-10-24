package io.legado.app.ui.book.read.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

internal data class RoleTtsRoleItem(
    val id: String,
    val name: String,
    val detail: String
)

@Composable
internal fun RoleTtsContent(
    enabled: Boolean,
    roles: List<RoleTtsRoleItem>,
    onEnabledChange: (Boolean) -> Unit,
    onRoleClick: (String) -> Unit,
    onAddRole: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("role_tts_root"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.role_tts_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                text = stringResource(R.string.role_tts_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .testTag("role_tts_enabled")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.role_tts_enable),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = enabled, onCheckedChange = null)
            }
        }
        item {
            Text(
                text = stringResource(R.string.role_tts_roles_heading),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(roles, key = { it.id }) { role ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRoleClick(role.id) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${role.name}\n${role.detail}"
                        this.role = Role.Button
                    }
                    .testTag("role_tts_role"),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(role.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        role.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onAddRole,
                modifier = Modifier.fillMaxWidth().testTag("role_tts_add_role")
            ) {
                Text(stringResource(R.string.role_tts_add_role))
            }
        }
        item {
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth().testTag("role_tts_generate")
            ) {
                Text(stringResource(R.string.role_tts_choose_chapters))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoleTtsPreview() {
    LegadoTheme {
        RoleTtsContent(
            enabled = false,
            roles = listOf(RoleTtsRoleItem("narrator", "旁白", "跟随旁白")),
            onEnabledChange = {}, onRoleClick = {}, onAddRole = {}, onGenerate = {}
        )
    }
}

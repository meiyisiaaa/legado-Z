package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

@Composable
internal fun PageKeyContent(
    previousKeys: String,
    nextKeys: String,
    onPreviousChange: (String) -> Unit,
    onNextChange: (String) -> Unit,
    onFocusChange: (Boolean?) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.custom_page_key),
            style = MaterialTheme.typography.titleLarge
        )
        OutlinedTextField(
            value = previousKeys,
            onValueChange = onPreviousChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                onFocusChange(if (focus.isFocused) true else null)
            },
            label = { Text(stringResource(R.string.prev_page_key)) },
            singleLine = true
        )
        OutlinedTextField(
            value = nextKeys,
            onValueChange = onNextChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                onFocusChange(if (focus.isFocused) false else null)
            },
            label = { Text(stringResource(R.string.next_page_key)) },
            singleLine = true
        )
        Text(
            text = stringResource(R.string.page_key_set_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PageKeyContentPreview() {
    LegadoTheme {
        PageKeyContent("", "", {}, {}, {}, {}, {})
    }
}

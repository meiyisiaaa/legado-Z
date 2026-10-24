package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BookshelfInput(label: String, initial: String = "", readOnly: Boolean = false,
    onChange: (String) -> Unit = {}) {
    var value by rememberSaveable { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        OutlinedTextField(value, { value = it; onChange(it) }, label = { Text(label) },
            readOnly = readOnly, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 10)
    }
}

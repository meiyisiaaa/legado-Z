package io.legado.app.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun LocalPasswordContent(onChange: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    OutlinedTextField(password, { password = it; onChange(it) }, label = { Text("密码") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
}

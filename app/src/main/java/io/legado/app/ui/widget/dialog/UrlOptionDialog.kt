package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout

class UrlOptionDialog(context: Context, private val success: (String) -> Unit) : Dialog(context) {

    private var values by mutableStateOf(UrlOptionValues())

    init {
        setCanceledOnTouchOutside(true)
        setContentView(
            ComposeDialogView(context).apply {
                setContent {
                    LegadoTheme {
                        UrlOptionDialogContent(
                            charsets = AppConst.charsets,
                            values = values,
                            onValuesChange = { values = it },
                            onDismiss = ::dismiss,
                            onSave = { values ->
                                success.invoke(GSON.toJson(getUrlOption(values)))
                                dismiss()
                            }
                        )
                    }
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(R.color.transparent)
    }

    private fun getUrlOption(values: UrlOptionValues): AnalyzeUrl.UrlOption {
        val urlOption = AnalyzeUrl.UrlOption()
        urlOption.useWebView(values.useWebView)
        urlOption.setMethod(values.method)
        urlOption.setCharset(values.charset)
        urlOption.setHeaders(values.headers)
        urlOption.setBody(values.body)
        urlOption.setRetry(values.retry)
        urlOption.setType(values.type)
        urlOption.setWebJs(values.webJs)
        urlOption.setJs(values.js)
        urlOption.setJs(values.bodyJs)
        urlOption.setDnsIp(values.dnsIp)
        return urlOption
    }
}

internal data class UrlOptionValues(
    val useWebView: Boolean = false,
    val method: String = "",
    val charset: String = "",
    val headers: String = "",
    val body: String = "",
    val retry: String = "",
    val type: String = "",
    val webJs: String = "",
    val js: String = "",
    val bodyJs: String = "",
    val dnsIp: String = ""
)

@Composable
internal fun UrlOptionDialogContent(
    charsets: List<String>,
    values: UrlOptionValues,
    onValuesChange: (UrlOptionValues) -> Unit,
    onDismiss: () -> Unit,
    onSave: (UrlOptionValues) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp
        ) {
            Column {
                LegadoDialogHeader(
                    title = stringResource(R.string.url_option),
                    onClose = onDismiss,
                    actions = {
                        TextButton(
                            onClick = { onSave(values) }
                        ) { Text(stringResource(R.string.ok)) }
                    }
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = values.useWebView,
                                role = Role.Checkbox,
                                onValueChange = { onValuesChange(values.copy(useWebView = it)) }
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = values.useWebView,
                            onCheckedChange = null
                        )
                        Text("useWebView", style = MaterialTheme.typography.bodyLarge)
                    }
                    SuggestedOutlinedField(
                        value = values.method,
                        onValueChange = { onValuesChange(values.copy(method = it)) },
                        label = "method",
                        suggestions = remember { listOf("POST", "GET") }
                    )
                    SuggestedOutlinedField(
                        value = values.charset,
                        onValueChange = { onValuesChange(values.copy(charset = it)) },
                        label = "charset",
                        suggestions = charsets
                    )
                    OutlinedTextField(values.headers, { onValuesChange(values.copy(headers = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("headers") }, minLines = 1)
                    OutlinedTextField(values.body, { onValuesChange(values.copy(body = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("body") }, minLines = 1)
                    OutlinedTextField(values.type, { onValuesChange(values.copy(type = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("type") }, minLines = 1)
                    OutlinedTextField(
                        values.retry, { onValuesChange(values.copy(retry = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("retry") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(values.webJs, { onValuesChange(values.copy(webJs = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("webJs") }, minLines = 1)
                    OutlinedTextField(values.js, { onValuesChange(values.copy(js = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("js") }, minLines = 1)
                    OutlinedTextField(values.bodyJs, { onValuesChange(values.copy(bodyJs = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("bodyJs") }, minLines = 1)
                    OutlinedTextField(values.dnsIp, { onValuesChange(values.copy(dnsIp = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("dnsIp") }, minLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SuggestedOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = "${label} suggestions"
                    )
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UrlOptionDialogPreview() {
    var values by remember { mutableStateOf(UrlOptionValues()) }
    LegadoTheme {
        UrlOptionDialogContent(
            charsets = listOf("UTF-8", "GBK"),
            values = values,
            onValuesChange = { values = it },
            onDismiss = {},
            onSave = {}
        )
    }
}

package io.legado.app.ui.widget.number

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposeDialogView
import io.legado.app.utils.hideSoftInput

class NumberPickerDialog(context: Context, private val isDecimalMode: Boolean = false) {
    private val context = context
    private var numberPicker: NumberPicker? = null
    private var title: String? = null
    private var maxValue: Int? = null
    private var minValue: Int? = null
    private var value: Int? = null
    private var customButtonText: Int? = null
    private var customButtonListener: (() -> Unit)? = null

    fun setTitle(title: String): NumberPickerDialog {
        this.title = title
        return this
    }

    fun setMaxValue(value: Int): NumberPickerDialog {
        maxValue = value
        return this
    }

    fun setMinValue(value: Int): NumberPickerDialog {
        minValue = value
        return this
    }

    fun setValue(value: Int): NumberPickerDialog {
        this.value = value
        return this
    }

    fun setCustomButton(textId: Int, listener: (() -> Unit)?): NumberPickerDialog {
        customButtonText = textId
        customButtonListener = listener
        return this
    }

    fun show(callBack: ((value: Int) -> Unit)?) {
        val dialog = Dialog(context)
        val content = ComposeDialogView(context)
        dialog.setContentView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnDismissListener { numberPicker = null }
        content.setContent {
            LegadoTheme {
                NumberPickerDialogContent(
                    title = title,
                    minValue = minValue,
                    maxValue = maxValue,
                    initialValue = value,
                    decimalMode = isDecimalMode,
                    customButtonText = customButtonText,
                    onPickerReady = { numberPicker = it },
                    onCustom = {
                        numberPicker?.let { picker ->
                            picker.clearFocus()
                            picker.hideSoftInput()
                            customButtonListener?.invoke()
                        }
                        dialog.dismiss()
                    },
                    onCancel = dialog::dismiss,
                    onConfirm = {
                        numberPicker?.let { picker ->
                            picker.clearFocus()
                            picker.hideSoftInput()
                            callBack?.invoke(picker.value)
                        }
                        dialog.dismiss()
                    }
                )
            }
        }
        dialog.setOnShowListener {
            val width = (context.resources.displayMetrics.widthPixels * 0.88f).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}

@Composable
internal fun NumberPickerDialogContent(
    title: String?,
    minValue: Int?,
    maxValue: Int?,
    initialValue: Int?,
    decimalMode: Boolean,
    customButtonText: Int?,
    onPickerReady: (NumberPicker) -> Unit,
    onCustom: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            if (!title.isNullOrEmpty()) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { viewContext ->
                    NumberPicker(viewContext).also { picker ->
                        picker.id = R.id.number_picker
                        configureNumberPicker(picker, minValue, maxValue, initialValue, decimalMode)
                        onPickerReady(picker)
                    }
                },
                update = { picker ->
                    onPickerReady(picker)
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (customButtonText != null) {
                    TextButton(onClick = onCustom) {
                        Text(androidx.compose.ui.res.stringResource(customButtonText))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                }
                Button(onClick = onConfirm) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ok))
                }
            }
        }
    }
}

private fun configureNumberPicker(
    picker: NumberPicker,
    minValue: Int?,
    maxValue: Int?,
    initialValue: Int?,
    decimalMode: Boolean
) {
    minValue?.let { picker.minValue = it }
    maxValue?.let { picker.maxValue = it }
    initialValue?.let { picker.value = it }
    if (decimalMode && minValue != null && maxValue != null) {
        picker.displayedValues = Array(maxValue - minValue + 1) { index ->
            ((minValue + index) / 10.0).toString()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberPickerDialogPreview() {
    LegadoTheme {
        NumberPickerDialogContent("Number", 0, 10, 3, false, null, {}, {}, {}, {})
    }
}

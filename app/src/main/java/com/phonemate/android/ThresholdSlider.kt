package com.phonemate.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ThresholdSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current
    val minimum = range.start.roundToInt()
    val maximum = range.endInclusive.roundToInt()

    LaunchedEffect(value) {
        if (input.toIntOrNull() != value) {
            input = value.toString()
        }
    }

    fun commitInput() {
        val committedValue = input.toIntOrNull()?.coerceIn(minimum, maximum) ?: value
        input = committedValue.toString()
        if (committedValue != value) {
            onValueChange(committedValue)
        }
    }

    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                modifier = Modifier.weight(1f),
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = range
            )
            OutlinedTextField(
                modifier = Modifier
                    .width(112.dp)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) commitInput()
                    },
                value = input,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all(Char::isDigit)) {
                        input = newValue
                        newValue.toIntOrNull()?.let { enteredValue ->
                            onValueChange(enteredValue.coerceIn(minimum, maximum))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitInput()
                        focusManager.clearFocus()
                    }
                ),
                suffix = { Text(unit) },
                singleLine = true
            )
        }
    }
}

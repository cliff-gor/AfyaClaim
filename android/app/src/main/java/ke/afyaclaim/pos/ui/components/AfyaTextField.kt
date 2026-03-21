package ke.afyaclaim.pos.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes

@Composable
fun AfyaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    placeholder: String? = null,
) {
    val c = Afya.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.muted) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = c.muted) } },
        trailingIcon = trailing,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        shape = AfyaShapes.input,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.ink,
            unfocusedBorderColor = c.outline,
            focusedLabelColor = c.ink,
            unfocusedLabelColor = c.muted,
            cursorColor = c.ink,
            errorBorderColor = c.coral,
            errorLabelColor = c.coral,
            errorSupportingTextColor = c.coral,
            focusedSupportingTextColor = c.muted,
            unfocusedSupportingTextColor = c.muted,
        ),
    )
}

package cc.hosaka.okonomi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.theme.Dimens
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * A pill shaped search field with a leading search icon and a
 * clear action that is shown while the field has text.
 *
 * A `null` [onTextChange] renders the field disabled.
 */
@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    text: String,
    placeholder: String,
    onTextChange: ((String) -> Unit)?,
    onClear: (() -> Unit)? = onTextChange?.let { { it("") } },
    onSearch: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val contentColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.titleMedium
        .copy(color = contentColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Height should be the size of
            // the Material 3 top bar.
            .height(64.dp)
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = 8.dp,
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(16.dp),
        )
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        }
        Spacer(
            modifier = Modifier
                .size(16.dp),
        )
        BasicTextField(
            modifier = Modifier
                .weight(1f),
            value = text,
            onValueChange = { onTextChange?.invoke(it) },
            enabled = onTextChange != null,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch?.invoke() ?: focusManager.clearFocus()
                },
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            },
        )
        Spacer(
            modifier = Modifier
                .size(8.dp),
        )
        AnimatedVisibility(
            visible = text.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            IconButton(
                enabled = onClear != null,
                onClick = {
                    onClear?.invoke()
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = stringResource(Res.string.search_clear),
                )
            }
        }
    }
}

package cc.hosaka.okonomi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
 *
 * A non-null [focusRequester] is attached to the inner text field so
 * callers can focus it programmatically.
 *
 * Focus arriving on a field that already holds text puts the caret at
 * the end of it, so reselecting the Search tab leaves the reader ready
 * to refine the query rather than to type in front of it.
 *
 * [trailing] is drawn at the trailing edge, after the clear action rather
 * than in place of it: clear is the action the reader reaches for while
 * typing and it keeps the position it has always had. A caller that
 * passes nothing gets exactly the field it always got.
 */
@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    text: String,
    placeholder: String,
    onTextChange: ((String) -> Unit)?,
    onClear: (() -> Unit)? = onTextChange?.let { { it("") } },
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    trailing: @Composable RowScope.() -> Unit = {},
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
        // Selection-aware state, so focus can land the caret at the end
        // of what is already typed. A plain String field cannot: nothing
        // tracks the selection, and the platform puts the caret at
        // offset zero when focus arrives from a tab reselect rather than
        // from a tap that named a position.
        //
        // The caller stays the single source of truth for the query, so
        // a value pushed in from outside (the clear action, a restored
        // query) replaces what is here and puts the caret at the end of
        // it. What must *not* be treated as such a push is the caller
        // echoing back what the field just reported: [text] arrives
        // asynchronously (querySink -> combine -> stateIn), so between a
        // keystroke and its echo the two disagree for a frame or more.
        // Rebuilding the value on that disagreement rebuilt the caret at
        // the end of the string, which meant typing into the middle of
        // an existing query lost the caret and the next keystroke landed
        // at the end. Reacting to [text] *changing* instead of to the
        // two differing tells the two cases apart.
        var fieldValue by remember {
            mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        }
        // The last value seen from the caller. A push is a change of it;
        // an echo is not.
        var lastPushed by remember { mutableStateOf(text) }
        if (text != lastPushed) {
            lastPushed = text
            // An echo that arrives late still changes [text], and by
            // then the field already holds it: nothing to do.
            if (text != fieldValue.text) {
                fieldValue = TextFieldValue(text, TextRange(text.length))
            }
        }
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                // Every focus gain moves the caret to the end, not only
                // the tab reselect this exists for. The handler is told
                // that focus arrived and nothing about why: a reselect,
                // a programmatic request and a tap that named a position
                // are the same event here. Taps survive it in practice
                // because the tap sets its own selection after focus
                // lands, but that is the gesture's ordering rather than
                // a distinction this code makes. Do not read the comment
                // as one.
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        fieldValue = fieldValue.copy(
                            selection = TextRange(fieldValue.text.length),
                        )
                    }
                },
            value = fieldValue,
            onValueChange = { value ->
                // The selection is always accepted, so typing and
                // dragging the caret behave exactly as before; only the
                // text is reported upward.
                fieldValue = value
                if (value.text != text) {
                    onTextChange?.invoke(value.text)
                }
            },
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
        trailing()
    }
}

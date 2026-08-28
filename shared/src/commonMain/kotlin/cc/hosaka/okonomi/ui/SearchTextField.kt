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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
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
 * A pill shaped search field, by default with a leading search icon and
 * a clear action that is shown while the field has text.
 *
 * A `null` [onTextChange] draws no text field at all: the pill holds a
 * plain [Text] and drops to [readOnlyContainerColor], a recessed tone
 * the editable field never wears. A bar with no way to type into it must
 * not look like one that has, nor announce itself as one — a disabled
 * text field tells a screen reader "edit box, disabled", and being a
 * merge boundary it also swallows any heading the caller marks the pill
 * with. The radical screen's bar is exactly this case: a read-only line
 * naming what is being shown, which is a title rather than a query.
 *
 * A non-null [focusRequester] is attached to the inner text field so
 * callers can focus it programmatically.
 *
 * Focus arriving on a field that already holds text puts the caret at
 * the end of it, so reselecting the Search tab leaves the reader ready
 * to refine the query rather than to type in front of it.
 *
 * [leading] is the pill's leading content, defaulting to the search icon
 * this has always drawn. It is a slot rather than a flag so a caller
 * that is not a search — again, the radical bar — can put nothing there,
 * mirroring [trailing]. The gap between the icon and the text belongs to
 * the slot rather than to the Row, so a caller that draws nothing there
 * leaves no hole: only the pill's own start padding stays, and the title
 * begins where the icon used to.
 *
 * The clear action is drawn only when the field is editable and [onClear]
 * is non-null. It used to be drawn disabled whenever there was text,
 * which on a field nothing can clear is an affordance for an action that
 * does not exist. Both conditions are checked, not just [onClear]: its
 * default is derived from [onTextChange], but a caller can pass one
 * without the other, and a clear button on a bar nothing can type into
 * is the exact thing the read-only bar must never grow.
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
    leading: @Composable RowScope.() -> Unit = { SearchFieldIcon() },
    trailing: @Composable RowScope.() -> Unit = {},
) {
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
                color = if (onTextChange != null) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    readOnlyContainerColor()
                },
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
            leading()
        }
        if (onTextChange == null) {
            ReadOnlyLabel(
                modifier = Modifier
                    .weight(1f),
                text = text,
                placeholder = placeholder,
                textStyle = textStyle,
                contentColor = contentColor,
            )
        } else {
            QueryField(
                modifier = Modifier
                    .weight(1f),
                text = text,
                placeholder = placeholder,
                onTextChange = onTextChange,
                onSearch = onSearch,
                focusRequester = focusRequester,
                textStyle = textStyle,
            )
        }
        Spacer(
            modifier = Modifier
                .size(8.dp),
        )
        AnimatedVisibility(
            visible = onTextChange != null && onClear != null && text.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            IconButton(
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

/**
 * What the pill holds when nothing can be typed into it.
 *
 * A [Text], not a disabled [BasicTextField]. Drawn as a text field it
 * announces itself "edit box, disabled" to a screen reader, and — being
 * an editable node, which is a semantics merge boundary — it is also
 * what a reader lands on, so any heading the caller marks the pill with
 * is never reached. The one caller that is read-only for good, the
 * radical screen's bar, is a title rather than a query, and a title is
 * text.
 *
 * The placeholder is shown for an empty value the same way the editable
 * field shows it, so a read-only pill is never blank.
 */
@Composable
private fun ReadOnlyLabel(
    modifier: Modifier,
    text: String,
    placeholder: String,
    textStyle: TextStyle,
    contentColor: Color,
) {
    Text(
        modifier = modifier,
        text = text.ifEmpty { placeholder },
        style = textStyle,
        color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The editable half of the pill, extracted so the read-only case is a
 * sibling of it rather than a set of flags threaded through it. Nothing
 * here changed when that case was added.
 */
@Composable
private fun QueryField(
    modifier: Modifier,
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSearch: (() -> Unit)?,
    focusRequester: FocusRequester?,
    textStyle: TextStyle,
) {
    val focusManager = LocalFocusManager.current
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
        modifier = modifier
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
                onTextChange(value.text)
            }
        },
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
}

/**
 * The field's default leading content.
 *
 * Tagged rather than described: the icon is decorative and carries no
 * content description, so it puts nothing in the semantics tree that a
 * test could ask about, and giving it one would make every screen reader
 * announce a picture of the thing the field already says it is. The tag
 * is the one observable fact about it, and the radical bar's claim to
 * carry no search icon is only checkable through it.
 */
@Composable
private fun SearchFieldIcon() {
    Icon(
        modifier = Modifier
            .testTag(SEARCH_FIELD_ICON_TAG),
        imageVector = Icons.Outlined.Search,
        contentDescription = null,
    )
    // The gap between the icon and the query, and it belongs to the icon:
    // emitted by the Row instead, a field that draws no leading content
    // would carry the gap for something that is not there.
    Spacer(
        modifier = Modifier
            .size(16.dp),
    )
}

internal const val SEARCH_FIELD_ICON_TAG = "search-text-field-icon"

/**
 * The pill's tone when nothing can be typed into it. One step below the
 * editable [MaterialTheme.colorScheme.surfaceContainerHigh] rather than
 * above it: a read-only bar is a label, and a label recedes.
 */
@Composable
private fun readOnlyContainerColor() = MaterialTheme.colorScheme.surfaceContainerLow

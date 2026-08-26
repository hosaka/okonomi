/*
 * Copyright 2026 turtlekazu
 * Copyright 2026 Alex March
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.hosaka.okonomi.ui.furigana

import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/** Font size and colour for a style that states neither. */
private const val FALLBACK_FONT_SIZE = 14f

private val FALLBACK_COLOR = Color.Black

/**
 * A single line of text in a real [TextView], because the two settings
 * that remove Android's extra vertical space —
 * [TextView.isFallbackLineSpacing] and [TextView.setIncludeFontPadding]
 * — have no equivalent in `BasicText`. Compose's own line-height rules
 * are put back on top through [ComposeLineHeightSpan], so the line sits
 * where the rest of the screen expects it to.
 *
 * Only the style properties a ruby unit actually sets are carried over;
 * upstream mapped the whole of [TextStyle] onto spans, and every branch
 * of that this app never reaches was dropped rather than kept for
 * diffability.
 */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
internal fun FontPaddingFreeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = style.color.takeOrElse { FALLBACK_COLOR }
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else FALLBACK_FONT_SIZE.sp
    val lineHeight = if (style.lineHeight.isSpecified) style.lineHeight else fontSize
    val letterSpacing = style.letterSpacing

    val resolver: FontFamily.Resolver = LocalFontFamilyResolver.current
    val typeface = remember(resolver, style.fontFamily, style.fontWeight, style.fontStyle, style.fontSynthesis) {
        resolver.resolve(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = style.fontStyle ?: FontStyle.Normal,
            fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
        )
    }.value as Typeface

    AndroidView(
        modifier = modifier,
        factory = {
            TextView(context).apply {
                isFallbackLineSpacing = false
                includeFontPadding = style.platformStyle?.paragraphStyle?.includeFontPadding == true
                maxLines = 1
                isSingleLine = true
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            textView.typeface = typeface
            style.fontFeatureSettings?.let { textView.fontFeatureSettings = it }
            // TextView states letter spacing in ems; Compose styles here
            // state it in sp, except where a caller passes 0 in either.
            textView.letterSpacing = when {
                !letterSpacing.isSpecified -> 0f
                letterSpacing.type == TextUnitType.Sp -> letterSpacing.value / fontSize.value
                else -> letterSpacing.value
            }
            val spannable = SpannableString(text)
            spannable.setSpan(
                ComposeLineHeightSpan(
                    lineHeight = with(density) { lineHeight.toPx().roundToInt() },
                    style = style.lineHeightStyle,
                ),
                0,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            textView.setText(spannable, TextView.BufferType.SPANNABLE)
        },
    )
}

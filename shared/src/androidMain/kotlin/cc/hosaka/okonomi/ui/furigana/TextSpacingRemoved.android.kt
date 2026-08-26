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

import android.os.Build
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal actual fun TextSpacingRemoved(
    text: String,
    style: TextStyle,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    } else {
        FontPaddingFreeText(
            text = text,
            style = style,
            modifier = modifier,
        )
    }
}

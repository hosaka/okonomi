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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * One unwrapped line of text drawn without the vertical padding the
 * platform text stack adds around it. Both halves of a ruby unit go
 * through this: the ruby is positioned by hand relative to the base, so
 * either one gaining invisible padding moves it off the character it
 * belongs to.
 *
 * On iOS this is `BasicText` with proportional line-height trimming. On
 * Android from API 28 it is a real `TextView` with
 * `isFallbackLineSpacing` and `includeFontPadding` turned off — neither
 * is reachable through `BasicText` — with Compose's own line-height
 * rules reapplied as a span. Below API 28 the padding does not appear
 * and `BasicText` is used directly.
 *
 * Vendored from Furiganable; see [FuriganaText] for the provenance.
 */
@Composable
internal expect fun TextSpacingRemoved(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
)

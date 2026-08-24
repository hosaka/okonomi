/*
 * Copyright (C) 2026  Okonomi Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cc.hosaka.okonomi.deinflect

import cc.hosaka.okonomi.db.StoredValues

/**
 * Bridge from JMdict's fine-grained part-of-speech codes (the CSV `sense.pos`
 * format, e.g. "v5r,vt") to the coarse Yomitan condition flags used by the
 * deinflection engine.
 *
 * Mapping table:
 * - `v1`, `v1-s` -> v1 (ichidan)
 * - all `v5*` codes (v5u, v5u-s, v5k, v5k-s, v5g, v5s, v5t, v5n, v5b, v5m,
 *   v5r, v5r-i, v5aru) -> v5 (godan)
 * - `vk` -> vk (kuru)
 * - `vs`, `vs-i`, `vs-s` -> vs (suru)
 * - `vz` -> vz (zuru)
 * - `adj-i`, `adj-ix` -> adj-i
 * - `v-unspec` -> the umbrella v flags (v1 | v5 | vk | vs | vz)
 * - anything else contributes 0 (never throws)
 *
 * The archaic verb classes (`v2*`, `v4*`, `vn`, `vr`, `vs-c`) deliberately
 * map to 0: the ported rule set carries no conjugation rules for them, so
 * flags would never match a derivation.
 */
fun posCodesToConditionFlags(pos: String?): Long {
    var flags = 0L
    for (code in StoredValues.codes(pos)) {
        flags = flags or conditionFlagsForPosCode(code)
    }
    return flags
}

/**
 * Condition flags resolved once via the engine's dictionary-entry surface
 * (isDictionaryForm-restricted parts-of-speech lookup). The umbrella v
 * condition is not a dictionary form, so it is composed from its five
 * dictionary-form children, which is exactly how the lattice defines it.
 */
private object PosConditionFlags {
    val v1: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("v1"))
    val v5: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("v5"))
    val vk: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("vk"))
    val vs: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("vs"))
    val vz: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("vz"))
    val adjI: Long = JapaneseDeinflector.conditionFlagsForPartsOfSpeech(listOf("adj-i"))
    val v: Long = v1 or v5 or vk or vs or vz
}

private fun conditionFlagsForPosCode(code: String): Long = when {
    code == "v1" || code == "v1-s" -> PosConditionFlags.v1
    code.startsWith("v5") -> PosConditionFlags.v5
    code == "vk" -> PosConditionFlags.vk
    code == "vs" || code == "vs-i" || code == "vs-s" -> PosConditionFlags.vs
    code == "vz" -> PosConditionFlags.vz
    code == "adj-i" || code == "adj-ix" -> PosConditionFlags.adjI
    code == "v-unspec" -> PosConditionFlags.v
    else -> 0L
}

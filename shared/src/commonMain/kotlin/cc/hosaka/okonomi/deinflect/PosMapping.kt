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
 * - `v5uru` -> v1: the old uru class inflects on its え stem like an
 *   ichidan verb, which is the paradigm the conjugator gives it
 * - every other `v5*` code (v5u, v5u-s, v5k, v5k-s, v5g, v5s, v5t, v5n,
 *   v5b, v5m, v5r, v5r-i, v5aru) -> v5 (godan)
 * - `vk` -> vk (kuru)
 * - `vs`, `vs-i`, `vs-s` -> vs (suru)
 * - `vs-c` -> vs | v5: the precursor class is written both ways (兼する,
 *   兼す), and the union is what its two paradigms need
 * - `vz` -> vz (zuru)
 * - `adj-i`, `adj-ix`, `aux-adj` -> adj-i
 * - `v-unspec` -> the umbrella v flags (v1 | v5 | vk | vs | vz)
 * - anything else contributes 0 (never throws)
 *
 * The archaic verb classes (`v2*`, `v4*`, `vn`, `vr`) deliberately map to
 * 0: the ported rule set carries no conjugation rules for them, so flags
 * would never match a derivation.
 *
 * This table and [cc.hosaka.okonomi.lang.conjugationClassOf] classify the
 * same JMdict codes from opposite directions and must stay in step: a code
 * the conjugator produces forms for but that maps to 0 here can never be
 * round-trip verified, and a code that gains flags here without a paradigm
 * there is a class the Forms tab silently drops. [UNCONJUGATED_POS_CODES]
 * names the deliberate exceptions, and `ConjugationVocabularyTest` holds
 * both directions.
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

/**
 * The codes that carry condition flags but deliberately have no
 * conjugation paradigm, so the two-way vocabulary check knows they are
 * omissions on purpose rather than gaps.
 *
 * `vs` is a noun stored without its する — there is no verb in the
 * headword to inflect. `v-unspec` says a word is a verb without saying
 * which kind, and no paradigm follows from that.
 */
val UNCONJUGATED_POS_CODES: Set<String> = setOf("vs", "v-unspec")

private fun conditionFlagsForPosCode(code: String): Long = when {
    code == "v1" || code == "v1-s" || code == "v5uru" -> PosConditionFlags.v1
    code.startsWith("v5") -> PosConditionFlags.v5
    code == "vk" -> PosConditionFlags.vk
    code == "vs" || code == "vs-i" || code == "vs-s" -> PosConditionFlags.vs
    code == "vs-c" -> PosConditionFlags.vs or PosConditionFlags.v5
    code == "vz" -> PosConditionFlags.vz
    code == "adj-i" || code == "adj-ix" || code == "aux-adj" -> PosConditionFlags.adjI
    code == "v-unspec" -> PosConditionFlags.v
    else -> 0L
}

package cc.hosaka.okonomi.lang

import cc.hosaka.okonomi.deinflect.Deinflection
import cc.hosaka.okonomi.deinflect.JapaneseDeinflector
import cc.hosaka.okonomi.deinflect.LanguageTransformer
import cc.hosaka.okonomi.deinflect.posCodesToConditionFlags

/**
 * The deinflector used as an oracle for the forward conjugator, and an
 * honest account of what it can and cannot decide.
 *
 * **What it catches.** A cell that is not a well-formed inflection of
 * the base at all (有らない, 画餅に帰すらない, 食べるぬ) has no derivation
 * back, and neither does one belonging to a different class than the
 * entry's part of speech. With [requiredTransforms] it also catches a
 * well-formed form filed under the wrong row — a potential rendered as
 * the passive, an imperative rendered as the te-form.
 *
 * **What it cannot catch.** It is one-directional. It says nothing
 * about over-generation: あれる and ござらせる deinflect perfectly and
 * are still not forms those verbs have, so a class that lacks a row
 * must simply not emit one — no assertion here will notice. It says
 * nothing about a form we failed to produce. And where the ported rule
 * set is itself incomplete ([oracleIsBlind]) it says nothing at all,
 * so those cells are pinned by expected text instead.
 */

/** Which column of the table a cell came from. */
internal enum class Polarity { Affirmative, Negative }

internal data class Cell(val id: FormId, val polarity: Polarity)

/** The affirmative and negative of [form], skipping an absent negative. */
internal fun cellsOf(form: Form): List<Pair<Cell, String>> = buildList {
    add(Cell(form.id, Polarity.Affirmative) to form.affirmative)
    form.negative?.let { add(Cell(form.id, Polarity.Negative) to it) }
}

/**
 * The transform ids a derivation of this cell has to name. Each element
 * is an alternatives set: the derivation must use at least one id from
 * every set. An ichidan potential and passive are the same string, so
 * both rows accept the deinflector's single "potential or passive"
 * rule; what neither accepts is the other row's own rule.
 */
internal fun requiredTransforms(cell: Cell): List<Set<String>> = buildList {
    when (cell.id) {
        FormId.NonPast -> Unit
        FormId.NonPastPolite -> add(setOf("-ます"))
        FormId.Past -> add(setOf("-た"))
        FormId.PastPolite -> {
            add(setOf("-た"))
            add(setOf("-ます"))
        }
        FormId.Te -> add(setOf("-て"))
        FormId.Potential -> add(setOf("potential", "potential or passive"))
        FormId.Passive -> add(setOf("passive", "potential or passive"))
        FormId.Causative -> add(setOf("causative"))
        FormId.CausativePassive -> {
            add(setOf("causative"))
            add(setOf("passive", "potential or passive"))
        }
        FormId.Imperative -> add(setOf("imperative"))
        FormId.Volitional -> add(setOf("volitional"))
        FormId.ConditionalBa -> add(setOf("-ば"))
        FormId.ConditionalTara -> add(setOf("-たら"))
        FormId.Desiderative -> add(setOf("-たい"))
    }
    if (cell.polarity == Polarity.Negative) {
        add(setOf("negative"))
    }
}

/**
 * Ids a derivation of this cell must not name. Kept to the pair the
 * rows could actually be confused for: rendering the potential as the
 * passive, or the passive as the potential, is a mistake the required
 * sets alone would let through for a godan verb.
 */
internal fun forbiddenTransforms(cell: Cell): Set<String> = when (cell.id) {
    FormId.Potential -> setOf("passive")
    FormId.Passive -> setOf("potential")
    else -> emptySet()
}

/**
 * True when the ported rule set has no way back from this cell, so
 * neither a round-trip nor a trace assertion means anything about it.
 * Every entry names the gap in the deinflector, not a doubt about the
 * form: the correctness of these cells is pinned by expected text in
 * `ConjugationRoundTripTest` and `ConjugatorTest`.
 */
internal fun oracleIsBlind(
    conjugationClass: ConjugationClass,
    base: String,
    cell: Cell,
): Boolean = when {
    // The non-past affirmative is the base itself, and the identity
    // candidate always deinflects to it with conditions 0, which match
    // anything. The assertion could not fail, so it is not made.
    cell == Cell(FormId.NonPast, Polarity.Affirmative) -> true

    // The prohibitive. japanese-transforms carries only the んな slang
    // contraction of 〜るな, never the plain form.
    cell == Cell(FormId.Imperative, Polarity.Negative) -> true

    // 呉れ is くれる's imperative and is right, but the rule set reaches
    // it only along the continuative rule (れ ← れる); no derivation of
    // it names the imperative transform.
    conjugationClass == ConjugationClass.IchidanKureru &&
        cell == Cell(FormId.Imperative, Polarity.Affirmative) -> true

    // ください and いらっしゃい are the everyday imperatives of this
    // class and are spelled with い, never れ. The imperative transform
    // knows only the regular 下され for a る-ending verb, so the right
    // form has no derivation naming its own row.
    conjugationClass == ConjugationClass.GodanHonorific &&
        cell == Cell(FormId.Imperative, Polarity.Affirmative) -> true

    // です is a copula, not an inflection; the rule set reaches 高い
    // from 高くあります instead, which is not what jisho shows.
    conjugationClass.isAdjective && cell.id in politeRows -> true

    // Hiragana inflections on a katakana or small-kana stem: ウザくない
    // is what people write, but the rule set appends a hiragana い and
    // so lands on ウザい, never back on ウザイ.
    conjugationClass.isAdjective && !base.endsWith("い") -> true

    // いい borrows the よ of よい, which is a different spelling of the
    // headword, so nothing leads back from よくない to いい.
    conjugationClass == ConjugationClass.AdjIx && base.endsWithAnyII -> true

    // ない, なかった, なくて, なければ, なかったら are forms of ない, a
    // separate dictionary entry. Only ある's polite negatives (regular
    // ありません) come back.
    conjugationClass == ConjugationClass.GodanAru &&
        cell.polarity == Polarity.Negative &&
        cell.id !in politeRows -> true

    // 愛せる is the 漢語 special class's potential; upstream's する
    // rules know only できる/出来る, so the correct form has no way back.
    conjugationClass == ConjugationClass.SuruSpecial && cell.id == FormId.Potential -> true

    // 出来る deinflects to する, never to the 為る spelling of the same
    // headword.
    conjugationClass == ConjugationClass.Suru &&
        cell.id == FormId.Potential &&
        base.endsWith("為る") -> true

    // The honorific polite forms are whole-word rules upstream, listing
    // いらっしゃいます and くださいます literally, so a headword spelled any
    // other way (居らっしゃる, 為さる, ご覧なさる) has no rule to match.
    conjugationClass == ConjugationClass.GodanHonorific && cell.id in politeRows -> true

    // The う special class's て/た forms are a fixed list of verbs
    // upstream (問う, 乞う, 給う …). 厭う is tagged v5u-s in JMdict and
    // is not on it, so 厭うて is right and unreachable at once.
    conjugationClass == ConjugationClass.GodanUSpecial &&
        cell.polarity == Polarity.Affirmative &&
        cell.id in uSpecialRows -> true

    else -> false
}

private val politeRows = setOf(FormId.NonPastPolite, FormId.PastPolite)

private val uSpecialRows = setOf(FormId.Past, FormId.Te, FormId.ConditionalTara)

private val ConjugationClass.isAdjective: Boolean
    get() = this == ConjugationClass.AdjI || this == ConjugationClass.AdjIx

private val String.endsWithAnyII: Boolean
    get() = endsWith("いい") || endsWith("イイ")

/**
 * A derivation of [text] that reaches [base], is compatible with the
 * entry's [code], and names the transforms [cell] requires — or null
 * when no candidate does all three.
 *
 * Compatibility is the rule the search path applies: a candidate
 * matches when its conditions are unconstrained or share a flag with
 * the entry's parts of speech.
 */
internal fun derivationOf(text: String, base: String, code: String, cell: Cell): Deinflection? {
    val entryFlags = posCodesToConditionFlags(code)
    val required = requiredTransforms(cell)
    val forbidden = forbiddenTransforms(cell)
    return JapaneseDeinflector.deinflect(text).firstOrNull { candidate ->
        candidate.text == base &&
            LanguageTransformer.conditionsMatch(candidate.conditions, entryFlags) &&
            required.all { alternatives -> candidate.trace.any { it in alternatives } } &&
            candidate.trace.none { it in forbidden }
    }
}

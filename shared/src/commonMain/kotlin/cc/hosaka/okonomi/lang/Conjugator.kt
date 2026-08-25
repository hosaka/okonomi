package cc.hosaka.okonomi.lang

/**
 * Forward conjugation: the rows a learner meets in text, produced from a
 * surface form plus one JMdict part-of-speech code.
 *
 * This is the mirror image of `deinflect/`, which only ever runs
 * backwards. Yomitan ships no forward generator, so the paradigms here
 * are our own. The deinflector cross-checks them, but only in one
 * direction: it can prove a produced form is *reachable*, never that a
 * form we failed to produce was needed, and never that a reachable form
 * belongs in the row we put it in. Over-generation is this code's real
 * failure mode, so a class that does not have a row must not emit one.
 *
 * The class vocabulary must stay in step with
 * [cc.hosaka.okonomi.deinflect.posCodesToConditionFlags]: every code
 * with a paradigm here carries condition flags there, and every code
 * with flags there either has a paradigm here or is named in that
 * file's list of codes deliberately left unconjugated.
 * `ConjugationVocabularyTest` holds both directions.
 */

/**
 * One row of the table: the row's identity, its affirmative form, and
 * its negative. A null [negative] means the row has no negative cell at
 * all — the volitional's true negative is the literary 〜まい, and
 * 食べないだろう is a paraphrase rather than a form, so the cell is left
 * empty instead of filled with either.
 */
data class Form(
    val id: FormId,
    val affirmative: String,
    val negative: String?,
)

/**
 * The rows of the table, in render order: jisho's ten, then the four
 * Alex added because the ported rules can verify all four of them.
 *
 * No paradigm has to fill all of them. い-adjectives stop after the
 * te-form and pick up only the two conditionals, because there is no
 * such thing as an い-adjective potential, imperative or 〜たい, and
 * 高かろう is literary; ある and the honorific class stop in the same
 * place, because あれる, ござらせる and ありたい are not words either.
 */
enum class FormId {
    NonPast,
    NonPastPolite,
    Past,
    PastPolite,
    Te,
    Potential,
    Passive,
    Causative,
    CausativePassive,
    Imperative,
    Volitional,
    ConditionalBa,
    ConditionalTara,
    Desiderative,
}

/**
 * The paradigms this file can produce. Several JMdict codes share one
 * class — the nine regular godan codes all resolve to [Godan], whose
 * row comes from the base's final kana — and one code can resolve
 * differently depending on the base (see [Suru]).
 */
enum class ConjugationClass {
    /** `v1`, and `v5uru` — see [paradigms]. */
    Ichidan,

    /** `v1-s`: くれる, whose imperative is くれ rather than くれろ. */
    IchidanKureru,

    /** The nine regular godan codes, distinguished only by final kana. */
    Godan,

    /** `v5k-s`: 行く, whose te/past forms are 行って/行った. */
    GodanIku,

    /** `v5r-i`: ある, whose plain negative is ない rather than あらない. */
    GodanAru,

    /** `v5aru`: いらっしゃる — polite いらっしゃいます, imperative ください. */
    GodanHonorific,

    /** `v5u-s`: 問う, whose te/past forms are 問うて/問うた. */
    GodanUSpecial,

    /** `vk`: 来る, written 来 or くる depending on the headword. */
    Kuru,

    /** `vs-i` (and `vs-c` written with する): potential is できる. */
    Suru,

    /** `vs-s`: the 漢語 special class, whose potential is 愛せる. */
    SuruSpecial,

    /** `vz`: 信ずる. */
    Zuru,

    /** `adj-i`, and `aux-adj`, which inflects identically. */
    AdjI,

    /** `adj-ix`: the よい/いい class, where いい inflects on the よ stem. */
    AdjIx,
}

/**
 * The tails an `adj-i` family headword can end in. JMdict carries
 * katakana spellings (ウザイ, キモイ, イクナイ) and small-kana ones
 * (熱っちぃ) alongside the ordinary い.
 */
private val ADJECTIVE_TAILS = listOf("い", "イ", "ぃ")

/** ある is written three ways, and the class exists for exactly that verb. */
private val ARU_TAILS = listOf("ある", "有る", "在る")

/**
 * The spellings of 行く that take the irregular て/た forms, matching
 * upstream's own list. The ゆく reading is deliberately absent.
 */
private val IKU_TAILS = listOf("いく", "行く", "逝く", "往く")

/** The two spellings a する-class headword carries its する in. */
private val SURU_TAILS = listOf("する", "為る")

/** The two ways JMdict spells the いい half of the よい/いい class. */
private val II_TAILS = listOf("いい", "イイ")

/**
 * The final kana each regular godan code names. The base is required to
 * end in it: JMdict does carry entries whose code and spelling disagree
 * (画餅に帰する is tagged `v5s` but ends in る), and keying the row off
 * the spelling alone turns that into confidently wrong output —
 * 画餅に帰すらない — where the empty state is the honest answer.
 */
private val GODAN_TAIL_BY_CODE = mapOf(
    "v5u" to "う",
    "v5k" to "く",
    "v5g" to "ぐ",
    "v5s" to "す",
    "v5t" to "つ",
    "v5n" to "ぬ",
    "v5b" to "ぶ",
    "v5m" to "む",
    "v5r" to "る",
)

/**
 * Every code that names a paradigm, and how the base decides which one.
 * Resolution and the set of conjugable codes come from this one table
 * so the two cannot drift apart.
 *
 * `vs` is deliberately absent: its headword is stored without する
 * (勉強, not 勉強する), so there is no verb here to inflect — jisho
 * shows no table for such entries either. Rendering a bare noun as a
 * verb would be worse than showing nothing. `v-unspec` is absent for
 * the same reason in reverse: it names a verb without naming which
 * kind, and no paradigm follows from that.
 */
private val paradigms: Map<String, (String) -> ConjugationClass?> = buildMap {
    put("v1", tailed(listOf("る"), ConjugationClass.Ichidan))
    put("v1-s", tailed(listOf("る"), ConjugationClass.IchidanKureru))
    // The old uru class survives only in 得る, which inflects on its え
    // stem exactly as an ichidan verb does; no sense in the shipped
    // dictionary carries the code, and any entry that could also
    // carries v1, so it folds into the same table.
    put("v5uru", tailed(listOf("る"), ConjugationClass.Ichidan))
    for ((code, tail) in GODAN_TAIL_BY_CODE) {
        put(code, tailed(listOf(tail), ConjugationClass.Godan))
    }
    put("v5k-s", tailed(listOf("く"), ConjugationClass.GodanIku))
    put("v5r-i", tailed(ARU_TAILS, ConjugationClass.GodanAru))
    put("v5aru", tailed(listOf("る"), ConjugationClass.GodanHonorific))
    put("v5u-s", tailed(listOf("う"), ConjugationClass.GodanUSpecial))
    put("vk", tailed(listOf("来る", "くる"), ConjugationClass.Kuru))
    put("vs-i", tailed(SURU_TAILS, ConjugationClass.Suru))
    put("vs-s", tailed(SURU_TAILS, ConjugationClass.SuruSpecial))
    // The precursor class covers both headwords written with する and
    // the older す forms (兼す, 熟す), which are plain す godan verbs.
    put("vs-c") { base ->
        when {
            SURU_TAILS.any { base.endsWith(it) } -> ConjugationClass.Suru
            base.endsWith("す") -> ConjugationClass.Godan
            else -> null
        }
    }
    put("vz", tailed(listOf("ずる"), ConjugationClass.Zuru))
    put("adj-i", tailed(ADJECTIVE_TAILS, ConjugationClass.AdjI))
    put("adj-ix", tailed(ADJECTIVE_TAILS, ConjugationClass.AdjIx))
    // たい, らしい, っぽい: an auxiliary adjective is an adjective, and
    // needs no paradigm of its own. The tail check keeps out the
    // members that are not adjectives at all (まじき, 可し, ねー).
    put("aux-adj", tailed(ADJECTIVE_TAILS, ConjugationClass.AdjI))
}

private fun tailed(tails: List<String>, conjugationClass: ConjugationClass): (String) -> ConjugationClass? =
    { base -> if (tails.any { base.endsWith(it) }) conjugationClass else null }

/** Every JMdict part-of-speech code this file can conjugate. */
val conjugablePosCodes: Set<String>
    get() = paradigms.keys

/**
 * The paradigm [code] selects for [base], or null when the code names
 * nothing this file conjugates, or when [base] does not end in the kana
 * that code inflects.
 *
 * A base without its inflecting tail is a data case, not a crash:
 * JMdict carries kanji forms that stop short of the okurigana, and
 * codes that disagree with the spelling they are attached to. Both fall
 * to the tab's empty state rather than to an invented stem.
 */
fun conjugationClassOf(code: String, base: String): ConjugationClass? = paradigms[code]?.invoke(base)

/** The rows of [base] conjugated as [code], or empty when it has none. */
fun conjugate(base: String, code: String): List<Form> {
    val conjugationClass = conjugationClassOf(code, base) ?: return emptyList()
    return conjugate(base, conjugationClass)
}

private fun conjugate(base: String, conjugationClass: ConjugationClass): List<Form> = when (conjugationClass) {
    ConjugationClass.Ichidan -> ichidan(base, imperative = base.dropLastOrNull("る")?.plus("ろ"))
    ConjugationClass.IchidanKureru -> ichidan(base, imperative = base.dropLastOrNull("る"))
    ConjugationClass.Godan -> godan(base)
    ConjugationClass.GodanIku -> godanIku(base)
    ConjugationClass.GodanAru -> godanAru(base)
    ConjugationClass.GodanHonorific -> godanHonorific(base)
    ConjugationClass.GodanUSpecial -> godanUSpecial(base)
    ConjugationClass.Kuru -> kuru(base)
    ConjugationClass.Suru -> suru(base, special = false)
    ConjugationClass.SuruSpecial -> suru(base, special = true)
    ConjugationClass.Zuru -> zuru(base)
    ConjugationClass.AdjI -> adjective(base, stem = base.dropLastOfAny(ADJECTIVE_TAILS))
    ConjugationClass.AdjIx -> adjective(base, stem = yoiStem(base))
}

/** One conjugable class of an entry, paired with the code that named it. */
data class Conjugation(
    val code: String,
    val conjugationClass: ConjugationClass,
    val forms: List<Form>,
)

/**
 * Every table [base] should show, given the part-of-speech codes of its
 * senses in source order.
 *
 * The first sense carrying a conjugable code decides the first table.
 * Two senses in genuinely different classes get one table each — 厭う is
 * both a plain う godan verb and a `v5u-s` one, and the two differ
 * exactly where a learner would be misled (厭って against 厭うて). Two
 * codes that resolve to the same class produce one table, headed by the
 * first of them, because their rows would be identical.
 *
 * An expression tagged with a verb code (`exp,v5t` 腹が立つ — 5,224
 * entries) conjugates on its tail like any other verb, which the frozen
 * spec requires and which is right for the many that behave like verbs
 * (気を付ける → 気を付けろ, 手を出す → 手を出せ). It does produce rows an
 * idiom does not really have — 腹が立て, 腹が立たせる — because nothing in
 * the data marks which expressions are frozen. Suppressing the derived
 * rows for every `exp` entry would cost the correct forms of thousands
 * of live ones to spare the wrong forms of the frozen ones, so the
 * trade is left where the spec put it.
 */
fun conjugations(base: String, posCodes: List<String>): List<Conjugation> {
    val seen = mutableSetOf<ConjugationClass>()
    return posCodes.mapNotNull { code ->
        val conjugationClass = conjugationClassOf(code, base) ?: return@mapNotNull null
        if (!seen.add(conjugationClass)) return@mapNotNull null
        val forms = conjugate(base, conjugationClass)
        if (forms.isEmpty()) null else Conjugation(code, conjugationClass, forms)
    }
}

/**
 * A verb paradigm stated as the few pieces every row is built from.
 * [negativeStem] takes ない/なかった/なくて/なければ/なかったら, [politeStem] takes
 * ます/ません/ました/ませんでした, and the derived forms are dictionary
 * forms ending in る, so their negatives fall out uniformly.
 *
 * [ba] is stated per class rather than derived: every base ending in る
 * replaces it with れば whatever its class, while a godan verb takes the
 * e row of its own final kana. [tara] is not stated at all — it is the
 * past form plus ら, in every class the deinflector knows.
 *
 * A null form means the class does not have that row. Only the first
 * five rows and the two conditionals are universal.
 */
private class VerbParadigm(
    val nonPast: String,
    val negativeStem: String,
    val politeStem: String,
    val past: String,
    val te: String,
    val ba: String,
    val potential: String? = null,
    val passive: String? = null,
    val causative: String? = null,
    val causativePassive: String? = null,
    val imperative: String? = null,
    val volitional: String? = null,
    val desiderative: String? = null,
)

private fun VerbParadigm.rows(): List<Form> = buildList {
    add(Form(FormId.NonPast, nonPast, negativeStem + "ない"))
    add(Form(FormId.NonPastPolite, politeStem + "ます", politeStem + "ません"))
    add(Form(FormId.Past, past, negativeStem + "なかった"))
    add(Form(FormId.PastPolite, politeStem + "ました", politeStem + "ませんでした"))
    add(Form(FormId.Te, te, negativeStem + "なくて"))
    potential?.let { add(Form(FormId.Potential, it, it.negatedDictionaryForm())) }
    passive?.let { add(Form(FormId.Passive, it, it.negatedDictionaryForm())) }
    causative?.let { add(Form(FormId.Causative, it, it.negatedDictionaryForm())) }
    causativePassive?.let { add(Form(FormId.CausativePassive, it, it.negatedDictionaryForm())) }
    // The prohibitive is the plain form plus な; unlike every other cell
    // here it has no reverse rule in the ported deinflector, which
    // carries only the んな slang contraction of it.
    imperative?.let { add(Form(FormId.Imperative, it, nonPast + "な")) }
    volitional?.let { add(Form(FormId.Volitional, it, null)) }
    add(Form(FormId.ConditionalBa, ba, negativeStem + "なければ"))
    add(Form(FormId.ConditionalTara, past + "ら", negativeStem + "なかったら"))
    // たい then inflects as an い-adjective in its own right; one row is
    // the point, not a paradigm nested inside a paradigm.
    desiderative?.let { add(Form(FormId.Desiderative, it, it.dropLast(1) + "くない")) }
}

/** る → らない, for the ichidan forms every derived row ends in. */
private fun String.negatedDictionaryForm(): String = dropLast(1) + "ない"

private fun ichidan(base: String, imperative: String?): List<Form> {
    val stem = base.dropLastOrNull("る") ?: return emptyList()
    return VerbParadigm(
        nonPast = base,
        negativeStem = stem,
        politeStem = stem,
        past = stem + "た",
        te = stem + "て",
        // Not a copy-paste slip and not a bug to fix: an ichidan verb's
        // potential and passive are the same string, 食べられる, and the
        // deinflector reaches both through one "potential or passive"
        // rule for exactly that reason.
        potential = stem + "られる",
        passive = stem + "られる",
        causative = stem + "させる",
        causativePassive = stem + "させられる",
        imperative = imperative,
        volitional = stem + "よう",
        desiderative = stem + "たい",
        ba = stem + "れば",
    ).rows()
}

/**
 * The four vowel rows and two euphonic endings a regular godan verb
 * needs. Nine JMdict codes share this one table: the final kana decides
 * everything, and nine hand-written paradigms would be nine places to
 * be wrong.
 */
private class GodanRow(
    val a: String,
    val i: String,
    val e: String,
    val o: String,
    val te: String,
    val ta: String,
)

private val godanRows = mapOf(
    // う takes わ, not あ, in the negative stem: 買う → 買わない.
    'う' to GodanRow(a = "わ", i = "い", e = "え", o = "お", te = "って", ta = "った"),
    'く' to GodanRow(a = "か", i = "き", e = "け", o = "こ", te = "いて", ta = "いた"),
    'ぐ' to GodanRow(a = "が", i = "ぎ", e = "げ", o = "ご", te = "いで", ta = "いだ"),
    'す' to GodanRow(a = "さ", i = "し", e = "せ", o = "そ", te = "して", ta = "した"),
    'つ' to GodanRow(a = "た", i = "ち", e = "て", o = "と", te = "って", ta = "った"),
    'ぬ' to GodanRow(a = "な", i = "に", e = "ね", o = "の", te = "んで", ta = "んだ"),
    'ぶ' to GodanRow(a = "ば", i = "び", e = "べ", o = "ぼ", te = "んで", ta = "んだ"),
    'む' to GodanRow(a = "ま", i = "み", e = "め", o = "も", te = "んで", ta = "んだ"),
    'る' to GodanRow(a = "ら", i = "り", e = "れ", o = "ろ", te = "って", ta = "った"),
)

private fun godanParadigm(
    base: String,
    row: GodanRow,
    stem: String,
    politeStem: String = stem + row.i,
    past: String = stem + row.ta,
    te: String = stem + row.te,
    negativeStem: String = stem + row.a,
    // False for the two classes that stop after the universal five and
    // the conditionals: あれる, あろう, ありたい, ござらせる are not words.
    derivedRows: Boolean = true,
    // Stated separately from [derivedRows] because the honorific class
    // has an imperative without having any of the rest.
    imperative: String? = if (derivedRows) stem + row.e else null,
) = VerbParadigm(
    nonPast = base,
    negativeStem = negativeStem,
    politeStem = politeStem,
    past = past,
    te = te,
    ba = stem + row.e + "ば",
    potential = if (derivedRows) stem + row.e + "る" else null,
    passive = if (derivedRows) stem + row.a + "れる" else null,
    causative = if (derivedRows) stem + row.a + "せる" else null,
    causativePassive = if (derivedRows) stem + row.a + "せられる" else null,
    imperative = imperative,
    volitional = if (derivedRows) stem + row.o + "う" else null,
    desiderative = if (derivedRows) stem + row.i + "たい" else null,
)

private fun godan(base: String): List<Form> {
    val row = godanRows[base.lastOrNull()] ?: return emptyList()
    return godanParadigm(base, row, stem = base.dropLast(1)).rows()
}

/**
 * The 行く class. The irregular て/た forms belong to the いく reading —
 * 行って, never 行いて — and to it alone: 暮れゆく and 心ゆく carry the same
 * `v5k-s` code but read ゆく, where the regular く euphony applies and
 * 暮れゆって would be nonsense. Upstream's own list of the verbs that
 * take the irregular forms says the same thing.
 */
private fun godanIku(base: String): List<Form> {
    val stem = base.dropLastOrNull("く") ?: return emptyList()
    val row = godanRows.getValue('く')
    val irregular = IKU_TAILS.any { base.endsWith(it) }
    return godanParadigm(
        base = base,
        row = row,
        stem = stem,
        past = if (irregular) stem + "った" else stem + row.ta,
        te = if (irregular) stem + "って" else stem + row.te,
    ).rows()
}

/**
 * ある, in any of its three spellings. Suppletive in the plain negative
 * — ない, never あらない — and short of the derived rows entirely: あれる,
 * あられる, あらせる and あれ are not forms this verb has, and an
 * expression built on it (花も実も有る) has them no more than the verb
 * does. Its polite negative is the regular ありません.
 */
private fun godanAru(base: String): List<Form> {
    val negativeStem = base.dropLastOfAny(ARU_TAILS) ?: return emptyList()
    val stem = base.dropLast(1)
    return godanParadigm(
        base = base,
        row = godanRows.getValue('る'),
        stem = stem,
        negativeStem = negativeStem,
        derivedRows = false,
    ).rows()
}

/**
 * The -aru honorifics: いらっしゃる, 下さる, 為さる, ご覧なさる. They take the
 * い stem twice over — where the regular row would give both ります and
 * れ — and they have no other derived rows, because ござれる and ござらせる
 * are not words.
 *
 * The imperative is the row this class most needs: ください and
 * いらっしゃい are everyday words, and 下さる is the 118th most common
 * entry in the dictionary. It is spelled 下さい, never 下され, and every
 * member of the class behaves the same way (仰い, ご覧なさい), which is
 * also the membership Yomitan's own honorific list carries.
 */
private fun godanHonorific(base: String): List<Form> {
    val stem = base.dropLastOrNull("る") ?: return emptyList()
    return godanParadigm(
        base = base,
        row = godanRows.getValue('る'),
        stem = stem,
        politeStem = stem + "い",
        derivedRows = false,
        imperative = stem + "い",
    ).rows()
}

private fun godanUSpecial(base: String): List<Form> {
    val stem = base.dropLastOrNull("う") ?: return emptyList()
    return godanParadigm(
        base = base,
        row = godanRows.getValue('う'),
        stem = stem,
        // 問うて/問うた, against the regular 問って/問った.
        past = base + "た",
        te = base + "て",
    ).rows()
}

/**
 * 来る is two headwords in one: 来る and くる. The kanji form writes
 * every stem with the same character, while the kana form spells out
 * こ/き/く, so the stems are picked per spelling rather than derived.
 * (JMdict's 來る is a search-only spelling and never a headword, so no
 * branch here produces it.)
 */
private fun kuru(base: String): List<Form> {
    val prefix: String
    val negativeStem: String
    val politeStem: String
    if (base.endsWith("来る")) {
        prefix = base.dropLast(2)
        negativeStem = prefix + "来"
        politeStem = prefix + "来"
    } else {
        prefix = base.dropLastOrNull("くる") ?: return emptyList()
        negativeStem = prefix + "こ"
        politeStem = prefix + "き"
    }
    return VerbParadigm(
        nonPast = base,
        negativeStem = negativeStem,
        politeStem = politeStem,
        past = politeStem + "た",
        te = politeStem + "て",
        potential = negativeStem + "られる",
        passive = negativeStem + "られる",
        causative = negativeStem + "させる",
        causativePassive = negativeStem + "させられる",
        imperative = negativeStem + "い",
        volitional = negativeStem + "よう",
        desiderative = politeStem + "たい",
        ba = base.dropLast(1) + "れば",
    ).rows()
}

/**
 * The する classes whose headword already carries する (愛する, 物にする,
 * 為る). [special] is JMdict's `vs-s`: the 漢語 verbs whose potential is
 * 愛せる, where the plain class takes できる.
 *
 * The 為 spelling takes 為せる and 為せられる in the causative rows, not
 * 為させる and 為させられる. The deinflector cannot decide this — its
 * corpus carries all four as valid — so the tiebreak is internal
 * consistency: the passive row is 為れる, which reads 為 as the さ stem,
 * and 為させる beside it would read 為 as し/す in one row and さ in the
 * next. One reading of 為 down the whole column. Do not "correct" this
 * back to 為させる; both round-trip, and that is the point.
 */
private fun suru(base: String, special: Boolean): List<Form> {
    val kanji = base.endsWith("為る")
    val prefix = (if (kanji) base.dropLastOrNull("為る") else base.dropLastOrNull("する")) ?: return emptyList()
    val stem = prefix + if (kanji) "為" else "し"
    val potential = when {
        special -> prefix + "せる"
        kanji -> prefix + "出来る"
        else -> prefix + "できる"
    }
    return VerbParadigm(
        nonPast = base,
        negativeStem = stem,
        politeStem = stem,
        past = stem + "た",
        te = stem + "て",
        potential = potential,
        passive = prefix + if (kanji) "為れる" else "される",
        causative = prefix + if (kanji) "為せる" else "させる",
        causativePassive = prefix + if (kanji) "為せられる" else "させられる",
        imperative = stem + "ろ",
        volitional = stem + "よう",
        desiderative = stem + "たい",
        ba = prefix + if (kanji) "為れば" else "すれば",
    ).rows()
}

/** 信ずる: the じ stem throughout, with 〜ぜられる for potential and passive. */
private fun zuru(base: String): List<Form> {
    val prefix = base.dropLastOrNull("ずる") ?: return emptyList()
    val stem = prefix + "じ"
    return VerbParadigm(
        nonPast = base,
        negativeStem = stem,
        politeStem = stem,
        past = stem + "た",
        te = stem + "て",
        potential = prefix + "ぜられる",
        passive = prefix + "ぜられる",
        causative = stem + "させる",
        causativePassive = stem + "させられる",
        imperative = stem + "ろ",
        volitional = stem + "よう",
        desiderative = stem + "たい",
        ba = base.dropLast(1) + "れば",
    ).rows()
}

/**
 * The stem of an `adj-ix` headword. 良い and 頭が良い inflect on the
 * character before い like any other adjective; いい and 気持ちいい
 * cannot, because いかった is not a word — they borrow the よ of よい.
 */
private fun yoiStem(base: String): String? = when (val prefix = base.dropLastOfAny(II_TAILS)) {
    null -> base.dropLastOfAny(ADJECTIVE_TAILS)
    else -> prefix + "よ"
}

/**
 * The rows an い-adjective has: the five that parallel a verb's, plus
 * the two conditionals. No potential, passive, causative, imperative or
 * 〜たい, because adjectives do not take them, and no volitional,
 * because 高かろう is literary rather than a form a learner meets.
 *
 * The polite rows take です, which is a copula rather than an
 * inflection — jisho shows them that way, and 高くあります would be a
 * strange thing to teach in its place.
 */
private fun adjective(base: String, stem: String?): List<Form> {
    if (stem == null) return emptyList()
    return listOf(
        Form(FormId.NonPast, base, stem + "くない"),
        Form(FormId.NonPastPolite, base + "です", stem + "くないです"),
        Form(FormId.Past, stem + "かった", stem + "くなかった"),
        Form(FormId.PastPolite, stem + "かったです", stem + "くなかったです"),
        Form(FormId.Te, stem + "くて", stem + "くなくて"),
        Form(FormId.ConditionalBa, stem + "ければ", stem + "くなければ"),
        Form(FormId.ConditionalTara, stem + "かったら", stem + "くなかったら"),
    )
}

/** The text without [suffix], or null when it does not end with it. */
private fun String.dropLastOrNull(suffix: String): String? =
    if (endsWith(suffix)) dropLast(suffix.length) else null

/** The text without the first of [suffixes] it ends with, or null. */
private fun String.dropLastOfAny(suffixes: List<String>): String? =
    suffixes.firstNotNullOfOrNull { dropLastOrNull(it) }

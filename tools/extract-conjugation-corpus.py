#!/usr/bin/env python3
"""Regenerates the committed conjugation corpus fixture from the shipped dictionary.

    ./gradlew :tools:dictgen:generateDictionary
    python3 tools/extract-conjugation-corpus.py

Writes shared/src/commonTest/kotlin/cc/hosaka/okonomi/lang/ConjugationCorpus.kt.

Deterministic by construction: every selection is ordered and ties break by entry
id, so the same JMdict build reproduces the file byte for byte.
"""
import os
import sqlite3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, "tools", "dictgen", "build", "okonomi.db")
OUT = os.path.join(
    ROOT, "shared", "src", "commonTest", "kotlin", "cc", "hosaka", "okonomi", "lang", "ConjugationCorpus.kt"
)

# Kept in step with Conjugator.kt's `paradigms` table by ConjugationVocabularyTest.
CONJUGABLE = [
    "v1", "v1-s", "v5u", "v5k", "v5g", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r",
    "v5k-s", "v5r-i", "v5aru", "v5u-s", "v5uru", "vk", "vs-i", "vs-s", "vs-c",
    "vz", "adj-i", "adj-ix", "aux-adj",
]

# Controls: codes the conjugator must leave alone.
NON_CONJUGABLE = [
    "vs", "n", "adv", "exp", "adj-na", "adj-no", "pn", "int", "v2k-s", "v4r", "vn", "vr", "v-unspec",
]

# The tail each code requires; entries that fail it are sampled as negative controls.
TAILS = {
    "v1": ["る"], "v1-s": ["る"], "v5uru": ["る"],
    "v5u": ["う"], "v5k": ["く"], "v5g": ["ぐ"], "v5s": ["す"], "v5t": ["つ"],
    "v5n": ["ぬ"], "v5b": ["ぶ"], "v5m": ["む"], "v5r": ["る"],
    "v5k-s": ["く"], "v5r-i": ["ある", "有る", "在る"], "v5aru": ["る"], "v5u-s": ["う"],
    "vk": ["来る", "くる"], "vs-i": ["する", "為る"], "vs-s": ["する", "為る"],
    "vs-c": ["する", "為る", "す"], "vz": ["ずる"],
    "adj-i": ["い", "イ", "ぃ"], "adj-ix": ["い", "イ", "ぃ"], "aux-adj": ["い", "イ", "ぃ"],
}

KATAKANA = set(chr(c) for c in range(0x30A0, 0x3100))

# Entries the review round named, and the bases ConjugationRoundTripTest seeds from.
# Pinned by id so a later resample cannot quietly drop them.
NAMED = [
    1000000, 1157170, 1231840, 1296400, 1343950, 1358280, 1454500, 1547720,
    1562350, 1949750, 1975230, 2018300, 2146840, 2820690, 2871942,
]

HEADER = '''package cc.hosaka.okonomi.lang

/**
 * A sample of the shipped dictionary, committed so the conjugation
 * sweep runs in the default suite without depending on a build artifact
 * being present. Sibling in spirit to `JapaneseTransformsCorpus`, which
 * pins the deinflector against its own fixture for the same reason.
 *
 * Scope, stated plainly: this is not every conjugable entry. It is,
 * for each part-of-speech code the conjugator handles and a control set
 * of codes it does not, the 60 most common entries by `common_rank`,
 * the 25 longest headwords (compounds and expressions inflecting on a
 * tail), up to 15 katakana or small-kana spellings, and up to 10 entries
 * whose headword does not end in the kana the code names — the last
 * group being negative controls, which must produce no table at all.
 * Every entry the review round named is pinned by id on top of that.
 *
 * Regenerate with `tools/extract-conjugation-corpus.py`; every
 * selection is ordered and ties break by entry id, so the same JMdict
 * build reproduces this file.
 *
 * [headword] is what `EntryDetail.headword` resolves to (first kanji
 * form by ord, else first reading) and [posCodes] what
 * `EntryDetail.posCodes` resolves to (distinct across senses, in sense
 * order), so a corpus row is exactly what the Forms tab is handed.
 */
internal data class CorpusEntry(
    val entryId: Long,
    val headword: String,
    val posCodes: List<String>,
)

/**
 * {count} entries; see the file header for how they were chosen.
 *
 * Split across several functions because a single list literal this
 * long overruns the JVM's 64KB limit on a class initializer.
 */
internal val conjugationCorpus: List<CorpusEntry> = buildList {
{chunkCalls}
}
{chunks}

/**
 * Every distinct part-of-speech code in the shipped dictionary at the
 * JMdict build this fixture was taken from. `ConjugationVocabularyTest`
 * asserts each one is either conjugated or explicitly ignored, so a
 * code JMdict adds later shows up as a red test rather than as a tab
 * that quietly has nothing to say.
 */
internal val shippedPosCodes: List<String> = listOf(
{codes}
)
'''


def headword(cur, entry_id):
    row = cur.execute("select text from kanji_form where entry_id=? order by ord limit 1", (entry_id,)).fetchone()
    if row:
        return row[0]
    row = cur.execute("select text from reading where entry_id=? order by ord limit 1", (entry_id,)).fetchone()
    return row[0] if row else None


def pos_codes(cur, entry_id):
    codes = []
    for (pos,) in cur.execute("select pos from sense where entry_id=? order by ord", (entry_id,)):
        for code in (pos or "").split(","):
            code = code.strip()
            if code and code not in codes:
                codes.append(code)
    return codes


def entries_with(cur, code):
    return [
        row[0]
        for row in cur.execute(
            "select distinct entry_id from sense where ','||pos||',' like ? order by entry_id", ("%," + code + ",%",)
        )
    ]


def main():
    con = sqlite3.connect(DB)
    cur = con.cursor()
    ranks = dict(cur.execute("select id, common_rank from entry"))
    picked = {}

    def take(entry_id):
        if entry_id in picked:
            return
        h = headword(cur, entry_id)
        if h:
            picked[entry_id] = (h, pos_codes(cur, entry_id))

    for code in CONJUGABLE + NON_CONJUGABLE:
        rows = [(i, headword(cur, i)) for i in entries_with(cur, code)]
        rows = [(i, h) for i, h in rows if h]
        by_rank = sorted(rows, key=lambda r: (ranks.get(r[0], 1 << 30), r[0]))
        for i, _ in by_rank[:60]:
            take(i)
        for i, _ in sorted(rows, key=lambda r: (-len(r[1]), r[0]))[:25]:
            take(i)
        kana = [r for r in by_rank if any(c in KATAKANA for c in r[1]) or "ぃ" in r[1]]
        for i, _ in kana[:15]:
            take(i)
        tails = TAILS.get(code)
        if tails:
            bad = [r for r in by_rank if not any(r[1].endswith(t) for t in tails)]
            for i, _ in bad[:10]:
                take(i)

    for entry_id in NAMED:
        take(entry_id)

    all_codes = set()
    for (pos,) in cur.execute("select distinct pos from sense where pos is not null"):
        for code in pos.split(","):
            if code.strip():
                all_codes.add(code.strip())

    entries = []
    for entry_id in sorted(picked):
        h, codes = picked[entry_id]
        joined = ", ".join('"' + c + '"' for c in codes)
        entries.append('    CorpusEntry(' + str(entry_id) + ', "' + h + '", listOf(' + joined + ')),')

    size = 300
    groups = [entries[i:i + size] for i in range(0, len(entries), size)]
    calls = "\n".join("    addAll(corpusPart%d())" % i for i in range(len(groups)))
    chunks = "\n".join(
        "\nprivate fun corpusPart%d(): List<CorpusEntry> = listOf(\n%s\n)" % (i, "\n".join(g))
        for i, g in enumerate(groups)
    )

    body = (
        HEADER.replace("{count}", str(len(entries)))
        .replace("{chunkCalls}", calls)
        .replace("{chunks}", chunks)
        .replace("{codes}", "\n".join('    "' + c + '",' for c in sorted(all_codes)))
    )
    with open(OUT, "w") as f:
        f.write(body)
    print("wrote", OUT, len(entries), "entries,", len(all_codes), "codes")


main()

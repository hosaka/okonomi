/*
 * Copyright (C) 2024-2026  Yomitan Authors
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
 *
 * Ported to Kotlin from Yomitan's ext/js/language/ja/japanese-transforms.js
 * at commit 77e200428902abf4fa48284df92da7af3dcb4162 (2026-08-18).
 * Machine-generated from the evaluated upstream module (conversion script not
 * committed). Transform order, ids and rules mirror the upstream file so
 * diffs against upstream stay reviewable. Descriptions and i18n metadata are
 * not ported.
 */
package cc.hosaka.okonomi.deinflect

private val ikuVerbs = listOf("いく", "行く", "逝く", "往く")
private val godanUSpecialVerbs = listOf(
    "こう", "とう", "請う", "乞う", "恋う", "問う", "訪う", "宣う", "曰う", "給う", "賜う", "揺蕩う",
)
private val specialHonorificMasuVerbs = listOf(
    "いらっしゃる", "ござる", "なさる", "くださる", "下さる", "おっしゃる", "仰る", "仰有る",
)
private val fuVerbTeConjugations = listOf(
    "のたまう" to "のたもう",
    "たまう" to "たもう",
    "たゆたう" to "たゆとう",
)

private fun irregularVerbSuffixInflections(
    suffix: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): List<RuleDescriptor> = buildList {
    for (verb in ikuVerbs) {
        add(suffixInflection("${verb[0]}っ$suffix", verb, conditionsIn, conditionsOut))
    }
    for (verb in godanUSpecialVerbs) {
        add(suffixInflection(verb + suffix, verb, conditionsIn, conditionsOut))
    }
    for ((verb, teRoot) in fuVerbTeConjugations) {
        add(suffixInflection(teRoot + suffix, verb, conditionsIn, conditionsOut))
    }
}

private fun specialHonorificMasuInflections(
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): List<RuleDescriptor> = specialHonorificMasuVerbs.map { verb ->
    wholeWordInflection(verb.dropLast(1) + "います", verb, conditionsIn, conditionsOut)
}

private val japaneseConditions: List<ConditionDescriptor> = listOf(
    ConditionDescriptor(type = "v", name = "Verb", isDictionaryForm = false, subConditions = listOf("v1", "v5", "vk", "vs", "vz")),
    ConditionDescriptor(type = "v1", name = "Ichidan verb", isDictionaryForm = true, subConditions = listOf("v1d", "v1p")),
    ConditionDescriptor(type = "v1d", name = "Ichidan verb, dictionary form", isDictionaryForm = false),
    ConditionDescriptor(type = "v1p", name = "Ichidan verb, progressive or perfect form", isDictionaryForm = false),
    ConditionDescriptor(type = "v5", name = "Godan verb", isDictionaryForm = true, subConditions = listOf("v5d", "v5s")),
    ConditionDescriptor(type = "v5d", name = "Godan verb, dictionary form", isDictionaryForm = false),
    ConditionDescriptor(type = "v5s", name = "Godan verb, short causative form", isDictionaryForm = false, subConditions = listOf("v5ss", "v5sp")),
    ConditionDescriptor(type = "v5ss", name = "Godan verb, short causative form having さす ending (cannot conjugate with passive form)", isDictionaryForm = false),
    ConditionDescriptor(type = "v5sp", name = "Godan verb, short causative form not having さす ending (can conjugate with passive form)", isDictionaryForm = false),
    ConditionDescriptor(type = "vk", name = "Kuru verb", isDictionaryForm = true),
    ConditionDescriptor(type = "vs", name = "Suru verb", isDictionaryForm = true),
    ConditionDescriptor(type = "vz", name = "Zuru verb", isDictionaryForm = true),
    ConditionDescriptor(type = "adj-i", name = "Adjective with i ending", isDictionaryForm = true),
    ConditionDescriptor(type = "-ます", name = "Polite -ます ending", isDictionaryForm = false),
    ConditionDescriptor(type = "-ません", name = "Polite negative -ません ending", isDictionaryForm = false),
    ConditionDescriptor(type = "-て", name = "Intermediate -て endings for progressive or perfect tense", isDictionaryForm = false),
    ConditionDescriptor(type = "-ば", name = "Intermediate -ば endings for conditional contraction", isDictionaryForm = false),
    ConditionDescriptor(type = "-く", name = "Intermediate -く endings for adverbs", isDictionaryForm = false),
    ConditionDescriptor(type = "-た", name = "-た form ending", isDictionaryForm = false),
    ConditionDescriptor(type = "-ん", name = "-ん negative ending", isDictionaryForm = false),
    ConditionDescriptor(type = "-なさい", name = "Intermediate -なさい ending (polite imperative)", isDictionaryForm = false),
    ConditionDescriptor(type = "-ゃ", name = "Intermediate -や ending (conditional contraction)", isDictionaryForm = false),
)

private fun transform0(): TransformDescriptor = TransformDescriptor(
    id = "-ば",
    name = "-ば",
    rules = listOf(
        suffixInflection("ければ", "い", listOf("-ば"), listOf("adj-i")),
        suffixInflection("えば", "う", listOf("-ば"), listOf("v5")),
        suffixInflection("へば", "う", listOf("-ば"), listOf("v5")),
        suffixInflection("けば", "く", listOf("-ば"), listOf("v5")),
        suffixInflection("げば", "ぐ", listOf("-ば"), listOf("v5")),
        suffixInflection("せば", "す", listOf("-ば"), listOf("v5")),
        suffixInflection("てば", "つ", listOf("-ば"), listOf("v5")),
        suffixInflection("ねば", "ぬ", listOf("-ば"), listOf("v5")),
        suffixInflection("べば", "ぶ", listOf("-ば"), listOf("v5")),
        suffixInflection("めば", "む", listOf("-ば"), listOf("v5")),
        suffixInflection("れば", "る", listOf("-ば"), listOf("v1", "v5", "vk", "vs", "vz")),
        suffixInflection("れば", "", listOf("-ば"), listOf("-ます")),
    ),
)

private fun transform1(): TransformDescriptor = TransformDescriptor(
    id = "-ゃ",
    name = "-ゃ",
    rules = listOf(
        suffixInflection("けりゃ", "ければ", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("きゃ", "ければ", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("や", "えば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("きゃ", "けば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("ぎゃ", "げば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("しゃ", "せば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("ちゃ", "てば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("にゃ", "ねば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("びゃ", "べば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("みゃ", "めば", listOf("-ゃ"), listOf("-ば")),
        suffixInflection("りゃ", "れば", listOf("-ゃ"), listOf("-ば")),
    ),
)

private fun transform2(): TransformDescriptor = TransformDescriptor(
    id = "-ちゃ",
    name = "-ちゃ",
    rules = listOf(
        suffixInflection("ちゃ", "る", listOf("v5"), listOf("v1")),
        suffixInflection("いじゃ", "ぐ", listOf("v5"), listOf("v5")),
        suffixInflection("いちゃ", "く", listOf("v5"), listOf("v5")),
        suffixInflection("しちゃ", "す", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃ", "う", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃ", "く", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃ", "つ", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃ", "る", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃ", "ぬ", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃ", "ぶ", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃ", "む", listOf("v5"), listOf("v5")),
        suffixInflection("じちゃ", "ずる", listOf("v5"), listOf("vz")),
        suffixInflection("しちゃ", "する", listOf("v5"), listOf("vs")),
        suffixInflection("為ちゃ", "為る", listOf("v5"), listOf("vs")),
        suffixInflection("きちゃ", "くる", listOf("v5"), listOf("vk")),
        suffixInflection("来ちゃ", "来る", listOf("v5"), listOf("vk")),
        suffixInflection("來ちゃ", "來る", listOf("v5"), listOf("vk")),
    ),
)

private fun transform3(): TransformDescriptor = TransformDescriptor(
    id = "-ちゃう",
    name = "-ちゃう",
    rules = listOf(
        suffixInflection("ちゃう", "る", listOf("v5"), listOf("v1")),
        suffixInflection("いじゃう", "ぐ", listOf("v5"), listOf("v5")),
        suffixInflection("いちゃう", "く", listOf("v5"), listOf("v5")),
        suffixInflection("しちゃう", "す", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃう", "う", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃう", "く", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃう", "つ", listOf("v5"), listOf("v5")),
        suffixInflection("っちゃう", "る", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃう", "ぬ", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃう", "ぶ", listOf("v5"), listOf("v5")),
        suffixInflection("んじゃう", "む", listOf("v5"), listOf("v5")),
        suffixInflection("じちゃう", "ずる", listOf("v5"), listOf("vz")),
        suffixInflection("しちゃう", "する", listOf("v5"), listOf("vs")),
        suffixInflection("為ちゃう", "為る", listOf("v5"), listOf("vs")),
        suffixInflection("きちゃう", "くる", listOf("v5"), listOf("vk")),
        suffixInflection("来ちゃう", "来る", listOf("v5"), listOf("vk")),
        suffixInflection("來ちゃう", "來る", listOf("v5"), listOf("vk")),
    ),
)

private fun transform4(): TransformDescriptor = TransformDescriptor(
    id = "-ちまう",
    name = "-ちまう",
    rules = listOf(
        suffixInflection("ちまう", "る", listOf("v5"), listOf("v1")),
        suffixInflection("いじまう", "ぐ", listOf("v5"), listOf("v5")),
        suffixInflection("いちまう", "く", listOf("v5"), listOf("v5")),
        suffixInflection("しちまう", "す", listOf("v5"), listOf("v5")),
        suffixInflection("っちまう", "う", listOf("v5"), listOf("v5")),
        suffixInflection("っちまう", "く", listOf("v5"), listOf("v5")),
        suffixInflection("っちまう", "つ", listOf("v5"), listOf("v5")),
        suffixInflection("っちまう", "る", listOf("v5"), listOf("v5")),
        suffixInflection("んじまう", "ぬ", listOf("v5"), listOf("v5")),
        suffixInflection("んじまう", "ぶ", listOf("v5"), listOf("v5")),
        suffixInflection("んじまう", "む", listOf("v5"), listOf("v5")),
        suffixInflection("じちまう", "ずる", listOf("v5"), listOf("vz")),
        suffixInflection("しちまう", "する", listOf("v5"), listOf("vs")),
        suffixInflection("為ちまう", "為る", listOf("v5"), listOf("vs")),
        suffixInflection("きちまう", "くる", listOf("v5"), listOf("vk")),
        suffixInflection("来ちまう", "来る", listOf("v5"), listOf("vk")),
        suffixInflection("來ちまう", "來る", listOf("v5"), listOf("vk")),
    ),
)

private fun transform5(): TransformDescriptor = TransformDescriptor(
    id = "-しまう",
    name = "-しまう",
    rules = listOf(
        suffixInflection("てしまう", "て", listOf("v5"), listOf("-て")),
        suffixInflection("でしまう", "で", listOf("v5"), listOf("-て")),
    ),
)

private fun transform6(): TransformDescriptor = TransformDescriptor(
    id = "-なさい",
    name = "-なさい",
    rules = listOf(
        suffixInflection("なさい", "る", listOf("-なさい"), listOf("v1")),
        suffixInflection("いなさい", "う", listOf("-なさい"), listOf("v5")),
        suffixInflection("きなさい", "く", listOf("-なさい"), listOf("v5")),
        suffixInflection("ぎなさい", "ぐ", listOf("-なさい"), listOf("v5")),
        suffixInflection("しなさい", "す", listOf("-なさい"), listOf("v5")),
        suffixInflection("ちなさい", "つ", listOf("-なさい"), listOf("v5")),
        suffixInflection("になさい", "ぬ", listOf("-なさい"), listOf("v5")),
        suffixInflection("びなさい", "ぶ", listOf("-なさい"), listOf("v5")),
        suffixInflection("みなさい", "む", listOf("-なさい"), listOf("v5")),
        suffixInflection("りなさい", "る", listOf("-なさい"), listOf("v5")),
        suffixInflection("じなさい", "ずる", listOf("-なさい"), listOf("vz")),
        suffixInflection("しなさい", "する", listOf("-なさい"), listOf("vs")),
        suffixInflection("為なさい", "為る", listOf("-なさい"), listOf("vs")),
        suffixInflection("きなさい", "くる", listOf("-なさい"), listOf("vk")),
        suffixInflection("来なさい", "来る", listOf("-なさい"), listOf("vk")),
        suffixInflection("來なさい", "來る", listOf("-なさい"), listOf("vk")),
    ),
)

private fun transform7(): TransformDescriptor = TransformDescriptor(
    id = "-そう",
    name = "-そう",
    rules = listOf(
        suffixInflection("そう", "い", listOf(), listOf("adj-i")),
        suffixInflection("さう", "い", listOf(), listOf("adj-i")),
        suffixInflection("そう", "る", listOf(), listOf("v1")),
        suffixInflection("さう", "る", listOf(), listOf("v1")),
        suffixInflection("いそう", "う", listOf(), listOf("v5")),
        suffixInflection("ひさう", "う", listOf(), listOf("v5")),
        suffixInflection("きそう", "く", listOf(), listOf("v5")),
        suffixInflection("きさう", "く", listOf(), listOf("v5")),
        suffixInflection("ぎそう", "ぐ", listOf(), listOf("v5")),
        suffixInflection("ぎさう", "ぐ", listOf(), listOf("v5")),
        suffixInflection("しそう", "す", listOf(), listOf("v5")),
        suffixInflection("しさう", "す", listOf(), listOf("v5")),
        suffixInflection("ちそう", "つ", listOf(), listOf("v5")),
        suffixInflection("ちさう", "つ", listOf(), listOf("v5")),
        suffixInflection("にそう", "ぬ", listOf(), listOf("v5")),
        suffixInflection("にさう", "ぬ", listOf(), listOf("v5")),
        suffixInflection("びそう", "ぶ", listOf(), listOf("v5")),
        suffixInflection("びさう", "ぶ", listOf(), listOf("v5")),
        suffixInflection("みそう", "む", listOf(), listOf("v5")),
        suffixInflection("みさう", "む", listOf(), listOf("v5")),
        suffixInflection("りそう", "る", listOf(), listOf("v5")),
        suffixInflection("りさう", "る", listOf(), listOf("v5")),
        suffixInflection("じそう", "ずる", listOf(), listOf("vz")),
        suffixInflection("じさう", "ずる", listOf(), listOf("vz")),
        suffixInflection("しそう", "する", listOf(), listOf("vs")),
        suffixInflection("しさう", "する", listOf(), listOf("vs")),
        suffixInflection("為そう", "為る", listOf(), listOf("vs")),
        suffixInflection("為さう", "為る", listOf(), listOf("vs")),
        suffixInflection("きそう", "くる", listOf(), listOf("vk")),
        suffixInflection("きさう", "くる", listOf(), listOf("vk")),
        suffixInflection("来そう", "来る", listOf(), listOf("vk")),
        suffixInflection("来さう", "来る", listOf(), listOf("vk")),
        suffixInflection("來そう", "來る", listOf(), listOf("vk")),
        suffixInflection("來さう", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform8(): TransformDescriptor = TransformDescriptor(
    id = "-すぎる",
    name = "-すぎる",
    rules = listOf(
        suffixInflection("すぎる", "い", listOf("v1"), listOf("adj-i")),
        suffixInflection("すぎる", "る", listOf("v1"), listOf("v1")),
        suffixInflection("いすぎる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("きすぎる", "く", listOf("v1"), listOf("v5")),
        suffixInflection("ぎすぎる", "ぐ", listOf("v1"), listOf("v5")),
        suffixInflection("しすぎる", "す", listOf("v1"), listOf("v5")),
        suffixInflection("ちすぎる", "つ", listOf("v1"), listOf("v5")),
        suffixInflection("にすぎる", "ぬ", listOf("v1"), listOf("v5")),
        suffixInflection("びすぎる", "ぶ", listOf("v1"), listOf("v5")),
        suffixInflection("みすぎる", "む", listOf("v1"), listOf("v5")),
        suffixInflection("りすぎる", "る", listOf("v1"), listOf("v5")),
        suffixInflection("じすぎる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("しすぎる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為すぎる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("きすぎる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来すぎる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來すぎる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform9(): TransformDescriptor = TransformDescriptor(
    id = "-過ぎる",
    name = "-過ぎる",
    rules = listOf(
        suffixInflection("過ぎる", "い", listOf("v1"), listOf("adj-i")),
        suffixInflection("過ぎる", "る", listOf("v1"), listOf("v1")),
        suffixInflection("い過ぎる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("き過ぎる", "く", listOf("v1"), listOf("v5")),
        suffixInflection("ぎ過ぎる", "ぐ", listOf("v1"), listOf("v5")),
        suffixInflection("し過ぎる", "す", listOf("v1"), listOf("v5")),
        suffixInflection("ち過ぎる", "つ", listOf("v1"), listOf("v5")),
        suffixInflection("に過ぎる", "ぬ", listOf("v1"), listOf("v5")),
        suffixInflection("び過ぎる", "ぶ", listOf("v1"), listOf("v5")),
        suffixInflection("み過ぎる", "む", listOf("v1"), listOf("v5")),
        suffixInflection("り過ぎる", "る", listOf("v1"), listOf("v5")),
        suffixInflection("じ過ぎる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("し過ぎる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為過ぎる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("き過ぎる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来過ぎる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來過ぎる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform10(): TransformDescriptor = TransformDescriptor(
    id = "-たい",
    name = "-たい",
    rules = listOf(
        suffixInflection("たい", "る", listOf("adj-i"), listOf("v1")),
        suffixInflection("いたい", "う", listOf("adj-i"), listOf("v5")),
        suffixInflection("きたい", "く", listOf("adj-i"), listOf("v5")),
        suffixInflection("ぎたい", "ぐ", listOf("adj-i"), listOf("v5")),
        suffixInflection("したい", "す", listOf("adj-i"), listOf("v5")),
        suffixInflection("ちたい", "つ", listOf("adj-i"), listOf("v5")),
        suffixInflection("にたい", "ぬ", listOf("adj-i"), listOf("v5")),
        suffixInflection("びたい", "ぶ", listOf("adj-i"), listOf("v5")),
        suffixInflection("みたい", "む", listOf("adj-i"), listOf("v5")),
        suffixInflection("りたい", "る", listOf("adj-i"), listOf("v5")),
        suffixInflection("じたい", "ずる", listOf("adj-i"), listOf("vz")),
        suffixInflection("したい", "する", listOf("adj-i"), listOf("vs")),
        suffixInflection("為たい", "為る", listOf("adj-i"), listOf("vs")),
        suffixInflection("きたい", "くる", listOf("adj-i"), listOf("vk")),
        suffixInflection("来たい", "来る", listOf("adj-i"), listOf("vk")),
        suffixInflection("來たい", "來る", listOf("adj-i"), listOf("vk")),
    ),
)

private fun transform11(): TransformDescriptor = TransformDescriptor(
    id = "-たら",
    name = "-たら",
    rules = buildList {
        add(suffixInflection("かったら", "い", listOf(), listOf("adj-i")))
        add(suffixInflection("たら", "る", listOf(), listOf("v1")))
        add(suffixInflection("いたら", "く", listOf(), listOf("v5")))
        add(suffixInflection("いだら", "ぐ", listOf(), listOf("v5")))
        add(suffixInflection("したら", "す", listOf(), listOf("v5")))
        add(suffixInflection("ったら", "う", listOf(), listOf("v5")))
        add(suffixInflection("ったら", "つ", listOf(), listOf("v5")))
        add(suffixInflection("ったら", "る", listOf(), listOf("v5")))
        add(suffixInflection("んだら", "ぬ", listOf(), listOf("v5")))
        add(suffixInflection("んだら", "ぶ", listOf(), listOf("v5")))
        add(suffixInflection("んだら", "む", listOf(), listOf("v5")))
        add(suffixInflection("じたら", "ずる", listOf(), listOf("vz")))
        add(suffixInflection("したら", "する", listOf(), listOf("vs")))
        add(suffixInflection("為たら", "為る", listOf(), listOf("vs")))
        add(suffixInflection("きたら", "くる", listOf(), listOf("vk")))
        add(suffixInflection("来たら", "来る", listOf(), listOf("vk")))
        add(suffixInflection("來たら", "來る", listOf(), listOf("vk")))
        addAll(irregularVerbSuffixInflections("たら", listOf(), listOf("v5")))
        add(suffixInflection("ましたら", "ます", listOf(), listOf("-ます")))
    },
)

private fun transform12(): TransformDescriptor = TransformDescriptor(
    id = "-たり",
    name = "-たり",
    rules = buildList {
        add(suffixInflection("かったり", "い", listOf(), listOf("adj-i")))
        add(suffixInflection("たり", "る", listOf(), listOf("v1")))
        add(suffixInflection("いたり", "く", listOf(), listOf("v5")))
        add(suffixInflection("いだり", "ぐ", listOf(), listOf("v5")))
        add(suffixInflection("したり", "す", listOf(), listOf("v5")))
        add(suffixInflection("ったり", "う", listOf(), listOf("v5")))
        add(suffixInflection("ったり", "つ", listOf(), listOf("v5")))
        add(suffixInflection("ったり", "る", listOf(), listOf("v5")))
        add(suffixInflection("んだり", "ぬ", listOf(), listOf("v5")))
        add(suffixInflection("んだり", "ぶ", listOf(), listOf("v5")))
        add(suffixInflection("んだり", "む", listOf(), listOf("v5")))
        add(suffixInflection("じたり", "ずる", listOf(), listOf("vz")))
        add(suffixInflection("したり", "する", listOf(), listOf("vs")))
        add(suffixInflection("為たり", "為る", listOf(), listOf("vs")))
        add(suffixInflection("きたり", "くる", listOf(), listOf("vk")))
        add(suffixInflection("来たり", "来る", listOf(), listOf("vk")))
        add(suffixInflection("來たり", "來る", listOf(), listOf("vk")))
        addAll(irregularVerbSuffixInflections("たり", listOf(), listOf("v5")))
    },
)

private fun transform13(): TransformDescriptor = TransformDescriptor(
    id = "-て",
    name = "-て",
    rules = buildList {
        add(suffixInflection("くて", "い", listOf("-て"), listOf("adj-i")))
        add(suffixInflection("て", "る", listOf("-て"), listOf("v1")))
        add(suffixInflection("いて", "く", listOf("-て"), listOf("v5")))
        add(suffixInflection("いで", "ぐ", listOf("-て"), listOf("v5")))
        add(suffixInflection("して", "す", listOf("-て"), listOf("v5")))
        add(suffixInflection("って", "う", listOf("-て"), listOf("v5")))
        add(suffixInflection("って", "つ", listOf("-て"), listOf("v5")))
        add(suffixInflection("って", "る", listOf("-て"), listOf("v5")))
        add(suffixInflection("んで", "ぬ", listOf("-て"), listOf("v5")))
        add(suffixInflection("んで", "ぶ", listOf("-て"), listOf("v5")))
        add(suffixInflection("んで", "む", listOf("-て"), listOf("v5")))
        add(suffixInflection("じて", "ずる", listOf("-て"), listOf("vz")))
        add(suffixInflection("して", "する", listOf("-て"), listOf("vs")))
        add(suffixInflection("為て", "為る", listOf("-て"), listOf("vs")))
        add(suffixInflection("きて", "くる", listOf("-て"), listOf("vk")))
        add(suffixInflection("来て", "来る", listOf("-て"), listOf("vk")))
        add(suffixInflection("來て", "來る", listOf("-て"), listOf("vk")))
        addAll(irregularVerbSuffixInflections("て", listOf("-て"), listOf("v5")))
        add(suffixInflection("まして", "ます", listOf(), listOf("-ます")))
    },
)

private fun transform14(): TransformDescriptor = TransformDescriptor(
    id = "-ず",
    name = "-ず",
    rules = listOf(
        suffixInflection("ず", "る", listOf(), listOf("v1")),
        suffixInflection("かず", "く", listOf(), listOf("v5")),
        suffixInflection("がず", "ぐ", listOf(), listOf("v5")),
        suffixInflection("さず", "す", listOf(), listOf("v5")),
        suffixInflection("たず", "つ", listOf(), listOf("v5")),
        suffixInflection("なず", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ばず", "ぶ", listOf(), listOf("v5")),
        suffixInflection("まず", "む", listOf(), listOf("v5")),
        suffixInflection("らず", "る", listOf(), listOf("v5")),
        suffixInflection("わず", "う", listOf(), listOf("v5")),
        suffixInflection("はず", "う", listOf(), listOf("v5")),
        suffixInflection("ぜず", "ずる", listOf(), listOf("vz")),
        suffixInflection("せず", "する", listOf(), listOf("vs")),
        suffixInflection("為ず", "為る", listOf(), listOf("vs")),
        suffixInflection("こず", "くる", listOf(), listOf("vk")),
        suffixInflection("来ず", "来る", listOf(), listOf("vk")),
        suffixInflection("來ず", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform15(): TransformDescriptor = TransformDescriptor(
    id = "-ぬ",
    name = "-ぬ",
    rules = listOf(
        suffixInflection("ぬ", "る", listOf(), listOf("v1")),
        suffixInflection("かぬ", "く", listOf(), listOf("v5")),
        suffixInflection("がぬ", "ぐ", listOf(), listOf("v5")),
        suffixInflection("さぬ", "す", listOf(), listOf("v5")),
        suffixInflection("たぬ", "つ", listOf(), listOf("v5")),
        suffixInflection("なぬ", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ばぬ", "ぶ", listOf(), listOf("v5")),
        suffixInflection("まぬ", "む", listOf(), listOf("v5")),
        suffixInflection("らぬ", "る", listOf(), listOf("v5")),
        suffixInflection("わぬ", "う", listOf(), listOf("v5")),
        suffixInflection("はぬ", "う", listOf(), listOf("v5")),
        suffixInflection("ぜぬ", "ずる", listOf(), listOf("vz")),
        suffixInflection("せぬ", "する", listOf(), listOf("vs")),
        suffixInflection("為ぬ", "為る", listOf(), listOf("vs")),
        suffixInflection("こぬ", "くる", listOf(), listOf("vk")),
        suffixInflection("来ぬ", "来る", listOf(), listOf("vk")),
        suffixInflection("來ぬ", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform16(): TransformDescriptor = TransformDescriptor(
    id = "-ん",
    name = "-ん",
    rules = listOf(
        suffixInflection("ん", "る", listOf("-ん"), listOf("v1")),
        suffixInflection("かん", "く", listOf("-ん"), listOf("v5")),
        suffixInflection("がん", "ぐ", listOf("-ん"), listOf("v5")),
        suffixInflection("さん", "す", listOf("-ん"), listOf("v5")),
        suffixInflection("たん", "つ", listOf("-ん"), listOf("v5")),
        suffixInflection("なん", "ぬ", listOf("-ん"), listOf("v5")),
        suffixInflection("ばん", "ぶ", listOf("-ん"), listOf("v5")),
        suffixInflection("まん", "む", listOf("-ん"), listOf("v5")),
        suffixInflection("らん", "る", listOf("-ん"), listOf("v5")),
        suffixInflection("わん", "う", listOf("-ん"), listOf("v5")),
        suffixInflection("はん", "う", listOf("-ん"), listOf("v5")),
        suffixInflection("ぜん", "ずる", listOf("-ん"), listOf("vz")),
        suffixInflection("せん", "する", listOf("-ん"), listOf("vs")),
        suffixInflection("為ん", "為る", listOf("-ん"), listOf("vs")),
        suffixInflection("こん", "くる", listOf("-ん"), listOf("vk")),
        suffixInflection("来ん", "来る", listOf("-ん"), listOf("vk")),
        suffixInflection("來ん", "來る", listOf("-ん"), listOf("vk")),
    ),
)

private fun transform17(): TransformDescriptor = TransformDescriptor(
    id = "-んばかり",
    name = "-んばかり",
    rules = listOf(
        suffixInflection("んばかり", "る", listOf(), listOf("v1")),
        suffixInflection("かんばかり", "く", listOf(), listOf("v5")),
        suffixInflection("がんばかり", "ぐ", listOf(), listOf("v5")),
        suffixInflection("さんばかり", "す", listOf(), listOf("v5")),
        suffixInflection("たんばかり", "つ", listOf(), listOf("v5")),
        suffixInflection("なんばかり", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ばんばかり", "ぶ", listOf(), listOf("v5")),
        suffixInflection("まんばかり", "む", listOf(), listOf("v5")),
        suffixInflection("らんばかり", "る", listOf(), listOf("v5")),
        suffixInflection("わんばかり", "う", listOf(), listOf("v5")),
        suffixInflection("はんばかり", "う", listOf(), listOf("v5")),
        suffixInflection("ぜんばかり", "ずる", listOf(), listOf("vz")),
        suffixInflection("せんばかり", "する", listOf(), listOf("vs")),
        suffixInflection("為んばかり", "為る", listOf(), listOf("vs")),
        suffixInflection("こんばかり", "くる", listOf(), listOf("vk")),
        suffixInflection("来んばかり", "来る", listOf(), listOf("vk")),
        suffixInflection("來んばかり", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform18(): TransformDescriptor = TransformDescriptor(
    id = "-んとする",
    name = "-んとする",
    rules = listOf(
        suffixInflection("んとする", "る", listOf("vs"), listOf("v1")),
        suffixInflection("かんとする", "く", listOf("vs"), listOf("v5")),
        suffixInflection("がんとする", "ぐ", listOf("vs"), listOf("v5")),
        suffixInflection("さんとする", "す", listOf("vs"), listOf("v5")),
        suffixInflection("たんとする", "つ", listOf("vs"), listOf("v5")),
        suffixInflection("なんとする", "ぬ", listOf("vs"), listOf("v5")),
        suffixInflection("ばんとする", "ぶ", listOf("vs"), listOf("v5")),
        suffixInflection("まんとする", "む", listOf("vs"), listOf("v5")),
        suffixInflection("らんとする", "る", listOf("vs"), listOf("v5")),
        suffixInflection("わんとする", "う", listOf("vs"), listOf("v5")),
        suffixInflection("はんとする", "う", listOf("vs"), listOf("v5")),
        suffixInflection("ぜんとする", "ずる", listOf("vs"), listOf("vz")),
        suffixInflection("せんとする", "する", listOf("vs"), listOf("vs")),
        suffixInflection("為んとする", "為る", listOf("vs"), listOf("vs")),
        suffixInflection("こんとする", "くる", listOf("vs"), listOf("vk")),
        suffixInflection("来んとする", "来る", listOf("vs"), listOf("vk")),
        suffixInflection("來んとする", "來る", listOf("vs"), listOf("vk")),
    ),
)

private fun transform19(): TransformDescriptor = TransformDescriptor(
    id = "-む",
    name = "-む",
    rules = listOf(
        suffixInflection("む", "る", listOf(), listOf("v1")),
        suffixInflection("かむ", "く", listOf(), listOf("v5")),
        suffixInflection("がむ", "ぐ", listOf(), listOf("v5")),
        suffixInflection("さむ", "す", listOf(), listOf("v5")),
        suffixInflection("たむ", "つ", listOf(), listOf("v5")),
        suffixInflection("なむ", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ばむ", "ぶ", listOf(), listOf("v5")),
        suffixInflection("まむ", "む", listOf(), listOf("v5")),
        suffixInflection("らむ", "る", listOf(), listOf("v5")),
        suffixInflection("わむ", "う", listOf(), listOf("v5")),
        suffixInflection("はむ", "う", listOf(), listOf("v5")),
        suffixInflection("ぜむ", "ずる", listOf(), listOf("vz")),
        suffixInflection("せむ", "する", listOf(), listOf("vs")),
        suffixInflection("為む", "為る", listOf(), listOf("vs")),
        suffixInflection("こむ", "くる", listOf(), listOf("vk")),
        suffixInflection("来む", "来る", listOf(), listOf("vk")),
        suffixInflection("來む", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform20(): TransformDescriptor = TransformDescriptor(
    id = "-ざる",
    name = "-ざる",
    rules = listOf(
        suffixInflection("ざる", "る", listOf(), listOf("v1")),
        suffixInflection("かざる", "く", listOf(), listOf("v5")),
        suffixInflection("がざる", "ぐ", listOf(), listOf("v5")),
        suffixInflection("さざる", "す", listOf(), listOf("v5")),
        suffixInflection("たざる", "つ", listOf(), listOf("v5")),
        suffixInflection("なざる", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ばざる", "ぶ", listOf(), listOf("v5")),
        suffixInflection("まざる", "む", listOf(), listOf("v5")),
        suffixInflection("らざる", "る", listOf(), listOf("v5")),
        suffixInflection("わざる", "う", listOf(), listOf("v5")),
        suffixInflection("はざる", "う", listOf(), listOf("v5")),
        suffixInflection("ぜざる", "ずる", listOf(), listOf("vz")),
        suffixInflection("せざる", "する", listOf(), listOf("vs")),
        suffixInflection("為ざる", "為る", listOf(), listOf("vs")),
        suffixInflection("こざる", "くる", listOf(), listOf("vk")),
        suffixInflection("来ざる", "来る", listOf(), listOf("vk")),
        suffixInflection("來ざる", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform21(): TransformDescriptor = TransformDescriptor(
    id = "-ねば",
    name = "-ねば",
    rules = listOf(
        suffixInflection("ねば", "る", listOf("-ば"), listOf("v1")),
        suffixInflection("かねば", "く", listOf("-ば"), listOf("v5")),
        suffixInflection("がねば", "ぐ", listOf("-ば"), listOf("v5")),
        suffixInflection("さねば", "す", listOf("-ば"), listOf("v5")),
        suffixInflection("たねば", "つ", listOf("-ば"), listOf("v5")),
        suffixInflection("なねば", "ぬ", listOf("-ば"), listOf("v5")),
        suffixInflection("ばねば", "ぶ", listOf("-ば"), listOf("v5")),
        suffixInflection("まねば", "む", listOf("-ば"), listOf("v5")),
        suffixInflection("らねば", "る", listOf("-ば"), listOf("v5")),
        suffixInflection("わねば", "う", listOf("-ば"), listOf("v5")),
        suffixInflection("はねば", "う", listOf("-ば"), listOf("v5")),
        suffixInflection("ぜねば", "ずる", listOf("-ば"), listOf("vz")),
        suffixInflection("せねば", "する", listOf("-ば"), listOf("vs")),
        suffixInflection("為ねば", "為る", listOf("-ば"), listOf("vs")),
        suffixInflection("こねば", "くる", listOf("-ば"), listOf("vk")),
        suffixInflection("来ねば", "来る", listOf("-ば"), listOf("vk")),
        suffixInflection("來ねば", "來る", listOf("-ば"), listOf("vk")),
    ),
)

private fun transform22(): TransformDescriptor = TransformDescriptor(
    id = "-く",
    name = "-く",
    rules = listOf(
        suffixInflection("く", "い", listOf("-く"), listOf("adj-i")),
    ),
)

private fun transform23(): TransformDescriptor = TransformDescriptor(
    id = "causative",
    name = "causative",
    rules = listOf(
        suffixInflection("させる", "る", listOf("v1"), listOf("v1")),
        suffixInflection("かせる", "く", listOf("v1"), listOf("v5")),
        suffixInflection("がせる", "ぐ", listOf("v1"), listOf("v5")),
        suffixInflection("させる", "す", listOf("v1"), listOf("v5")),
        suffixInflection("たせる", "つ", listOf("v1"), listOf("v5")),
        suffixInflection("なせる", "ぬ", listOf("v1"), listOf("v5")),
        suffixInflection("ばせる", "ぶ", listOf("v1"), listOf("v5")),
        suffixInflection("ませる", "む", listOf("v1"), listOf("v5")),
        suffixInflection("らせる", "る", listOf("v1"), listOf("v5")),
        suffixInflection("わせる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("はせる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("じさせる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("ぜさせる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("させる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為せる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("せさせる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為させる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("こさせる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来させる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來させる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform24(): TransformDescriptor = TransformDescriptor(
    id = "short causative",
    name = "short causative",
    rules = listOf(
        suffixInflection("さす", "る", listOf("v5ss"), listOf("v1")),
        suffixInflection("かす", "く", listOf("v5sp"), listOf("v5")),
        suffixInflection("がす", "ぐ", listOf("v5sp"), listOf("v5")),
        suffixInflection("さす", "す", listOf("v5ss"), listOf("v5")),
        suffixInflection("たす", "つ", listOf("v5sp"), listOf("v5")),
        suffixInflection("なす", "ぬ", listOf("v5sp"), listOf("v5")),
        suffixInflection("ばす", "ぶ", listOf("v5sp"), listOf("v5")),
        suffixInflection("ます", "む", listOf("v5sp"), listOf("v5")),
        suffixInflection("らす", "る", listOf("v5sp"), listOf("v5")),
        suffixInflection("わす", "う", listOf("v5sp"), listOf("v5")),
        suffixInflection("はす", "う", listOf("v5sp"), listOf("v5")),
        suffixInflection("じさす", "ずる", listOf("v5ss"), listOf("vz")),
        suffixInflection("ぜさす", "ずる", listOf("v5ss"), listOf("vz")),
        suffixInflection("さす", "する", listOf("v5ss"), listOf("vs")),
        suffixInflection("為す", "為る", listOf("v5ss"), listOf("vs")),
        suffixInflection("こさす", "くる", listOf("v5ss"), listOf("vk")),
        suffixInflection("来さす", "来る", listOf("v5ss"), listOf("vk")),
        suffixInflection("來さす", "來る", listOf("v5ss"), listOf("vk")),
    ),
)

private fun transform25(): TransformDescriptor = TransformDescriptor(
    id = "imperative",
    name = "imperative",
    rules = listOf(
        suffixInflection("ろ", "る", listOf(), listOf("v1")),
        suffixInflection("よ", "る", listOf(), listOf("v1")),
        suffixInflection("え", "う", listOf(), listOf("v5")),
        suffixInflection("へ", "う", listOf(), listOf("v5")),
        suffixInflection("け", "く", listOf(), listOf("v5")),
        suffixInflection("げ", "ぐ", listOf(), listOf("v5")),
        suffixInflection("せ", "す", listOf(), listOf("v5")),
        suffixInflection("て", "つ", listOf(), listOf("v5")),
        suffixInflection("ね", "ぬ", listOf(), listOf("v5")),
        suffixInflection("べ", "ぶ", listOf(), listOf("v5")),
        suffixInflection("め", "む", listOf(), listOf("v5")),
        suffixInflection("れ", "る", listOf(), listOf("v5")),
        suffixInflection("じろ", "ずる", listOf(), listOf("vz")),
        suffixInflection("ぜよ", "ずる", listOf(), listOf("vz")),
        suffixInflection("しろ", "する", listOf(), listOf("vs")),
        suffixInflection("せよ", "する", listOf(), listOf("vs")),
        suffixInflection("為ろ", "為る", listOf(), listOf("vs")),
        suffixInflection("為よ", "為る", listOf(), listOf("vs")),
        suffixInflection("こい", "くる", listOf(), listOf("vk")),
        suffixInflection("来い", "来る", listOf(), listOf("vk")),
        suffixInflection("來い", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform26(): TransformDescriptor = TransformDescriptor(
    id = "continuative",
    name = "continuative",
    rules = listOf(
        suffixInflection("い", "いる", listOf(), listOf("v1d")),
        suffixInflection("え", "える", listOf(), listOf("v1d")),
        suffixInflection("き", "きる", listOf(), listOf("v1d")),
        suffixInflection("ぎ", "ぎる", listOf(), listOf("v1d")),
        suffixInflection("け", "ける", listOf(), listOf("v1d")),
        suffixInflection("げ", "げる", listOf(), listOf("v1d")),
        suffixInflection("じ", "じる", listOf(), listOf("v1d")),
        suffixInflection("せ", "せる", listOf(), listOf("v1d")),
        suffixInflection("ぜ", "ぜる", listOf(), listOf("v1d")),
        suffixInflection("ち", "ちる", listOf(), listOf("v1d")),
        suffixInflection("て", "てる", listOf(), listOf("v1d")),
        suffixInflection("で", "でる", listOf(), listOf("v1d")),
        suffixInflection("に", "にる", listOf(), listOf("v1d")),
        suffixInflection("ね", "ねる", listOf(), listOf("v1d")),
        suffixInflection("ひ", "ひる", listOf(), listOf("v1d")),
        suffixInflection("び", "びる", listOf(), listOf("v1d")),
        suffixInflection("へ", "へる", listOf(), listOf("v1d")),
        suffixInflection("べ", "べる", listOf(), listOf("v1d")),
        suffixInflection("み", "みる", listOf(), listOf("v1d")),
        suffixInflection("め", "める", listOf(), listOf("v1d")),
        suffixInflection("り", "りる", listOf(), listOf("v1d")),
        suffixInflection("れ", "れる", listOf(), listOf("v1d")),
        suffixInflection("い", "う", listOf(), listOf("v5")),
        suffixInflection("ひ", "う", listOf(), listOf("v5")),
        suffixInflection("き", "く", listOf(), listOf("v5")),
        suffixInflection("ぎ", "ぐ", listOf(), listOf("v5")),
        suffixInflection("し", "す", listOf(), listOf("v5")),
        suffixInflection("ち", "つ", listOf(), listOf("v5")),
        suffixInflection("に", "ぬ", listOf(), listOf("v5")),
        suffixInflection("び", "ぶ", listOf(), listOf("v5")),
        suffixInflection("み", "む", listOf(), listOf("v5")),
        suffixInflection("り", "る", listOf(), listOf("v5")),
        suffixInflection("き", "くる", listOf(), listOf("vk")),
        suffixInflection("し", "する", listOf(), listOf("vs")),
        suffixInflection("来", "来る", listOf(), listOf("vk")),
        suffixInflection("來", "來る", listOf(), listOf("vk")),
    ),
)

private fun transform27(): TransformDescriptor = TransformDescriptor(
    id = "negative",
    name = "negative",
    rules = listOf(
        suffixInflection("くない", "い", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("ない", "る", listOf("adj-i"), listOf("v1")),
        suffixInflection("かない", "く", listOf("adj-i"), listOf("v5")),
        suffixInflection("がない", "ぐ", listOf("adj-i"), listOf("v5")),
        suffixInflection("さない", "す", listOf("adj-i"), listOf("v5")),
        suffixInflection("たない", "つ", listOf("adj-i"), listOf("v5")),
        suffixInflection("なない", "ぬ", listOf("adj-i"), listOf("v5")),
        suffixInflection("ばない", "ぶ", listOf("adj-i"), listOf("v5")),
        suffixInflection("まない", "む", listOf("adj-i"), listOf("v5")),
        suffixInflection("らない", "る", listOf("adj-i"), listOf("v5")),
        suffixInflection("わない", "う", listOf("adj-i"), listOf("v5")),
        suffixInflection("はない", "う", listOf("adj-i"), listOf("v5")),
        suffixInflection("じない", "ずる", listOf("adj-i"), listOf("vz")),
        suffixInflection("しない", "する", listOf("adj-i"), listOf("vs")),
        suffixInflection("為ない", "為る", listOf("adj-i"), listOf("vs")),
        suffixInflection("こない", "くる", listOf("adj-i"), listOf("vk")),
        suffixInflection("来ない", "来る", listOf("adj-i"), listOf("vk")),
        suffixInflection("來ない", "來る", listOf("adj-i"), listOf("vk")),
        suffixInflection("ません", "ます", listOf("-ません"), listOf("-ます")),
    ),
)

private fun transform28(): TransformDescriptor = TransformDescriptor(
    id = "-さ",
    name = "-さ",
    rules = listOf(
        suffixInflection("さ", "い", listOf(), listOf("adj-i")),
    ),
)

private fun transform29(): TransformDescriptor = TransformDescriptor(
    id = "passive",
    name = "passive",
    rules = listOf(
        suffixInflection("かれる", "く", listOf("v1"), listOf("v5")),
        suffixInflection("がれる", "ぐ", listOf("v1"), listOf("v5")),
        suffixInflection("される", "す", listOf("v1"), listOf("v5d", "v5sp")),
        suffixInflection("たれる", "つ", listOf("v1"), listOf("v5")),
        suffixInflection("なれる", "ぬ", listOf("v1"), listOf("v5")),
        suffixInflection("ばれる", "ぶ", listOf("v1"), listOf("v5")),
        suffixInflection("まれる", "む", listOf("v1"), listOf("v5")),
        suffixInflection("われる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("はれる", "う", listOf("v1"), listOf("v5")),
        suffixInflection("られる", "る", listOf("v1"), listOf("v5")),
        suffixInflection("じされる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("ぜされる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("される", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為れる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("こられる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来られる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來られる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform30(): TransformDescriptor = TransformDescriptor(
    id = "-た",
    name = "-た",
    rules = buildList {
        add(suffixInflection("かった", "い", listOf("-た"), listOf("adj-i")))
        add(suffixInflection("た", "る", listOf("-た"), listOf("v1")))
        add(suffixInflection("いた", "く", listOf("-た"), listOf("v5")))
        add(suffixInflection("いだ", "ぐ", listOf("-た"), listOf("v5")))
        add(suffixInflection("した", "す", listOf("-た"), listOf("v5")))
        add(suffixInflection("った", "う", listOf("-た"), listOf("v5")))
        add(suffixInflection("った", "つ", listOf("-た"), listOf("v5")))
        add(suffixInflection("った", "る", listOf("-た"), listOf("v5")))
        add(suffixInflection("んだ", "ぬ", listOf("-た"), listOf("v5")))
        add(suffixInflection("んだ", "ぶ", listOf("-た"), listOf("v5")))
        add(suffixInflection("んだ", "む", listOf("-た"), listOf("v5")))
        add(suffixInflection("じた", "ずる", listOf("-た"), listOf("vz")))
        add(suffixInflection("した", "する", listOf("-た"), listOf("vs")))
        add(suffixInflection("為た", "為る", listOf("-た"), listOf("vs")))
        add(suffixInflection("きた", "くる", listOf("-た"), listOf("vk")))
        add(suffixInflection("来た", "来る", listOf("-た"), listOf("vk")))
        add(suffixInflection("來た", "來る", listOf("-た"), listOf("vk")))
        addAll(irregularVerbSuffixInflections("た", listOf("-た"), listOf("v5")))
        add(suffixInflection("ました", "ます", listOf("-た"), listOf("-ます")))
        add(suffixInflection("でした", "", listOf("-た"), listOf("-ません")))
        add(suffixInflection("かった", "", listOf("-た"), listOf("-ません", "-ん")))
    },
)

private fun transform31(): TransformDescriptor = TransformDescriptor(
    id = "-ます",
    name = "-ます",
    rules = buildList {
        add(suffixInflection("ます", "る", listOf("-ます"), listOf("v1")))
        addAll(specialHonorificMasuInflections(listOf("-ます"), listOf("v5d")))
        add(suffixInflection("います", "う", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("ひます", "う", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("きます", "く", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("ぎます", "ぐ", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("します", "す", listOf("-ます"), listOf("v5d", "v5s")))
        add(suffixInflection("ちます", "つ", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("にます", "ぬ", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("びます", "ぶ", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("みます", "む", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("ります", "る", listOf("-ます"), listOf("v5d")))
        add(suffixInflection("じます", "ずる", listOf("-ます"), listOf("vz")))
        add(suffixInflection("します", "する", listOf("-ます"), listOf("vs")))
        add(suffixInflection("為ます", "為る", listOf("-ます"), listOf("vs")))
        add(suffixInflection("きます", "くる", listOf("-ます"), listOf("vk")))
        add(suffixInflection("来ます", "来る", listOf("-ます"), listOf("vk")))
        add(suffixInflection("來ます", "來る", listOf("-ます"), listOf("vk")))
        add(suffixInflection("くあります", "い", listOf("-ます"), listOf("adj-i")))
    },
)

private fun transform32(): TransformDescriptor = TransformDescriptor(
    id = "potential",
    name = "potential",
    rules = listOf(
        suffixInflection("れる", "る", listOf("v1"), listOf("v1", "v5d")),
        suffixInflection("える", "う", listOf("v1"), listOf("v5d")),
        suffixInflection("ける", "く", listOf("v1"), listOf("v5d")),
        suffixInflection("げる", "ぐ", listOf("v1"), listOf("v5d")),
        suffixInflection("せる", "す", listOf("v1"), listOf("v5d")),
        suffixInflection("てる", "つ", listOf("v1"), listOf("v5d")),
        suffixInflection("ねる", "ぬ", listOf("v1"), listOf("v5d")),
        suffixInflection("べる", "ぶ", listOf("v1"), listOf("v5d")),
        suffixInflection("める", "む", listOf("v1"), listOf("v5d")),
        suffixInflection("できる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("出来る", "する", listOf("v1"), listOf("vs")),
        suffixInflection("これる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来れる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來れる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform33(): TransformDescriptor = TransformDescriptor(
    id = "potential or passive",
    name = "potential or passive",
    rules = listOf(
        suffixInflection("られる", "る", listOf("v1"), listOf("v1")),
        suffixInflection("ざれる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("ぜられる", "ずる", listOf("v1"), listOf("vz")),
        suffixInflection("せられる", "する", listOf("v1"), listOf("vs")),
        suffixInflection("為られる", "為る", listOf("v1"), listOf("vs")),
        suffixInflection("こられる", "くる", listOf("v1"), listOf("vk")),
        suffixInflection("来られる", "来る", listOf("v1"), listOf("vk")),
        suffixInflection("來られる", "來る", listOf("v1"), listOf("vk")),
    ),
)

private fun transform34(): TransformDescriptor = TransformDescriptor(
    id = "volitional",
    name = "volitional",
    rules = listOf(
        suffixInflection("よう", "る", listOf(), listOf("v1")),
        suffixInflection("やう", "る", listOf(), listOf("v1")),
        suffixInflection("おう", "う", listOf(), listOf("v5")),
        suffixInflection("はう", "う", listOf(), listOf("v5")),
        suffixInflection("こう", "く", listOf(), listOf("v5")),
        suffixInflection("かう", "く", listOf(), listOf("v5")),
        suffixInflection("ごう", "ぐ", listOf(), listOf("v5")),
        suffixInflection("がう", "ぐ", listOf(), listOf("v5")),
        suffixInflection("そう", "す", listOf(), listOf("v5")),
        suffixInflection("さう", "す", listOf(), listOf("v5")),
        suffixInflection("とう", "つ", listOf(), listOf("v5")),
        suffixInflection("たう", "つ", listOf(), listOf("v5")),
        suffixInflection("のう", "ぬ", listOf(), listOf("v5")),
        suffixInflection("なう", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ぼう", "ぶ", listOf(), listOf("v5")),
        suffixInflection("ばう", "ぶ", listOf(), listOf("v5")),
        suffixInflection("もう", "む", listOf(), listOf("v5")),
        suffixInflection("まう", "む", listOf(), listOf("v5")),
        suffixInflection("ろう", "る", listOf(), listOf("v5")),
        suffixInflection("らう", "る", listOf(), listOf("v5")),
        suffixInflection("じよう", "ずる", listOf(), listOf("vz")),
        suffixInflection("じやう", "ずる", listOf(), listOf("vz")),
        suffixInflection("しよう", "する", listOf(), listOf("vs")),
        suffixInflection("しやう", "する", listOf(), listOf("vs")),
        suffixInflection("為よう", "為る", listOf(), listOf("vs")),
        suffixInflection("為やう", "為る", listOf(), listOf("vs")),
        suffixInflection("こよう", "くる", listOf(), listOf("vk")),
        suffixInflection("こやう", "くる", listOf(), listOf("vk")),
        suffixInflection("来よう", "来る", listOf(), listOf("vk")),
        suffixInflection("来やう", "来る", listOf(), listOf("vk")),
        suffixInflection("來よう", "來る", listOf(), listOf("vk")),
        suffixInflection("來やう", "來る", listOf(), listOf("vk")),
        suffixInflection("ましょう", "ます", listOf(), listOf("-ます")),
        suffixInflection("ませう", "ます", listOf(), listOf("-ます")),
        suffixInflection("かろう", "い", listOf(), listOf("adj-i")),
        suffixInflection("からう", "い", listOf(), listOf("adj-i")),
    ),
)

private fun transform35(): TransformDescriptor = TransformDescriptor(
    id = "volitional slang",
    name = "volitional slang",
    rules = listOf(
        suffixInflection("よっか", "る", listOf(), listOf("v1")),
        suffixInflection("おっか", "う", listOf(), listOf("v5")),
        suffixInflection("こっか", "く", listOf(), listOf("v5")),
        suffixInflection("ごっか", "ぐ", listOf(), listOf("v5")),
        suffixInflection("そっか", "す", listOf(), listOf("v5")),
        suffixInflection("とっか", "つ", listOf(), listOf("v5")),
        suffixInflection("のっか", "ぬ", listOf(), listOf("v5")),
        suffixInflection("ぼっか", "ぶ", listOf(), listOf("v5")),
        suffixInflection("もっか", "む", listOf(), listOf("v5")),
        suffixInflection("ろっか", "る", listOf(), listOf("v5")),
        suffixInflection("じよっか", "ずる", listOf(), listOf("vz")),
        suffixInflection("しよっか", "する", listOf(), listOf("vs")),
        suffixInflection("為よっか", "為る", listOf(), listOf("vs")),
        suffixInflection("こよっか", "くる", listOf(), listOf("vk")),
        suffixInflection("来よっか", "来る", listOf(), listOf("vk")),
        suffixInflection("來よっか", "來る", listOf(), listOf("vk")),
        suffixInflection("ましょっか", "ます", listOf(), listOf("-ます")),
    ),
)

private fun transform36(): TransformDescriptor = TransformDescriptor(
    id = "-まい",
    name = "-まい",
    rules = listOf(
        suffixInflection("まい", "", listOf(), listOf("v")),
        suffixInflection("まい", "る", listOf(), listOf("v1")),
        suffixInflection("じまい", "ずる", listOf(), listOf("vz")),
        suffixInflection("しまい", "する", listOf(), listOf("vs")),
        suffixInflection("為まい", "為る", listOf(), listOf("vs")),
        suffixInflection("こまい", "くる", listOf(), listOf("vk")),
        suffixInflection("来まい", "来る", listOf(), listOf("vk")),
        suffixInflection("來まい", "來る", listOf(), listOf("vk")),
        suffixInflection("まい", "", listOf(), listOf("-ます")),
    ),
)

private fun transform37(): TransformDescriptor = TransformDescriptor(
    id = "-おく",
    name = "-おく",
    rules = listOf(
        suffixInflection("ておく", "て", listOf("v5"), listOf("-て")),
        suffixInflection("でおく", "で", listOf("v5"), listOf("-て")),
        suffixInflection("とく", "て", listOf("v5"), listOf("-て")),
        suffixInflection("どく", "で", listOf("v5"), listOf("-て")),
        suffixInflection("ないでおく", "ない", listOf("v5"), listOf("adj-i")),
        suffixInflection("ないどく", "ない", listOf("v5"), listOf("adj-i")),
    ),
)

private fun transform38(): TransformDescriptor = TransformDescriptor(
    id = "-いる",
    name = "-いる",
    rules = listOf(
        suffixInflection("ている", "て", listOf("v1"), listOf("-て")),
        suffixInflection("てゐる", "て", listOf("v1"), listOf("-て")),
        suffixInflection("ておる", "て", listOf("v5"), listOf("-て")),
        suffixInflection("てる", "て", listOf("v1p"), listOf("-て")),
        suffixInflection("でいる", "で", listOf("v1"), listOf("-て")),
        suffixInflection("でゐる", "で", listOf("v1"), listOf("-て")),
        suffixInflection("でおる", "で", listOf("v5"), listOf("-て")),
        suffixInflection("でる", "で", listOf("v1p"), listOf("-て")),
        suffixInflection("とる", "て", listOf("v5"), listOf("-て")),
        suffixInflection("ないでいる", "ない", listOf("v1"), listOf("adj-i")),
        suffixInflection("ないでゐる", "ない", listOf("v1"), listOf("adj-i")),
    ),
)

private fun transform39(): TransformDescriptor = TransformDescriptor(
    id = "-ふ",
    name = "-ふ",
    rules = listOf(
        suffixInflection("ふ", "う", listOf(), listOf("v5")),
    ),
)

private fun transform40(): TransformDescriptor = TransformDescriptor(
    id = "-き",
    name = "-き",
    rules = listOf(
        suffixInflection("き", "い", listOf(), listOf("adj-i")),
    ),
)

private fun transform41(): TransformDescriptor = TransformDescriptor(
    id = "-げ",
    name = "-げ",
    rules = listOf(
        suffixInflection("げ", "い", listOf(), listOf("adj-i")),
        suffixInflection("気", "い", listOf(), listOf("adj-i")),
    ),
)

private fun transform42(): TransformDescriptor = TransformDescriptor(
    id = "-がる",
    name = "-がる",
    rules = listOf(
        suffixInflection("がる", "い", listOf("v5"), listOf("adj-i")),
    ),
)

private fun transform43(): TransformDescriptor = TransformDescriptor(
    id = "-やがる",
    name = "-やがる",
    rules = listOf(
        suffixInflection("やがる", "る", listOf("v5"), listOf("v1")),
        suffixInflection("いやがる", "う", listOf("v5"), listOf("v5")),
        suffixInflection("きやがる", "く", listOf("v5"), listOf("v5")),
        suffixInflection("ぎやがる", "ぐ", listOf("v5"), listOf("v5")),
        suffixInflection("しやがる", "す", listOf("v5"), listOf("v5")),
        suffixInflection("ちやがる", "つ", listOf("v5"), listOf("v5")),
        suffixInflection("にやがる", "ぬ", listOf("v5"), listOf("v5")),
        suffixInflection("びやがる", "ぶ", listOf("v5"), listOf("v5")),
        suffixInflection("みやがる", "む", listOf("v5"), listOf("v5")),
        suffixInflection("りやがる", "る", listOf("v5"), listOf("v5")),
        suffixInflection("じやがる", "ずる", listOf("v5"), listOf("vz")),
        suffixInflection("しやがる", "する", listOf("v5"), listOf("vs")),
        suffixInflection("為やがる", "為る", listOf("v5"), listOf("vs")),
        suffixInflection("きやがる", "くる", listOf("v5"), listOf("vk")),
        suffixInflection("来やがる", "来る", listOf("v5"), listOf("vk")),
        suffixInflection("來やがる", "來る", listOf("v5"), listOf("vk")),
    ),
)

private fun transform44(): TransformDescriptor = TransformDescriptor(
    id = "-え",
    name = "-え",
    rules = listOf(
        suffixInflection("ねえ", "ない", listOf(), listOf("adj-i")),
        suffixInflection("めえ", "むい", listOf(), listOf("adj-i")),
        suffixInflection("みい", "むい", listOf(), listOf("adj-i")),
        suffixInflection("ちぇえ", "つい", listOf(), listOf("adj-i")),
        suffixInflection("ちい", "つい", listOf(), listOf("adj-i")),
        suffixInflection("せえ", "すい", listOf(), listOf("adj-i")),
        suffixInflection("ええ", "いい", listOf(), listOf("adj-i")),
        suffixInflection("ええ", "わい", listOf(), listOf("adj-i")),
        suffixInflection("ええ", "よい", listOf(), listOf("adj-i")),
        suffixInflection("いぇえ", "よい", listOf(), listOf("adj-i")),
        suffixInflection("うぇえ", "わい", listOf(), listOf("adj-i")),
        suffixInflection("けえ", "かい", listOf(), listOf("adj-i")),
        suffixInflection("げえ", "がい", listOf(), listOf("adj-i")),
        suffixInflection("げえ", "ごい", listOf(), listOf("adj-i")),
        suffixInflection("せえ", "さい", listOf(), listOf("adj-i")),
        suffixInflection("めえ", "まい", listOf(), listOf("adj-i")),
        suffixInflection("ぜえ", "ずい", listOf(), listOf("adj-i")),
        suffixInflection("っぜえ", "ずい", listOf(), listOf("adj-i")),
        suffixInflection("れえ", "らい", listOf(), listOf("adj-i")),
        suffixInflection("れえ", "らい", listOf(), listOf("adj-i")),
        suffixInflection("ちぇえ", "ちゃい", listOf(), listOf("adj-i")),
        suffixInflection("でえ", "どい", listOf(), listOf("adj-i")),
        suffixInflection("れえ", "れい", listOf(), listOf("adj-i")),
        suffixInflection("べえ", "ばい", listOf(), listOf("adj-i")),
        suffixInflection("てえ", "たい", listOf(), listOf("adj-i")),
        suffixInflection("ねぇ", "ない", listOf(), listOf("adj-i")),
        suffixInflection("めぇ", "むい", listOf(), listOf("adj-i")),
        suffixInflection("みぃ", "むい", listOf(), listOf("adj-i")),
        suffixInflection("ちぃ", "つい", listOf(), listOf("adj-i")),
        suffixInflection("せぇ", "すい", listOf(), listOf("adj-i")),
        suffixInflection("けぇ", "かい", listOf(), listOf("adj-i")),
        suffixInflection("げぇ", "がい", listOf(), listOf("adj-i")),
        suffixInflection("げぇ", "ごい", listOf(), listOf("adj-i")),
        suffixInflection("せぇ", "さい", listOf(), listOf("adj-i")),
        suffixInflection("めぇ", "まい", listOf(), listOf("adj-i")),
        suffixInflection("ぜぇ", "ずい", listOf(), listOf("adj-i")),
        suffixInflection("っぜぇ", "ずい", listOf(), listOf("adj-i")),
        suffixInflection("れぇ", "らい", listOf(), listOf("adj-i")),
        suffixInflection("でぇ", "どい", listOf(), listOf("adj-i")),
        suffixInflection("れぇ", "れい", listOf(), listOf("adj-i")),
        suffixInflection("べぇ", "ばい", listOf(), listOf("adj-i")),
        suffixInflection("てぇ", "たい", listOf(), listOf("adj-i")),
    ),
)

private fun transform45(): TransformDescriptor = TransformDescriptor(
    id = "n-slang",
    name = "n-slang",
    rules = listOf(
        suffixInflection("んなさい", "りなさい", listOf(), listOf("-なさい")),
        suffixInflection("らんない", "られない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("んない", "らない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("んなきゃ", "らなきゃ", listOf(), listOf("-ゃ")),
        suffixInflection("んなきゃ", "れなきゃ", listOf(), listOf("-ゃ")),
    ),
)

private fun transform46(): TransformDescriptor = TransformDescriptor(
    id = "imperative negative slang",
    name = "imperative negative slang",
    rules = listOf(
        suffixInflection("んな", "る", listOf(), listOf("v")),
    ),
)

private fun transform47(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben negative",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("へん", "ない", listOf(), listOf("adj-i")),
        suffixInflection("ひん", "ない", listOf(), listOf("adj-i")),
        suffixInflection("せえへん", "しない", listOf(), listOf("adj-i")),
        suffixInflection("へんかった", "なかった", listOf("-た"), listOf("-た")),
        suffixInflection("ひんかった", "なかった", listOf("-た"), listOf("-た")),
        suffixInflection("うてへん", "ってない", listOf(), listOf("adj-i")),
    ),
)

private fun transform48(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben -て",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うて", "って", listOf("-て"), listOf("-て")),
        suffixInflection("おうて", "あって", listOf("-て"), listOf("-て")),
        suffixInflection("こうて", "かって", listOf("-て"), listOf("-て")),
        suffixInflection("ごうて", "がって", listOf("-て"), listOf("-て")),
        suffixInflection("そうて", "さって", listOf("-て"), listOf("-て")),
        suffixInflection("ぞうて", "ざって", listOf("-て"), listOf("-て")),
        suffixInflection("とうて", "たって", listOf("-て"), listOf("-て")),
        suffixInflection("どうて", "だって", listOf("-て"), listOf("-て")),
        suffixInflection("のうて", "なって", listOf("-て"), listOf("-て")),
        suffixInflection("ほうて", "はって", listOf("-て"), listOf("-て")),
        suffixInflection("ぼうて", "ばって", listOf("-て"), listOf("-て")),
        suffixInflection("もうて", "まって", listOf("-て"), listOf("-て")),
        suffixInflection("ろうて", "らって", listOf("-て"), listOf("-て")),
        suffixInflection("ようて", "やって", listOf("-て"), listOf("-て")),
        suffixInflection("ゆうて", "いって", listOf("-て"), listOf("-て")),
    ),
)

private fun transform49(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben -た",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うた", "った", listOf("-た"), listOf("-た")),
        suffixInflection("おうた", "あった", listOf("-た"), listOf("-た")),
        suffixInflection("こうた", "かった", listOf("-た"), listOf("-た")),
        suffixInflection("ごうた", "がった", listOf("-た"), listOf("-た")),
        suffixInflection("そうた", "さった", listOf("-た"), listOf("-た")),
        suffixInflection("ぞうた", "ざった", listOf("-た"), listOf("-た")),
        suffixInflection("とうた", "たった", listOf("-た"), listOf("-た")),
        suffixInflection("どうた", "だった", listOf("-た"), listOf("-た")),
        suffixInflection("のうた", "なった", listOf("-た"), listOf("-た")),
        suffixInflection("ほうた", "はった", listOf("-た"), listOf("-た")),
        suffixInflection("ぼうた", "ばった", listOf("-た"), listOf("-た")),
        suffixInflection("もうた", "まった", listOf("-た"), listOf("-た")),
        suffixInflection("ろうた", "らった", listOf("-た"), listOf("-た")),
        suffixInflection("ようた", "やった", listOf("-た"), listOf("-た")),
        suffixInflection("ゆうた", "いった", listOf("-た"), listOf("-た")),
    ),
)

private fun transform50(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben -たら",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うたら", "ったら", listOf(), listOf()),
        suffixInflection("おうたら", "あったら", listOf(), listOf()),
        suffixInflection("こうたら", "かったら", listOf(), listOf()),
        suffixInflection("ごうたら", "がったら", listOf(), listOf()),
        suffixInflection("そうたら", "さったら", listOf(), listOf()),
        suffixInflection("ぞうたら", "ざったら", listOf(), listOf()),
        suffixInflection("とうたら", "たったら", listOf(), listOf()),
        suffixInflection("どうたら", "だったら", listOf(), listOf()),
        suffixInflection("のうたら", "なったら", listOf(), listOf()),
        suffixInflection("ほうたら", "はったら", listOf(), listOf()),
        suffixInflection("ぼうたら", "ばったら", listOf(), listOf()),
        suffixInflection("もうたら", "まったら", listOf(), listOf()),
        suffixInflection("ろうたら", "らったら", listOf(), listOf()),
        suffixInflection("ようたら", "やったら", listOf(), listOf()),
        suffixInflection("ゆうたら", "いったら", listOf(), listOf()),
    ),
)

private fun transform51(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben -たり",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うたり", "ったり", listOf(), listOf()),
        suffixInflection("おうたり", "あったり", listOf(), listOf()),
        suffixInflection("こうたり", "かったり", listOf(), listOf()),
        suffixInflection("ごうたり", "がったり", listOf(), listOf()),
        suffixInflection("そうたり", "さったり", listOf(), listOf()),
        suffixInflection("ぞうたり", "ざったり", listOf(), listOf()),
        suffixInflection("とうたり", "たったり", listOf(), listOf()),
        suffixInflection("どうたり", "だったり", listOf(), listOf()),
        suffixInflection("のうたり", "なったり", listOf(), listOf()),
        suffixInflection("ほうたり", "はったり", listOf(), listOf()),
        suffixInflection("ぼうたり", "ばったり", listOf(), listOf()),
        suffixInflection("もうたり", "まったり", listOf(), listOf()),
        suffixInflection("ろうたり", "らったり", listOf(), listOf()),
        suffixInflection("ようたり", "やったり", listOf(), listOf()),
        suffixInflection("ゆうたり", "いったり", listOf(), listOf()),
    ),
)

private fun transform52(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben -く",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("う", "く", listOf(), listOf("-く")),
        suffixInflection("こう", "かく", listOf(), listOf("-く")),
        suffixInflection("ごう", "がく", listOf(), listOf("-く")),
        suffixInflection("そう", "さく", listOf(), listOf("-く")),
        suffixInflection("とう", "たく", listOf(), listOf("-く")),
        suffixInflection("のう", "なく", listOf(), listOf("-く")),
        suffixInflection("ぼう", "ばく", listOf(), listOf("-く")),
        suffixInflection("もう", "まく", listOf(), listOf("-く")),
        suffixInflection("ろう", "らく", listOf(), listOf("-く")),
        suffixInflection("よう", "よく", listOf(), listOf("-く")),
        suffixInflection("しゅう", "しく", listOf(), listOf("-く")),
    ),
)

private fun transform53(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben adjective -て",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うて", "くて", listOf("-て"), listOf("-て")),
        suffixInflection("こうて", "かくて", listOf("-て"), listOf("-て")),
        suffixInflection("ごうて", "がくて", listOf("-て"), listOf("-て")),
        suffixInflection("そうて", "さくて", listOf("-て"), listOf("-て")),
        suffixInflection("とうて", "たくて", listOf("-て"), listOf("-て")),
        suffixInflection("のうて", "なくて", listOf("-て"), listOf("-て")),
        suffixInflection("ぼうて", "ばくて", listOf("-て"), listOf("-て")),
        suffixInflection("もうて", "まくて", listOf("-て"), listOf("-て")),
        suffixInflection("ろうて", "らくて", listOf("-て"), listOf("-て")),
        suffixInflection("ようて", "よくて", listOf("-て"), listOf("-て")),
        suffixInflection("しゅうて", "しくて", listOf("-て"), listOf("-て")),
    ),
)

private fun transform54(): TransformDescriptor = TransformDescriptor(
    id = "kansai-ben adjective negative",
    name = "kansai-ben",
    rules = listOf(
        suffixInflection("うない", "くない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("こうない", "かくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("ごうない", "がくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("そうない", "さくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("とうない", "たくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("のうない", "なくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("ぼうない", "ばくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("もうない", "まくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("ろうない", "らくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("ようない", "よくない", listOf("adj-i"), listOf("adj-i")),
        suffixInflection("しゅうない", "しくない", listOf("adj-i"), listOf("adj-i")),
    ),
)

/**
 * The complete Japanese deinflection descriptor: condition lattice plus all
 * 55 transforms ported from upstream.
 */
val japaneseTransforms: LanguageTransformDescriptor = LanguageTransformDescriptor(
    language = "ja",
    conditions = japaneseConditions,
    transforms = listOf(
        transform0(),
        transform1(),
        transform2(),
        transform3(),
        transform4(),
        transform5(),
        transform6(),
        transform7(),
        transform8(),
        transform9(),
        transform10(),
        transform11(),
        transform12(),
        transform13(),
        transform14(),
        transform15(),
        transform16(),
        transform17(),
        transform18(),
        transform19(),
        transform20(),
        transform21(),
        transform22(),
        transform23(),
        transform24(),
        transform25(),
        transform26(),
        transform27(),
        transform28(),
        transform29(),
        transform30(),
        transform31(),
        transform32(),
        transform33(),
        transform34(),
        transform35(),
        transform36(),
        transform37(),
        transform38(),
        transform39(),
        transform40(),
        transform41(),
        transform42(),
        transform43(),
        transform44(),
        transform45(),
        transform46(),
        transform47(),
        transform48(),
        transform49(),
        transform50(),
        transform51(),
        transform52(),
        transform53(),
        transform54(),
    ),
)

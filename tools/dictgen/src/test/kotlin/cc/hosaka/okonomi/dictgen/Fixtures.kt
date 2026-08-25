package cc.hosaka.okonomi.dictgen

import java.io.File

object Fixtures {

    const val JMDICT_DATE = "2026-08-21"

    val jmdict = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE JMdict [
        <!ENTITY v1 "Ichidan verb">
        <!ENTITY vt "transitive verb">
        <!ENTITY uk "word usually written using kana alone">
        <!ENTITY food "food, cooking">
        <!ENTITY ksb "Kansai-ben">
        ]>
        <JMdict>
        <!-- JMdict created: $JMDICT_DATE -->
        <entry>
        <ent_seq>1358280</ent_seq>
        <k_ele>
        <keb>食べる</keb>
        <ke_pri>ichi1</ke_pri>
        <ke_pri>news2</ke_pri>
        <ke_pri>nf25</ke_pri>
        </k_ele>
        <r_ele>
        <reb>たべる</reb>
        <re_pri>ichi1</re_pri>
        <re_pri>news2</re_pri>
        <re_pri>nf25</re_pri>
        </r_ele>
        <r_ele>
        <reb>タベル</reb>
        <re_nokanji/>
        <re_restr>食べる</re_restr>
        </r_ele>
        <sense>
        <pos>&v1;</pos>
        <pos>&vt;</pos>
        <field>&food;</field>
        <dial>&ksb;</dial>
        <stagk>食べる</stagk>
        <gloss>to eat</gloss>
        <gloss xml:lang="ger">essen</gloss>
        <example>
        <ex_srce exsrc_type="tat">303697</ex_srce>
        <ex_text>食べる</ex_text>
        <ex_sent xml:lang="jpn">早く食べる</ex_sent>
        <ex_sent xml:lang="eng">Eat quickly.</ex_sent>
        </example>
        </sense>
        <sense>
        <misc>&uk;</misc>
        <s_inf>colloquial</s_inf>
        <stagr>たべる</stagr>
        <stagr>タベル</stagr>
        <gloss>to live on</gloss>
        </sense>
        </entry>
        </JMdict>
    """.trimIndent()

    val jmnedict = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE JMnedict [
        <!ENTITY fem "female given name or forename">
        ]>
        <JMnedict>
        <entry>
        <ent_seq>5000002</ent_seq>
        <k_ele>
        <keb>〆ヱ</keb>
        </k_ele>
        <r_ele>
        <reb>しめえ</reb>
        </r_ele>
        <trans>
        <name_type>&fem;</name_type>
        <trans_det>Shimee</trans_det>
        </trans>
        </entry>
        </JMnedict>
    """.trimIndent()

    val kanjidic = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kanjidic2>
        <header><file_version>4</file_version></header>
        <character>
        <literal>食</literal>
        <codepoint><cp_value cp_type="ucs">98df</cp_value></codepoint>
        <radical><rad_value rad_type="classical">184</rad_value></radical>
        <misc>
        <grade>2</grade>
        <stroke_count>9</stroke_count>
        <stroke_count>10</stroke_count>
        <freq>328</freq>
        <jlpt>3</jlpt>
        </misc>
        <reading_meaning>
        <rmgroup>
        <reading r_type="ja_on">ショク</reading>
        <reading r_type="pinyin">shi2</reading>
        <reading r_type="ja_kun">た.べる</reading>
        <meaning>eat</meaning>
        <meaning m_lang="fr">manger</meaning>
        </rmgroup>
        <nanori>ぐい</nanori>
        </reading_meaning>
        </character>
        </kanjidic2>
    """.trimIndent()

    val radk = """
        #
        # test radkfile
        #
        ${'$'} 一 1
        食倉
        ${'$'} 人 2
        食
    """.trimIndent()

    /**
     * Five Japanese sentences covering the ordering rules: sentence 5 is
     * inside the readable length band and must lead; 1 and 2 are both
     * below it and the same length, so the tilde on 1 is the only thing
     * that can separate them; 4 is referenced by no usable index row.
     */
    val jpnSentences = """
        1	jpn	早く食べる。
        2	jpn	何を食べる。
        3	jpn	犬が寝る。
        4	jpn	使われない。
        5	jpn	私は毎日パンを食べる。
    """.trimIndent()

    val engSentences = """
        10	eng	Eat quickly.
        11	eng	What will you eat?
        12	eng	The dog sleeps.
        13	eng	I eat bread every day.
        99	eng	Referenced by nothing.
    """.trimIndent()

    /**
     * Row 3's words are in no entry, so its sentence keeps no link and
     * is pruned; row 4 names eng_id 0 ("no translation") and row 5 a
     * Japanese sentence that does not exist, so neither survives.
     */
    val jpnIndices = """
        1	10	早く{早く} 食べる~
        2	11	何(なに) を 食べる{食べる}
        3	12	犬(いぬ) が 寝る
        4	0	使う{使われない}
        404	11	何(なに)
        5	13	私(わたし) は 毎日 パン を 食べる
    """.trimIndent()

    fun writeDataDir(dir: File) {
        dir.mkdirs()
        File(dir, "JMdict_e.xml").writeText(jmdict)
        File(dir, "JMnedict.xml").writeText(jmnedict)
        File(dir, "kanjidic2.xml").writeText(kanjidic)
        File(dir, "radkfile").writeBytes(radk.toByteArray(charset("EUC-JP")))
        File(dir, "jpn_sentences.tsv").writeText(jpnSentences)
        File(dir, "eng_sentences.tsv").writeText(engSentences)
        File(dir, "jpn_indices.csv").writeText(jpnIndices)
    }
}

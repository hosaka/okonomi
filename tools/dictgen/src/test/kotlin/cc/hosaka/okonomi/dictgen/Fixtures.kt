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

    fun writeDataDir(dir: File) {
        dir.mkdirs()
        File(dir, "JMdict_e_examp.xml").writeText(jmdict)
        File(dir, "JMnedict.xml").writeText(jmnedict)
        File(dir, "kanjidic2.xml").writeText(kanjidic)
        File(dir, "radkfile").writeBytes(radk.toByteArray(charset("EUC-JP")))
    }
}

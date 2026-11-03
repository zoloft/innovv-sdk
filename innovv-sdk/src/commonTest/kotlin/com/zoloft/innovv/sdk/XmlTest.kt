package com.zoloft.innovv.sdk

import com.zoloft.innovv.sdk.internal.Xml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XmlTest {
    @Test
    fun readsSimpleTags() {
        val xml = "<Function><Cmd>3016</Cmd><Status>0</Status></Function>"
        assertEquals(3016, Xml.intTag(xml, "Cmd"))
        assertEquals(0, Xml.intTag(xml, "Status"))
    }

    @Test
    fun stripsCdata() {
        val xml = "<SSID><![CDATA[K5 Camera]]></SSID>"
        assertEquals("K5 Camera", Xml.textTag(xml, "SSID"))
    }

    @Test
    fun decodesEntities() {
        val xml = "<Name>A &amp; B &lt;K5&gt;</Name>"
        assertEquals("A & B <K5>", Xml.textTag(xml, "Name"))
    }

    @Test
    fun readsAllBlocks() {
        val xml = """
            <LIST>
            <File><NAME>a.mp4</NAME></File>
            <File><NAME>b.jpg</NAME></File>
            </LIST>
        """.trimIndent()
        assertEquals(2, Xml.blocks(xml, "File").size)
    }

    @Test
    fun missingTagIsNull() {
        assertNull(Xml.textTag("<Function/>", "Value"))
    }
}

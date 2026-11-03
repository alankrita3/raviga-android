package com.raviga.app.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

/** Review #22 (Android): numeric character references in Word XML read as whole characters; a bad one is skipped. */
class WordReferencesTest {
    @Test fun anEmojiPastTheBasicPlaneIsWhole() {
        assertEquals("Cake 🎂 orders", PlainFormats.unescapeXml("Cake &#x1F382; orders"))
        assertEquals("Cake 🎂 orders", PlainFormats.unescapeXml("Cake &#127874; orders"))
    }

    @Test fun ordinaryReferencesStillWork() {
        assertEquals("café & <b>", PlainFormats.unescapeXml("caf&#xE9; &amp; &lt;b&gt;"))
    }

    @Test fun aReferenceThatIsNoCharacterIsDroppedAndTheRestKept() {
        assertEquals("before  after", PlainFormats.unescapeXml("before &#99999999999; after"))
        assertEquals("a b", PlainFormats.unescapeXml("a &#x110000;b"))
        assertEquals("a b", PlainFormats.unescapeXml("a &#xD800;b"))
    }

    @Test fun aWholeDocumentWithABadReferenceStillReads() {
        val xml = "<w:p><w:r><w:t>Bakery app &#99999999999;</w:t></w:r></w:p><w:p><w:r><w:t>Orders by phone</w:t></w:r></w:p>"
        assertEquals("Bakery app \nOrders by phone\n", PlainFormats.docxXml(xml))
    }
}

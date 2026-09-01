package com.itantra

import com.itantra.domain.model.AlertTemplate
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.IndicLanguage
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelUnitTest {

    @Test
    fun testIndicLanguageFromCode() {
        assertEquals(IndicLanguage.HINDI, IndicLanguage.fromCode("hi"))
        assertEquals(IndicLanguage.TAMIL, IndicLanguage.fromCode("ta"))
        assertEquals(IndicLanguage.TELUGU, IndicLanguage.fromCode("te"))
        assertEquals(IndicLanguage.BENGALI, IndicLanguage.fromCode("bn"))
        assertEquals(IndicLanguage.GUJARATI, IndicLanguage.fromCode("gu"))
        assertEquals(IndicLanguage.MARATHI, IndicLanguage.fromCode("mr"))
        assertEquals(IndicLanguage.KANNADA, IndicLanguage.fromCode("kn"))
        assertEquals(IndicLanguage.MALAYALAM, IndicLanguage.fromCode("ml"))
        assertEquals(IndicLanguage.ODIA, IndicLanguage.fromCode("or"))
        assertEquals(IndicLanguage.ENGLISH, IndicLanguage.fromCode("en"))
        // Fallback check
        assertEquals(IndicLanguage.HINDI, IndicLanguage.fromCode("unknown_code"))
    }

    @Test
    fun testAlertTemplatesContainRequiredTranslations() {
        for (template in AlertTemplate.entries) {
            assertNotNull(template.displayName)
            assertTrue(template.templateText.containsKey("en"))
            assertTrue(template.templateText.containsKey("hi"))
            val hindiText = template.templateText["hi"]
            assertNotNull(hindiText)
            assertFalse(hindiText!!.isBlank())
        }
    }

    @Test
    fun testTransceiverMessageCreation() {
        val msg = TransceiverMessage(
            type = MessageType.SPEECH,
            text = "नमस्ते, यह एक परीक्षण संदेश है",
            srcLang = "hi",
            dstLang = "ta",
            senderId = "node-alpha-101",
            timestamp = 1700000000000L,
            confidence = 0.94f,
            sequence = 1,
            direction = Direction.SENT
        )

        assertEquals(MessageType.SPEECH, msg.type)
        assertEquals("नमस्ते, यह एक परीक्षण संदेश है", msg.text)
        assertEquals("hi", msg.srcLang)
        assertEquals("ta", msg.dstLang)
        assertEquals(0.94f, msg.confidence, 0.001f)
        assertEquals(Direction.SENT, msg.direction)
    }

    @Test
    fun testPeerDeviceState() {
        val peer = PeerDevice(
            id = "bt-peer-01",
            name = "Tactical-Node-2",
            address = "AA:BB:CC:DD:EE:FF",
            connectionType = ConnectionType.BLUETOOTH,
            isConnected = true,
            rssi = -65,
            batteryPercent = 88
        )

        assertTrue(peer.isConnected)
        assertEquals(ConnectionType.BLUETOOTH, peer.connectionType)
        assertEquals(-65, peer.rssi)
        assertEquals(88, peer.batteryPercent)
    }
}

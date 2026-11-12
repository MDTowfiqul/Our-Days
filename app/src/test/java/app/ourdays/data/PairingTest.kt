package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen android.util.Base64
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairingTest {
    @Test
    fun pairingFileRoundTripKeepsKey() {
        val original = Pairing.create("p1")
        val text = Pairing.pairingFileText(original)
        assertEquals(original.key.toList(), Pairing.keyFromQr(text)?.toList())
    }

    @Test
    fun pairingFileNameHasPairingExtension() {
        assertEquals("our-days-pairing.ourpair", Pairing.pairingFileName())
    }

    @Test
    fun isPairingFileDetectsBothPrefixes() {
        assertTrue(Pairing.isPairingFile("ourdays:pair:1:AAAA".toByteArray(Charsets.US_ASCII)))
        assertTrue(Pairing.isPairingFile("sinceus:pair:1:AAAA".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun isPairingFileRejectsSyncFiles() {
        assertEquals(false, Pairing.isPairingFile("SINCEUS-SYNC1".toByteArray(Charsets.US_ASCII)))
        assertEquals(false, Pairing.isPairingFile(ByteArray(0)))
        assertEquals(false, Pairing.isPairingFile("hello".toByteArray(Charsets.US_ASCII)))
    }
}

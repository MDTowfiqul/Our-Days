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
    fun qrTextForOtherRelationshipCarriesItsId() {
        val original = Pairing.create("p1")
        val (key, relationshipId) = Pairing.fromQr(Pairing.qrText(original, "r2"))!!
        assertEquals(original.key.toList(), key.toList())
        assertEquals("r2", relationshipId)
    }

    @Test
    fun mainQrHasNoSuffixAndReadsAsMain() {
        val original = Pairing.create("p1")
        val (key, relationshipId) = Pairing.fromQr(Pairing.qrText(original))!!
        assertEquals(original.key.toList(), key.toList())
        assertEquals(PeopleCodec.MAIN_ID, relationshipId)
    }

    @Test
    fun pairingFileNameIncludesSlug() {
        assertEquals("our-days-pairing-a-b.ourpair", Pairing.pairingFileName("A & B"))
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

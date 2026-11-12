package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen android.util.Base64
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairingTest {
    @Test
    fun qrTextRoundTripKeepsKey() {
        val original = Pairing.create("p1")
        val text = Pairing.qrText(original)
        assertEquals(original.key.toList(), Pairing.keyFromQr(text)?.toList())
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
}

package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen org.json (in reinen JVM-Tests nur als Attrappe vorhanden)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumTest {

    @Test
    fun entitlementCodecRoundtripAndBadInput() {
        val original = PremiumEntitlement("GPA.1234-5678", 1723456789000, "play")
        assertEquals(original, PremiumEntitlementCodec.decode(PremiumEntitlementCodec.encode(original)))
        assertNull(PremiumEntitlementCodec.decode(null))
        assertNull(PremiumEntitlementCodec.decode(""))
        assertNull(PremiumEntitlementCodec.decode("kein json"))
        assertNull(PremiumEntitlementCodec.decode("""{"at":5,"src":"play"}"""))
    }

    @Test
    fun unlockCodeRoundtripAndFormat() {
        val code = PremiumUnlockCode.codeFromOrderId("GPA.1234-5678-9012")
        assertTrue(code.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")))
        val payload = PremiumUnlockCode.parse(code)
        assertNotNull(payload)
        // Dieselbe Bestellung ergibt denselben Code, der Code zeigt sich selbst wieder
        assertEquals(code, PremiumUnlockCode.codeFromOrderId("GPA.1234-5678-9012"))
        assertEquals(code, PremiumUnlockCode.codeFromPayload(payload!!))
        assertEquals(code, PremiumUnlockCode.displayCode("GPA.1234-5678-9012"))
        // Ein per Code freigeschaltetes Handy zeigt denselben Code wieder, keinen neuen
        assertEquals(code, PremiumUnlockCode.displayCode("code:$payload"))
    }

    @Test
    fun unlockCodeAcceptsTypingVariantsAndRejectsTampering() {
        val code = PremiumUnlockCode.codeFromOrderId("GPA.aaaa")
        assertNotNull(PremiumUnlockCode.parse(code.lowercase()))
        assertNotNull(PremiumUnlockCode.parse(code.replace("-", " ")))
        assertNotNull(PremiumUnlockCode.parse("  $code  "))
        assertNull(PremiumUnlockCode.parse("SHORT"))
        assertNull(PremiumUnlockCode.parse(code.dropLast(1) + "X"))
        val tampered = code.toCharArray().also { it[0] = if (it[0] == 'A') 'B' else 'A' }.concatToString()
        assertNull(PremiumUnlockCode.parse(tampered))
    }

    @Test
    fun differentOrdersGiveDifferentCodes() {
        assertNotEquals(
            PremiumUnlockCode.codeFromOrderId("GPA.1111"),
            PremiumUnlockCode.codeFromOrderId("GPA.2222"),
        )
    }

    @Test
    fun mergePremiumNeverDowngrades() {
        val old = PremiumEntitlement("GPA.1", 1000, "play")
        val new = PremiumEntitlement("GPA.2", 2000, "play")
        assertEquals(new, mergePremium(old, new))
        assertEquals(new, mergePremium(null, new))
        assertEquals(old, mergePremium(old, null))
        assertNull(mergePremium(null, null))
        // Gleichstand: die lokale Fassung bleibt (stabil, keine Flatter)
        assertEquals(old, mergePremium(old, old.copy(source = "sync")))
    }
}

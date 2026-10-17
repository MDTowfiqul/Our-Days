package app.ourdays.cloud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CloudCryptoTest {
    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        CloudCrypto.encrypt(ByteArrayInputStream(data), out, password)
        return out.toByteArray()
    }

    @Test
    fun roundTrip() {
        val data = "Hallo Ainon 🌙 – unsere Momente".toByteArray(Charsets.UTF_8)
        val dec = ByteArrayOutputStream()
        CloudCrypto.decrypt(ByteArrayInputStream(encrypt(data, "geheim123")), dec, "geheim123")
        assertArrayEquals(data, dec.toByteArray())
    }

    @Test
    fun wrongPasswordFails() {
        val enc = encrypt("wichtig".toByteArray(), "richtig")
        try {
            CloudCrypto.decrypt(ByteArrayInputStream(enc), ByteArrayOutputStream(), "falsch")
            fail("expected exception for wrong password")
        } catch (_: Exception) {
        }
    }

    @Test
    fun saltsDiffer() {
        val a = encrypt("gleich".toByteArray(), "pw")
        val b = encrypt("gleich".toByteArray(), "pw")
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun truncatedFails() {
        val enc = encrypt("daten".toByteArray(), "pw")
        try {
            CloudCrypto.decrypt(
                ByteArrayInputStream(enc.copyOf(4)),
                ByteArrayOutputStream(),
                "pw",
            )
            fail("expected exception for truncated input")
        } catch (_: Exception) {
        }
    }
}

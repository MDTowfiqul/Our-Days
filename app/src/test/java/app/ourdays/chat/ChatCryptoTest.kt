package app.ourdays.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen org.json und android.util.Base64
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatCryptoTest {
    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    private fun message(text: String = "Hallo Ainon 🌙 – wie war dein Tag?") =
        ChatMessage(id = "m1", from = "person-a", at = 1_760_000_000_000L, text = text)

    @Test
    fun codecRoundTripKeepsFields() {
        val original = message()
        val decoded = ChatMessage.fromJson(original.toJson())
        assertEquals(original, decoded)
    }

    @Test
    fun codecUnknownVersionFails() {
        assertNull(ChatMessage.fromJson("""{"v":99,"id":"x","at":1,"text":"hi"}"""))
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val original = message()
        val decoded = ChatCrypto.decrypt(key, ChatCrypto.encrypt(key, original))
        assertEquals(original, decoded)
    }

    @Test
    fun encryptedTextHasPrefixAndHidesPlaintext() {
        val cipher = ChatCrypto.encrypt(key, message())
        assertTrue(cipher.startsWith(ChatCrypto.PREFIX))
        assertTrue(!cipher.contains("Hallo Ainon"))
    }

    @Test
    fun wrongKeyFails() {
        val cipher = ChatCrypto.encrypt(key, message())
        assertNull(ChatCrypto.decrypt(otherKey, cipher))
    }

    @Test
    fun tamperedCipherFails() {
        val cipher = ChatCrypto.encrypt(key, message())
        val tampered = cipher.dropLast(2) + if (cipher.last() == 'A') 'B' else 'A'
        assertNull(ChatCrypto.decrypt(key, tampered))
    }

    @Test
    fun nonChatTextIsIgnored() {
        assertNull(ChatCrypto.decrypt(key, "just a normal message"))
        assertNull(ChatCrypto.decrypt(key, ""))
    }

    @Test
    fun sameMessageEncryptsDifferently() {
        val a = ChatCrypto.encrypt(key, message())
        val b = ChatCrypto.encrypt(key, message())
        assertNotEquals(a, b)
    }
}

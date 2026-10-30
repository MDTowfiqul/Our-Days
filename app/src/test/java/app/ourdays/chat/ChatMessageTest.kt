package app.ourdays.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen org.json und android.util.Base64
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatMessageTest {
    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun v1TextStillReadsAsText() {
        val parsed = ChatMessage.fromJson("""{"v":1,"id":"old","from":"p1","at":1000,"text":"hello"}""")
        assertEquals(ChatMessage("old", "p1", 1000L, "hello"), parsed)
    }

    @Test
    fun v2MediaRoundTripKeepsFields() {
        val original = ChatMessage("m9", "p2", 2000L, "beach day", ChatMessage.KIND_PHOTO, "m9.jpg", "image/jpeg")
        assertEquals(original, ChatMessage.fromJson(original.toJson()))
    }

    @Test
    fun unknownVersionFails() {
        assertNull(ChatMessage.fromJson("""{"v":99,"id":"x","at":1,"text":"hi"}"""))
    }

    @Test
    fun mediaMetaEncryptDecryptRoundTrip() {
        val meta = ChatMediaMeta("m9", "p2", 2000L, ChatMessage.KIND_VIDEO, "our trip", "m9.mp4", "video/mp4")
        val sealed = ChatCrypto.encryptMeta(key, meta)
        assertEquals(meta, ChatCrypto.decryptMeta(key, sealed))
    }

    @Test
    fun mediaMetaWrongKeyFails() {
        val meta = ChatMediaMeta("m9", "p2", 2000L, ChatMessage.KIND_PHOTO, "", "m9.jpg", "image/jpeg")
        val sealed = ChatCrypto.encryptMeta(key, meta)
        assertNull(ChatCrypto.decryptMeta(otherKey, sealed))
        assertNull(ChatCrypto.decryptMeta(key, "not a chat caption"))
    }
}

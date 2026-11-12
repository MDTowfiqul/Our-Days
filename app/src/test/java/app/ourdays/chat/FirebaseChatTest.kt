package app.ourdays.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// Robolectric wegen org.json, Base64 und SharedPreferences
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseChatTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun messageCodecRoundtrip() {
        val fields = FirebaseChat.encodeMessageFields("our-days-chat abc", 1700000000000L, "me-1")
        val doc = JSONObject()
            .put("name", "projects/p/databases/(default)/documents/rooms/r1/messages/msg-1")
            .put("fields", fields.getJSONObject("fields"))
        val back = FirebaseChat.decodeMessage(doc)
        assertNotNull(back)
        assertEquals("msg-1", back!!.id)
        assertEquals("our-days-chat abc", back.payload)
        assertEquals(1700000000000L, back.at)
    }

    @Test
    fun messageCodecRejectsGarbage() {
        assertNull(FirebaseChat.decodeMessage(JSONObject()))
        assertNull(FirebaseChat.decodeMessage(JSONObject().put("name", "x")))
    }

    @Test
    fun newerThanQueryAsksOnlyForNewerMessages() {
        val body = JSONObject(FirebaseChat.newerThanQueryBody("our-days-767b2", "room-1", 1700000000000L))
        assertEquals(
            "projects/our-days-767b2/databases/(default)/documents/rooms/room-1",
            body.getString("parent"),
        )
        val q = body.getJSONObject("structuredQuery")
        assertEquals("messages", q.getJSONArray("from").getJSONObject(0).getString("collectionId"))
        val filter = q.getJSONObject("where").getJSONObject("fieldFilter")
        assertEquals("at", filter.getJSONObject("field").getString("fieldPath"))
        assertEquals("GREATER_THAN", filter.getString("op"))
        assertEquals("1700000000000", filter.getJSONObject("value").getString("integerValue"))
        val order = q.getJSONArray("orderBy").getJSONObject(0)
        assertEquals("at", order.getJSONObject("field").getString("fieldPath"))
        assertEquals("DESCENDING", order.getString("direction"))
        assertEquals(100, q.getInt("limit"))
    }

    @Test
    fun runQueryAnswersParse() {
        val doc = JSONObject()
            .put("name", "projects/p/databases/(default)/documents/rooms/r1/messages/msg-1")
            .put("fields", FirebaseChat.encodeMessageFields("payload-1", 1700000001000L, "me-1").getJSONObject("fields"))
        val text = org.json.JSONArray()
            .put(JSONObject().put("document", doc).put("readTime", "2026-11-12T00:00:00Z"))
            .put(JSONObject().put("readTime", "2026-11-12T00:00:00Z"))
            .toString()
        val back = FirebaseChat.parseRunQuery(text)
        assertEquals(1, back.size)
        assertEquals("msg-1", back[0].id)
        assertEquals("payload-1", back[0].payload)
        assertEquals(1700000001000L, back[0].at)
        assertTrue(FirebaseChat.parseRunQuery("not json").isEmpty())
        assertTrue(FirebaseChat.parseRunQuery("[]").isEmpty())
    }

    @Test
    fun codesAreSixDigits() {
        repeat(200) {
            val code = FirebaseChat.generateCode()
            assertTrue(FirebaseChat.isValidCode(code))
            assertEquals(6, code.length)
            assertTrue(code.toInt() in 100000..999999)
        }
        assertFalse(FirebaseChat.isValidCode("12345"))
        assertFalse(FirebaseChat.isValidCode("1234567"))
        assertFalse(FirebaseChat.isValidCode("12345a"))
        assertFalse(FirebaseChat.isValidCode(""))
    }

    @Test
    fun quotaErrorsAreRecognised() {
        assertTrue(FirebaseChat.errorFor(429, "{}") is FirebaseChat.QuotaExceeded)
        assertTrue(
            FirebaseChat.errorFor(
                403,
                """{"error":{"code":403,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded"}}""",
            ) is FirebaseChat.QuotaExceeded,
        )
        val other = FirebaseChat.errorFor(404, """{"error":{"code":404,"status":"NOT_FOUND","message":"nope"}}""")
        assertFalse(other is FirebaseChat.QuotaExceeded)
        assertTrue(other.message!!.contains("404"))
    }

    @Test
    fun channelIdComesFromChannelPostsOnly() {
        val root = JSONObject().put(
            "result",
            org.json.JSONArray(
                listOf(
                    JSONObject().put("update_id", 1).put(
                        "channel_post",
                        JSONObject().put("message_id", 10)
                            .put("chat", JSONObject().put("id", -1001234567890L).put("type", "channel")),
                    ),
                    JSONObject().put("update_id", 2).put(
                        "message",
                        JSONObject().put("message_id", 11)
                            .put("chat", JSONObject().put("id", 12345L).put("type", "private")),
                    ),
                ),
            ),
        )
        assertEquals("-1001234567890", TelegramChat.parseChannelId(root))
        assertNull(TelegramChat.parseChannelId(JSONObject()))
        val noChannel = JSONObject().put(
            "result",
            org.json.JSONArray(
                listOf(
                    JSONObject().put("update_id", 1).put(
                        "message",
                        JSONObject().put("message_id", 11)
                            .put("chat", JSONObject().put("id", 12345L).put("type", "private")),
                    ),
                ),
            ),
        )
        assertNull(TelegramChat.parseChannelId(noChannel))
    }

    @Test
    fun telegramTextCapFitsThe4096Limit() {
        // Worst case: a full-length sender id in the JSON. 2,800 plain
        // characters must fit; the old 4,000 cap did not (payload > 4,096).
        val longId = UUID.randomUUID().toString()
        val short = ChatMessage(UUID.randomUUID().toString(), longId, 1L, "x".repeat(2800))
        assertTrue(ChatCrypto.encrypt(key, short).length <= 4096)
        val tooLong = ChatMessage(UUID.randomUUID().toString(), longId, 1L, "x".repeat(4000))
        assertTrue(ChatCrypto.encrypt(key, tooLong).length > 4096)
    }

    @Test
    fun mediaCapIsTheReceiveLimit() {
        assertEquals(20 * 1024 * 1024, ChatViewModel.MAX_MEDIA_BYTES)
    }

    @Test
    fun configDefaultsToTelegramAndRoundtripsFirebase() {
        ChatConfigStore.clear(context)
        val fresh = ChatConfigStore.load(context)
        assertEquals(ChatTransport.TELEGRAM, fresh.transport)
        assertFalse(fresh.isConfigured)

        val firebase = ChatConfig(transport = ChatTransport.FIREBASE, roomId = "room-1", enabled = true)
        ChatConfigStore.save(context, firebase)
        val back = ChatConfigStore.load(context)
        assertEquals(ChatTransport.FIREBASE, back.transport)
        assertEquals("room-1", back.roomId)
        assertTrue(back.isConfigured)

        // A Telegram config without a room is only configured with token + channel.
        val telegram = ChatConfig(botToken = "t", chatId = "-1001", enabled = true)
        ChatConfigStore.save(context, telegram)
        val back2 = ChatConfigStore.load(context)
        assertEquals(ChatTransport.TELEGRAM, back2.transport)
        assertTrue(back2.isConfigured)
        ChatConfigStore.clear(context)
    }
}

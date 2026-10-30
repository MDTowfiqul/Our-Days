package app.ourdays.chat

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric wegen org.json
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelegramChatParseTest {
    private fun update(id: Int, message: JSONObject?): JSONObject {
        val u = JSONObject().put("update_id", id)
        if (message != null) u.put("channel_post", message)
        return u
    }

    @Test
    fun parsesChannelPostsAndSkipsOthers() {
        val root = JSONObject().put(
            "result",
            JSONArray(
                listOf(
                update(1, JSONObject().put("message_id", 10).put("date", 1000L).put("text", "our-days-chat abc")),
                update(2, JSONObject().put("message_id", 11).put("date", 1001L).put("text", "hello, not a chat message")),
                update(3, JSONObject().put("message_id", 12).put("date", 1002L).put("document", JSONObject())),
                update(4, JSONObject().put("message_id", 9).put("date", 999L).put("text", "our-days-chat xyz")),
                ),
            ),
        )
        val parsed = TelegramChat.parseUpdates(root)
        assertEquals(listOf(9L, 10L), parsed.map { it.messageId })
        assertEquals("our-days-chat xyz", parsed[0].payload)
        assertEquals(999L, parsed[0].dateSeconds)
    }

    @Test
    fun parsesDirectMessagesToo() {
        val root = JSONObject().put(
            "result",
            JSONArray(
                listOf(
                    JSONObject().put("update_id", 1).put(
                    "message",
                    JSONObject().put("message_id", 5).put("date", 2000L).put("text", "our-days-chat direct"),
                    ),
                ),
            ),
        )
        val parsed = TelegramChat.parseUpdates(root)
        assertEquals(1, parsed.size)
        assertEquals(5L, parsed[0].messageId)
    }

    @Test
    fun emptyResultParsesToEmpty() {
        assertEquals(emptyList<TelegramChat.RawChat>(), TelegramChat.parseUpdates(JSONObject()))
        assertEquals(
            emptyList<TelegramChat.RawChat>(),
            TelegramChat.parseUpdates(JSONObject().put("result", JSONArray())),
        )
    }

    @Test
    fun parsesMediaDocumentsWithChatCaption() {
        val media = JSONObject()
            .put("message_id", 20)
            .put("date", 3000L)
            .put("caption", "our-days-chat meta-envelope")
            .put(
                "document",
                JSONObject().put("file_id", "file-1").put("file_name", "abc.jpg").put("mime_type", "image/jpeg"),
            )
        val notChat = JSONObject()
            .put("message_id", 21)
            .put("date", 3001L)
            .put("caption", "a normal caption")
            .put("document", JSONObject().put("file_id", "file-2"))
        val noCaption = JSONObject()
            .put("message_id", 22)
            .put("date", 3002L)
            .put("document", JSONObject().put("file_id", "file-3"))
        val root = JSONObject().put("result", JSONArray(listOf(update(5, media), update(6, notChat), update(7, noCaption))))
        val parsed = TelegramChat.parseMedia(root)
        assertEquals(1, parsed.size)
        assertEquals("file-1", parsed[0].fileId)
        assertEquals("abc.jpg", parsed[0].fileName)
        assertEquals("image/jpeg", parsed[0].mimeType)
        assertEquals("our-days-chat meta-envelope", parsed[0].captionPayload)
        assertEquals(5L, parsed[0].updateId)
    }

    @Test
    fun maxUpdateIdTracksHighest() {
        val root = JSONObject().put(
            "result",
            JSONArray(
                listOf(
                    update(3, null),
                    update(9, JSONObject().put("message_id", 1).put("text", "our-days-chat x")),
                    update(4, null),
                ),
            ),
        )
        assertEquals(9L, TelegramChat.maxUpdateId(root))
        assertEquals(0L, TelegramChat.maxUpdateId(JSONObject()))
    }
}

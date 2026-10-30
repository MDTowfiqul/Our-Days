package app.ourdays.chat

import org.json.JSONObject
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
            listOf(
                update(1, JSONObject().put("message_id", 10).put("date", 1000L).put("text", "our-days-chat abc")),
                update(2, JSONObject().put("message_id", 11).put("date", 1001L).put("text", "hello, not a chat message")),
                update(3, JSONObject().put("message_id", 12).put("date", 1002L).put("document", JSONObject())),
                update(4, JSONObject().put("message_id", 9).put("date", 999L).put("text", "our-days-chat xyz")),
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
            listOf(
                JSONObject().put("update_id", 1).put(
                    "message",
                    JSONObject().put("message_id", 5).put("date", 2000L).put("text", "our-days-chat direct"),
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
            TelegramChat.parseUpdates(JSONObject().put("result", listOf<JSONObject>())),
        )
    }
}

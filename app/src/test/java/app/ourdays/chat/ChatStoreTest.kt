package app.ourdays.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric für Context/Dateien
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun mergeDedupesAndSorts() {
        ChatStore.clear(context)
        val first = ChatMessage("a", "p1", 2000L, "second")
        val second = ChatMessage("b", "p2", 1000L, "first")
        val merged = ChatStore.merge(context, listOf(first, second))
        assertEquals(listOf("b", "a"), merged.map { it.id })
        // Erneut einspielen ändert nichts
        val again = ChatStore.merge(context, listOf(first))
        assertEquals(listOf("b", "a"), again.map { it.id })
        assertEquals(merged, ChatStore.load(context))
    }

    @Test
    fun emptyStoreLoadsEmpty() {
        ChatStore.clear(context)
        assertEquals(emptyList<ChatMessage>(), ChatStore.load(context))
    }

    @Test
    fun editEventReplacesTextAndMarksEdited() {
        ChatStore.clear(context)
        ChatStore.merge(context, listOf(
            ChatMessage("a", "p1", 1000L, "first"),
            ChatMessage("b", "p2", 2000L, "second"),
        ))
        val event = ChatMessage("e1", "p1", 3000L, "first, fixed", ChatMessage.KIND_EDIT, targetId = "a")
        val updated = ChatStore.applyEvents(context, listOf(event))
        assertEquals(listOf("a", "b"), updated.map { it.id })
        assertEquals("first, fixed", updated[0].text)
        assertEquals(true, updated[0].edited)
        assertEquals("second", updated[1].text)
        assertEquals(false, updated[1].edited)
        // The event itself is never stored as a message
        assertEquals(updated, ChatStore.load(context))
    }

    @Test
    fun deleteEventRemovesMessage() {
        ChatStore.clear(context)
        ChatStore.merge(context, listOf(
            ChatMessage("a", "p1", 1000L, "first"),
            ChatMessage("b", "p2", 2000L, "second"),
        ))
        val event = ChatMessage("e2", "p2", 3000L, "", ChatMessage.KIND_DELETE, targetId = "a")
        val updated = ChatStore.applyEvents(context, listOf(event))
        assertEquals(listOf("b"), updated.map { it.id })
    }

    @Test
    fun editOfMissingOrMediaMessageDoesNothing() {
        ChatStore.clear(context)
        ChatStore.merge(context, listOf(
            ChatMessage("a", "p1", 1000L, "photo caption", ChatMessage.KIND_PHOTO, "a.jpg", "image/jpeg"),
        ))
        val events = listOf(
            ChatMessage("e1", "p1", 3000L, "new caption", ChatMessage.KIND_EDIT, targetId = "a"),
            ChatMessage("e2", "p1", 3000L, "ghost", ChatMessage.KIND_EDIT, targetId = "missing"),
        )
        val updated = ChatStore.applyEvents(context, events)
        assertEquals("photo caption", updated[0].text)
        assertEquals(false, updated[0].edited)
    }
}

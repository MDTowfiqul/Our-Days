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
}

package app.ourdays.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

// Robolectric wegen org.json
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FunTest {

    @Test
    fun surpriseCodecRoundTrip() {
        val list = listOf(
            Surprise(relationshipId = "main", occasion = SurpriseOccasions.PROPOSAL, message = "Will you marry me?", from = "a", createdAt = 1000L),
            Surprise(relationshipId = "main", occasion = SurpriseOccasions.EID, message = "Eid Mubarak!", from = null, createdAt = 2000L, revealed = true),
        )
        assertEquals(list, SurpriseCodec.decode(SurpriseCodec.encode(list)))
    }

    @Test
    fun surpriseCodecIgnoresGarbage() {
        assertTrue(SurpriseCodec.decode(null).isEmpty())
        assertTrue(SurpriseCodec.decode("kein json").isEmpty())
        assertTrue(SurpriseCodec.decode("[]").isEmpty())
        // ohne Nachricht oder ohne Beziehung fällt der Eintrag weg
        assertTrue(SurpriseCodec.decode("""[{"id":"x","relationship":"main","occasion":"birthday","message":"  "}]""").isEmpty())
        // unbekannter Anlass wird zu „einfach so“
        val one = SurpriseCodec.decode("""[{"id":"x","relationship":"main","occasion":"falsch","message":"Hi"}]""")
        assertEquals(1, one.size)
        assertEquals(SurpriseOccasions.JUST_BECAUSE, one[0].occasion)
    }

    @Test
    fun mapCodecsRoundTrip() {
        val longs = mapOf("main" to 20330L, "r2" to 20000L)
        assertEquals(longs, LongMapCodec.decode(LongMapCodec.encode(longs)))
        val ints = mapOf("main" to 3, "r2" to 12)
        assertEquals(ints, IntMapCodec.decode(IntMapCodec.encode(ints)))
        assertTrue(LongMapCodec.decode("kaputt").isEmpty())
        assertTrue(IntMapCodec.decode(null).isEmpty())
    }

    @Test
    fun missSpanCountsDaysHoursMinutesSeconds() {
        val span = missSpan(LocalDate.of(2026, 11, 15), LocalDateTime.of(2026, 11, 18, 10, 30, 45))
        assertEquals(3L, span.days)
        assertEquals(10L, span.hours)
        assertEquals(30L, span.minutes)
        assertEquals(45L, span.seconds)
    }

    @Test
    fun missSpanNeverNegative() {
        val span = missSpan(LocalDate.of(2026, 11, 20), LocalDateTime.of(2026, 11, 18, 0, 0, 0))
        assertEquals(MissSpan(0, 0, 0, 0), span)
    }

    @Test
    fun syncCodecKeepsSurprises() {
        val data = SyncData(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            from = "a",
            surprises = listOf(
                Surprise(relationshipId = "main", occasion = "birthday", message = "Happy birthday!", from = "a"),
            ),
        )
        assertEquals(data.surprises, SyncCodec.decode(SyncCodec.encode(data)).surprises)
        // alte Datei ohne Überraschungen
        val old = SyncCodec.decode("""{"app":"app.ourdays","format":1,"people":[],"relationships":[],"moments":[],"deleted":{}}""")
        assertTrue(old.surprises.isEmpty())
        // der Absender steht in der Datei und füllt leere „from“ bei Überraschungen
        val withFrom = SyncCodec.decode(
            """{"app":"app.ourdays","format":1,"from":"a","people":[],"relationships":[],"moments":[],"deleted":{},"surprises":[{"id":"s1","relationship":"main","occasion":"birthday","message":"Happy!","from":null}]}""",
        )
        assertEquals("a", withFrom.surprises.firstOrNull()?.from)
    }

    @Test
    fun syncMergeUnionsSurprises() {
        val local = Surprise(relationshipId = "main", occasion = "eid", message = "Eid Mubarak", from = "a", createdAt = 1000L)
        val remote = local.copy(revealed = true)
        val other = Surprise(relationshipId = "main", occasion = "birthday", message = "Happy!", from = "b", createdAt = 2000L)
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = SyncData(emptyList(), emptyList(), emptyList(), emptyMap(), "b", surprises = listOf(remote, other)),
            surprises = listOf(local),
        )
        assertEquals(2, result.surprises.size)
        // geöffnet ist geöffnet, egal auf welchem Handy
        assertTrue(result.surprises.first { it.id == local.id }.revealed)
        assertEquals(listOf(local.id, other.id), result.surprises.map { it.id })
    }
}

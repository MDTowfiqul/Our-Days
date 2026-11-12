package app.ourdays.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val day1 = LocalDate.of(2026, 11, 12)
    private val day2 = LocalDate.of(2020, 5, 11)

    private fun remote(people: List<Person>, relationships: List<Relationship>) =
        SyncData(people, relationships, emptyList(), emptyMap(), null)

    @Test
    fun remotePersonOverwritesLocalNameWithSameId() {
        val result = SyncMerge.merge(
            people = listOf(Person("p1", "A"), Person("p2", "B")),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote(
                listOf(Person("p1", "Towfiqul"), Person("p2", "Ainon")),
                emptyList(),
            ),
        )
        assertEquals(listOf("Towfiqul", "Ainon"), result.people.map { it.name })
    }

    @Test
    fun remotePersonKeepsLocalBirthdayWhenRemoteHasNone() {
        val result = SyncMerge.merge(
            people = listOf(Person("p1", "A", birthday = day2)),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote(listOf(Person("p1", "Towfiqul")), emptyList()),
        )
        assertEquals(day2, result.people.single().birthday)
    }

    @Test
    fun localOnlyAndRemoteOnlyPeopleAreBothKept() {
        val result = SyncMerge.merge(
            people = listOf(Person("p1", "A")),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote(listOf(Person("p2", "B")), emptyList()),
        )
        assertEquals(listOf("B", "A"), result.people.map { it.name })
    }

    @Test
    fun remoteRelationshipOverwritesLocalStartDateWithSameId() {
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote(
                emptyList(),
                listOf(Relationship("r1", listOf("p1", "p2"), day2)),
            ),
        )
        assertEquals(day2, result.relationships.single().startDate)
    }

    @Test
    fun localOnlyRelationshipsAreKept() {
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = remote(
                emptyList(),
                listOf(Relationship("r2", listOf("p1", "p3"), day2)),
            ),
        )
        assertEquals(listOf("r2", "r1"), result.relationships.map { it.id })
    }

    @Test
    fun deletedPersonDisappearsAndMarkerIsKept() {
        val now = System.currentTimeMillis()
        val result = SyncMerge.merge(
            people = listOf(Person("p1", "A"), Person("p2", "B")),
            relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = SyncData(
                people = listOf(Person("p1", "A")),
                relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
                moments = emptyList(),
                deleted = emptyMap(),
                from = null,
                deletedPeople = mapOf("p2" to now),
            ),
        )
        assertEquals(listOf("p1"), result.people.map { it.id })
        assertTrue("p2" in result.deletedPeople)
    }

    @Test
    fun deletedRelationshipDisappears() {
        val now = System.currentTimeMillis()
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = listOf(
                Relationship("r1", listOf("p1", "p2"), day1),
                Relationship("r2", listOf("p1", "p3"), day2),
            ),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = SyncData(
                people = emptyList(),
                relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
                moments = emptyList(),
                deleted = emptyMap(),
                from = null,
                deletedRelationships = mapOf("r2" to now),
            ),
        )
        assertEquals(listOf("r1"), result.relationships.map { it.id })
        assertTrue("r2" in result.deletedRelationships)
    }

    @Test
    fun atLeastOneRelationshipAlwaysRemains() {
        val now = System.currentTimeMillis()
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = listOf(Relationship("r1", listOf("p1", "p2"), day1)),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = SyncData(
                people = emptyList(),
                relationships = emptyList(),
                moments = emptyList(),
                deleted = emptyMap(),
                from = null,
                deletedRelationships = mapOf("r1" to now),
            ),
        )
        assertEquals(listOf("r1"), result.relationships.map { it.id })
    }

    @Test
    fun deletedMarkersAreUnioned() {
        val result = SyncMerge.merge(
            people = emptyList(),
            relationships = emptyList(),
            moments = emptyList(),
            deleted = emptyMap(),
            remote = SyncData(
                people = emptyList(),
                relationships = emptyList(),
                moments = emptyList(),
                deleted = emptyMap(),
                from = null,
                deletedPeople = mapOf("p8" to 200L),
            ),
            deletedPeople = mapOf("p9" to 100L),
        )
        assertEquals(setOf("p8", "p9"), result.deletedPeople.keys)
    }
}

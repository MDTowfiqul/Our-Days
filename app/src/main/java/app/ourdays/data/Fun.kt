package app.ourdays.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Hosted plan page (GitHub Pages, Our-Days repo docs/plan/): the app shares a link there */
const val PLAN_PAGE_BASE_URL = "https://mdtowfiqul.github.io/Our-Days/plan/"

/**
 * Link to the hosted plan page. Params: kind=date|trip, names, date (ISO),
 * time (HH:mm), menu (date) or dest (trip), msg optional. Blank values are
 * left out; every value is URL-encoded so Bengali names and "&" survive.
 */
fun buildPlanLink(
    kind: String,
    names: String,
    date: LocalDate?,
    time: LocalTime?,
    menu: String?,
    destination: String?,
    message: String? = null,
): String {
    val params = listOfNotNull(
        "kind" to kind,
        "names" to names,
        "date" to date?.toString(),
        "time" to time?.toString(),
        "menu" to menu,
        "dest" to destination,
        "msg" to message,
    ).filter { it.second?.isNotBlank() == true }
    return buildString {
        append(PLAN_PAGE_BASE_URL)
        append("?")
        append(
            params.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
            },
        )
    }
}

/**
 * Überraschung für die andere Person: Sie reist in der Abgleich-Datei mit und
 * wird dort verspielt geöffnet (ausweichender Nein-Knopf, dann die Nachricht).
 * Angelegt wird sie auf dem Handy, das sie schickt; geöffnet auf dem anderen.
 */
data class Surprise(
    val id: String = UUID.randomUUID().toString(),
    val relationshipId: String,
    /** proposal, birthday, eid, anniversary, just_because */
    val occasion: String,
    val message: String,
    /** ID der Person, die sie geschickt hat, null = unbekannt */
    val from: String?,
    val createdAt: Long = System.currentTimeMillis(),
    /** true, sobald sie auf irgendeinem Handy geöffnet wurde */
    val revealed: Boolean = false,
)

object SurpriseOccasions {
    const val PROPOSAL = "proposal"
    const val BIRTHDAY = "birthday"
    const val EID = "eid"
    const val ANNIVERSARY = "anniversary"
    const val JUST_BECAUSE = "just_because"
    /** Eigener Anlass: steht nur in der Auswahl, gespeichert wird der getippte Name */
    const val CUSTOM = "custom"
    val ALL = listOf(PROPOSAL, BIRTHDAY, EID, ANNIVERSARY, JUST_BECAUSE)
}

object SurpriseCodec {
    fun encode(list: List<Surprise>): String = JSONArray().apply {
        list.forEach { s ->
            put(
                JSONObject().apply {
                    put("id", s.id)
                    put("relationship", s.relationshipId)
                    put("occasion", s.occasion)
                    put("message", s.message)
                    put("from", s.from ?: JSONObject.NULL)
                    put("createdAt", s.createdAt)
                    put("revealed", s.revealed)
                },
            )
        }
    }.toString()

    fun decode(json: String?): List<Surprise> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { null } ?: return@mapNotNull null
            val relationshipId = o.optString("relationship").ifBlank { null } ?: return@mapNotNull null
            val occasion = o.optString("occasion").trim().take(40).ifBlank { null }
                ?: SurpriseOccasions.JUST_BECAUSE
            val message = o.optString("message").trim().take(500).ifBlank { null } ?: return@mapNotNull null
            Surprise(
                id = id,
                relationshipId = relationshipId,
                occasion = occasion,
                message = message,
                // „from“:null steht in der Datei — optString liefert dafür je nach org.json
                // den Text "null" statt "", also erst isNull prüfen
                from = if (o.isNull("from")) null else o.optString("from").ifBlank { null },
                createdAt = o.optLong("createdAt"),
                revealed = o.optBoolean("revealed", false),
            )
        }
    }
}

/** Zeit seit dem letzten Treffen, tickt jede Sekunde (vom Tagesanfang des Datums an) */
data class MissSpan(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

fun missSpan(lastMet: LocalDate, now: LocalDateTime): MissSpan {
    val total = ChronoUnit.SECONDS.between(lastMet.atStartOfDay(), now).coerceAtLeast(0)
    return MissSpan(total / 86400, (total % 86400) / 3600, (total % 3600) / 60, total % 60)
}

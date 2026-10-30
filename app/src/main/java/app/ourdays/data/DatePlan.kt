package app.ourdays.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * A planned date: something the couple wants to do together, as opposed to a
 * [Moment] (something that already happened) or an anniversary (computed from
 * the relationship start). Date plans are their own thing: they have a
 * countdown, their own reminders, and they sync between the paired phones
 * like moments do.
 */
data class DatePlan(
    val id: String,
    val title: String,
    val date: LocalDate,
    /** Time of day, null = all day */
    val time: LocalTime? = null,
    val note: String = "",
    /** Place, empty = none given */
    val place: String = "",
    /** Advance reminder in days, 0 = only on the day itself (values from REMIND_OPTIONS) */
    val remindBefore: Int = 0,
    /** Belongs to this relationship, null = all */
    val relationshipId: String? = null,
    /** Last change in milliseconds, decides on merge which version wins */
    val updatedAt: Long = 0,
    /** Who created the plan (ID of a [Person]), null = unknown */
    val addedBy: String? = null,
)

object DatePlanCodec {
    fun encode(plans: List<DatePlan>): String = JSONArray().apply {
        plans.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id)
                    .put("title", p.title)
                    .put("date", p.date.toEpochDay())
                    .put("time", p.time?.toSecondOfDay() ?: -1)
                    .put("note", p.note)
                    .put("place", p.place)
                    .put("remind", p.remindBefore)
                    .put("rel", p.relationshipId ?: JSONObject.NULL)
                    .put("updated", p.updatedAt)
                    .put("by", p.addedBy ?: JSONObject.NULL),
            )
        }
    }.toString()

    /** Unreadable entries are skipped instead of losing all date plans. */
    fun decode(json: String?): List<DatePlan> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                DatePlan(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    date = LocalDate.ofEpochDay(o.getLong("date")),
                    time = o.optInt("time", -1).takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) },
                    note = o.optString("note", ""),
                    place = o.optString("place", ""),
                    remindBefore = o.optInt("remind", 0).takeIf { it in REMIND_OPTIONS } ?: 0,
                    relationshipId = if (o.isNull("rel")) null else o.optString("rel").ifBlank { null },
                    updatedAt = o.optLong("updated", 0),
                    addedBy = if (o.isNull("by")) null else o.optString("by").ifBlank { null },
                )
            }.getOrNull()
        }
    }
}

object DatePlanMath {
    /** Upcoming plans from [today] (inclusive), soonest first. */
    fun upcoming(plans: List<DatePlan>, today: LocalDate, count: Int = 4): List<DatePlan> =
        plans.filter { !it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MIN }))
            .take(count)

    /** The next plan, null when nothing is planned. */
    fun next(plans: List<DatePlan>, today: LocalDate): DatePlan? = upcoming(plans, today, 1).firstOrNull()

    /** Past plans, most recent first. */
    fun past(plans: List<DatePlan>, today: LocalDate): List<DatePlan> =
        plans.filter { it.date.isBefore(today) }
            .sortedWith(compareByDescending<DatePlan> { it.date }.thenByDescending { it.time ?: LocalTime.MIN })

    /** Days until the plan: positive = in the future, 0 = today, negative = past. */
    fun daysUntil(plan: DatePlan, today: LocalDate): Long = ChronoUnit.DAYS.between(today, plan.date)

    /** Plans on [day]. */
    fun dueOn(plans: List<DatePlan>, day: LocalDate): List<DatePlan> = plans.filter { it.date == day }

    /** Days a reminder is due: the day itself, plus the advance day when set. */
    fun reminderDays(plan: DatePlan): List<LocalDate> = buildList {
        add(plan.date)
        if (plan.remindBefore > 0) add(plan.date.minusDays(plan.remindBefore.toLong()))
    }

    /** Plans with a reminder on [day] (day itself or their advance day). */
    fun remindersOn(plans: List<DatePlan>, day: LocalDate): List<DatePlan> =
        plans.filter { day in reminderDays(it) }
}

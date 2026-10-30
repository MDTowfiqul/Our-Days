package app.ourdays.data

import android.content.Context
import app.ourdays.AppLanguage
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Couples quizzes for Our Entertainment. Content lives here with both languages side
 * by side, so an English and a Bengali question can never drift apart (unlike parallel
 * string arrays). UI chrome still comes from strings.xml.
 */

// ---- Question sets ----

/** "How well do you know me": one partner answers about themselves, the other guesses. */
data class KnowMeQuestion(
    val id: String,
    val en: String,
    val bn: String,
    val optionsEn: List<String>,
    val optionsBn: List<String>,
) {
    fun text(bengali: Boolean): String = if (bengali) bn else en
    fun options(bengali: Boolean): List<String> = if (bengali) optionsBn else optionsEn
}

/** Would-you-rather: both partners answer, then compare. */
data class WrQuestion(
    val id: String,
    val en: String,
    val bn: String,
    val aEn: String,
    val aBn: String,
    val bEn: String,
    val bBn: String,
) {
    fun text(bengali: Boolean): String = if (bengali) bn else en
    fun a(bengali: Boolean): String = if (bengali) aBn else aEn
    fun b(bengali: Boolean): String = if (bengali) bBn else bEn
}

val KNOW_ME_QUESTIONS = listOf(
    KnowMeQuestion(
        "ideal_weekend", "My ideal weekend is…", "আমার আদর্শ ছুটির দিন কাটে…",
        listOf("Beach day", "Mountain trip", "Exploring a city", "Quiet day at home"),
        listOf("সমুদ্রসৈকতে", "পাহাড়ে ঘুরে", "শহর ঘুরে", "বাড়িতে শান্তিতে"),
    ),
    KnowMeQuestion(
        "awake", "I feel most awake…", "আমি সবচেয়ে চনমনে থাকি…",
        listOf("Early morning", "Late morning", "Afternoon", "Late at night"),
        listOf("খুব ভোরে", "বেলা করে", "বিকেলে", "গভীর রাতে"),
    ),
    KnowMeQuestion(
        "comfort_food", "My comfort food is…", "আমার প্রিয় আরামের খাবার…",
        listOf("Home-cooked food", "Biryani", "Noodles", "Sweets"),
        listOf("ঘরের রান্না", "বিরিয়ানি", "নুডলস", "মিষ্টি"),
    ),
    KnowMeQuestion(
        "film_night", "My favourite film night is…", "আমার পছন্দের সিনেমার রাত…",
        listOf("Comedy", "Romance", "Action", "Documentary"),
        listOf("কমেডি", "রোমান্টিক", "অ্যাকশন", "তথ্যচিত্র"),
    ),
    KnowMeQuestion(
        "weather", "My favourite weather is…", "আমার পছন্দের আবহাওয়া…",
        listOf("Sunny and warm", "Rainy", "Cool and breezy", "Winter cold"),
        listOf("রোদেলা গরম", "বৃষ্টি", "ঠান্ডা হাওয়া", "শীতের ঠান্ডা"),
    ),
    KnowMeQuestion(
        "dream_trip", "My dream trip would be…", "আমার স্বপ্নের ভ্রমণ…",
        listOf("Beach island", "Mountains", "Historic city", "Quiet village"),
        listOf("সমুদ্রদ্বীপ", "পাহাড়", "ঐতিহাসিক শহর", "শান্ত গ্রাম"),
    ),
    KnowMeQuestion(
        "music", "Music I reach for…", "আমি যে গান শুনতে পছন্দ করি…",
        listOf("Nasheeds", "Pop", "Classical", "Lo-fi beats"),
        listOf("নাশিদ", "পপ", "ক্লাসিক্যাল", "লো-ফাই"),
    ),
    KnowMeQuestion(
        "gift", "The gift I love most…", "যে উপহার আমার সবচেয়ে ভালো লাগে…",
        listOf("A handwritten letter", "Flowers", "Something useful", "Something handmade"),
        listOf("হাতে লেখা চিঠি", "ফুল", "কাজের জিনিস", "হাতে তৈরি জিনিস"),
    ),
    KnowMeQuestion(
        "free_hour", "With one free hour I would…", "এক ঘণ্টা ফাঁকা পেলে আমি…",
        listOf("Read a book", "Take a walk", "Call a loved one", "Just rest"),
        listOf("বই পড়ব", "হাঁটতে যাব", "প্রিয়জনকে ফোন করব", "শুধু বিশ্রাম নেব"),
    ),
    KnowMeQuestion(
        "celebrate", "I celebrate best by…", "আমি সবচেয়ে ভালো উদযাপন করি…",
        listOf("Big gathering", "Quiet dinner for two", "A small trip", "Praying together"),
        listOf("বড় আয়োজন করে", "দুজনে শান্ত ডিনারে", "ছোট ভ্রমণে", "একসাথে নামাজ পড়ে"),
    ),
)

val WR_QUESTIONS = listOf(
    WrQuestion("tea_coffee", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Tea", "চা", "Coffee", "কফি"),
    WrQuestion("beach_mountains", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Beach", "সমুদ্রসৈকত", "Mountains", "পাহাড়"),
    WrQuestion("early_late", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Wake up early", "ভোরে ওঠা", "Stay up late", "রাত জাগা"),
    WrQuestion("book_film", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Read a book", "বই পড়া", "Watch a film", "সিনেমা দেখা"),
    WrQuestion("city_village", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Live in a city", "শহরে থাকা", "Live in a village", "গ্রামে থাকা"),
    WrQuestion("cook_order", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Cook at home", "বাড়িতে রান্না", "Order food", "খাবার অর্ডার"),
    WrQuestion("rain_sun", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Rainy day", "বৃষ্টির দিন", "Sunny day", "রোদের দিন"),
    WrQuestion("walk_drive", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Walk", "হাঁটা", "Drive", "গাড়িতে যাওয়া"),
    WrQuestion("call_text", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Phone call", "ফোন কল", "Text message", "মেসেজ"),
    WrQuestion("sweet_salty", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Something sweet", "মিষ্টি কিছু", "Something salty", "নোনতা কিছু"),
    WrQuestion("plan_spontaneous", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "Plan everything", "সব পরিকল্পনা করে", "Be spontaneous", "হঠাৎ করে"),
    WrQuestion("gift_letter", "Would you rather…", "তুমি কোনটা বেছে নেবে…", "A gift", "উপহার", "A handwritten letter", "হাতে লেখা চিঠি"),
)

// ---- Daily challenge ----

/**
 * The daily challenge: five would-you-rather questions, picked by the date alone.
 * Both phones run the same app version over the same ordered pool with the same
 * seed, so both get the identical set with no server at all. Compare answers after
 * both have answered (answers travel in the sync file).
 */
object DailyChallenge {
    const val COUNT = 5

    fun questionsFor(date: LocalDate): List<WrQuestion> =
        WR_QUESTIONS.shuffled(Random(date.toEpochDay())).take(COUNT)
}

// ---- Quiz records (travel in the sync file under "quiz") ----

/** One partner's own answers to the know-me set. */
data class KnowMeSheet(val owner: String, val answers: Map<String, Int>, val updatedAt: Long)

/** A guess at a partner's know-me sheet. */
data class KnowMeAttempt(val sheetOwner: String, val guesser: String, val answers: Map<String, Int>, val updatedAt: Long)

/** One partner's would-you-rather answers for one date (ISO date string). */
data class WrAnswers(val date: String, val player: String, val answers: Map<String, Int>, val updatedAt: Long)

data class QuizSyncData(
    val sheets: List<KnowMeSheet> = emptyList(),
    val attempts: List<KnowMeAttempt> = emptyList(),
    val wr: List<WrAnswers> = emptyList(),
)

object QuizCodec {
    fun encode(q: QuizSyncData): String {
        fun answersToJson(answers: Map<String, Int>): JSONObject =
            JSONObject().apply { answers.forEach { (id, choice) -> put(id, choice) } }
        return JSONObject()
            .put("sheets", JSONArray().apply {
                q.sheets.forEach { s ->
                    put(JSONObject().put("owner", s.owner).put("answers", answersToJson(s.answers)).put("updatedAt", s.updatedAt))
                }
            })
            .put("attempts", JSONArray().apply {
                q.attempts.forEach { a ->
                    put(JSONObject().put("sheetOwner", a.sheetOwner).put("guesser", a.guesser).put("answers", answersToJson(a.answers)).put("updatedAt", a.updatedAt))
                }
            })
            .put("wr", JSONArray().apply {
                q.wr.forEach { w ->
                    put(JSONObject().put("date", w.date).put("player", w.player).put("answers", answersToJson(w.answers)).put("updatedAt", w.updatedAt))
                }
            })
            .toString()
    }

    fun decode(json: String?): QuizSyncData {
        if (json.isNullOrBlank()) return QuizSyncData()
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return QuizSyncData()
        fun answersFromJson(obj: JSONObject?): Map<String, Int> {
            if (obj == null) return emptyMap()
            return obj.keys().asSequence().associateWith { obj.optInt(it) }
        }
        fun sheets(): List<KnowMeSheet> {
            val arr = o.optJSONArray("sheets") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val s = arr.getJSONObject(i)
                    KnowMeSheet(s.getString("owner"), answersFromJson(s.optJSONObject("answers")), s.getLong("updatedAt"))
                }.getOrNull()
            }
        }
        fun attempts(): List<KnowMeAttempt> {
            val arr = o.optJSONArray("attempts") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val a = arr.getJSONObject(i)
                    KnowMeAttempt(a.getString("sheetOwner"), a.getString("guesser"), answersFromJson(a.optJSONObject("answers")), a.getLong("updatedAt"))
                }.getOrNull()
            }
        }
        fun wr(): List<WrAnswers> {
            val arr = o.optJSONArray("wr") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val w = arr.getJSONObject(i)
                    WrAnswers(w.getString("date"), w.getString("player"), answersFromJson(w.optJSONObject("answers")), w.getLong("updatedAt"))
                }.getOrNull()
            }
        }
        return QuizSyncData(sheets(), attempts(), wr())
    }
}

/** Merge: per record key, the newer updatedAt wins. */
object QuizMerge {
    fun merge(local: QuizSyncData, remote: QuizSyncData): QuizSyncData {
        fun <T> latest(localList: List<T>, remoteList: List<T>, key: (T) -> String, time: (T) -> Long): List<T> {
            val byKey = localList.associateBy(key).toMutableMap()
            remoteList.forEach { r ->
                val old = byKey[key(r)]
                if (old == null || time(r) > time(old)) byKey[key(r)] = r
            }
            return byKey.values.toList()
        }
        return QuizSyncData(
            sheets = latest(local.sheets, remote.sheets, { it.owner }, { it.updatedAt }),
            attempts = latest(local.attempts, remote.attempts, { "${it.sheetOwner}:${it.guesser}" }, { it.updatedAt }),
            wr = latest(local.wr, remote.wr, { "${it.date}:${it.player}" }, { it.updatedAt }),
        )
    }
}

// ---- Scoring ----

/** Know-me score: how many guesses match the sheet. */
fun knowMeScore(sheet: KnowMeSheet, attempt: KnowMeAttempt): Int =
    KNOW_ME_QUESTIONS.count { q -> sheet.answers[q.id] != null && sheet.answers[q.id] == attempt.answers[q.id] }

/** Would-you-rather agreement: how many answers match across the questions both answered. */
fun wrAgreement(a: WrAnswers, b: WrAnswers): Int =
    a.answers.entries.count { (id, choice) -> b.answers[id] == choice }

// ---- Language ----

/** True when quiz content should show in Bengali (app language, else system language). */
fun quizIsBengali(context: Context): Boolean {
    val tag = AppLanguage.current(context)
    return when (tag) {
        "bn" -> true
        "en" -> false
        else -> Locale.getDefault().language == "bn"
    }
}

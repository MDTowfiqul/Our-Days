package app.ourdays.chat

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Our Chat over Firebase (Firestore REST API, no Firebase SDK).
 *
 * Why REST instead of the SDK: the app already talks to Telegram and Drive
 * over plain REST with okhttp, the F-Droid build must stay free of Google
 * libraries, and the SDK would pull Play services into every flavor. The
 * only config is assets/firebase.json (project id + public api key — the key
 * is not a secret; the Firestore rules decide what a signed-in phone may do).
 *
 * Privacy: messages are sealed with the pairing key before they leave the
 * phone (ChatCrypto), so Firestore only ever sees ciphertext. The room id is
 * a random UUID; the 6-digit pairing code is the only way to find it, and the
 * code is deleted as soon as the partner joins. Text only — photos and videos
 * stay on the Telegram transport, because Cloud Storage is not on Firebase's
 * free Spark plan (Blaze-only since Feb 2026).
 *
 * Limits (Spark free plan, shared across every couple using the app):
 * 50,000 reads / 20,000 writes / 20,000 deletes a day, 1 GiB stored. A
 * message is 1 write + 2 reads, so the plan covers about 20,000 messages a
 * day. When the quota is hit Firestore answers 429 RESOURCE_EXHAUSTED and
 * the app says so plainly instead of failing silently. Messages older than
 * 90 days are deleted on refresh so the 1 GiB never fills up.
 */
object FirebaseChat {
    /** Thrown when the shared daily quota is exhausted; the UI says so plainly. */
    class QuotaExceeded : IOException("firebase daily limit reached")

    data class FirebaseConfig(val projectId: String, val apiKey: String)

    /** One message document as stored in rooms/{roomId}/messages/{id}. */
    data class RemoteMessage(val id: String, val payload: String, val at: Long)

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val PREFS = "our_chat_firebase"
    private const val CODE_TTL_MS = 24L * 3600 * 1000
    const val RETENTION_MS = 90L * 24 * 3600 * 1000
    private const val MAX_FETCH = 500
    /** The old-message prune runs at most this often (see pruneIfDue). */
    private const val PRUNE_INTERVAL_MS = 24L * 3600 * 1000

    @Volatile
    private var cachedConfig: FirebaseConfig? = null
    @Volatile
    private var configChecked = false

    fun loadConfig(context: Context): FirebaseConfig? {
        if (configChecked) return cachedConfig
        cachedConfig = runCatching {
            context.assets.open("firebase.json").use { it.readBytes().toString(Charsets.UTF_8) }
                .let { JSONObject(it) }
                .let { FirebaseConfig(it.getString("project_id"), it.getString("api_key")) }
        }.getOrNull()
        configChecked = true
        return cachedConfig
    }

    fun isAvailable(context: Context): Boolean = loadConfig(context) != null

    // ---- pairing codes (pure, unit-tested) ----

    /** 6 digits, first digit never 0, so the code reads the same everywhere. */
    fun generateCode(random: SecureRandom = SecureRandom()): String =
        (100000 + random.nextInt(900000)).toString()

    fun isValidCode(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }

    // ---- Firestore document codec (pure, unit-tested) ----

    fun encodeMessageFields(payload: String, at: Long, from: String?): JSONObject = JSONObject()
        .put("fields", JSONObject()
            .put("data", JSONObject().put("stringValue", payload))
            .put("at", JSONObject().put("integerValue", at.toString()))
            .put("from", JSONObject().put("stringValue", from ?: "")))

    fun decodeMessage(doc: JSONObject): RemoteMessage? = runCatching {
        val name = doc.getString("name")
        val id = name.substringAfterLast('/')
        val fields = doc.getJSONObject("fields")
        RemoteMessage(
            id = id,
            payload = fields.getJSONObject("data").getString("stringValue"),
            at = fields.getJSONObject("at").getString("integerValue").toLong(),
        )
    }.getOrNull()

    /** 429, or a RESOURCE_EXHAUSTED error body, means the shared daily quota is gone. */
    fun errorFor(httpCode: Int, body: String): IOException {
        val status = runCatching { JSONObject(body).optJSONObject("error")?.optString("status") }.getOrNull()
        if (httpCode == 429 || status == "RESOURCE_EXHAUSTED") return QuotaExceeded()
        val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        return IOException("firebase http $httpCode${if (message.isNullOrBlank()) "" else ": $message"}")
    }

    // ---- auth (anonymous sign-in, token kept in prefs) ----

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storeToken(context: Context, idToken: String, refreshToken: String, expiresInSec: Long) {
        prefs(context).edit()
            .putString("id_token", idToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() + expiresInSec * 1000)
            .apply()
    }

    private fun signUp(context: Context, config: FirebaseConfig): String {
        val body = JSONObject().put("returnSecureToken", true).toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${config.apiKey}")
            .post(body).build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body!!.string()
            if (!resp.isSuccessful) throw errorFor(resp.code, text)
            val json = JSONObject(text)
            storeToken(context, json.getString("idToken"), json.getString("refreshToken"), json.getLong("expiresIn"))
            json.getString("idToken")
        }
    }

    private fun refreshToken(context: Context, config: FirebaseConfig): String {
        val stored = prefs(context).getString("refresh_token", "").orEmpty()
        if (stored.isBlank()) return signUp(context, config)
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", stored).build()
        val req = Request.Builder()
            .url("https://securetoken.googleapis.com/v1/token?key=${config.apiKey}")
            .post(body).build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body!!.string()
            if (!resp.isSuccessful) return signUp(context, config)
            val json = JSONObject(text)
            storeToken(context, json.getString("id_token"), json.getString("refresh_token"), json.getLong("expires_in"))
            json.getString("id_token")
        }
    }

    /** A fresh id token, signing up anonymously on first use. */
    private fun ensureToken(context: Context, config: FirebaseConfig): String {
        val p = prefs(context)
        val token = p.getString("id_token", "").orEmpty()
        val expiresAt = p.getLong("expires_at", 0L)
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + 60_000) return token
        return refreshToken(context, config)
    }

    // ---- low-level calls (token refresh once on 401) ----

    private fun firestoreBase(config: FirebaseConfig) =
        "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents"

    private fun call(context: Context, config: FirebaseConfig, request: (String) -> Request): String {
        var token = ensureToken(context, config)
        var resp = http.newCall(request(token)).execute()
        var text = resp.body!!.string()
        if (resp.code == 401) {
            resp.close()
            token = refreshToken(context, config)
            resp = http.newCall(request(token)).execute()
            text = resp.body!!.string()
        }
        resp.use {
            if (!it.isSuccessful) throw errorFor(it.code, text)
            return text
        }
    }

    private fun authed(token: String, url: String, method: String, body: String? = null): Request {
        val builder = Request.Builder().url(url).addHeader("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post((body ?: "").toRequestBody(JSON))
            else -> throw IllegalArgumentException("method $method")
        }
        return builder.build()
    }

    // ---- rooms and codes ----

    /**
     * Creates a room and its 6-digit code. The code is the only way for the
     * partner's phone to find the room; it is deleted when they join.
     */
    fun createRoom(context: Context): Pair<String, String> {
        val config = loadConfig(context) ?: throw IOException("firebase not configured")
        val roomId = java.util.UUID.randomUUID().toString()
        val code = generateCode()
        val now = System.currentTimeMillis()
        val base = firestoreBase(config)
        call(context, config, { token ->
            authed(token, "$base/rooms?documentId=$roomId", "POST",
                JSONObject().put("fields", JSONObject()
                    .put("createdAt", JSONObject().put("integerValue", now.toString()))).toString())
        })
        call(context, config, { token ->
            authed(token, "$base/codes?documentId=$code", "POST",
                JSONObject().put("fields", JSONObject()
                    .put("roomId", JSONObject().put("stringValue", roomId))
                    .put("createdAt", JSONObject().put("integerValue", now.toString()))).toString())
        })
        return roomId to code
    }

    /** The room id for a code, or null when the code is unknown or older than a day. Deletes the code on success. */
    fun lookupRoom(context: Context, code: String): String? {
        if (!isValidCode(code)) return null
        val config = loadConfig(context) ?: throw IOException("firebase not configured")
        val base = firestoreBase(config)
        val text = try {
            call(context, config, { token -> authed(token, "$base/codes/$code", "GET") })
        } catch (e: QuotaExceeded) {
            throw e
        } catch (_: IOException) {
            return null
        }
        val fields = runCatching { JSONObject(text).getJSONObject("fields") }.getOrNull() ?: return null
        val createdAt = runCatching { fields.getJSONObject("createdAt").getString("integerValue").toLong() }.getOrDefault(0L)
        if (System.currentTimeMillis() - createdAt > CODE_TTL_MS) return null
        val roomId = runCatching { fields.getJSONObject("roomId").getString("stringValue") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        runCatching { call(context, config, { token -> authed(token, "$base/codes/$code", "DELETE") }) }
        return roomId
    }

    // ---- messages ----

    /** Sends one sealed payload; the document id is the message id, so a retry never duplicates it. */
    fun sendMessage(context: Context, roomId: String, from: String?, payload: String, messageId: String, at: Long) {
        val config = loadConfig(context) ?: throw IOException("firebase not configured")
        val base = firestoreBase(config)
        call(context, config, { token ->
            authed(token, "$base/rooms/$roomId/messages?documentId=$messageId", "POST",
                encodeMessageFields(payload, at, from).toString())
        })
    }

    /**
     * Newest messages first, up to 500 — the same cap as local history.
     * With [sinceMillis] > 0 only messages newer than that time are asked for
     * (a filtered query, so an idle poll costs about one read instead of one
     * per stored message); 0 means the full list, for the first load.
     */
    fun fetchMessages(context: Context, roomId: String, sinceMillis: Long = 0): List<RemoteMessage> {
        val config = loadConfig(context) ?: throw IOException("firebase not configured")
        if (sinceMillis > 0) {
            val text = call(context, config, { token ->
                authed(token, "${firestoreBase(config)}:runQuery", "POST",
                    newerThanQueryBody(config.projectId, roomId, sinceMillis))
            })
            return parseRunQuery(text)
        }
        val base = firestoreBase(config)
        val out = mutableListOf<RemoteMessage>()
        var pageToken: String? = null
        while (out.size < MAX_FETCH) {
            val url = "$base/rooms/$roomId/messages".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("orderBy", "at desc")
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build().toString()
            val text = call(context, config, { token -> authed(token, url, "GET") })
            val root = JSONObject(text)
            val docs = root.optJSONArray("documents") ?: break
            for (i in 0 until docs.length()) {
                decodeMessage(docs.getJSONObject(i))?.let { out += it }
            }
            pageToken = root.optString("nextPageToken", "").ifBlank { null } ?: break
        }
        return out
    }

    /** Body of the "messages newer than X" query. Pure, so tests can check it. */
    fun newerThanQueryBody(projectId: String, roomId: String, sinceMillis: Long): String = JSONObject()
        .put("parent", "projects/$projectId/databases/(default)/documents/rooms/$roomId")
        .put("structuredQuery", JSONObject()
            .put("from", JSONArray().put(JSONObject().put("collectionId", "messages")))
            .put("where", JSONObject().put("fieldFilter", JSONObject()
                .put("field", JSONObject().put("fieldPath", "at"))
                .put("op", "GREATER_THAN")
                .put("value", JSONObject().put("integerValue", sinceMillis.toString()))))
            .put("orderBy", JSONArray().put(JSONObject()
                .put("field", JSONObject().put("fieldPath", "at"))
                .put("direction", "DESCENDING")))
            .put("limit", 100))
        .toString()

    /** Messages out of a runQuery answer: one entry per document; the trailing read-time-only entry has none. Pure, for tests. */
    fun parseRunQuery(text: String): List<RemoteMessage> {
        val out = mutableListOf<RemoteMessage>()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return out
        for (i in 0 until arr.length()) {
            val doc = arr.optJSONObject(i)?.optJSONObject("document") ?: continue
            decodeMessage(doc)?.let { out += it }
        }
        return out
    }

    /** Deletes old messages, but at most once a day — the prune lists the oldest page on every call, so running it on every refresh would burn reads. */
    fun pruneIfDue(context: Context, roomId: String, olderThanMillis: Long) {
        if (loadConfig(context) == null) return
        val p = prefs(context)
        val last = p.getLong("last_prune", 0L)
        if (System.currentTimeMillis() - last < PRUNE_INTERVAL_MS) return
        deleteOldMessages(context, roomId, olderThanMillis)
        p.edit().putLong("last_prune", System.currentTimeMillis()).apply()
    }

    /** Deletes messages older than [olderThanMillis], best effort — one page per refresh is enough. */
    fun deleteOldMessages(context: Context, roomId: String, olderThanMillis: Long) {
        val config = loadConfig(context) ?: return
        val base = firestoreBase(config)
        val url = "$base/rooms/$roomId/messages".toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "100")
            .addQueryParameter("orderBy", "at asc")
            .build().toString()
        val text = runCatching { call(context, config, { token -> authed(token, url, "GET") }) }.getOrNull() ?: return
        val docs = JSONObject(text).optJSONArray("documents") ?: return
        for (i in 0 until docs.length()) {
            val doc = decodeMessage(docs.getJSONObject(i)) ?: continue
            if (doc.at >= olderThanMillis) continue
            runCatching { call(context, config, { token -> authed(token, "$base/rooms/$roomId/messages/${doc.id}", "DELETE") }) }
        }
    }
}

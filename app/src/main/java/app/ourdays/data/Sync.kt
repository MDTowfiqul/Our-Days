package app.ourdays.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kopplung zweier Handys für den Abgleich: ein gemeinsamer geheimer Schlüssel, der einmal per
 * QR-Code übertragen wird. Er liegt nur auf den beiden Handys (nicht in Sicherungen) und
 * verschlüsselt jede Abgleich-Datei.
 */
data class Pairing(
    /** 32 zufällige Bytes */
    val key: ByteArray,
    /** Wer auf diesem Handy die App nutzt (ID einer [Person]), null = nicht angegeben */
    val me: String?,
    /** Zeitpunkt der Kopplung in Millisekunden */
    val since: Long,
    /** Letzter erfolgreicher Abgleich in Millisekunden, 0 = noch keiner */
    val lastSync: Long = 0,
) {
    /** Kurzer Fingerabdruck des Schlüssels, steht unverschlüsselt in jeder Abgleich-Datei */
    val fingerprint: ByteArray get() = SyncCrypto.fingerprint(key)

    override fun equals(other: Any?) = other is Pairing && key.contentEquals(other.key) &&
        me == other.me && since == other.since && lastSync == other.lastSync

    override fun hashCode() = key.contentHashCode() * 31 + (me?.hashCode() ?: 0)

    companion object {
        private const val PREFIX = "ourdays:pair:1:"
        /** Alter QR-Präfix aus der Since-Us-Zeit: alte QR-Codes bleiben lesbar */
        private const val OLD_PREFIX = "sinceus:pair:1:"

        fun create(me: String?): Pairing =
            Pairing(ByteArray(32).also { SecureRandom().nextBytes(it) }, me, System.currentTimeMillis())

        /**
         * Inhalt des QR-Codes. Für die Hauptbeziehung steht nur der Schlüssel drin (wie bisher,
         * damit alte Handys ihn weiter lesen können); für jede weitere Beziehung hängt die
         * Beziehungs-ID an, damit das andere Handy weiß, welche Verbindung es koppelt.
         */
        fun qrText(p: Pairing, relationshipId: String = PeopleCodec.MAIN_ID): String {
            val base = PREFIX + Base64.encodeToString(p.key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return if (relationshipId == PeopleCodec.MAIN_ID) base else "$base:$relationshipId"
        }

        /** Kopplung als Datei, für Handys, die keinen QR-Code scannen können: die Datei enthält denselben Text wie der QR-Code. */
        const val PAIRING_MIME = "application/vnd.ourdays.pairing"
        const val PAIRING_EXTENSION = "ourpair"
        fun pairingFileName(title: String = ""): String {
            val slug = slug(title)
            return if (slug.isEmpty()) "our-days-pairing.$PAIRING_EXTENSION" else "our-days-pairing-$slug.$PAIRING_EXTENSION"
        }

        fun pairingFileText(p: Pairing, relationshipId: String = PeopleCodec.MAIN_ID): String = qrText(p, relationshipId)

        /** Dateitauglicher Kurzname, z. B. „A & B“ → „a-b“ */
        fun slug(text: String): String =
            text.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
                .split('-').filter { it.isNotEmpty() }.joinToString("-")

        /** True, wenn [bytes] mit einem Kopplungs-Präfix beginnen — die Datei enthält eine Kopplung, keinen Abgleich. */
        fun isPairingFile(bytes: ByteArray): Boolean {
            val prefixes = listOf(PREFIX, OLD_PREFIX).map { it.toByteArray(Charsets.US_ASCII) }
            return prefixes.any { p -> bytes.size >= p.size && bytes.copyOfRange(0, p.size).contentEquals(p) }
        }

        /** Schlüssel und Beziehungs-ID aus einem gescannten QR-Code, null = kein Kopplungscode von Our Days */
        fun fromQr(text: String?): Pair<ByteArray, String>? {
            if (text == null) return null
            val encoded = when {
                text.startsWith(PREFIX) -> text.removePrefix(PREFIX)
                text.startsWith(OLD_PREFIX) -> text.removePrefix(OLD_PREFIX)
                else -> return null
            }
            // Schlüssel ist base64url ohne Füllzeichen; die Beziehungs-ID hängt nach einem Doppelpunkt an
            val parts = encoded.split(':')
            val key = runCatching { Base64.decode(parts[0], Base64.URL_SAFE) }.getOrNull()
                ?.takeIf { it.size == 32 } ?: return null
            val relationshipId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: PeopleCodec.MAIN_ID
            return key to relationshipId
        }

        /** Schlüssel aus einem gescannten QR-Code, null = kein Kopplungscode von Our Days */
        fun keyFromQr(text: String?): ByteArray? = fromQr(text)?.first
    }
}

/**
 * Speichert die Kopplungen in eigenen Einstellungen, die bewusst nicht gesichert werden.
 * Jede Beziehung hat ihren eigenen Schlüssel: [loadAll] gibt Beziehungs-ID → Kopplung zurück.
 * Eine einzelne Kopplung aus der Zeit vor dem Pro-Beziehung-Abgleich wandert einmalig auf die
 * Hauptbeziehung („main“) über.
 */
object PairingStore {
    private const val PREFS = "pairing"
    private const val ALL_KEY = "pairings"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(context: Context): Map<String, Pairing> {
        val p = prefs(context)
        p.getString(ALL_KEY, null)?.let { json ->
            return runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().mapNotNull { id ->
                    val e = o.optJSONObject(id) ?: return@mapNotNull null
                    val key = runCatching { Base64.decode(e.optString("key"), Base64.NO_WRAP) }.getOrNull()
                        ?.takeIf { it.size == 32 } ?: return@mapNotNull null
                    id to Pairing(key, e.optString("me").takeIf { it.isNotEmpty() }, e.optLong("since"), e.optLong("last_sync"))
                }.toMap()
            }.getOrNull() ?: emptyMap()
        }
        // Einmalige Übernahme: eine einzelne alte Kopplung wird die der Hauptbeziehung
        val old = loadLegacy(p) ?: return emptyMap()
        saveAll(context, mapOf(PeopleCodec.MAIN_ID to old))
        p.edit().remove("key").remove("me").remove("since").remove("last_sync").apply()
        return mapOf(PeopleCodec.MAIN_ID to old)
    }

    private fun loadLegacy(p: android.content.SharedPreferences): Pairing? {
        val key = p.getString("key", null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.size == 32 } ?: return null
        return Pairing(key, p.getString("me", null), p.getLong("since", 0), p.getLong("last_sync", 0))
    }

    /** Kopplung der Hauptbeziehung, null = nicht gekoppelt (wird z. B. für „Wer hat den Moment angelegt“ gebraucht) */
    fun load(context: Context): Pairing? = loadAll(context)[PeopleCodec.MAIN_ID]

    /** Wer auf diesem Handy die App nutzt — steht auf jeder Kopplung, hier reicht irgendeine */
    fun me(context: Context): String? = loadAll(context).values.firstNotNullOfOrNull { it.me }

    fun saveAll(context: Context, pairings: Map<String, Pairing>) {
        val o = JSONObject()
        pairings.forEach { (id, p) ->
            o.put(id, JSONObject().apply {
                put("key", Base64.encodeToString(p.key, Base64.NO_WRAP))
                if (p.me != null) put("me", p.me)
                put("since", p.since)
                put("last_sync", p.lastSync)
            })
        }
        prefs(context).edit().putString(ALL_KEY, o.toString()).apply()
    }

    fun save(context: Context, relationshipId: String, pairing: Pairing) {
        val all = loadAll(context).toMutableMap()
        all[relationshipId] = pairing
        saveAll(context, all)
    }

    fun remove(context: Context, relationshipId: String) {
        val all = loadAll(context).toMutableMap()
        all.remove(relationshipId)
        saveAll(context, all)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/**
 * Verschlüsselung der Abgleich-Datei mit AES-256-GCM in Blöcken, damit auch große Dateien mit
 * vielen Fotos nicht komplett in den Speicher müssen.
 *
 * Aufbau: MAGIC, 8 Bytes Fingerabdruck, 8 Bytes zufälliger Nonce-Anfang, dann Blöcke aus
 * Länge (4 Bytes) und verschlüsseltem Inhalt. Die Nonce jedes Blocks ist Nonce-Anfang plus
 * Blocknummer; ob ein Block der letzte ist, fließt mit in die Prüfung ein. So fällt jede
 * Veränderung, Vertauschung oder abgeschnittene Datei auf.
 */
object SyncCrypto {
    /** Dateikopf aus der Since-Us-Zeit, bewusst unverändert: alte Abgleich-Dateien müssen lesbar bleiben */
    private val MAGIC = "SINCEUS-SYNC1".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128
    private const val MAX_BLOCK = CHUNK + 16

    fun fingerprint(key: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("sinceus-pair".toByteArray() + key).copyOf(8)

    /** true, wenn [header] (die ersten Bytes einer Datei) zu einer Abgleich-Datei mit diesem Schlüssel gehört */
    fun belongsTo(header: ByteArray, key: ByteArray): Boolean =
        header.size >= MAGIC.size + 8 &&
            header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) &&
            header.copyOfRange(MAGIC.size, MAGIC.size + 8).contentEquals(fingerprint(key))

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        val out = DataOutputStream(output)
        val prefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        out.write(MAGIC)
        out.write(fingerprint(key))
        out.write(prefix)
        val buffer = ByteArray(CHUNK)
        var counter = 0
        var current = readFully(input, buffer)
        while (true) {
            // Einen Block vorauslesen, um zu wissen, ob dieser der letzte ist
            val next = ByteArray(CHUNK)
            val nextLength = if (current < CHUNK) 0 else readFully(input, next)
            val last = nextLength == 0
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter, last).doFinal(buffer, 0, current)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            System.arraycopy(next, 0, buffer, 0, nextLength)
            current = nextLength
            counter++
        }
        out.flush()
    }

    /** Entschlüsselt nach [output]; wirft eine Exception bei falschem Schlüssel oder veränderter Datei */
    fun decrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        val data = DataInputStream(input)
        val header = ByteArray(MAGIC.size + 8)
        data.readFully(header)
        if (!belongsTo(header, key)) throw IOException("not paired with this key")
        val prefix = ByteArray(8)
        data.readFully(prefix)
        var counter = 0
        while (true) {
            val length = try {
                data.readInt()
            } catch (_: EOFException) {
                throw IOException("file truncated")
            }
            if (length !in 16..MAX_BLOCK) throw IOException("bad block")
            val block = ByteArray(length)
            data.readFully(block)
            // Erst als „nicht letzter Block“ prüfen, sonst als letzter
            val plain = runCatching { cipher(Cipher.DECRYPT_MODE, key, prefix, counter, false).doFinal(block) }.getOrNull()
            if (plain != null) {
                output.write(plain)
                counter++
                continue
            }
            output.write(cipher(Cipher.DECRYPT_MODE, key, prefix, counter, true).doFinal(block))
            if (data.read() != -1) throw IOException("data after last block")
            return
        }
    }

    private fun cipher(mode: Int, key: ByteArray, prefix: ByteArray, counter: Int, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}

/** Was ein Handy dem anderen schickt: eine Beziehung mit ihren Menschen und Momenten samt gelöschter Einträge */
data class SyncData(
    val people: List<Person>,
    val relationships: List<Relationship>,
    val moments: List<Moment>,
    /** ID gelöschter Momente mit Zeitpunkt des Löschens */
    val deleted: Map<String, Long>,
    /** Wer die Datei geschickt hat (ID einer [Person]) */
    val from: String?,
    /** Welche Beziehung die Datei trägt, null = alle (alte Dateien) */
    val relationshipId: String? = null,
    /** IDs gelöschter Menschen mit Zeitpunkt des Löschens */
    val deletedPeople: Map<String, Long> = emptyMap(),
    /** IDs gelöschter Beziehungen mit Zeitpunkt des Löschens */
    val deletedRelationships: Map<String, Long> = emptyMap(),
)

object SyncCodec {
    private const val APP = "app.ourdays"
    const val FORMAT = 1
    const val JSON = "sync.json"
    const val MIME = "application/vnd.ourdays.sync"
    const val EXTENSION = "ourdays"
    /** Alte Dateiendung: alte Abgleich-Dateien bleiben lesbar und werden beim Öffnen erkannt */
    const val OLD_EXTENSION = "sinceus"

    fun fileName(today: java.time.LocalDate, title: String = ""): String {
        val slug = Pairing.slug(title)
        return if (slug.isEmpty()) "our-days-$today.$EXTENSION" else "our-days-$slug-$today.$EXTENSION"
    }

    fun encode(d: SyncData): String = JSONObject()
        .put("app", APP)
        .put("format", FORMAT)
        .put("from", d.from ?: JSONObject.NULL)
        .put("relationship", d.relationshipId ?: JSONObject.NULL)
        .put("people", JSONArray(PeopleCodec.encodePeople(d.people)))
        .put("relationships", JSONArray(PeopleCodec.encodeRelationships(d.relationships)))
        .put(
            "moments",
            JSONArray(MomentCodec.encode(d.moments.map { m -> m.copy(photoPaths = m.photoPaths.map { java.io.File(it).name }) })),
        )
        .put("deleted", JSONObject().apply { d.deleted.forEach { (id, time) -> put(id, time) } })
        .put("deletedPeople", JSONObject().apply { d.deletedPeople.forEach { (id, time) -> put(id, time) } })
        .put("deletedRelationships", JSONObject().apply { d.deletedRelationships.forEach { (id, time) -> put(id, time) } })
        .toString()

    fun decode(json: String): SyncData {
        val o = JSONObject(json)
        require(o.optString("app") == APP || o.optString("app") == "app.sinceus") { "not an Our Days sync file" }
        require(o.optInt("format", 0) in 1..FORMAT) { "unsupported sync format" }
        fun markers(key: String): Map<String, Long> {
            val m = o.optJSONObject(key) ?: return emptyMap()
            return m.keys().asSequence().associateWith { m.optLong(it) }
        }
        val deleted = o.optJSONObject("deleted")
        return SyncData(
            people = PeopleCodec.decodePeople(o.optJSONArray("people")?.toString()).orEmpty(),
            relationships = PeopleCodec.decodeRelationships(o.optJSONArray("relationships")?.toString()).orEmpty(),
            moments = MomentCodec.decode(o.optJSONArray("moments")?.toString())
                .map { m -> m.copy(photoPaths = m.photoPaths.mapNotNull { it.takeIf { name -> Backup.isSafeName(name) } }) },
            deleted = deleted?.keys()?.asSequence()?.associateWith { deleted.optLong(it) }.orEmpty(),
            from = if (o.isNull("from")) null else o.optString("from").ifBlank { null },
            relationshipId = if (o.isNull("relationship")) null else o.optString("relationship").ifBlank { null },
            deletedPeople = markers("deletedPeople"),
            deletedRelationships = markers("deletedRelationships"),
        )
    }
}

/** Ergebnis des Zusammenführens */
data class MergeResult(
    val people: List<Person>,
    val relationships: List<Relationship>,
    val moments: List<Moment>,
    val deleted: Map<String, Long>,
    /** Neue oder geänderte Momente aus der Datei (ihre Fotos müssen übernommen werden) */
    val taken: List<Moment>,
    val added: Int,
    val changed: Int,
    val removed: Int,
    /** Gelöschte Menschen (vereinigt, damit das Löschen auch auf dem anderen Handy ankommt) */
    val deletedPeople: Map<String, Long> = emptyMap(),
    /** Gelöschte Beziehungen (vereinigt) */
    val deletedRelationships: Map<String, Long> = emptyMap(),
)

object SyncMerge {
    /**
     * Führt die eigenen Daten mit denen des anderen Handys zusammen. Bei Momenten gewinnt die
     * jüngere Änderung, gelöschte Momente bleiben gelöscht. Bei Menschen und Beziehungen
     * gewinnt die Datei: Ein Eintrag aus der Datei ersetzt den Eintrag mit derselben ID
     * hier, damit nach einem Abgleich der neue Name oder das neue Datum auch auf diesem
     * Handy ankommt; nur hier vorhandene Einträge bleiben erhalten. Wer auf einem Handy
     * gelöscht wurde, verschwindet nach dem Abgleich auch auf dem anderen (Löschmerker
     * gewinnen immer; ein neu angelegter Mensch bekommt eine neue ID und ist davon nicht
     * betroffen). Mindestens eine Beziehung bleibt immer bestehen.
     */
    fun merge(
        people: List<Person>,
        relationships: List<Relationship>,
        moments: List<Moment>,
        deleted: Map<String, Long>,
        remote: SyncData,
        deletedPeople: Map<String, Long> = emptyMap(),
        deletedRelationships: Map<String, Long> = emptyMap(),
    ): MergeResult {
        val allDeleted = (deleted.keys + remote.deleted.keys).associateWith { maxOf(deleted[it] ?: 0, remote.deleted[it] ?: 0) }
        val byId = moments.associateBy { it.id }.toMutableMap()
        val taken = mutableListOf<Moment>()
        var added = 0
        var changed = 0
        remote.moments.forEach { m ->
            val gone = allDeleted[m.id]
            if (gone != null && gone >= m.updatedAt) return@forEach
            val local = byId[m.id]
            if (local == null) {
                added++
            } else if (local.updatedAt >= m.updatedAt) {
                return@forEach
            } else {
                changed++
            }
            byId[m.id] = m
            taken += m
        }
        var removed = 0
        allDeleted.forEach { (id, time) ->
            val local = byId[id]
            if (local != null && local.updatedAt <= time) {
                byId.remove(id)
                removed++
            }
        }
        val allDeletedPeople = (deletedPeople.keys + remote.deletedPeople.keys)
            .associateWith { maxOf(deletedPeople[it] ?: 0, remote.deletedPeople[it] ?: 0) }
        val allDeletedRelationships = (deletedRelationships.keys + remote.deletedRelationships.keys)
            .associateWith { maxOf(deletedRelationships[it] ?: 0, remote.deletedRelationships[it] ?: 0) }
        // Wer die Datei geschickt hat, gibt bei Menschen und Beziehungen den Ton an: Ein
        // Eintrag aus der Datei ersetzt den Eintrag mit derselben ID hier, sonst käme nach
        // einem Abgleich nie der neue Name oder das neue Datum auf diesem Handy an. Nur hier
        // vorhandene Einträge bleiben erhalten; ein fehlender Geburtstag wird aus dem jeweils
        // anderen Eintrag übernommen.
        val mergedPeople = (
            remote.people.map { r ->
                r.copy(birthday = r.birthday ?: people.firstOrNull { it.id == r.id }?.birthday)
            } +
                people.filter { p -> remote.people.none { it.id == p.id } }
            ).filterNot { it.id in allDeletedPeople }.take(MAX_PEOPLE)
        val mergedRelationships = (
            remote.relationships +
                relationships.filter { r -> remote.relationships.none { it.id == r.id } }
            ).filterNot { it.id in allDeletedRelationships }.take(MAX_RELATIONSHIPS)
                // Mindestens eine Beziehung bleibt: sonst würde der Abgleich das Handy
                // ohne jede Verbindung zurücklassen
                .ifEmpty { relationships.take(1) }
        return MergeResult(
            people = mergedPeople,
            relationships = mergedRelationships,
            moments = byId.values.sortedBy { it.date },
            // Alte Löschmerker nach einem Jahr vergessen, damit die Liste nicht endlos wächst
            deleted = allDeleted.filterValues { it > System.currentTimeMillis() - YEAR_MS },
            taken = taken,
            added = added,
            changed = changed,
            removed = removed,
            deletedPeople = allDeletedPeople.filterValues { it > System.currentTimeMillis() - YEAR_MS },
            deletedRelationships = allDeletedRelationships.filterValues { it > System.currentTimeMillis() - YEAR_MS },
        )
    }

    private const val YEAR_MS = 365L * 24 * 3600 * 1000
}

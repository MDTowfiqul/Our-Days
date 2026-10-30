package app.ourdays.data

import org.json.JSONObject
import java.security.MessageDigest

/** One-time Play Billing product for premium. Never a subscription. */
const val PREMIUM_PRODUCT_ID = "premium_lifetime"

/**
 * Proof that premium was unlocked. It lives on the device (never in a
 * backup, never on a server) and travels to the partner phone only inside
 * the already encrypted sync file.
 */
data class PremiumEntitlement(
    /** Play order ID, or "code:<payload>" for a manual unlock code */
    val orderId: String,
    val purchasedAt: Long,
    /** "play", "code" or "sync" */
    val source: String,
)

object PremiumEntitlementCodec {
    fun encode(e: PremiumEntitlement): String = JSONObject()
        .put("order", e.orderId)
        .put("at", e.purchasedAt)
        .put("src", e.source)
        .toString()

    /** Null for missing or unreadable data: no entitlement, no premium. */
    fun decode(json: String?): PremiumEntitlement? {
        if (json.isNullOrBlank()) return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val orderId = o.optString("order").ifBlank { return null }
        return PremiumEntitlement(
            orderId = orderId,
            purchasedAt = o.optLong("at", 0),
            source = o.optString("src", "play").ifBlank { "play" },
        )
    }
}

/** The newer entitlement wins; a missing one never downgrades the other side. */
fun mergePremium(local: PremiumEntitlement?, remote: PremiumEntitlement?): PremiumEntitlement? =
    when {
        local == null -> remote
        remote == null -> local
        remote.purchasedAt > local.purchasedAt -> remote
        else -> local
    }

/**
 * Manual unlock code: the buying phone shows a short code derived from its
 * Play order ID, the partner types it in. The code carries a checksum, so
 * typos are caught — but be honest about what it proves: nothing
 * cryptographic. It only proves the two partners shared a code, which is
 * enough here because partners are not adversaries and there is no server
 * to defraud. The sync-channel entitlement is the stronger proof (AES-GCM
 * proves it came from the paired phone).
 */
object PremiumUnlockCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O/1/I/L
    private const val PAYLOAD_LEN = 8
    private const val CHECK_LEN = 4

    /**
     * Code for an already-known payload (used when this phone itself was
     * unlocked with a code, so it shows the same code again).
     */
    fun codeFromPayload(payload: String): String {
        val check = encodeChars(sha("ourdays-premium-check:$payload"), CHECK_LEN)
        return (payload + check).chunked(4).joinToString("-")
    }

    /**
     * The code to show for an entitlement: code-unlocked phones show their
     * original code again, Play purchases derive it from the order ID.
     */
    fun displayCode(orderId: String): String =
        if (orderId.startsWith("code:")) codeFromPayload(orderId.removePrefix("code:")) else codeFromOrderId(orderId)

    /**
     * Returns the payload when [code] is well-formed (dashes, spaces and
     * lowercase are ignored), null otherwise. The payload becomes the
     * entitlement ID ("code:<payload>") — the real order ID is never shared.
     */
    fun parse(code: String): String? {
        val raw = code.uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != PAYLOAD_LEN + CHECK_LEN) return null
        if (raw.any { it !in ALPHABET }) return null
        val payload = raw.substring(0, PAYLOAD_LEN)
        val check = raw.substring(PAYLOAD_LEN)
        if (encodeChars(sha("ourdays-premium-check:$payload"), CHECK_LEN) != check) return null
        return payload
    }

    private fun sha(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    private fun encodeChars(digest: ByteArray, count: Int): String = buildString {
        var i = 0
        while (length < count) {
            append(ALPHABET[(digest[i % digest.size].toInt() and 0xFF) % ALPHABET.length])
            i++
        }
    }
}

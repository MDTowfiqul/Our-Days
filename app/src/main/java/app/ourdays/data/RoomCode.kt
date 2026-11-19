package app.ourdays.data

import android.util.Base64
import java.security.MessageDigest

/** Base URL of the hosted games (GitHub Pages, Our-Days repo docs/games/) */
const val GAMES_BASE_URL = "https://mdtowfiqul.github.io/Our-Days/games/"

/**
 * Room code for live play: the first 6 chars of the SHA-256 hex of the pairing
 * key. Both phones hold the same key, so both compute the same code — no
 * sharing step. Lowercase hex only, safe in a URL.
 */
fun roomCodeFor(pairingKey: String): String {
    val hex = MessageDigest.getInstance("SHA-256")
        .digest(pairingKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return hex.take(6)
}

/** Room code for a pairing: the key as the same Base64 string the QR code carries. */
fun roomCodeForPairing(pairing: Pairing): String =
    roomCodeFor(Base64.encodeToString(pairing.key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

/** URL of a game page for a room, with the version param (old builds fail gracefully). */
fun gameUrl(game: String, roomCode: String): String =
    "$GAMES_BASE_URL$game/?room=$roomCode&v=1"

/** URL of the games index page for a room. */
fun gamesIndexUrl(roomCode: String): String = "${GAMES_BASE_URL}?room=$roomCode&v=1"

/** URL of the Focus together page for a room. */
fun focusUrl(roomCode: String): String = gameUrl("focus", roomCode)

/** URL of the Watch together page for a room. */
fun watchUrl(roomCode: String): String = gameUrl("watch", roomCode)

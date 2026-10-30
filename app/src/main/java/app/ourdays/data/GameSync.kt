package app.ourdays.data

import org.json.JSONArray
import org.json.JSONObject

enum class GameKind { DOTS, GOMOKU }
enum class GameMode { PARTNER, AI_EASY, AI_HARD }

/**
 * One game of Our Entertainment. Moves travel inside the normal sync file (see
 * SyncCodec "games"): a versioned JSON record, sealed with the pairing key like
 * everything else in the file. Old app versions ignore the unknown "games" key,
 * so adding games does not break sync with them.
 *
 * Players: [player1] and [player2] are person ids; [player2] is "ai" for solo
 * games. [turn] is 1 or 2 (player number, not person id). [board] holds only
 * positions; turn/finished/winner live here so the UI never has to parse twice.
 */
data class GameState(
    val id: String,
    val kind: GameKind,
    val mode: GameMode,
    val player1: String,
    val player2: String,
    val turn: Int,
    val board: String,
    val finished: Boolean,
    val winner: Int,
    val updatedAt: Long,
    /** Person id (or "ai") that made the last move, for turn display */
    val lastMoveBy: String? = null,
) {
    fun dotsState(): DotsBoxesState? {
        if (kind != GameKind.DOTS) return null
        val o = runCatching { JSONObject(board) }.getOrNull() ?: return null
        fun ints(key: String): List<Int> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.optInt(it) }
        }
        val boxes = ints("boxes")
        if (boxes.size != 9) return null
        return DotsBoxesState(
            hEdges = ints("h").toSet(),
            vEdges = ints("v").toSet(),
            boxes = boxes,
            turn = turn,
            finished = finished,
            winner = winner,
        )
    }

    fun gomokuState(): GomokuState? {
        if (kind != GameKind.GOMOKU) return null
        val o = runCatching { JSONObject(board) }.getOrNull() ?: return null
        val arr = o.optJSONArray("cells") ?: return null
        val cells = (0 until arr.length()).map { arr.optInt(it) }
        if (cells.size != Gomoku.SIZE * Gomoku.SIZE) return null
        return GomokuState(
            cells = cells,
            turn = turn,
            finished = finished,
            winner = winner,
            lastMove = o.optInt("last", -1),
        )
    }
}

/** Board payloads for [GameState.board]. */
fun dotsBoard(state: DotsBoxesState): String = JSONObject()
    .put("h", JSONArray(state.hEdges.sorted()))
    .put("v", JSONArray(state.vEdges.sorted()))
    .put("boxes", JSONArray(state.boxes))
    .toString()

fun gomokuBoard(state: GomokuState): String = JSONObject()
    .put("cells", JSONArray(state.cells))
    .put("last", state.lastMove)
    .toString()

fun emptyDotsBoard(): String = dotsBoard(DotsBoxesState())

fun emptyGomokuBoard(): String = gomokuBoard(GomokuState())

object GameCodec {
    const val VERSION = 1

    fun toJson(g: GameState): JSONObject = JSONObject()
        .put("v", VERSION)
        .put("id", g.id)
        .put("kind", g.kind.name)
        .put("mode", g.mode.name)
        .put("p1", g.player1)
        .put("p2", g.player2)
        .put("turn", g.turn)
        .put("board", g.board)
        .put("finished", g.finished)
        .put("winner", g.winner)
        .put("updatedAt", g.updatedAt)
        .put("lastMoveBy", g.lastMoveBy ?: JSONObject.NULL)

    fun fromJson(o: JSONObject): GameState? = runCatching {
        GameState(
            id = o.getString("id"),
            kind = GameKind.valueOf(o.getString("kind")),
            mode = GameMode.valueOf(o.getString("mode")),
            player1 = o.getString("p1"),
            player2 = o.getString("p2"),
            turn = o.getInt("turn"),
            board = o.getString("board"),
            finished = o.getBoolean("finished"),
            winner = o.getInt("winner"),
            updatedAt = o.getLong("updatedAt"),
            lastMoveBy = if (o.isNull("lastMoveBy")) null else o.optString("lastMoveBy").ifBlank { null },
        )
    }.getOrNull()

    fun encode(games: List<GameState>): String =
        JSONArray().apply { games.forEach { put(toJson(it)) } }.toString()

    fun decode(json: String?): List<GameState> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
    }
}

/** Merge for games: per game id, the newer updatedAt wins. */
object GameMerge {
    data class Result(val games: List<GameState>, val newlyMyTurn: List<GameState>)

    fun merge(local: List<GameState>, remote: List<GameState>, myId: String?): Result {
        val byId = local.associateBy { it.id }.toMutableMap()
        val newlyMyTurn = mutableListOf<GameState>()
        remote.forEach { r ->
            val old = byId[r.id]
            if (old == null || r.updatedAt > old.updatedAt) {
                byId[r.id] = r
                // Notify only when the turn actually comes to this phone: a partner
                // game, unfinished, now my turn, and it was not already my turn.
                if (r.mode == GameMode.PARTNER && !r.finished && myId != null &&
                    turnIsMine(r, myId) && (old == null || !turnIsMine(old, myId))
                ) {
                    newlyMyTurn += r
                }
            }
        }
        return Result(byId.values.sortedByDescending { it.updatedAt }, newlyMyTurn)
    }

    /** True if it is [myId]'s turn in [g]. Player 1 moves when turn == 1. */
    fun turnIsMine(g: GameState, myId: String?): Boolean {
        val myNumber = when (myId) {
            g.player1 -> 1
            g.player2 -> 2
            else -> return false
        }
        return g.turn == myNumber
    }
}

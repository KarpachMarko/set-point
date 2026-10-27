package dev.setpoint.core.data

import android.content.Context
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Player
import dev.setpoint.core.scoring.Scoring
import dev.setpoint.core.scoring.Team
import org.json.JSONArray
import org.json.JSONObject

data class MatchSession(
    val match: MatchState,
    val startedAt: Long?,
    val endedAt: Long?,
) {
    val completed: Boolean get() = match.winner != null
}

/** Persists match rules and the point log, allowing restore and undo across launches. */
class MatchRepository(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private data class ArchivedSession(val session: MatchSession, val payload: JSONObject)
    private val preferences = context.applicationContext.getSharedPreferences("matches", Context.MODE_PRIVATE)
    private var initial: MatchState? = null
    private var points: List<Team> = emptyList()
    private var state: MatchState? = null
    private var startedAt: Long? = null
    private var endedAt: Long? = null
    private var archive: List<ArchivedSession> = emptyList()
    private var defaults = preferences.getString("settings", null)?.let { readRules(JSONObject(it)) }
        ?: MatchRules()

    init {
        preferences.getString("current", null)?.let { serialized ->
            val saved = JSONObject(serialized)
            // Version 1 contained only id + points; replay with the original default rules.
            initial = MatchState(
                id = saved.getString("id"),
                rules = saved.optJSONObject("rules")?.let(::readRules) ?: MatchRules(),
                firstServer = Player.valueOf(saved.optString("firstServer", Player.ME.name)),
            )
            val array = saved.getJSONArray("points")
            points = List(array.length()) { Team.valueOf(array.getString(it)) }
            state = points.fold(checkNotNull(initial), Scoring::award)
            startedAt = saved.timestamp("startedAt")
            endedAt = saved.timestamp("endedAt")
        }
        val history = JSONArray(preferences.getString("history", "[]"))
        archive = List(history.length()) { index ->
            val saved = history.getJSONObject(index)
            val first = MatchState(id = saved.getString("id"),
                rules = readRules(saved.getJSONObject("rules")),
                firstServer = Player.valueOf(saved.getString("firstServer")))
            val log = saved.getJSONArray("points")
            val match = (0 until log.length()).fold(first) { match, i ->
                Scoring.award(match, Team.valueOf(log.getString(i)))
            }
            ArchivedSession(MatchSession(match, saved.timestamp("startedAt"), saved.timestamp("endedAt")), saved)
        }.take(10)
    }

    fun current(): MatchState? = state
    fun history(): List<MatchSession> {
        // Existing completed games have no timestamps; retain them without inventing dates.
        val legacy = state?.takeIf { it.winner != null && archive.none { entry -> entry.session.match.id == it.id } }
        return (listOfNotNull(legacy?.let { MatchSession(it, startedAt, endedAt) }) +
            archive.map { it.session }).take(10)
    }
    fun settings(): MatchRules = defaults
    fun launchWorkoutOnStart(): Boolean = preferences.getBoolean("launchWorkoutOnStart", true)

    fun saveLaunchWorkoutOnStart(enabled: Boolean) {
        check(preferences.edit().putBoolean("launchWorkoutOnStart", enabled).commit()) {
            "Could not save workout launch setting"
        }
    }

    fun canUndo(): Boolean = points.isNotEmpty()
    fun isSessionActive(): Boolean = state?.let {
        it.winner == null && preferences.getString("sessionMatchId", null) == it.id
    } == true

    fun resumeSession() {
        val match = state ?: return
        if (match.winner == null && !isSessionActive()) {
            check(preferences.edit().putString("sessionMatchId", match.id).commit()) {
                "Could not resume match"
            }
        }
    }

    fun pauseSession() {
        check(preferences.edit().remove("sessionMatchId").commit()) { "Could not pause match" }
    }

    fun saveSettings(rules: MatchRules) {
        check(preferences.edit().putString("settings", writeRules(rules).toString()).commit()) {
            "Could not save settings"
        }
        defaults = rules
    }

    fun start(servingTeam: Team): MatchState {
        val timestamp = now()
        val next = MatchState(rules = defaults,
            firstServer = if (servingTeam == Team.A) Player.ME else Player.OPPONENT_1)
        val previous = state
        val history = if (previous != null) {
            val end = if (previous.winner == null) timestamp else endedAt
            archived(previous, checkNotNull(initial), points, startedAt, end)
        } else archive
        persist(next, emptyList(), sessionActive = true, start = timestamp, end = null, history = history)
        initial = next
        points = emptyList()
        state = next
        startedAt = timestamp
        endedAt = null
        return next
    }

    fun award(team: Team): MatchState? {
        val current = state ?: return null
        if (current.winner != null) return current
        val nextPoints = points + team
        val next = Scoring.award(current, team)
        val end = if (next.winner != null) now() else null
        val history = if (next.winner != null)
            archived(next, checkNotNull(initial), nextPoints, startedAt, end) else archive
        persist(checkNotNull(initial), nextPoints, sessionActive = isSessionActive() && next.winner == null,
            end = end, history = history)
        points = nextPoints
        state = next
        endedAt = end
        return next
    }

    fun undo(): MatchState? {
        if (points.isEmpty()) return state
        val nextPoints = points.dropLast(1)
        val next = nextPoints.fold(checkNotNull(initial), Scoring::award)
        persist(checkNotNull(initial), nextPoints, sessionActive = next.winner == null, end = null,
            history = archive.filterNot { it.session.match.id == next.id })
        points = nextPoints
        state = next
        endedAt = null
        return next
    }

    private fun serialized(match: MatchState, log: List<Team>, start: Long?, end: Long?): JSONObject =
        JSONObject().put("schemaVersion", 4).put("id", match.id)
            .put("rules", writeRules(match.rules)).put("firstServer", match.firstServer.name)
            .put("points", JSONArray(log.map { it.name }))
            .put("startedAt", start ?: JSONObject.NULL).put("endedAt", end ?: JSONObject.NULL)

    private fun archived(match: MatchState, first: MatchState, log: List<Team>, start: Long?, end: Long?) =
        (listOf(ArchivedSession(MatchSession(match, start, end), serialized(first, log, start, end))) +
            archive.filterNot { it.session.match.id == match.id }).take(10)

    private fun persist(match: MatchState, log: List<Team>, sessionActive: Boolean,
        start: Long? = startedAt, end: Long? = endedAt, history: List<ArchivedSession> = archive) {
        val value = serialized(match, log, start, end)
        val editor = preferences.edit().putString("current", value.toString())
            .putString("history", JSONArray(history.map { it.payload }).toString())
        if (sessionActive) editor.putString("sessionMatchId", match.id) else editor.remove("sessionMatchId")
        check(editor.commit()) {
            "Could not save match"
        }
        archive = history
    }

    private fun JSONObject.timestamp(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun writeRules(rules: MatchRules) = JSONObject()
        .put("gamesToWinSet", rules.gamesToWinSet)
        .put("goldenPoint", rules.goldenPoint)
        .put("tieBreakEnabled", rules.tieBreakEnabled)
        .put("setsToWinMatch", rules.setsToWinMatch ?: JSONObject.NULL)

    private fun readRules(value: JSONObject) = MatchRules(
        gamesToWinSet = value.optInt("gamesToWinSet", 6),
        goldenPoint = value.optBoolean("goldenPoint", false),
        tieBreakEnabled = value.optBoolean("tieBreakEnabled", true),
        // Older matches had a fixed two-set target; explicit null means endless mode.
        setsToWinMatch = if (value.has("setsToWinMatch") && value.isNull("setsToWinMatch")) null
            else value.optInt("setsToWinMatch", 2),
    )
}

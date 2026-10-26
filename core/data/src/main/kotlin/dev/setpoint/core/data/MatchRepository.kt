package dev.setpoint.core.data

import android.content.Context
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Player
import dev.setpoint.core.scoring.Scoring
import dev.setpoint.core.scoring.Team
import org.json.JSONArray
import org.json.JSONObject

/** Persists match rules and the point log, allowing restore and undo across launches. */
class MatchRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("matches", Context.MODE_PRIVATE)
    private var initial: MatchState? = null
    private var points: List<Team> = emptyList()
    private var state: MatchState? = null
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
        }
    }

    fun current(): MatchState? = state
    fun settings(): MatchRules = defaults
    fun canUndo(): Boolean = points.isNotEmpty()

    fun saveSettings(rules: MatchRules) {
        check(preferences.edit().putString("settings", writeRules(rules).toString()).commit()) {
            "Could not save settings"
        }
        defaults = rules
    }

    fun start(servingTeam: Team): MatchState {
        val next = MatchState(rules = defaults,
            firstServer = if (servingTeam == Team.A) Player.ME else Player.OPPONENT_1)
        persist(next, emptyList())
        initial = next
        points = emptyList()
        state = next
        return next
    }

    fun award(team: Team): MatchState? {
        val current = state ?: return null
        if (current.winner != null) return current
        val nextPoints = points + team
        val next = Scoring.award(current, team)
        persist(checkNotNull(initial), nextPoints)
        points = nextPoints
        state = next
        return next
    }

    fun undo(): MatchState? {
        if (points.isEmpty()) return state
        val nextPoints = points.dropLast(1)
        val next = nextPoints.fold(checkNotNull(initial), Scoring::award)
        persist(checkNotNull(initial), nextPoints)
        points = nextPoints
        state = next
        return next
    }

    private fun persist(match: MatchState, log: List<Team>) {
        val value = JSONObject().put("schemaVersion", 3).put("id", match.id)
            .put("rules", writeRules(match.rules)).put("firstServer", match.firstServer.name)
            .put("points", JSONArray(log.map { it.name }))
        check(preferences.edit().putString("current", value.toString()).commit()) {
            "Could not save match"
        }
    }

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

package dev.setpoint.core.data

import android.content.Context
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Scoring
import dev.setpoint.core.scoring.Team
import org.json.JSONArray
import org.json.JSONObject

/** Persists the point log, allowing deterministic restore and undo across launches. */
class MatchRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("matches", Context.MODE_PRIVATE)
    private var id: String
    private val points = mutableListOf<Team>()
    init {
        val saved = preferences.getString("current", null)?.let { JSONObject(it) }
        id = saved?.getString("id") ?: MatchState().id
        saved?.getJSONArray("points")?.let { array ->
            repeat(array.length()) { points += Team.valueOf(array.getString(it)) }
        }
    }
    fun current(): MatchState = points.fold(MatchState(id = id), Scoring::award)
    fun canUndo(): Boolean = points.isNotEmpty()
    fun award(team: Team): MatchState {
        if (current().winner == null) { points += team; persist() }
        return current()
    }
    fun undo(): MatchState {
        if (points.isNotEmpty()) { points.removeAt(points.lastIndex); persist() }
        return current()
    }
    private fun persist() {
        val value = JSONObject().put("schemaVersion", 1).put("id", id)
            .put("points", JSONArray(points.map { it.name }))
        check(preferences.edit().putString("current", value.toString()).commit()) {
            "Could not save match"
        }
    }
}

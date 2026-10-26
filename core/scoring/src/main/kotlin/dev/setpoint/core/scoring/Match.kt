package dev.setpoint.core.scoring

import java.util.UUID
import kotlin.math.abs

enum class Team { A, B;
    fun opponent() = if (this == A) B else A
}

data class SetScore(val a: Int, val b: Int)

data class MatchState(
    val id: String = UUID.randomUUID().toString(),
    val pointsA: Int = 0,
    val pointsB: Int = 0,
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val completedSets: List<SetScore> = emptyList(),
    val server: Team = Team.A,
    val tieBreakFirstServer: Team = Team.A,
    val winner: Team? = null,
) {
    val isTieBreak: Boolean get() = gamesA == 6 && gamesB == 6
    fun sets(team: Team): Int = completedSets.count {
        if (team == Team.A) it.a > it.b else it.b > it.a
    }
    fun pointLabel(team: Team): String {
        val own = if (team == Team.A) pointsA else pointsB
        val other = if (team == Team.A) pointsB else pointsA
        if (isTieBreak) return own.toString()
        if (own >= 3 && other >= 3) return if (own > other) "AD" else "40"
        return listOf("0", "15", "30", "40")[own.coerceAtMost(3)]
    }
}

/** Best of three sets, advantage games, seven-point tie-break at 6–6. */
object Scoring {
    fun award(state: MatchState, team: Team): MatchState {
        if (state.winner != null) return state
        val a = state.pointsA + if (team == Team.A) 1 else 0
        val b = state.pointsB + if (team == Team.B) 1 else 0
        val updated = state.copy(pointsA = a, pointsB = b)
        if (state.isTieBreak) {
            if (maxOf(a, b) >= 7 && abs(a - b) >= 2) {
                return finishSet(updated, if (team == Team.A) 7 else 6,
                    if (team == Team.B) 7 else 6, state.tieBreakFirstServer.opponent())
            }
            val nextServer = if (((a + b + 1) / 2) % 2 == 1)
                state.tieBreakFirstServer.opponent() else state.tieBreakFirstServer
            return updated.copy(server = nextServer)
        }
        if (maxOf(a, b) < 4 || abs(a - b) < 2) return updated
        val ga = state.gamesA + if (team == Team.A) 1 else 0
        val gb = state.gamesB + if (team == Team.B) 1 else 0
        val next = state.server.opponent()
        if (maxOf(ga, gb) >= 6 && abs(ga - gb) >= 2) return finishSet(updated, ga, gb, next)
        return updated.copy(pointsA = 0, pointsB = 0, gamesA = ga, gamesB = gb,
            server = next, tieBreakFirstServer = next)
    }

    private fun finishSet(state: MatchState, a: Int, b: Int, nextServer: Team): MatchState {
        val next = state.copy(pointsA = 0, pointsB = 0, gamesA = 0, gamesB = 0,
            completedSets = state.completedSets + SetScore(a, b), server = nextServer,
            tieBreakFirstServer = nextServer)
        return next.copy(winner = Team.entries.firstOrNull { next.sets(it) == 2 })
    }
}

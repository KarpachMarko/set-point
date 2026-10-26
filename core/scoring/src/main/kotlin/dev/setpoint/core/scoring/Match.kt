package dev.setpoint.core.scoring

import java.util.UUID
import kotlin.math.abs

enum class Team { A, B;
    fun opponent() = if (this == A) B else A
}

/** Order of service alternates teams and then partners. */
enum class Player(val team: Team) {
    ME(Team.A), OPPONENT_1(Team.B), PARTNER(Team.A), OPPONENT_2(Team.B),
}

data class MatchRules(
    val gamesToWinSet: Int = 6,
    val goldenPoint: Boolean = false,
    val tieBreakEnabled: Boolean = true,
    /** Null keeps playing sets without declaring a match winner. */
    val setsToWinMatch: Int? = 2,
) {
    init {
        require(gamesToWinSet in 1..12)
        require(setsToWinMatch == null || setsToWinMatch in 1..12)
    }
}

data class SetScore(val a: Int, val b: Int)

data class MatchState(
    val id: String = UUID.randomUUID().toString(),
    val rules: MatchRules = MatchRules(),
    val firstServer: Player = Player.ME,
    val pointsA: Int = 0,
    val pointsB: Int = 0,
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val completedSets: List<SetScore> = emptyList(),
    val servingTurn: Int = 0,
    val tieBreakFirstServingTurn: Int = 0,
    val winner: Team? = null,
) {
    val isTieBreak: Boolean get() = rules.tieBreakEnabled &&
        gamesA == rules.gamesToWinSet && gamesB == rules.gamesToWinSet
    val servingPlayer: Player get() = Player.entries[(firstServer.ordinal + servingTurn) % 4]
    val server: Team get() = servingPlayer.team
    // Right-hand service court from the server's perspective; mirrored for the top team.
    val servingFromRight: Boolean get() = (pointsA + pointsB) % 2 == 0
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

/** Rules are fixed when a match starts and retained during replay. */
object Scoring {
    fun award(state: MatchState, team: Team): MatchState {
        if (state.winner != null) return state
        val a = state.pointsA + if (team == Team.A) 1 else 0
        val b = state.pointsB + if (team == Team.B) 1 else 0
        val updated = state.copy(pointsA = a, pointsB = b)
        if (state.isTieBreak) {
            if (maxOf(a, b) >= 7 && abs(a - b) >= 2) {
                val target = state.rules.gamesToWinSet
                return finishSet(updated, target + if (team == Team.A) 1 else 0,
                    target + if (team == Team.B) 1 else 0,
                    (state.tieBreakFirstServingTurn + 1) % 4)
            }
            return updated.copy(servingTurn =
                (state.tieBreakFirstServingTurn + (a + b + 1) / 2) % 4)
        }
        if (maxOf(a, b) < 4 || (!state.rules.goldenPoint && abs(a - b) < 2)) return updated
        val ga = state.gamesA + if (team == Team.A) 1 else 0
        val gb = state.gamesB + if (team == Team.B) 1 else 0
        val next = (state.servingTurn + 1) % 4
        if (maxOf(ga, gb) >= state.rules.gamesToWinSet && abs(ga - gb) >= 2) {
            return finishSet(updated, ga, gb, next)
        }
        return updated.copy(pointsA = 0, pointsB = 0, gamesA = ga, gamesB = gb,
            servingTurn = next, tieBreakFirstServingTurn = next)
    }

    private fun finishSet(state: MatchState, a: Int, b: Int, nextTurn: Int): MatchState {
        val next = state.copy(pointsA = 0, pointsB = 0, gamesA = 0, gamesB = 0,
            completedSets = state.completedSets + SetScore(a, b), servingTurn = nextTurn,
            tieBreakFirstServingTurn = nextTurn)
        return next.copy(winner = state.rules.setsToWinMatch?.let { target ->
            Team.entries.firstOrNull { next.sets(it) >= target }
        })
    }
}

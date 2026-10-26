package dev.setpoint.core.scoring

import org.junit.Assert.*
import org.junit.Test

class ScoringTest {
    private fun points(state: MatchState, team: Team, count: Int) =
        (1..count).fold(state) { s, _ -> Scoring.award(s, team) }

    @Test fun `deuce requires two consecutive points`() {
        var match = points(points(MatchState(), Team.A, 3), Team.B, 3)
        match = Scoring.award(match, Team.A)
        assertEquals("AD", match.pointLabel(Team.A))
        match = Scoring.award(match, Team.B)
        assertEquals("40", match.pointLabel(Team.A))
        match = points(match, Team.B, 2)
        assertEquals(1, match.gamesB)
        assertEquals(0, match.pointsB)
        assertEquals(Team.B, match.server)
    }
    @Test fun `tie break changes server and requires two point lead`() {
        var match = MatchState(gamesA = 6, gamesB = 6)
        match = Scoring.award(match, Team.A)
        assertEquals(Team.B, match.server)
        match = points(match, Team.B, 2)
        assertEquals(Team.A, match.server)
        match = match.copy(pointsA = 6, pointsB = 6)
        match = Scoring.award(match, Team.A)
        assertTrue(match.isTieBreak)
        match = Scoring.award(match, Team.A)
        assertEquals(listOf(SetScore(7, 6)), match.completedSets)
        assertEquals(Team.B, match.server)
    }
    @Test fun `match ends after two sets and ignores additional points`() {
        val result = points(MatchState(), Team.A, 48)
        assertEquals(Team.A, result.winner)
        assertEquals(listOf(SetScore(6, 0), SetScore(6, 0)), result.completedSets)
        assertEquals(result, Scoring.award(result, Team.B))
    }
    @Test fun `set can finish seven five`() {
        val result = points(MatchState(gamesA = 6, gamesB = 5), Team.A, 4)
        assertEquals(listOf(SetScore(7, 5)), result.completedSets)
    }
}

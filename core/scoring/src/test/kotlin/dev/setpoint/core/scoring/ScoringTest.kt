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

    @Test fun `golden point ends the game immediately at deuce`() {
        val deuce = MatchState(rules = MatchRules(goldenPoint = true), pointsA = 3, pointsB = 3)
        for (team in Team.entries) {
            val result = Scoring.award(deuce, team)
            assertEquals(if (team == Team.A) 1 else 0, result.gamesA)
            assertEquals(if (team == Team.B) 1 else 0, result.gamesB)
            assertEquals(0, result.pointsA)
            assertEquals(0, result.pointsB)
        }
    }

    @Test fun `short sets finish at the configured target and still need two games lead`() {
        val rules = MatchRules(gamesToWinSet = 4)
        assertEquals(listOf(SetScore(4, 0)), points(MatchState(rules = rules), Team.A, 16).completedSets)
        val close = points(MatchState(rules = rules, gamesA = 3, gamesB = 3), Team.A, 4)
        assertTrue(close.completedSets.isEmpty())
        assertEquals(listOf(SetScore(5, 3)), points(close, Team.A, 4).completedSets)
    }

    @Test fun `custom target tie break completes set with correct games`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 4), gamesA = 4, gamesB = 4)
        assertTrue(start.isTieBreak)
        assertEquals(listOf(SetScore(4, 5)), points(start, Team.B, 7).completedSets)
    }

    @Test fun `without tie break games continue until a two game lead`() {
        val start = MatchState(rules = MatchRules(tieBreakEnabled = false), gamesA = 6, gamesB = 6)
        assertFalse(start.isTieBreak)
        val result = points(start, Team.B, 8)
        assertEquals(listOf(SetScore(6, 8)), result.completedSets)
    }

    @Test fun `golden point does not shorten tie break`() {
        val start = MatchState(rules = MatchRules(goldenPoint = true), gamesA = 6, gamesB = 6,
            pointsA = 6, pointsB = 6)
        val lead = Scoring.award(start, Team.A)
        assertTrue(lead.isTieBreak)
        assertEquals("7", lead.pointLabel(Team.A))
        assertEquals(listOf(SetScore(7, 6)), Scoring.award(lead, Team.A).completedSets)
    }

    @Test fun `all four players serve in sequence from selected first server`() {
        for (first in Player.entries) {
            var state = MatchState(firstServer = first)
            repeat(8) { game ->
                assertEquals(Player.entries[(first.ordinal + game) % 4], state.servingPlayer)
                state = points(state, if (game % 2 == 0) Team.A else Team.B, 4)
            }
        }
    }

    @Test fun `tie break player sequence is one then two points each`() {
        var state = MatchState(gamesA = 6, gamesB = 6, firstServer = Player.PARTNER)
        val expected = listOf(Player.PARTNER, Player.OPPONENT_2, Player.OPPONENT_2,
            Player.ME, Player.ME, Player.OPPONENT_1, Player.OPPONENT_1, Player.PARTNER)
        expected.forEachIndexed { index, player ->
            assertEquals(player, state.servingPlayer)
            state = Scoring.award(state, if (index % 2 == 0) Team.A else Team.B)
        }
    }

    @Test fun `next set starts with player after first tie break server`() {
        val start = MatchState(gamesA = 6, gamesB = 6, servingTurn = 2, tieBreakFirstServingTurn = 2)
        val result = points(start, Team.A, 7)
        assertEquals(Player.OPPONENT_2, result.servingPlayer)
        assertTrue(result.servingFromRight)
    }

    @Test fun `service court switches every point and resets with game`() {
        val start = MatchState()
        assertTrue(start.servingFromRight)
        assertFalse(Scoring.award(start, Team.A).servingFromRight)
        assertTrue(points(start, Team.A, 2).servingFromRight)
        assertTrue(points(start, Team.A, 4).servingFromRight)
    }

    @Test fun `replay restores rules score and serving and dropping a point undoes set win`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 4, goldenPoint = true),
            firstServer = Player.OPPONENT_2)
        val log = List(16) { Team.A }
        val result = log.fold(start, Scoring::award)
        val undone = log.dropLast(1).fold(start, Scoring::award)
        assertEquals(start.rules, result.rules)
        assertEquals(start.firstServer, result.firstServer)
        assertEquals(3, undone.gamesA)
        assertEquals("40", undone.pointLabel(Team.A))
        assertTrue(undone.completedSets.isEmpty())
        assertEquals(result, Scoring.award(undone, Team.A))
    }

    @Test fun `undo replay crosses match winning point`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 2))
        val log = List(16) { Team.B }
        assertEquals(Team.B, log.fold(start, Scoring::award).winner)
        val undone = log.dropLast(1).fold(start, Scoring::award)
        assertNull(undone.winner)
        assertEquals(1, undone.sets(Team.B))
        assertEquals(1, undone.gamesB)
    }

    @Test fun `invalid set targets are rejected`() {
        for (games in listOf(0, 13)) {
            assertThrows(IllegalArgumentException::class.java) { MatchRules(gamesToWinSet = games) }
        }
    }

    @Test fun `one set match finishes after first set including a tie break`() {
        val rules = MatchRules(setsToWinMatch = 1)
        val regular = points(MatchState(rules = rules), Team.B, 24)
        assertEquals(Team.B, regular.winner)
        assertEquals(listOf(SetScore(0, 6)), regular.completedSets)
        val tieBreak = points(MatchState(rules = rules, gamesA = 6, gamesB = 6), Team.A, 7)
        assertEquals(Team.A, tieBreak.winner)
        assertEquals(listOf(SetScore(7, 6)), tieBreak.completedSets)
    }

    @Test fun `custom match target counts sets won rather than total sets played`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 2, setsToWinMatch = 3))
        val twoSets = points(start, Team.A, 16)
        assertEquals(2, twoSets.sets(Team.A))
        assertNull(twoSets.winner)
        val fourSets = points(twoSets, Team.B, 16)
        assertEquals(4, fourSets.completedSets.size)
        assertNull(fourSets.winner)
        val final = points(fourSets, Team.B, 8)
        assertEquals(Team.B, final.winner)
        assertEquals(3, final.sets(Team.B))
        assertEquals(final, Scoring.award(final, Team.A))
    }

    @Test fun `endless mode continues scoring and rotating service after many sets`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 2, setsToWinMatch = null))
        val sixSets = points(start, Team.A, 48)
        assertEquals(6, sixSets.sets(Team.A))
        assertNull(sixSets.winner)
        assertEquals(Player.ME, sixSets.servingPlayer)
        val next = Scoring.award(sixSets, Team.B)
        assertEquals("15", next.pointLabel(Team.B))
        assertNull(next.winner)
        assertEquals(start.rules, next.rules)
    }

    @Test fun `endless tie break completes set without completing match`() {
        val start = MatchState(rules = MatchRules(setsToWinMatch = null),
            completedSets = List(3) { SetScore(6, 0) }, gamesA = 6, gamesB = 6)
        val result = points(start, Team.A, 7)
        assertEquals(4, result.sets(Team.A))
        assertNull(result.winner)
        assertFalse(result.isTieBreak)
        assertEquals(Player.OPPONENT_1, result.servingPlayer)
    }

    @Test fun `undo replay reopens match with custom winning target`() {
        val start = MatchState(rules = MatchRules(gamesToWinSet = 2, setsToWinMatch = 3))
        val log = List(24) { Team.A }
        assertEquals(Team.A, log.fold(start, Scoring::award).winner)
        val undone = log.dropLast(1).fold(start, Scoring::award)
        assertNull(undone.winner)
        assertEquals(2, undone.sets(Team.A))
        assertEquals("40", undone.pointLabel(Team.A))
    }

    @Test fun `invalid match targets are rejected`() {
        for (sets in listOf(0, -1, 13)) {
            assertThrows(IllegalArgumentException::class.java) { MatchRules(setsToWinMatch = sets) }
        }
    }
}

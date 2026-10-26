package dev.setpoint.wear

import dev.setpoint.core.scoring.Team
import org.junit.Assert.*
import org.junit.Test

class PointInputTest {
    @Test fun `touch selects first and only confirms the same team`() {
        val selected = PointInput().tap(Team.A)
        assertNull(selected.award)
        assertEquals(Team.A, selected.selection.team)
        val switched = selected.selection.tap(Team.B)
        assertNull(switched.award)
        val confirmed = switched.selection.tap(Team.B)
        assertEquals(Team.B, confirmed.award)
        assertNull(confirmed.selection.team)
    }

    @Test fun `large first bezel event selects without awarding`() {
        val selected = PointInput().rotate(1000f)
        assertNull(selected.award)
        assertEquals(Team.A, selected.selection.team)
        assertEquals(0f, selected.selection.progress)
        assertEquals(Team.A, selected.selection.rotate(PointInput.CONFIRM_PIXELS).award)
    }

    @Test fun `counterclockwise selects opponents and accumulates confirmation`() {
        val selected = PointInput().rotate(-1f).selection
        val partial = selected.rotate(-PointInput.CONFIRM_PIXELS / 2)
        assertNull(partial.award)
        assertEquals(0.5f, partial.selection.progress)
        assertEquals(Team.B, partial.selection.rotate(-PointInput.CONFIRM_PIXELS / 2).award)
    }

    @Test fun `reversing bezel resets confirmation for other team`() {
        val selected = PointInput().rotate(1f).selection.rotate(80f).selection
        val reversed = selected.rotate(-1000f)
        assertNull(reversed.award)
        assertEquals(Team.B, reversed.selection.team)
        assertEquals(0f, reversed.selection.progress)
    }

    @Test fun `bezel after touch requires its own selection step`() {
        val selected = PointInput().tap(Team.A).selection.rotate(1000f)
        assertNull(selected.award)
        assertEquals(0f, selected.selection.progress)
    }

    @Test fun `touch can confirm a bezel selection`() {
        val selected = PointInput().rotate(-1f).selection
        assertEquals(Team.B, selected.tap(Team.B).award)
    }

    @Test fun `invalid rotary deltas preserve selection`() {
        val selected = PointInput(team = Team.A)
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(PointInputResult(selected), selected.rotate(it))
        }
    }
}

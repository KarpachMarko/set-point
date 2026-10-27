package dev.setpoint.wear

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.setpoint.core.data.MatchRepository
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.Team
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchSessionTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before fun isolateStorage() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "session-test-${UUID.randomUUID()}-"
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences(prefix + name, mode)
        }
        preferences = context.getSharedPreferences("matches", Context.MODE_PRIVATE)
    }

    @After fun clearStorage() { preferences.edit().clear().commit() }

    @Test fun startingMatchPersistsSessionAcrossReconstruction() {
        val repository = MatchRepository(context)
        assertFalse(repository.isSessionActive())
        val match = repository.start(Team.B)
        assertTrue(repository.isSessionActive())
        val restored = MatchRepository(context)
        assertEquals(match, restored.current())
        assertTrue(restored.isSessionActive())
        assertEquals(match.id, preferences.getString("sessionMatchId", null))
    }

    @Test fun savedMatchWithoutSessionMarkerStaysPaused() {
        val repository = MatchRepository(context)
        val match = repository.start(Team.A)
        preferences.edit().remove("sessionMatchId").commit()
        val restored = MatchRepository(context)
        assertEquals(match, restored.current())
        assertFalse(restored.isSessionActive())
    }

    @Test fun pausingAndResumingPreserveRulesPointsAndUndo() {
        val repository = MatchRepository(context)
        repository.saveSettings(MatchRules(gamesToWinSet = 4, goldenPoint = true))
        repository.start(Team.A)
        val match = repository.award(Team.B)
        repository.pauseSession()
        val restored = MatchRepository(context)
        assertFalse(restored.isSessionActive())
        assertEquals(match, restored.current())
        assertTrue(restored.canUndo())
        restored.resumeSession()
        assertTrue(MatchRepository(context).isSessionActive())
        assertEquals(match, restored.current())
    }

    @Test fun winningStopsSessionAndUndoReactivatesIt() {
        val repository = MatchRepository(context)
        repository.saveSettings(MatchRules(gamesToWinSet = 2, setsToWinMatch = 1))
        repository.start(Team.A)
        repeat(8) { repository.award(Team.A) }
        assertEquals(Team.A, repository.current()?.winner)
        assertFalse(repository.isSessionActive())
        assertFalse(MatchRepository(context).isSessionActive())
        repository.resumeSession()
        assertFalse(repository.isSessionActive())
        repository.undo()
        assertNull(repository.current()?.winner)
        assertTrue(repository.isSessionActive())
        assertTrue(MatchRepository(context).isSessionActive())
        repository.award(Team.A)
        assertFalse(repository.isSessionActive())
    }

    @Test fun newMatchReplacesSessionIdentity() {
        val repository = MatchRepository(context)
        val first = repository.start(Team.A)
        repository.award(Team.A)
        val second = repository.start(Team.B)
        assertNotEquals(first.id, second.id)
        assertEquals(second.id, preferences.getString("sessionMatchId", null))
        assertFalse(repository.canUndo())
        assertTrue(MatchRepository(context).isSessionActive())
    }

    @Test fun endlessSessionContinuesAcrossSetsAndRestart() {
        val repository = MatchRepository(context)
        repository.saveSettings(MatchRules(gamesToWinSet = 2, setsToWinMatch = null))
        repository.start(Team.A)
        repeat(24) { repository.award(Team.A) }
        val restored = MatchRepository(context)
        assertEquals(3, restored.current()?.sets(Team.A))
        assertNull(restored.current()?.winner)
        assertTrue(restored.isSessionActive())
    }

    @Test fun settingsChangesDoNotResumePausedMatchOrChangeItsRules() {
        val repository = MatchRepository(context)
        val match = repository.start(Team.A)
        repository.pauseSession()
        repository.saveSettings(MatchRules(setsToWinMatch = null))
        val restored = MatchRepository(context)
        assertFalse(restored.isSessionActive())
        assertEquals(match, restored.current())
        assertNull(restored.settings().setsToWinMatch)
    }

    @Test fun workoutLaunchDefaultsOnForExistingSettingsAndRemembersChanges() {
        // Existing installations have scoring defaults but no workout-launch preference.
        MatchRepository(context).saveSettings(MatchRules(goldenPoint = true))
        val repository = MatchRepository(context)
        assertTrue(repository.launchWorkoutOnStart())
        repository.saveLaunchWorkoutOnStart(false)
        assertFalse(MatchRepository(context).launchWorkoutOnStart())
        repository.saveLaunchWorkoutOnStart(true)
        assertTrue(MatchRepository(context).launchWorkoutOnStart())
    }

    @Test fun workoutPreferenceStaysSeparateFromMatchRulesAndOtherSettings() {
        val repository = MatchRepository(context)
        val match = repository.start(Team.A)
        repository.saveLaunchWorkoutOnStart(false)
        repository.saveSettings(MatchRules(gamesToWinSet = 4, setsToWinMatch = null))
        val restored = MatchRepository(context)
        assertFalse(restored.launchWorkoutOnStart())
        assertEquals(match, restored.current())
        assertTrue(restored.isSessionActive())
        restored.start(Team.B)
        assertFalse(MatchRepository(context).launchWorkoutOnStart())
        assertNull(restored.current()?.rules?.setsToWinMatch)
    }

    @Test fun replacingPausedGameArchivesItsExactScoreAndTimes() {
        var time = 1_000L
        val repository = MatchRepository(context) { time }
        repository.start(Team.B)
        val score = repository.award(Team.A)
        repository.pauseSession()
        assertTrue(repository.history().isEmpty())
        time = 5_000L
        val restored = MatchRepository(context) { time }
        restored.start(Team.A)
        val session = MatchRepository(context).history().single()
        assertEquals(score, session.match)
        assertFalse(session.completed)
        assertEquals(1_000L, session.startedAt)
        assertEquals(5_000L, session.endedAt)
    }

    @Test fun finishedGameIsArchivedImmediatelyAndKeepsItsEndTimeOnReplacement() {
        var time = 1_000L
        val repository = MatchRepository(context) { time }
        repository.saveSettings(MatchRules(gamesToWinSet = 2, setsToWinMatch = 1))
        repository.start(Team.A)
        time = 2_000L
        repeat(8) { repository.award(Team.B) }
        val session = MatchRepository(context).history().single()
        assertTrue(session.completed)
        assertEquals(Team.B, session.match.winner)
        assertEquals(2_000L, session.endedAt)
        time = 9_000L
        MatchRepository(context) { time }.start(Team.A)
        assertEquals(listOf(session), MatchRepository(context).history())
    }

    @Test fun undoingWinRemovesResultAndRewinningRecordsNewEndTime() {
        var time = 1_000L
        val repository = MatchRepository(context) { time }
        repository.saveSettings(MatchRules(gamesToWinSet = 2, setsToWinMatch = 1))
        repository.start(Team.A)
        repeat(8) { repository.award(Team.A) }
        assertEquals(1, repository.history().size)
        val restored = MatchRepository(context) { time }
        restored.undo()
        assertTrue(MatchRepository(context).history().isEmpty())
        time = 4_000L
        restored.award(Team.A)
        assertEquals(4_000L, MatchRepository(context).history().single().endedAt)
        assertEquals(1_000L, MatchRepository(context).history().single().startedAt)
    }

    @Test fun historyKeepsOnlyTenMostRecentSessionsInNewestFirstOrder() {
        var time = 0L
        val repository = MatchRepository(context) { time }
        val ids = (0..12).map {
            time += 1_000L
            repository.start(Team.A).id
        }
        val history = MatchRepository(context).history()
        assertEquals(ids.dropLast(1).takeLast(10).reversed(), history.map { it.match.id })
        assertEquals(13_000L, history.first().endedAt)
        assertTrue(history.none { it.completed })
    }

    @Test fun endlessGameWithSetsIsInterruptedRatherThanDeclaredWon() {
        val repository = MatchRepository(context)
        repository.saveSettings(MatchRules(gamesToWinSet = 2, setsToWinMatch = null))
        repository.start(Team.A)
        repeat(16) { repository.award(Team.A) }
        repository.start(Team.B)
        val session = MatchRepository(context).history().single()
        assertEquals(2, session.match.sets(Team.A))
        assertFalse(session.completed)
        assertNull(session.match.winner)
        assertNotNull(session.endedAt)
    }

    @Test fun legacyGameWithoutDatesIsPreservedWhenReplaced() {
        val repository = MatchRepository(context)
        val match = repository.start(Team.A)
        val saved = org.json.JSONObject(preferences.getString("current", null)!!)
        saved.remove("startedAt")
        saved.remove("endedAt")
        saved.put("schemaVersion", 3)
        preferences.edit().putString("current", saved.toString()).commit()
        MatchRepository(context) { 8_000L }.start(Team.B)
        val session = MatchRepository(context).history().single()
        assertEquals(match, session.match)
        assertNull(session.startedAt)
        assertEquals(8_000L, session.endedAt)
    }
}

package dev.setpoint.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.setpoint.core.data.MatchRepository
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Team
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppState(
    val match: MatchState?,
    val settings: MatchRules,
    val canUndo: Boolean,
    val sessionActive: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class MatchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MatchRepository(application)
    private val notification = MatchSessionNotification(application)
    private val mutableState = MutableStateFlow(snapshot())
    val state = mutableState.asStateFlow()

    init { refreshSessionNotification() }

    private fun snapshot() = AppState(repository.current(), repository.settings(), repository.canUndo(),
        sessionActive = repository.isSessionActive())

    fun notificationsAllowed() = notification.notificationsAllowed()
    fun refreshSessionNotification() {
        val state = mutableState.value
        notification.update(state.match.takeIf { state.sessionActive })
    }

    fun start(servingTeam: Team, onStarted: () -> Unit) = act(onStarted) { start(servingTeam) }
    fun resumeSession(onResumed: () -> Unit) = act(onResumed) { resumeSession() }
    fun pauseSession(onPaused: () -> Unit) = act(onPaused) { pauseSession() }
    fun updateSettings(rules: MatchRules) = act { saveSettings(rules) }
    fun award(team: Team) = act { award(team) }
    fun undo() = act { undo() }
    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun act(onSuccess: () -> Unit = {}, action: MatchRepository.() -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.action() }
                mutableState.value = snapshot()
                refreshSessionNotification()
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    busy = false, error = error.message ?: "Could not save. Please try again.",
                )
            }
        }
    }
}

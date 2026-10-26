package dev.setpoint.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.setpoint.core.data.MatchRepository
import dev.setpoint.core.scoring.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MatchRepository(application)
    private val mutableState = MutableStateFlow(repository.current())
    val state = mutableState.asStateFlow()
    private val mutableCanUndo = MutableStateFlow(repository.canUndo())
    val canUndo = mutableCanUndo.asStateFlow()
    // A single dispatcher preserves action order while keeping disk writes off the UI thread.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val actions = Dispatchers.IO.limitedParallelism(1)
    fun award(team: Team) = viewModelScope.launch(actions) {
        mutableState.value = repository.award(team)
        mutableCanUndo.value = repository.canUndo()
    }
    fun undo() = viewModelScope.launch(actions) {
        mutableState.value = repository.undo()
        mutableCanUndo.value = repository.canUndo()
    }
}

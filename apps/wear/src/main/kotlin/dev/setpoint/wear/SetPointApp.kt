package dev.setpoint.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Team

internal val Lime = Color(0xFFD3F56A)
internal val Muted = Color(0xFFACB7B4)
internal val Surface = Color(0xFF17231F)

private enum class Screen { MENU, SETTINGS, NEW_MATCH, COURT, HISTORY }

@Composable
fun SetPointApp(state: AppState, model: MatchViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    BackHandler(enabled = screen != Screen.MENU && (!state.busy || screen == Screen.SETTINGS)) {
        screen = when (screen) {
            Screen.HISTORY -> Screen.COURT
            Screen.NEW_MATCH -> if (state.match?.winner != null) Screen.COURT else Screen.MENU
            else -> Screen.MENU
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            Screen.MENU -> MenuScreen(state,
                onNew = { screen = Screen.NEW_MATCH },
                onContinue = { screen = Screen.COURT },
                onSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(state.settings, state.busy,
                onChange = model::updateSettings)
            Screen.NEW_MATCH -> NewMatchScreen(state,
                onStart = { model.start(it) { screen = Screen.COURT } })
            Screen.COURT -> state.match?.let { match ->
                if (match.winner != null) {
                    MatchFinishedScreen(match, state.busy, state.canUndo,
                        onUndo = model::undo,
                        onNew = { screen = Screen.NEW_MATCH },
                        onHistory = { screen = Screen.HISTORY })
                } else {
                    CourtScreen(match, state.busy, state.canUndo,
                        onAward = model::award, onUndo = model::undo,
                        onHistory = { screen = Screen.HISTORY },
                        onMenu = { screen = Screen.MENU })
                }
            }
            Screen.HISTORY -> state.match?.let {
                SetHistoryScreen(it) { screen = Screen.COURT }
            }
        }
        state.error?.let { message ->
            ScrollPage {
                PageTitle("Unable to save")
                Text(message, textAlign = TextAlign.Center, fontSize = 13.sp)
                MenuAction("Dismiss", onClick = model::dismissError)
            }
        }
    }
}

@Composable
internal fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState)
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(behavior = rotaryBehavior, focusRequester = focusRequester)
            .verticalScroll(scrollState)
            .padding(horizontal = 26.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun PageTitle(title: String) {
    Text(title, color = Lime, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center)
}

@Composable
internal fun MenuAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MenuScreen(state: AppState, onNew: () -> Unit, onContinue: () -> Unit, onSettings: () -> Unit) {
    ScrollPage {
        PageTitle("SET POINT")
        Text("PADEL · ${state.settings.matchLengthLabel()}", color = Muted, fontSize = 10.sp)
        state.match?.let { match ->
            MenuAction(if (match.winner == null) "Continue game" else "View last game", !state.busy, onContinue)
            Text("My ${match.gamesA} : ${match.gamesB} Opp · Sets ${match.sets(Team.A)} : ${match.sets(Team.B)}",
                color = Muted, fontSize = 10.sp)
        }
        MenuAction("Start new game", !state.busy, onNew)
        MenuAction("Settings", !state.busy, onSettings)
    }
}

@Composable
private fun SettingsScreen(rules: MatchRules, busy: Boolean, onChange: (MatchRules) -> Unit) {
    // Keep the finite target when toggling endless mode within this visit.
    var lastFiniteSets by rememberSaveable { mutableIntStateOf(rules.setsToWinMatch ?: 2) }
    val games = rules.gamesToWinSet
    val sets = rules.setsToWinMatch ?: lastFiniteSets
    val endless = rules.setsToWinMatch == null
    ScrollPage {
        PageTitle("Settings")
        Text("FOR NEW GAMES", fontSize = 10.sp, color = Muted)
        Text("Games to win a set", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { onChange(rules.copy(gamesToWinSet = games - 1)) }, enabled = !busy && games > 1,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease games to win" }) {
                Text("−", fontSize = 24.sp)
            }
            Text("$games", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onChange(rules.copy(gamesToWinSet = games + 1)) }, enabled = !busy && games < 12,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase games to win" }) {
                Text("+", fontSize = 24.sp)
            }
        }
        Text("Win by two games", color = Muted, fontSize = 11.sp)
        Text("Sets to win match", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { onChange(rules.copy(setsToWinMatch = sets - 1)) }, enabled = !busy && !endless && sets > 1,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease sets to win" }) {
                Text("−", fontSize = 24.sp)
            }
            Text(if (endless) "∞" else "$sets", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onChange(rules.copy(setsToWinMatch = sets + 1)) }, enabled = !busy && !endless && sets < 12,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase sets to win" }) {
                Text("+", fontSize = 24.sp)
            }
        }
        RuleToggle("Endless mode", "Keep playing sets without a winner", endless, !busy) {
            if (!endless) lastFiniteSets = sets
            onChange(rules.copy(setsToWinMatch = if (endless) lastFiniteSets else null))
        }
        RuleToggle("Golden point", "At 40–40, next point wins", rules.goldenPoint, !busy) {
            onChange(rules.copy(goldenPoint = !rules.goldenPoint))
        }
        RuleToggle("Tie-break", "At $games–$games · first to 7, by 2", rules.tieBreakEnabled, !busy) {
            onChange(rules.copy(tieBreakEnabled = !rules.tieBreakEnabled))
        }
    }
}

@Composable
private fun RuleToggle(label: String, detail: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp))
        .toggleable(value = checked, enabled = enabled,
            role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { onClick() })
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp)
            Text(if (checked) "ON" else "OFF", color = if (checked) Lime else Muted, fontSize = 11.sp)
        }
        Text(detail, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NewMatchScreen(state: AppState, onStart: (Team) -> Unit) {
    ScrollPage {
        PageTitle("New game")
        Text("Who serves first?", fontSize = 13.sp)
        MenuAction("My team", enabled = !state.busy) { onStart(Team.A) }
        MenuAction("Opponents", enabled = !state.busy) { onStart(Team.B) }
        Text(state.settings.matchLengthLabel(), color = Muted, fontSize = 11.sp)
        if (state.match?.winner == null && state.match != null) {
            Text("Selecting a team replaces your current game.", color = Color(0xFFFFC58C),
                fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun MatchRules.matchLengthLabel() = setsToWinMatch?.let {
    "First to $it ${if (it == 1) "set" else "sets"}"
} ?: "Endless sets"

@Composable
private fun MatchFinishedScreen(
    match: MatchState,
    busy: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onNew: () -> Unit,
    onHistory: () -> Unit,
) {
    val winner = checkNotNull(match.winner)
    ScrollPage {
        PageTitle(if (winner == Team.A) "My team wins!" else "Opponents win!")
        Text("${match.sets(winner)} : ${match.sets(winner.opponent())} in sets",
            color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        MenuAction("Undo last point", enabled = canUndo && !busy, onClick = onUndo)
        MenuAction("Start new game", enabled = !busy, onClick = onNew)
        TextButton(onClick = onHistory, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Set results", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SetHistoryScreen(match: MatchState, onBack: () -> Unit) {
    ScrollPage {
        PageTitle("Set results")
        Text("MY TEAM  :  OPPONENTS", fontSize = 10.sp, color = Muted)
        if (match.completedSets.isEmpty()) {
            Text("No completed sets yet", color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp)
        }
        match.completedSets.forEachIndexed { index, score ->
            Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SET ${index + 1}", fontSize = 10.sp, color = Muted)
                Text("${score.a} : ${score.b}", fontSize = 24.sp, color = Lime, fontWeight = FontWeight.Bold)
            }
        }
        if (match.winner == null) {
            Text("Set ${match.completedSets.size + 1} · ${match.gamesA} : ${match.gamesB} in progress",
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
        } else {
            Text(if (match.winner == Team.A) "My team wins!" else "Opponents win",
                color = Lime, fontSize = 14.sp)
        }
        MenuAction("Back to court", onClick = onBack)
    }
}

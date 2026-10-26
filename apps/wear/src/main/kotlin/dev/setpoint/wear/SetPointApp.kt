package dev.setpoint.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import dev.setpoint.core.scoring.MatchRules
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Player
import dev.setpoint.core.scoring.Team
import kotlinx.coroutines.launch

internal val Lime = Color(0xFFD3F56A)
internal val Muted = Color(0xFFACB7B4)
internal val Surface = Color(0xFF17231F)

private enum class Screen { MENU, SETTINGS, NEW_MATCH, COURT, HISTORY }

@Composable
fun SetPointApp(state: AppState, model: MatchViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    BackHandler(enabled = screen != Screen.MENU && !state.busy) {
        screen = when (screen) {
            Screen.HISTORY -> Screen.COURT
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
                onSave = { model.saveSettings(it) { screen = Screen.MENU } },
                onBack = { screen = Screen.MENU })
            Screen.NEW_MATCH -> NewMatchScreen(state,
                onStart = { model.start(it) { screen = Screen.COURT } },
                onBack = { screen = Screen.MENU })
            Screen.COURT -> state.match?.let { match ->
                CourtScreen(match, state.busy, state.canUndo,
                    onAward = model::award, onUndo = model::undo,
                    onHistory = { screen = Screen.HISTORY },
                    onMenu = { screen = Screen.MENU })
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .onRotaryScrollEvent { event ->
                scope.launch { scrollState.scrollBy(event.verticalScrollPixels) }
                true
            }.focusRequester(focusRequester).focusable().verticalScroll(scrollState)
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
        Text("PADEL · BEST OF 3", color = Muted, fontSize = 10.sp)
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
private fun SettingsScreen(rules: MatchRules, busy: Boolean, onSave: (MatchRules) -> Unit, onBack: () -> Unit) {
    var games by rememberSaveable { mutableIntStateOf(rules.gamesToWinSet) }
    var golden by rememberSaveable { mutableStateOf(rules.goldenPoint) }
    var tieBreak by rememberSaveable { mutableStateOf(rules.tieBreakEnabled) }
    ScrollPage {
        PageTitle("Settings")
        Text("FOR NEW GAMES", fontSize = 10.sp, color = Muted)
        Text("Games to win a set", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { games-- }, enabled = !busy && games > 1,
                modifier = Modifier.size(48.dp)) { Text("−", fontSize = 24.sp) }
            Text("$games", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { games++ }, enabled = !busy && games < 12,
                modifier = Modifier.size(48.dp)) { Text("+", fontSize = 24.sp) }
        }
        Text("Win by two games", color = Muted, fontSize = 11.sp)
        RuleToggle("Golden point", "At 40–40, next point wins", golden, !busy) { golden = !golden }
        RuleToggle("Tie-break", "At $games–$games · first to 7, by 2", tieBreak, !busy) { tieBreak = !tieBreak }
        MenuAction("Save settings", !busy) { onSave(MatchRules(games, golden, tieBreak)) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Cancel") }
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
private fun NewMatchScreen(state: AppState, onStart: (Player) -> Unit, onBack: () -> Unit) {
    var firstServer by rememberSaveable { mutableStateOf(Player.ME) }
    ScrollPage {
        PageTitle("New game")
        Text("${state.settings.gamesToWinSet} games · ${if (state.settings.goldenPoint) "Golden point" else "Advantage"}",
            textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted)
        Text("Who serves first?", fontSize = 13.sp)
        Player.entries.forEach { player ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .background(if (firstServer == player) Surface else Color.Black, RoundedCornerShape(18.dp))
                .border(1.dp, if (firstServer == player) Lime else Surface, RoundedCornerShape(18.dp))
                .clickable(enabled = !state.busy) { firstServer = player }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (firstServer == player) "●  " else "○  ", color = Lime, fontSize = 12.sp)
                Text(player.label(), fontSize = 12.sp)
            }
        }
        if (state.match?.winner == null && state.match != null) {
            Text("Starting replaces your current game.", color = Color(0xFFFFC58C),
                fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        MenuAction(if (state.busy) "Starting…" else "Start game", !state.busy) { onStart(firstServer) }
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Cancel") }
    }
}

internal fun Player.label() = when (this) {
    Player.ME -> "Me"
    Player.PARTNER -> "My partner"
    Player.OPPONENT_1 -> "Opponent 1"
    Player.OPPONENT_2 -> "Opponent 2"
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

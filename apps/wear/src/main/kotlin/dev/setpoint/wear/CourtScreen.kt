package dev.setpoint.wear

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.Text
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Team
import kotlinx.coroutines.delay

private val MyTeamColor = Color(0xFF72CFA5)
private val OpponentsColor = Color(0xFF80BDDF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CourtScreen(
    match: MatchState,
    busy: Boolean,
    canUndo: Boolean,
    onAward: (Team) -> Unit,
    onUndo: () -> Unit,
    onHistory: () -> Unit,
    onMenu: () -> Unit,
) {
    var input by remember { mutableStateOf(PointInput()) }
    var rotaryBlockedUntil by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canScore = !busy && match.winner == null
    val currentBusy by rememberUpdatedState(busy)
    val clearSelection by rememberUpdatedState { input = PointInput() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) clearSelection()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(match) { input = PointInput() }
    LaunchedEffect(input) {
        if (input.team != null) {
            delay(3500)
            input = PointInput()
        }
    }
    fun accept(result: PointInputResult) {
        input = result.selection
        if (result.award != null) {
            rotaryBlockedUntil = SystemClock.uptimeMillis() + 650
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onAward(result.award)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    fun undo() {
        if (currentBusy) return
        input = PointInput()
        rotaryBlockedUntil = SystemClock.uptimeMillis() + 650
        if (canUndo) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onUndo()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .onRotaryScrollEvent { event ->
                if (canScore && SystemClock.uptimeMillis() >= rotaryBlockedUntil) {
                    accept(input.rotate(event.verticalScrollPixels))
                }
                true
            }.focusRequester(focusRequester).focusable(),
    ) {
        val width = maxWidth
        val height = maxHeight
        val serveLeft = if (match.server == Team.B) match.servingFromRight else !match.servingFromRight
        val ballX = if (serveLeft) 0.20f else 0.75f
        val ballY = if (match.server == Team.B) 0.28f else 0.68f
        // All four quarters are full-size touch targets; the score overlays do not intercept taps.
        Column(Modifier.fillMaxSize()) {
            listOf(Team.B, Team.A).forEach { team ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(2) { side ->
                        val teamLabel = if (team == Team.A) "My team" else "Opponents"
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(when {
                                input.team == team -> Color(0xFF3B5126)
                                team == Team.A -> Color(0xFF152D25)
                                else -> Color(0xFF14252D)
                            })
                            .semantics {
                                contentDescription = "$teamLabel, ${if (side == 0) "left" else "right"} court. Score ${match.pointLabel(team)}"
                                stateDescription = if (input.team == team) "Selected. Tap to confirm point" else "Tap to select"
                                customActions = listOf(CustomAccessibilityAction("Undo last point") { undo(); true })
                            }
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = if (input.team == team) "Confirm point" else "Select team",
                                onLongClickLabel = "Undo last point",
                                onClick = {
                                    focusRequester.requestFocus()
                                    if (canScore) accept(input.tap(team))
                                },
                                onLongClick = { undo() },
                            ))
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val line = Color(0xFF8AA59B).copy(alpha = 0.45f)
            val stroke = 1.dp.toPx()
            drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
            for (y in listOf(0.14f, 0.86f)) {
                drawLine(line, Offset(0f, size.height * y), Offset(size.width, size.height * y), stroke)
            }
            drawLine(Color(0xFFCAD6D0), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            if (match.winner == null) {
                val diameter = 12.dp.toPx()
                val origin = Offset(size.width * ballX, size.height * ballY)
                drawCircle(Lime, radius = diameter / 2, center = origin + Offset(diameter / 2, diameter / 2))
                drawArc(Color(0xFF4D6721), 110f, 140f, false,
                    topLeft = origin + Offset(diameter * 0.45f, 0f), size = Size(diameter, diameter),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            if (input.rotary && input.team != null) {
                drawArc(Lime, -90f, 360f * input.progress, false,
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            }
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp)
            .width(64.dp).height(30.dp).combinedClickable(onClick = onMenu)
            .semantics { contentDescription = "Main menu" }, contentAlignment = Alignment.Center) {
            Text("MENU", color = Muted, fontSize = 9.sp)
        }
        listOf(Team.B, Team.A).forEach { team ->
            val top = team == Team.B
            val selected = input.team == team
            // Measure the full text height instead of squeezing its baseline into a fixed box.
            Text(match.pointLabel(team),
                modifier = Modifier.align(Alignment.Center)
                    .offset(y = height * if (top) -0.25f else 0.25f),
                color = if (selected) Lime else Color.White,
                fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false)
        }
        if (match.winner == null) {
            // Even points serve from the right; the opponents' right is the viewer's left.
            Box(Modifier.offset(x = width * ballX, y = height * ballY)
                .size(12.dp).semantics {
                    val teamLabel = if (match.server == Team.A) "My team" else "Opponents"
                    contentDescription = "$teamLabel serving from ${if (match.servingFromRight) "right" else "left"}"
                })
        }
        Column(Modifier.align(Alignment.Center).width(104.dp).height(54.dp)
            .background(Color(0xFF080E0B), RoundedCornerShape(14.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Show set results", onLongClickLabel = "Show set results",
                onClick = { input = PointInput(); onHistory() },
                onLongClick = {
                    input = PointInput()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHistory()
                },
            ).semantics(mergeDescendants = true) {
                contentDescription = "Set ${match.completedSets.size + 1}. " +
                    "Games: my team ${match.gamesA}, opponents ${match.gamesB}. " +
                    "Sets won: my team ${match.sets(Team.A)}, opponents ${match.sets(Team.B)}. " +
                    "Tap for set results"
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Set ${match.completedSets.size + 1}${if (match.isTieBreak) " · TIE-BREAK" else ""}",
                color = Muted, fontSize = 7.sp, lineHeight = 9.sp,
                maxLines = 1, softWrap = false)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${match.gamesA}", color = MyTeamColor,
                    fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                Text(":", color = Muted, fontSize = 20.sp, lineHeight = 23.sp)
                Text("${match.gamesB}", color = OpponentsColor,
                    fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(3.dp))
            SetProgress(match, Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

@Composable
private fun SetProgress(match: MatchState, modifier: Modifier = Modifier) {
    val target = match.rules.setsToWinMatch
    // Each set won by the trailing team extends the shortest possible match by one.
    val slots = target?.let { it + minOf(match.sets(Team.A), match.sets(Team.B)) }
        ?: (match.completedSets.size + 1)
    // Endless matches keep the most recent results readable instead of shrinking forever.
    val first = if (target == null) (slots - 12).coerceAtLeast(0) else 0
    Canvas(modifier) {
        val count = slots - first
        val step = size.width / count
        val stroke = minOf(size.height, step / 3)
        val inset = minOf(2.dp.toPx(), step / 3)
        repeat(count) { index ->
            val set = match.completedSets.getOrNull(first + index)
            val color = when {
                set == null -> Muted.copy(alpha = 0.45f)
                set.a > set.b -> MyTeamColor
                else -> OpponentsColor
            }
            drawLine(color,
                Offset(step * index + inset, size.height / 2),
                Offset(step * (index + 1) - inset, size.height / 2),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

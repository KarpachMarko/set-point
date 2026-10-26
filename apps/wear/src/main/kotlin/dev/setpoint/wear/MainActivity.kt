package dev.setpoint.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.*
import dev.setpoint.core.scoring.Team

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val model: MatchViewModel = viewModel()
                val match by model.state.collectAsStateWithLifecycle()
                val canUndo by model.canUndo.collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Set Point", fontSize = 16.sp)
                    Text("Sets ${match.sets(Team.A)} : ${match.sets(Team.B)} · Games ${match.gamesA} : ${match.gamesB}", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Team.entries.forEach { team ->
                            Button(onClick = { model.award(team) },
                                enabled = match.winner == null,
                                modifier = Modifier.weight(1f).height(64.dp)) {
                                Text("${team.name}${if (match.server == team) " •" else ""} ${match.pointLabel(team)}")
                            }
                        }
                    }
                    if (match.winner != null) Text("Team ${match.winner} wins", fontSize = 14.sp)
                    TextButton(onClick = { model.undo() }, enabled = canUndo) { Text("Undo") }
                }
            }
        }
    }
}

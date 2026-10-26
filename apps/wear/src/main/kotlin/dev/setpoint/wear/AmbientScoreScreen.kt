package dev.setpoint.wear

import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Team

internal data class AmbientState(
    val active: Boolean = false,
    val lowBit: Boolean = false,
    val burnInProtection: Boolean = false,
    val timeMillis: Long = System.currentTimeMillis(),
)

/** Static, mostly black, with no scoring input. The OS sends the clock's ambient ticks. */
@Composable
internal fun AmbientScoreScreen(match: MatchState?, ambient: AmbientState, is24Hour: Boolean) {
    val time = DateFormat.format(if (is24Hour) "HH:mm" else "h:mm", ambient.timeMillis).toString()
    Canvas(Modifier.fillMaxSize().background(Color.Black).semantics {
        contentDescription = if (match == null) "Ambient clock. $time" else
            "Ambient match score. Opponents ${match.pointLabel(Team.B)}, " +
                "my team ${match.pointLabel(Team.A)}. Games ${match.gamesB} : ${match.gamesA}. $time"
    }) {
        val paint = Paint().apply {
            isAntiAlias = !ambient.lowBit
            color = if (ambient.lowBit) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        fun text(value: String, y: Float, textSize: Float, outline: Boolean = false) {
            paint.textSize = textSize
            paint.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = 1.dp.toPx()
            val baseline = y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            drawContext.canvas.nativeCanvas.drawText(value, size.width / 2, baseline, paint)
        }
        text(time, size.height * 0.09f, 11.sp.toPx())
        if (match == null) return@Canvas
        text(match.pointLabel(Team.B), size.height * 0.25f, 38.sp.toPx(), ambient.burnInProtection)
        text("${match.gamesB} : ${match.gamesA}", size.height * 0.5f, 20.sp.toPx())
        text(match.pointLabel(Team.A), size.height * 0.75f, 38.sp.toPx(), ambient.burnInProtection)
        text("Sets ${match.sets(Team.B)} : ${match.sets(Team.A)}", size.height * 0.9f, 10.sp.toPx())
    }
}

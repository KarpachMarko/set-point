package dev.setpoint.wear

import dev.setpoint.core.scoring.Team
import kotlin.math.abs

/** A separate selection step is mandatory for both touch and bezel input. */
internal data class PointInput(
    val team: Team? = null,
    val progress: Float = 0f,
    val rotary: Boolean = false,
) {
    fun tap(target: Team): PointInputResult = if (team == target) {
        PointInputResult(award = target)
    } else {
        PointInputResult(PointInput(team = target))
    }

    fun rotate(pixels: Float): PointInputResult {
        if (!pixels.isFinite() || pixels == 0f) return PointInputResult(this)
        val target = if (pixels > 0) Team.A else Team.B
        // Even a large first event only selects. Continuing in the same direction confirms.
        if (team != target || !rotary) {
            return PointInputResult(PointInput(target, 0f, rotary = true))
        }
        val next = progress + abs(pixels) / CONFIRM_PIXELS
        return if (next >= 1f) PointInputResult(award = target)
        else PointInputResult(copy(progress = next))
    }

    companion object { const val CONFIRM_PIXELS = 96f }
}

internal data class PointInputResult(val selection: PointInput = PointInput(), val award: Team? = null)

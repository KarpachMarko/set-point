package dev.setpoint.wear

import android.annotation.SuppressLint
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTrackedStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal enum class WorkoutAvailability { IDLE, ACTIVE, UNKNOWN }

// These public constants are documented for this check but carry a library restriction in 1.1.0.
@SuppressLint("RestrictedApi", "WrongConstant")
internal suspend fun checkWorkoutAvailability(
    timeoutMillis: Long = 2_500L,
    readInfo: suspend () -> ExerciseInfo,
): WorkoutAvailability = try {
    val info = withTimeoutOrNull(timeoutMillis) { readInfo() }
    when (info?.exerciseTrackedStatus) {
        ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS -> WorkoutAvailability.IDLE
        ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS,
        ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> WorkoutAvailability.ACTIVE
        else -> WorkoutAvailability.UNKNOWN
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    WorkoutAvailability.UNKNOWN
}

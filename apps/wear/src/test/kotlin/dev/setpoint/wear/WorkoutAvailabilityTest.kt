package dev.setpoint.wear

import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutAvailabilityTest {
    @Test fun `idle allows launch but either owner of an active workout prevents it`() = runBlocking {
        for ((status, expected) in listOf(
            ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS to WorkoutAvailability.IDLE,
            ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS to WorkoutAvailability.ACTIVE,
            ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS to WorkoutAvailability.ACTIVE,
            ExerciseTrackedStatus.UNKNOWN to WorkoutAvailability.UNKNOWN,
        )) {
            assertEquals(expected, checkWorkoutAvailability { ExerciseInfo(status, ExerciseType.UNKNOWN) })
        }
    }

    @Test fun `service errors prevent automatic launch`() = runBlocking {
        assertEquals(WorkoutAvailability.UNKNOWN, checkWorkoutAvailability {
            throw IllegalStateException("Health Services unavailable")
        })
        assertEquals(WorkoutAvailability.UNKNOWN, checkWorkoutAvailability {
            throw SecurityException("Query denied")
        })
    }

    @Test fun `hung queries time out instead of delaying navigation indefinitely`() = runBlocking {
        assertEquals(WorkoutAvailability.UNKNOWN,
            checkWorkoutAvailability(timeoutMillis = 10L) { awaitCancellation() })
    }

    @Test(expected = CancellationException::class)
    fun `leaving the activity cancels the query`() {
        runBlocking {
            checkWorkoutAvailability { throw CancellationException("Activity paused") }
        }
    }
}

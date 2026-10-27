package dev.setpoint.wear

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.health.services.client.HealthServices
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: MatchViewModel
    private var ambient by mutableStateOf(AmbientState())
    private var notificationsAllowed by mutableStateOf(false)
    private var resumeRequest by mutableIntStateOf(0)
    private var isResumed by mutableStateOf(false)
    private var launchingWorkout by mutableStateOf(false)
    private var workoutLaunchJob: Job? = null
    private val sessionPreferences by lazy { getSharedPreferences("session_ui", MODE_PRIVATE) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshNotifications()
        if (!granted) Toast.makeText(this,
            "Allow Set Point notifications in Settings to keep a match available after sleep.",
            Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[MatchViewModel::class.java]
        consumeResumeIntent(intent)
        lifecycle.addObserver(AmbientLifecycleObserver(this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    ambient = AmbientState(active = true,
                        lowBit = ambientDetails.deviceHasLowBitAmbient,
                        burnInProtection = ambientDetails.burnInProtectionRequired)
                }
                override fun onUpdateAmbient() {
                    ambient = ambient.copy(timeMillis = System.currentTimeMillis())
                }
                override fun onExitAmbient() { ambient = AmbientState() }
            }))
        refreshNotifications()
        setContent {
            MaterialTheme {
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.sessionActive, isResumed, launchingWorkout) {
                    if (state.sessionActive && isResumed && !launchingWorkout && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED &&
                        !sessionPreferences.getBoolean("notificationPermissionRequested", false)) {
                        enableMatchNotifications()
                    }
                }
                SetPointApp(state, model, ambient, DateFormat.is24HourFormat(this), resumeRequest,
                    notificationsAllowed, onEnableNotifications = ::enableMatchNotifications,
                    onResumeRequestHandled = { resumeRequest = 0 },
                    onNewMatchStarted = ::launchSamsungWorkout)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeResumeIntent(intent)
    }

    private fun consumeResumeIntent(intent: Intent) {
        if (intent.action == MatchSessionNotification.ACTION_RESUME_MATCH) {
            resumeRequest++
            setIntent(Intent(intent).setAction(null))
        }
    }

    override fun onResume() {
        super.onResume()
        launchingWorkout = false
        isResumed = true
        if (::model.isInitialized) refreshNotifications()
    }

    override fun onPause() {
        isResumed = false
        workoutLaunchJob?.cancel()
        super.onPause()
    }

    private fun launchSamsungWorkout() {
        if (!model.state.value.launchWorkoutOnStart || launchingWorkout) return
        val matchId = model.state.value.match?.id ?: return
        launchingWorkout = true
        workoutLaunchJob = lifecycleScope.launch {
            var launched = false
            try {
                val availability = checkWorkoutAvailability {
                    HealthServices.getClient(this@MainActivity).exerciseClient.getCurrentExerciseInfo()
                }
                // Never navigate after the user has left or started a different match.
                val current = model.state.value
                if (!isResumed || current.match?.id != matchId || !current.sessionActive ||
                    !current.launchWorkoutOnStart) return@launch
                if (availability == WorkoutAvailability.UNKNOWN) {
                    Log.w("SetPointWorkout", "Active workout status unavailable; skipping launch")
                    Toast.makeText(this@MainActivity,
                        "Couldn't check active workouts. Start Samsung Health manually if needed.",
                        Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (availability == WorkoutAvailability.ACTIVE) {
                    Log.i("SetPointWorkout", "Skipping workout launch: an exercise is already active")
                    return@launch
                }
                launched = openSamsungWorkout()
            } finally {
                if (!launched) launchingWorkout = false
            }
        }
    }

    private fun openSamsungWorkout(): Boolean {
        // The installed Samsung handler reads this string and calls ExerciseType.valueOf().
        // Without it, Samsung opens an empty screen and logs "invalid type name: null".
        return try {
            Log.i("SetPointWorkout", "Requesting Samsung Health workout: exercise.type=TENNIS")
            startActivity(Intent("com.samsung.android.wear.shealth.intent.action.START_WORKOUT")
                .setPackage("com.samsung.android.wear.shealth")
                // Keep the workout in Samsung Health's task so Set Point stays accessible separately.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("exercise.type", "TENNIS"))
            true
        } catch (error: ActivityNotFoundException) {
            Log.w("SetPointWorkout", "Samsung Health workout action is unavailable", error)
            Toast.makeText(this, "Samsung Health workout action is unavailable.", Toast.LENGTH_LONG).show()
            false
        } catch (error: SecurityException) {
            Log.w("SetPointWorkout", "Samsung Health rejected the workout action", error)
            Toast.makeText(this, "Samsung Health did not allow starting a workout.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun refreshNotifications() {
        notificationsAllowed = model.notificationsAllowed()
        model.refreshSessionNotification()
    }

    private fun enableMatchNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !sessionPreferences.getBoolean("notificationPermissionRequested", false)) {
            sessionPreferences.edit { putBoolean("notificationPermissionRequested", true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }
}

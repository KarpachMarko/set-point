package dev.setpoint.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: MatchViewModel
    private var ambient by mutableStateOf(AmbientState())
    private var notificationsAllowed by mutableStateOf(false)
    private var resumeRequest by mutableIntStateOf(0)
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
                LaunchedEffect(state.sessionActive) {
                    if (state.sessionActive && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED &&
                        !sessionPreferences.getBoolean("notificationPermissionRequested", false)) {
                        enableMatchNotifications()
                    }
                }
                SetPointApp(state, model, ambient, DateFormat.is24HourFormat(this), resumeRequest,
                    notificationsAllowed, onEnableNotifications = ::enableMatchNotifications,
                    onResumeRequestHandled = { resumeRequest = 0 })
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
        if (::model.isInitialized) refreshNotifications()
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

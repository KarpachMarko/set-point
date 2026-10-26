package dev.setpoint.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import dev.setpoint.core.scoring.MatchState
import dev.setpoint.core.scoring.Team

/** A static score needs no background polling or foreground service. The OS owns this indicator. */
internal class MatchSessionNotification(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var matchId: String? = null
    private var builder: NotificationCompat.Builder? = null
    private var ongoing: OngoingActivity? = null

    init {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Match in progress", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Keeps the current match available on your watch" })
    }

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun update(match: MatchState?) {
        if (match == null || match.winner != null || !notificationsAllowed()) {
            clear()
            return
        }
        val score = "${match.pointLabel(Team.A)} : ${match.pointLabel(Team.B)} · Games ${match.gamesA} : ${match.gamesB}"
        val status = Status.Builder().addTemplate(score).build()
        try {
            if (matchId != match.id || ongoing == null) {
                val resume = PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_RESUME_MATCH
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_match)
                    .setContentTitle("Padel match in progress")
                    .setContentText(score)
                    .setSubText("My team : Opponents")
                    .setContentIntent(resume)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setShowWhen(false)
                    .setLocalOnly(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                ongoing = OngoingActivity.Builder(context, NOTIFICATION_ID, checkNotNull(builder))
                    .setStaticIcon(R.drawable.ic_match)
                    .setTouchIntent(resume)
                    .setStatus(status)
                    .build()
                checkNotNull(ongoing).apply(context)
                matchId = match.id
                // The runtime permission is checked above, and revocation races are handled below.
                @Suppress("MissingPermission")
                manager.notify(NOTIFICATION_ID, checkNotNull(builder).build())
            } else {
                builder?.setContentText(score)
                @Suppress("MissingPermission")
                checkNotNull(ongoing).update(context, status)
            }
        } catch (_: SecurityException) {
            clear()
        }
    }

    private fun clear() {
        manager.cancel(NOTIFICATION_ID)
        matchId = null
        builder = null
        ongoing = null
    }

    companion object {
        const val ACTION_RESUME_MATCH = "dev.setpoint.wear.RESUME_MATCH"
        const val CHANNEL_ID = "match_session"
        const val NOTIFICATION_ID = 1
    }
}

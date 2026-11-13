package pk.auditgrid.audittest.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.AuditGridApiClient

/**
 * Background Android Service that continues polling `/api/mobile/sync` even when the mobile application UI is closed.
 * Automatically persists any newly assigned or updated duties, linked cities/territories/distributors/shops,
 * product updates, and audit form updates into `AuditGridMobileDatabase`, and triggers native Android
 * system notifications immediately whenever a duty is assigned, modified, or removed.
 */
class DutyBackgroundSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    companion object {
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context.applicationContext, DutyBackgroundSyncService::class.java)
                context.applicationContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context.applicationContext, DutyBackgroundSyncService::class.java)
                context.applicationContext.stopService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AuditNotificationHelper.initChannel(this)
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AuditNotificationHelper.initChannel(this)
        if (pollJob == null || pollJob?.isActive != true) {
            startPollingLoop()
        }
        return START_STICKY
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            val apiClient = AuditGridApiClient(applicationContext)
            while (isActive) {
                runCatching {
                    if (apiClient.hasStoredSession()) {
                        val auditorId = apiClient.currentAuditorId()
                        if (auditorId.isNotBlank() || apiClient.currentAuditorUsername().isNotBlank()) {
                            val syncResult = apiClient.syncDutiesAndNotifications(auditorId)
                            val effectiveId = syncResult.auditorId.ifBlank { apiClient.currentAuditorId() }
                            AuditNotificationHelper.processDutiesAndNotifications(
                                context = applicationContext,
                                auditorId = effectiveId,
                                duties = syncResult.duties,
                                notifications = syncResult.notifications
                            )
                        }
                    }
                }
                delay(15_000L)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep background duty & notification polling alive when app is swiped away from recents
        runCatching {
            val restartIntent = Intent(applicationContext, DutyBackgroundSyncService::class.java).apply {
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                2026,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 5_000L,
                pendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

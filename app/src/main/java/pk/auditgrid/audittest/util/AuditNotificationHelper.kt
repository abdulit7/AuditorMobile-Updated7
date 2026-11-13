package pk.auditgrid.audittest.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import pk.auditgrid.audittest.MainActivity
import pk.auditgrid.audittest.R
import pk.auditgrid.audittest.model.AuditorDuty
import pk.auditgrid.audittest.model.AuditorNotification
import kotlin.math.abs

object AuditNotificationHelper {
    private const val CHANNEL_ID = "auditgrid_duties_alerts_v3"
    private const val CHANNEL_NAME = "Duty Assignments & Updates"
    private const val PREFS_NAME = "auditgrid_notification_prefs"
    private const val SHOWN_IDS_KEY = "shown_notification_ids_v3"
    private const val DUTY_SIGS_PREFIX = "duty_signatures_v3_"
    private const val DUTY_CODES_PREFIX = "duty_codes_v3_"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications for newly assigned duties and any changes in duty schedule, area, or status."
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @Synchronized
    fun hasShownNotification(context: Context, notifId: String): Boolean {
        if (notifId.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(SHOWN_IDS_KEY, emptySet()) ?: emptySet()
        return shown.contains(notifId)
    }

    @Synchronized
    fun markNotificationShown(context: Context, notifId: String) {
        if (notifId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(SHOWN_IDS_KEY, emptySet()) ?: emptySet()).toMutableSet()
        current.add(notifId)
        prefs.edit().putStringSet(SHOWN_IDS_KEY, current).apply()
    }

    private fun dutySignature(d: AuditorDuty): String = listOf(
        d.code.trim(),
        d.status.trim().lowercase(),
        d.scheduledDate.trim(),
        d.title.trim(),
        d.cityId.trim(),
        d.cityIds.sorted().joinToString(","),
        d.cityName.trim(),
        d.cityNames.sorted().joinToString(","),
        d.territoryId.trim(),
        d.territoryIds.sorted().joinToString(","),
        d.territoryName.trim(),
        d.territoryNames.sorted().joinToString(","),
        d.notes.trim(),
        d.distributorsCount.toString(),
        d.shopsCount.toString(),
        d.updatedAt.trim()
    ).joinToString("|")

    private fun formatDutySummaryMessage(d: AuditorDuty): String {
        val citiesLabel = when {
            d.cityNames.isNotEmpty() -> d.cityNames.joinToString(", ")
            d.cityName.isNotBlank() -> d.cityName
            else -> "Assigned Area"
        }
        val territoriesLabel = when {
            d.territoryNames.isNotEmpty() -> d.territoryNames.joinToString(", ")
            d.territoryName.isNotBlank() -> d.territoryName
            else -> "All Localities"
        }
        val datePart = d.scheduledDate.ifBlank { "Scheduled" }
        val notesPart = if (d.notes.isNotBlank()) " · ${d.notes}" else ""
        return "Date: $datePart · Town: $citiesLabel · Localities: $territoriesLabel$notesPart"
    }

    /**
     * Inspects both server notifications and the synced duties list for any new or changed duties.
     * Whenever a duty is newly assigned, modified (date, status, city, territory, notes, etc.),
     * or removed, pushes a system notification directly to the Android notification panel.
     */
    @Synchronized
    fun processDutiesAndNotifications(
        context: Context,
        auditorId: String,
        duties: List<AuditorDuty>,
        notifications: List<AuditorNotification>
    ): List<AuditorNotification> {
        val appCtx = context.applicationContext
        initChannel(appCtx)

        // If notification permission is not yet granted on Android 13+, do not mark notifications
        // or duty snapshots as shown yet so they fire as soon as permission is granted.
        if (!hasNotificationPermission(appCtx)) {
            return emptyList()
        }

        val generatedNotifications = mutableListOf<AuditorNotification>()
        val notifiedDutyIdsThisTurn = mutableSetOf<String>()

        // 1. Push any unread server notifications
        val unread = notifications.filter { !it.isRead }
        for (notif in unread) {
            val key = "srv_notif_${notif.id}_${notif.createdAt}"
            if (!hasShownNotification(appCtx, key)) {
                markNotificationShown(appCtx, key)
                notif.dutyId?.let { notifiedDutyIdsThisTurn.add(it) }
                val intId = abs(key.hashCode()).coerceAtLeast(1001)
                postSystemNotification(
                    context = appCtx,
                    id = intId,
                    title = notif.title.ifBlank { "Duty Update" },
                    message = notif.message.ifBlank { "You have a new duty update." },
                    dutyId = notif.dutyId
                )
            }
        }

        if (auditorId.isBlank()) return generatedNotifications

        // 2. Compare duties against stored snapshot to detect any new or changed duties
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sigKey = "$DUTY_SIGS_PREFIX$auditorId"
        val codeKey = "$DUTY_CODES_PREFIX$auditorId"
        val existingSigsRaw = prefs.getString(sigKey, null)
        val existingCodesRaw = prefs.getString(codeKey, null)

        val oldSigs = mutableMapOf<String, String>()
        if (!existingSigsRaw.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(existingSigsRaw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    oldSigs[k] = json.optString(k, "")
                }
            }
        }

        val oldCodes = mutableMapOf<String, String>()
        if (!existingCodesRaw.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(existingCodesRaw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    oldCodes[k] = json.optString(k, "")
                }
            }
        }

        val isFirstSnapshot = existingSigsRaw == null
        val newSigsJson = JSONObject()
        val newCodesJson = JSONObject()

        for (duty in duties) {
            val newSig = dutySignature(duty)
            newSigsJson.put(duty.id, newSig)
            newCodesJson.put(duty.id, duty.code.ifBlank { duty.id })

            val prevSig = oldSigs[duty.id]
            if (prevSig == null) {
                // Newly assigned duty
                val changeKey = "duty_new_${auditorId}_${duty.id}_${newSig.hashCode()}"
                if (!notifiedDutyIdsThisTurn.contains(duty.id) && !hasShownNotification(appCtx, changeKey)) {
                    markNotificationShown(appCtx, changeKey)
                    val title = "New Duty Assigned: ${duty.code.ifBlank { duty.title.ifBlank { "Audit Duty" } }}"
                    val msg = formatDutySummaryMessage(duty)
                    val intId = abs(changeKey.hashCode()).coerceAtLeast(2001)
                    postSystemNotification(
                        context = appCtx,
                        id = intId,
                        title = title,
                        message = msg,
                        dutyId = duty.id
                    )
                    generatedNotifications.add(
                        AuditorNotification(
                            id = "local-new-${duty.id}-${abs(newSig.hashCode())}",
                            userId = auditorId,
                            title = title,
                            message = msg,
                            type = "duty_assigned",
                            entityType = "duty",
                            entityId = duty.id,
                            isRead = false,
                            createdAt = duty.updatedAt.ifBlank { duty.createdAt.ifBlank { currentIsoTimestamp() } }
                        )
                    )
                }
            } else if (!isFirstSnapshot && prevSig != newSig) {
                // Existing duty was modified (date, status, area, notes, etc.)
                val changeKey = "duty_chg_${auditorId}_${duty.id}_${newSig.hashCode()}"
                if (!notifiedDutyIdsThisTurn.contains(duty.id) && !hasShownNotification(appCtx, changeKey)) {
                    markNotificationShown(appCtx, changeKey)
                    val title = "Duty Updated: ${duty.code} (${duty.status})"
                    val msg = formatDutySummaryMessage(duty)
                    val intId = abs(changeKey.hashCode()).coerceAtLeast(3001)
                    postSystemNotification(
                        context = appCtx,
                        id = intId,
                        title = title,
                        message = msg,
                        dutyId = duty.id
                    )
                    generatedNotifications.add(
                        AuditorNotification(
                            id = "local-chg-${duty.id}-${abs(newSig.hashCode())}",
                            userId = auditorId,
                            title = title,
                            message = msg,
                            type = "duty_updated",
                            entityType = "duty",
                            entityId = duty.id,
                            isRead = false,
                            createdAt = duty.updatedAt.ifBlank { currentIsoTimestamp() }
                        )
                    )
                }
            }
        }

        // Check for removed/unassigned duties
        if (!isFirstSnapshot) {
            val currentIds = duties.map { it.id }.toSet()
            for ((oldId, _) in oldSigs) {
                if (oldId !in currentIds) {
                    val code = oldCodes[oldId] ?: oldId
                    val removeKey = "duty_rem_${auditorId}_${oldId}_${duties.size}"
                    if (!hasShownNotification(appCtx, removeKey)) {
                        markNotificationShown(appCtx, removeKey)
                        val title = "Duty Unassigned: $code"
                        val msg = "Duty $code was removed or reassigned on the web portal."
                        val intId = abs(removeKey.hashCode()).coerceAtLeast(4001)
                        postSystemNotification(
                            context = appCtx,
                            id = intId,
                            title = title,
                            message = msg,
                            dutyId = oldId
                        )
                    }
                }
            }
        }

        prefs.edit()
            .putString(sigKey, newSigsJson.toString())
            .putString(codeKey, newCodesJson.toString())
            .apply()

        return generatedNotifications
    }

    fun showDutyNotification(
        context: Context,
        title: String,
        message: String,
        dutyId: String? = null,
        notificationId: String? = null
    ) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        val key = notificationId?.takeIf { it.isNotBlank() } ?: "${dutyId.orEmpty()}_${title.hashCode()}_${message.hashCode()}"
        if (hasShownNotification(appCtx, key)) return
        markNotificationShown(appCtx, key)
        val intId = abs(key.hashCode()).coerceAtLeast(1001)
        postSystemNotification(appCtx, intId, title, message, dutyId)
    }

    fun showDutyNotification(
        context: Context,
        id: Int,
        title: String,
        message: String,
        dutyId: String? = null
    ) {
        postSystemNotification(context.applicationContext, id, title, message, dutyId)
    }

    private fun postSystemNotification(
        context: Context,
        id: Int,
        title: String,
        message: String,
        dutyId: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_section", "DUTIES")
            if (!dutyId.isNullOrBlank()) {
                putExtra("duty_id", dutyId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duty_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                nm.notify(id, notification)
            } else {
                NotificationManagerCompat.from(context).notify(id, notification)
            }
        }
    }
}

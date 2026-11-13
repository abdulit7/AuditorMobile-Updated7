package pk.auditgrid.audittest.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val DEFAULT_ALLOWED_RADIUS_M = 150
const val MAX_ACCEPTABLE_ACCURACY_M = 100

data class DevicePosition(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val at: String
)

enum class GpsErrorKind {
    DENIED, UNAVAILABLE, TIMEOUT, UNSUPPORTED
}

class GpsException(val kind: GpsErrorKind) : Exception(gpsErrorMessage(kind))

fun gpsErrorMessage(kind: GpsErrorKind): String = when (kind) {
    GpsErrorKind.DENIED ->
        "Location permission is off. Allow location for this app in Android settings, then tap \"Try again\"."
    GpsErrorKind.TIMEOUT ->
        "Getting your location took too long. Move to an open area and tap \"Try again\"."
    GpsErrorKind.UNSUPPORTED ->
        "This device does not support location services."
    GpsErrorKind.UNAVAILABLE ->
        "Your location is not available right now. Check that device location is on and tap \"Try again\"."
}

fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

data class MobileLocationStatus(
    val hasPermission: Boolean,
    val isServiceEnabled: Boolean
) {
    val isReady: Boolean
        get() = hasPermission && isServiceEnabled
}

fun isDeviceLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    val masterEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching { lm.isLocationEnabled }.getOrDefault(false)
    } else {
        true
    }
    val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val netEnabled = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    return masterEnabled && (gpsEnabled || netEnabled)
}

fun isMobileLocationReadyForAudit(context: Context): Boolean =
    hasLocationPermission(context) && isDeviceLocationEnabled(context)

fun getMobileLocationStatus(context: Context): MobileLocationStatus =
    MobileLocationStatus(
        hasPermission = hasLocationPermission(context),
        isServiceEnabled = isDeviceLocationEnabled(context)
    )

fun openDeviceLocationSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun openAppLocationPermissionSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Composable
fun rememberMobileLocationStatus(context: Context): State<MobileLocationStatus> {
    val state = remember { mutableStateOf(getMobileLocationStatus(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val updated = getMobileLocationStatus(context)
                if (!updated.isReady) {
                    WarmLocationManager.clearPosition()
                }
                state.value = updated
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(context) {
        while (isActive) {
            val updated = getMobileLocationStatus(context)
            if (!updated.isReady) {
                WarmLocationManager.clearPosition()
            }
            if (state.value != updated) {
                state.value = updated
            }
            delay(800L)
        }
    }

    return state
}

/**
 * Background Location Pre-Fetcher (WarmLocationManager):
 * Starts listening to both GPS_PROVIDER and NETWORK_PROVIDER in parallel as soon as an auditor
 * opens any audit form (Distributor, Retail Shop, Custom Form) or opens a location/shop creation modal.
 * When the user taps "Save" or "Submit", the pre-fetched GPS position is returned in 0 ms.
 */
object WarmLocationManager {
    private val _latestPosition = MutableStateFlow<DevicePosition?>(null)
    val latestPosition: StateFlow<DevicePosition?> = _latestPosition.asStateFlow()

    private val _isWarming = MutableStateFlow(false)
    val isWarming: StateFlow<Boolean> = _isWarming.asStateFlow()

    @Volatile
    private var lastFixWallClockMs: Long = 0L

    @Volatile
    var onFixCaptured: ((DevicePosition) -> Unit)? = null

    private var activeListener: LocationListener? = null
    private var activeRefCount: Int = 0

    fun clearPosition() {
        _latestPosition.value = null
        lastFixWallClockMs = 0L
    }

    fun recordFix(pos: DevicePosition, wallClockMs: Long = System.currentTimeMillis()) {
        val cur = _latestPosition.value
        val ageMs = wallClockMs - lastFixWallClockMs
        if (cur == null || ageMs > 8_000L || pos.accuracy <= cur.accuracy + 8.0) {
            _latestPosition.value = pos
            lastFixWallClockMs = wallClockMs
            runCatching { onFixCaptured?.invoke(pos) }
        }
    }

    fun getFreshWarmPosition(maxAgeMs: Long = 90_000L): DevicePosition? {
        val pos = _latestPosition.value ?: return null
        val age = System.currentTimeMillis() - lastFixWallClockMs
        return if (age in 0..maxAgeMs) pos else null
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startWarming(context: Context) {
        if (!isMobileLocationReadyForAudit(context)) {
            clearPosition()
            return
        }
        val appCtx = context.applicationContext
        val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        activeRefCount++
        // Immediately seed from any recent lastKnownLocation (< 10 minutes, or latest available)
        val now = System.currentTimeMillis()
        val allLastCandidates = listOfNotNull(
            runCatching { lm.getLastKnownLocation("fused") }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
        )
        val recentCandidates = allLastCandidates.filter { it.time > 0 && now - it.time <= 600_000L }
        (recentCandidates.minByOrNull { it.accuracy } ?: allLastCandidates.maxByOrNull { it.time })?.let { best ->
            recordFix(best.toDevicePosition(), best.time.takeIf { it > 0 } ?: now)
        }

        if (activeListener != null) return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                recordFix(location.toDevicePosition(), System.currentTimeMillis())
            }
        }
        activeListener = listener
        _isWarming.value = true

        val providers = buildList {
            if (runCatching { lm.isProviderEnabled("fused") }.getOrDefault(false)) {
                add("fused")
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }

        for (p in providers) {
            runCatching {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    @Synchronized
    fun stopWarming(context: Context) {
        activeRefCount = (activeRefCount - 1).coerceAtLeast(0)
        if (activeRefCount > 0) return
        val listener = activeListener ?: return
        activeListener = null
        _isWarming.value = false
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching { lm?.removeUpdates(listener) }
    }
}

@SuppressLint("MissingPermission")
suspend fun getDevicePosition(context: Context, timeoutMs: Long = 8000L): DevicePosition {
    if (!hasLocationPermission(context)) {
        WarmLocationManager.clearPosition()
        throw GpsException(GpsErrorKind.DENIED)
    }

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: throw GpsException(GpsErrorKind.UNSUPPORTED)

    if (!isDeviceLocationEnabled(context)) {
        WarmLocationManager.clearPosition()
        throw GpsException(GpsErrorKind.UNAVAILABLE)
    }

    // 1. Instant 0ms return if WarmLocationManager already pre-fetched a fresh fix while the form was open!
    WarmLocationManager.getFreshWarmPosition(maxAgeMs = 120_000L)?.let { warm ->
        return warm
    }

    val fusedEnabled = runCatching { lm.isProviderEnabled("fused") }.getOrDefault(false)
    val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val netEnabled = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    val passiveEnabled = runCatching { lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) }.getOrDefault(false)
    if (!fusedEnabled && !gpsEnabled && !netEnabled && !passiveEnabled) {
        WarmLocationManager.clearPosition()
        throw GpsException(GpsErrorKind.UNAVAILABLE)
    }

    // 2. Check recent last-known location across all providers (within 2 minutes)
    val now = System.currentTimeMillis()
    val candidates = listOfNotNull(
        runCatching { lm.getLastKnownLocation("fused") }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
    )
    val freshLast = candidates
        .filter { it.time > 0 && now - it.time <= 120_000L }
        .minByOrNull { it.accuracy }

    if (freshLast != null) {
        val pos = freshLast.toDevicePosition()
        WarmLocationManager.recordFix(pos)
        return pos
    }

    // 3. Race FUSED, GPS_PROVIDER, and NETWORK_PROVIDER simultaneously so whichever locks first wins immediately (works offline via GPS/Fused)
    val providers = buildList {
        if (fusedEnabled) add("fused")
        if (gpsEnabled) add(LocationManager.GPS_PROVIDER)
        if (netEnabled) add(LocationManager.NETWORK_PROVIDER)
        if (passiveEnabled && isEmpty()) add(LocationManager.PASSIVE_PROVIDER)
    }

    val loc = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Location?> { cont ->
            val resolved = AtomicBoolean(false)
            val signals = mutableListOf<CancellationSignal>()
            val listeners = mutableListOf<LocationListener>()

            fun cleanup() {
                signals.forEach { runCatching { it.cancel() } }
                listeners.forEach { runCatching { lm.removeUpdates(it) } }
            }

            cont.invokeOnCancellation { cleanup() }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                for (provider in providers) {
                    val signal = CancellationSignal()
                    signals.add(signal)
                    try {
                        lm.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (location != null && resolved.compareAndSet(false, true)) {
                                cleanup()
                                if (cont.isActive) cont.resume(location)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                for (provider in providers) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (resolved.compareAndSet(false, true)) {
                                cleanup()
                                if (cont.isActive) cont.resume(location)
                            }
                        }
                    }
                    listeners.add(listener)
                    try {
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } catch (_: Exception) {
                    }
                }
            }
        }
    } ?: (candidates.maxByOrNull { it.time } ?: candidates.minByOrNull { it.accuracy })

    if (loc != null) {
        val pos = loc.toDevicePosition()
        WarmLocationManager.recordFix(pos)
        return pos
    }

    // 4. Final offline fallback: if WarmLocationManager captured a position earlier in the session, return it
    WarmLocationManager.latestPosition.value?.let { cached ->
        if (hasCoords(cached.lat, cached.lng)) {
            return cached
        }
    }

    throw GpsException(GpsErrorKind.TIMEOUT)
}

internal fun Location.toDevicePosition(): DevicePosition {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(if (time > 0) time else System.currentTimeMillis()))
    return DevicePosition(
        lat = latitude,
        lng = longitude,
        accuracy = if (hasAccuracy()) accuracy.toDouble() else 25.0,
        at = iso
    )
}

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val rad = { d: Double -> d * Math.PI / 180.0 }
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2.0) * sin(dLng / 2.0)
    return 2.0 * r * asin(min(1.0, sqrt(a)))
}

fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int =
    haversineMeters(lat1, lng1, lat2, lng2).roundToInt()

fun makeReportCode(prefix: String): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val rand = (1..4).map { chars.random() }.joinToString("")
    val stamp = System.currentTimeMillis().toString(36).takeLast(4).uppercase(Locale.US)
    val year = Calendar.getInstance().get(Calendar.YEAR)
    return "$prefix-$year-$stamp$rand"
}

fun newSubmitKey(): String = UUID.randomUUID().toString()

fun pktToday(): String {
    val pktMillis = System.currentTimeMillis() + 5L * 3600_000L
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(pktMillis))
}

fun prettyDate(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(dateStr) ?: return dateStr
        val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fmt.format(date)
    } catch (_: Exception) {
        dateStr
    }
}

fun currentReportDateDisplay(): String {
    val cal = Calendar.getInstance()
    val months = arrayOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    )
    return "${cal.get(Calendar.DAY_OF_MONTH)} - ${months[cal.get(Calendar.MONTH)]} - ${cal.get(Calendar.YEAR)}."
}

fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun currentTimePkt(): String = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} PKT"

fun currentSqlUtcTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

fun currentIsoTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

fun directionsUrl(lat: Double, lng: Double, fromLat: Double? = null, fromLng: Double? = null): String {
    val origin = if (fromLat != null && fromLng != null) "&origin=$fromLat,$fromLng" else ""
    return "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving$origin"
}

fun hasCoords(lat: Double, lng: Double): Boolean =
    !(lat == 0.0 && lng == 0.0) && abs(lat) <= 90.0 && abs(lng) <= 180.0

fun compressBitmapToDataUrl(bitmap: Bitmap, maxDim: Int = 1600, quality: Int = 72): String {
    val maxSide = max(bitmap.width, bitmap.height)
    val scale = min(1.0f, maxDim.toFloat() / maxSide.toFloat())
    val scaled = if (scale < 1.0f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

suspend fun compressUriToDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: return@withContext null
            compressBitmapToDataUrl(bmp)
        }
    } catch (_: Exception) {
        null
    }
}

fun dataUrlToBytes(dataUrl: String): Pair<String, ByteArray> {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return "image/jpeg" to ByteArray(0)
    val head = dataUrl.substring(0, comma)
    val b64 = dataUrl.substring(comma + 1)
    val mime = Regex("data:([^;]+)").find(head)?.groupValues?.getOrNull(1) ?: "image/jpeg"
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    return mime to bytes
}

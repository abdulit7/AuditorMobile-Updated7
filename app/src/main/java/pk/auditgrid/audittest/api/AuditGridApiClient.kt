package pk.auditgrid.audittest.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.BuildConfig
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.util.currentIsoTimestamp
import pk.auditgrid.audittest.util.dataUrlToBytes
import pk.auditgrid.audittest.util.makeReportCode
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiError(
    override val message: String,
    val status: Int,
    val code: String? = null
) : Exception(message) {
    val isNetwork: Boolean get() = status == 0 || status >= 500
}

class AuditGridApiClient(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auditgrid_prefs", Context.MODE_PRIVATE)

    val mobileDb: AuditGridMobileDatabase = AuditGridMobileDatabase(context.applicationContext)

    @Volatile
    private var accessToken: String? = null
    private val refreshMutex = Mutex()
    var onSessionEnded: (() -> Unit)? = null
    var onConnectivityChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val BASE_KEY = "ag.apiBase"
        private const val RT_KEY = "ag.refreshToken"
        private const val SESSION_USER_KEY = "ag.session.username"
        private const val SESSION_USER_ID_KEY = "ag.session.userId"
        private const val SESSION_PASS_KEY = "ag.session.pass"
        private const val LOCAL_SESSION_KEY = "ag.session.local"
        private const val LOGGED_OUT_KEY = "ag.session.loggedOut"

        fun normalizeBase(v: String): String = v.trim().trimEnd('/')
    }

    init {
        // Purge any legacy dummy or fake local fallback data from earlier builds
        val wasFakeLocalSession = prefs.getBoolean(LOCAL_SESSION_KEY, false)
        val savedUserId = prefs.getString(SESSION_USER_ID_KEY, "") ?: ""
        if (wasFakeLocalSession ||
            savedUserId.startsWith("usr-local-") ||
            prefs.contains("ag.demoMode") ||
            prefs.contains("ag.demoSeeded") ||
            prefs.contains("ag.completed.dist.usr-demo-auditor-01") ||
            prefs.contains("ag.completed.retail.usr-demo-auditor-01") ||
            prefs.contains("ag.completed.custom.usr-demo-auditor-01")
        ) {
            val ed = prefs.edit()
                .remove(LOCAL_SESSION_KEY)
                .remove("ag.demoMode")
                .remove("ag.demoSeeded")
                .remove("ag.demo.webCreatedCount")
                .remove("ag.completed.dist.usr-demo-auditor-01")
                .remove("ag.completed.retail.usr-demo-auditor-01")
                .remove("ag.completed.custom.usr-demo-auditor-01")
            if (wasFakeLocalSession || savedUserId.startsWith("usr-local-")) {
                ed.remove(SESSION_USER_KEY)
                ed.remove(SESSION_USER_ID_KEY)
                ed.remove(RT_KEY)
            }
            ed.apply()
        }
    }

    fun currentAuditorId(): String =
        prefs.getString(SESSION_USER_ID_KEY, null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(SESSION_USER_KEY, null)?.let { mobileDb.loadSessionByUsername(it)?.user?.id }
            ?: ""

    fun currentAuditorUsername(): String =
        prefs.getString(SESSION_USER_KEY, null)?.takeIf { it.isNotBlank() }
            ?: ""

    fun getApiBase(): String {
        val stored = prefs.getString(BASE_KEY, null)
        if (!stored.isNullOrBlank() && !stored.startsWith("demo://")) return normalizeBase(stored)
        val envUrl = try {
            val field = BuildConfig::class.java.getDeclaredField("VITE_API_URL")
            field.get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
        return normalizeBase(envUrl).ifBlank { "http://192.168.1.20:3001" }
    }

    fun getConfiguredServerUrl(): String = getApiBase()

    fun setApiBase(v: String) {
        val normalized = normalizeBase(v)
        if (normalized.isNotBlank()) {
            prefs.edit().putString(BASE_KEY, normalized).apply()
        }
    }

    fun hasStoredSession(): Boolean {
        if (prefs.getBoolean(LOGGED_OUT_KEY, false)) return false
        val savedUsername = prefs.getString(SESSION_USER_KEY, null)
        if (!savedUsername.isNullOrBlank() && mobileDb.loadSessionByUsername(savedUsername) != null) {
            return true
        }
        return !prefs.getString(RT_KEY, null).isNullOrBlank() && getConfiguredServerUrl().isNotBlank()
    }

    private fun applySession(p: JSONObject, passwordPlain: String? = null): Session {
        accessToken = p.optString("accessToken").takeIf { it.isNotEmpty() }
        val rt = p.optString("refreshToken")
        val userObj = p.optJSONObject("user") ?: JSONObject()
        val parsedUser = AuditGridJsonParsers.parseUser(userObj)
        val roleObj = p.optJSONObject("role")
        val session = Session(
            user = parsedUser,
            role = roleObj?.let { AuditGridJsonParsers.parseRole(it) },
            surface = p.optString("surface", "mobile"),
            isGlobal = p.optBoolean("isGlobal", false)
        )
        val ed = prefs.edit()
            .putBoolean(LOCAL_SESSION_KEY, false)
            .putBoolean(LOGGED_OUT_KEY, false)
            .putString(SESSION_USER_KEY, parsedUser.username)
            .putString(SESSION_USER_ID_KEY, parsedUser.id)
            .remove(SESSION_PASS_KEY)
        if (rt.isNotEmpty()) {
            ed.putString(RT_KEY, rt)
        }
        ed.apply()
        mobileDb.saveUserSession(session, passwordPlain)
        return session
    }

    suspend fun checkServer(base: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${normalizeBase(base)}/api/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                json.optBoolean("ok", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendRaw(
        path: String,
        method: String = "GET",
        jsonBody: Any? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val base = getConfiguredServerUrl()
        if (base.isBlank()) {
            throw ApiError("Server address is not configured.", 0)
        }
        try {
            val url = URL(base + path)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                if (method == "PATCH") {
                    try {
                        requestMethod = "PATCH"
                    } catch (_: Exception) {
                        requestMethod = "POST"
                        setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    }
                } else {
                    requestMethod = method
                }
                val token = accessToken
                if (!token.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val audId = currentAuditorId()
                if (audId.isNotBlank()) {
                    setRequestProperty("X-Auditor-Id", audId)
                }
                val audUser = currentAuditorUsername()
                if (audUser.isNotBlank()) {
                    setRequestProperty("X-Auditor-Username", audUser)
                }
            }
            if (jsonBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val bytes = jsonBody.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (status in 200..499) {
                runCatching { onConnectivityChanged?.invoke(true) }
            } else if (status >= 500) {
                runCatching { onConnectivityChanged?.invoke(false) }
            }
            status to text
        } catch (e: ApiError) {
            if (e.isNetwork) {
                runCatching { onConnectivityChanged?.invoke(false) }
            }
            throw e
        } catch (_: Exception) {
            runCatching { onConnectivityChanged?.invoke(false) }
            throw ApiError(
                "Cannot reach the server. Check the server address, your Wi-Fi/mobile data, and that the server is running.",
                0
            )
        }
    }

    private fun readError(status: Int, body: String): ApiError {
        return try {
            val j = JSONObject(body)
            val err = j.optString("error").ifEmpty { "Request failed ($status)." }
            val code = j.optString("code").takeIf { it.isNotEmpty() }
            ApiError(err, status, code)
        } catch (_: Exception) {
            ApiError("Request failed ($status).", status)
        }
    }

    suspend fun refreshSession(): Session? = refreshMutex.withLock {
        val savedUser = prefs.getString(SESSION_USER_KEY, null)
        val token = prefs.getString(RT_KEY, null)
        if (token.isNullOrBlank()) {
            return@withLock savedUser?.let { mobileDb.loadSessionByUsername(it) }
        }
        try {
            val payload = JSONObject().apply {
                put("client", "native")
                put("refreshToken", token)
            }
            val (status, body) = sendRaw("/api/auth/refresh", "POST", payload)
            if (status !in 200..299) {
                accessToken = null
                if (status == 401) {
                    prefs.edit().remove(RT_KEY).apply()
                }
                return@withLock savedUser?.let { mobileDb.loadSessionByUsername(it) }
            }
            applySession(JSONObject(body))
        } catch (e: ApiError) {
            if (e.isNetwork) {
                if (!savedUser.isNullOrBlank()) {
                    val cached = mobileDb.loadSessionByUsername(savedUser)
                    if (cached != null) return@withLock cached
                }
                throw e
            }
            savedUser?.let { mobileDb.loadSessionByUsername(it) }
        } catch (_: Exception) {
            savedUser?.let { mobileDb.loadSessionByUsername(it) }
        }
    }

    suspend fun requestJson(
        path: String,
        method: String = "GET",
        jsonBody: Any? = null
    ): String {
        var (status, body) = sendRaw(path, method, jsonBody)
        if (status == 401 && !path.startsWith("/api/auth/")) {
            val refreshed = runCatching { refreshSession() }.getOrNull()
            if (refreshed != null) {
                val retry = sendRaw(path, method, jsonBody)
                status = retry.first
                body = retry.second
            } else {
                val savedUser = prefs.getString(SESSION_USER_KEY, null)
                val hasLocalUser = !savedUser.isNullOrBlank() && mobileDb.loadSessionByUsername(savedUser) != null
                if (!hasLocalUser) {
                    accessToken = null
                    onSessionEnded?.invoke()
                }
                throw ApiError("Your session has ended. Please sign in again.", 401, "unauthenticated")
            }
        }
        if (status !in 200..299) {
            throw readError(status, body)
        }
        return body
    }

    suspend fun login(username: String, password: String): Session {
        val cleanUser = username.trim()
        val payload = JSONObject().apply {
            put("username", cleanUser)
            put("password", password)
            put("surface", "mobile")
            put("client", "native")
        }
        return try {
            val (status, body) = sendRaw("/api/auth/login", "POST", payload)
            if (status !in 200..299) throw readError(status, body)
            applySession(JSONObject(body), passwordPlain = password)
        } catch (e: ApiError) {
            if (e.isNetwork) {
                // Only allow offline login if this user was previously downloaded from the server into mobileDb
                val offlineSession = mobileDb.authenticateOfflineUser(cleanUser, password)
                if (offlineSession != null) {
                    prefs.edit()
                        .putBoolean(LOCAL_SESSION_KEY, false)
                        .putBoolean(LOGGED_OUT_KEY, false)
                        .putString(SESSION_USER_KEY, offlineSession.user.username)
                        .putString(SESSION_USER_ID_KEY, offlineSession.user.id)
                        .remove(SESSION_PASS_KEY)
                        .apply()
                    return offlineSession
                }
                throw ApiError(
                    "Cannot reach the server (${getConfiguredServerUrl()}). Please connect to the server to sign in and download your user account, role, cities, territories, and forms.",
                    0
                )
            }
            throw e
        }
    }

    /**
     * Auto-syncs mobile users, roles, geography, places, and forms from the MySQL server
     * (`GET /api/auth/mobile-sync`) into `AuditGridMobileDatabase` before or during login,
     * and when starting the mobile application.
     */
    suspend fun syncMobileUsersPreLogin(baseOverride: String? = null): List<AppUser> = withContext(Dispatchers.IO) {
        val targetBase = normalizeBase(baseOverride ?: getConfiguredServerUrl())
        if (targetBase.isBlank() || !Regex("^https?://.+", RegexOption.IGNORE_CASE).matches(targetBase)) {
            throw ApiError("Please enter a valid server address (e.g. http://192.168.1.20:3000).", 0)
        }
        setApiBase(targetBase)
        val (status, body) = sendRaw("/api/auth/mobile-sync", "GET")
        if (status !in 200..299) {
            throw readError(status, body)
        }
        val json = JSONObject(body)
        val dummySession = Session(
            user = AppUser(id = "", code = "", username = "", fullName = "", roleId = ""),
            role = null,
            surface = "mobile",
            isGlobal = false
        )
        val parsedBoot = AuditGridJsonParsers.parseBootstrap(json, dummySession)
        val authHashes = mutableMapOf<String, String>()
        val usersArr = json.optJSONArray("users") ?: JSONArray()
        for (i in 0 until usersArr.length()) {
            val uObj = usersArr.optJSONObject(i) ?: continue
            val uid = uObj.optString("id")
            val hash = uObj.optString("mobileAuthHash")
            if (uid.isNotBlank() && hash.isNotBlank()) {
                authHashes[uid] = hash
            }
        }
        mobileDb.syncMobileUsersFromServer(parsedBoot, authHashes)
    }

    fun getLocalSyncedMobileUsers(): List<AppUser> =
        mobileDb.getSyncedMobileUsers()

    suspend fun restoreSession(): Session? {
        if (prefs.getBoolean(LOGGED_OUT_KEY, false)) {
            return null
        }
        val savedUser = prefs.getString(SESSION_USER_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        if (!hasStoredSession()) {
            return null
        }
        val restored = try {
            refreshSession() ?: mobileDb.loadSessionByUsername(savedUser)
        } catch (_: Exception) {
            mobileDb.loadSessionByUsername(savedUser)
        }
        if (restored == null || !restored.user.status.equals("Active", ignoreCase = true)) {
            accessToken = null
            prefs.edit()
                .remove(RT_KEY)
                .remove(SESSION_USER_KEY)
                .remove(SESSION_USER_ID_KEY)
                .apply()
            return null
        }
        return restored
    }

    suspend fun logout() {
        val token = prefs.getString(RT_KEY, null)
        if (!token.isNullOrEmpty()) {
            try {
                val payload = JSONObject().apply {
                    put("client", "native")
                    put("refreshToken", token)
                }
                sendRaw("/api/auth/logout", "POST", payload)
            } catch (_: Exception) {
            }
        }
        accessToken = null
        prefs.edit()
            .remove(RT_KEY)
            .remove(SESSION_USER_KEY)
            .remove(SESSION_USER_ID_KEY)
            .remove(SESSION_PASS_KEY)
            .putBoolean(LOCAL_SESSION_KEY, false)
            .putBoolean(LOGGED_OUT_KEY, true)
            .apply()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val payload = JSONObject().apply {
            put("currentPassword", currentPassword)
            put("newPassword", newPassword)
        }
        requestJson("/api/auth/change-password", "POST", payload)
    }

    /**
     * Downloads users, roles, cities, territories, locations, sub-locations, shops,
     * distributors, product groups, products, and forms from the server, links them
     * to each user, and saves everything into `AuditGridMobileDatabase`.
     */
    suspend fun fetchBootstrap(session: Session): BootstrapPayload {
        return try {
            val body = requestJson("/api/bootstrap")
            val parsed = AuditGridJsonParsers.parseBootstrap(JSONObject(body), session)
            val updatedSession = withContext(Dispatchers.IO) {
                mobileDb.saveBootstrapForUser(session, parsed)
            }
            prefs.edit()
                .putString(SESSION_USER_KEY, updatedSession.user.username)
                .putString(SESSION_USER_ID_KEY, updatedSession.user.id)
                .apply()
            parsed
        } catch (e: ApiError) {
            if (e.isNetwork) {
                withContext(Dispatchers.IO) {
                    mobileDb.loadBootstrapForUser(session)
                } ?: throw e
            } else {
                throw e
            }
        }
    }

    fun getUpdatedSessionFromMobileDb(currentSession: Session): Session {
        return mobileDb.loadSessionByUsername(currentSession.user.username) ?: currentSession
    }

    suspend fun fetchForms(): List<AuditFormDefinition> {
        return try {
            val body = requestJson("/api/forms")
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseFormDefinition(it) }
            }
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                if (uid.isNotBlank()) mobileDb.loadFormsForUser(uid) else emptyList()
            } else {
                throw e
            }
        }
    }

    suspend fun fetchFormBundle(): FormBundlePayload {
        val body = requestJson("/api/forms/bundle")
        return AuditGridJsonParsers.parseFormBundle(JSONObject(body))
    }

    /**
     * Imports a Form Package (.json) manually and merges its forms & field configs
     * into the user's mobile SQLite database (`AuditGridMobileDatabase`).
     */
    fun importFormBundleJson(auditorId: String, rawJson: String): FormBundlePayload {
        val parsedBundle = AuditGridJsonParsers.parseFormBundleFromRawJson(rawJson)
        if (parsedBundle.forms.isEmpty()) {
            throw IllegalArgumentException("Form package JSON does not contain any audit forms.")
        }
        // Ensure every imported form has a default config entry if not explicitly provided
        val enrichedConfigs = parsedBundle.formConfigs.toMutableMap()
        for (f in parsedBundle.forms) {
            if (!enrichedConfigs.containsKey(f.id)) {
                enrichedConfigs[f.id] = FormConfigPayload(formId = f.id, version = f.version.coerceAtLeast(1))
            }
        }
        val normalizedBundle = parsedBundle.copy(formConfigs = enrichedConfigs)
        val (mergedForms, mergedConfigs) = mobileDb.importManualFormBundleForUser(auditorId, normalizedBundle)
        return normalizedBundle.copy(
            forms = mergedForms,
            formConfigs = mergedConfigs
        )
    }

    fun saveServerSyncedForms(
        auditorId: String,
        serverForms: List<AuditFormDefinition>,
        serverConfigs: FormConfigMap,
        bundleHash: String = ""
    ): Pair<List<AuditFormDefinition>, FormConfigMap> {
        return mobileDb.saveServerFormsForUser(auditorId, serverForms, serverConfigs, bundleHash)
    }

    fun loadCachedBundleHash(auditorId: String): String =
        mobileDb.loadBundleHashForUser(auditorId)

    fun saveCachedBundleHash(auditorId: String, hash: String) {
        mobileDb.saveBundleHashForUser(auditorId, hash)
    }

    suspend fun createLocation(location: LocationItem): LocationItem {
        val payload = AuditGridJsonParsers.serializeLocation(location)
        return try {
            val res = requestJson("/api/locations", "POST", payload)
            val saved = AuditGridJsonParsers.parseLocation(JSONObject(res))
            withContext(Dispatchers.IO) {
                mobileDb.upsertLocation(saved)
            }
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                val localLoc = if (location.id.isBlank()) {
                    location.copy(id = "loc-mob-${UUID.randomUUID().toString().take(8)}")
                } else location
                withContext(Dispatchers.IO) {
                    mobileDb.upsertLocation(localLoc)
                    if (uid.isNotBlank()) {
                        mobileDb.enqueueSyncAction(
                            userId = uid,
                            actionType = "create_location",
                            targetId = localLoc.id,
                            payloadJson = AuditGridJsonParsers.serializeLocation(localLoc).toString()
                        )
                    }
                }
                localLoc
            } else {
                throw e
            }
        }
    }

    suspend fun createSubLocation(subLocation: SubLocation): SubLocation {
        val payload = AuditGridJsonParsers.serializeSubLocation(subLocation)
        return try {
            val res = requestJson("/api/sub-locations", "POST", payload)
            val saved = AuditGridJsonParsers.parseSubLocation(JSONObject(res))
            withContext(Dispatchers.IO) {
                mobileDb.upsertSubLocation(saved)
            }
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                val localSub = if (subLocation.id.isBlank()) {
                    subLocation.copy(id = "sub-mob-${UUID.randomUUID().toString().take(8)}")
                } else subLocation
                withContext(Dispatchers.IO) {
                    mobileDb.upsertSubLocation(localSub)
                    if (uid.isNotBlank()) {
                        mobileDb.enqueueSyncAction(
                            userId = uid,
                            actionType = "create_sub_location",
                            targetId = localSub.id,
                            payloadJson = AuditGridJsonParsers.serializeSubLocation(localSub).toString()
                        )
                    }
                }
                localSub
            } else {
                throw e
            }
        }
    }

    suspend fun createShop(shop: Shop): Shop {
        val payload = AuditGridJsonParsers.serializeShop(shop)
        return try {
            val res = requestJson("/api/shops", "POST", payload)
            val saved = AuditGridJsonParsers.parseShop(JSONObject(res))
            withContext(Dispatchers.IO) {
                mobileDb.upsertShop(saved)
            }
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                val localShop = if (shop.id.isBlank()) {
                    shop.copy(id = "shop-mob-${UUID.randomUUID().toString().take(8)}")
                } else shop
                withContext(Dispatchers.IO) {
                    mobileDb.upsertShop(localShop)
                    if (uid.isNotBlank()) {
                        mobileDb.enqueueSyncAction(
                            userId = uid,
                            actionType = "create_shop",
                            targetId = localShop.id,
                            payloadJson = AuditGridJsonParsers.serializeShop(localShop).toString()
                        )
                    }
                }
                localShop
            } else {
                throw e
            }
        }
    }

    suspend fun updatePlaceGps(
        targetType: String,
        targetId: String,
        lat: Double,
        lng: Double,
        accuracy: Double
    ): JSONObject {
        val payload = JSONObject().apply {
            put("targetType", targetType)
            put("targetId", targetId)
            put("lat", lat)
            put("lng", lng)
            put("accuracy", accuracy)
        }
        return try {
            val res = requestJson("/api/mobile/gps", "PATCH", payload)
            withContext(Dispatchers.IO) {
                mobileDb.updateLocalPlaceGps(targetType, targetId, lat, lng)
            }
            JSONObject(res)
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                withContext(Dispatchers.IO) {
                    mobileDb.updateLocalPlaceGps(targetType, targetId, lat, lng)
                    if (uid.isNotBlank()) {
                        mobileDb.enqueueSyncAction(
                            userId = uid,
                            actionType = "update_place_gps",
                            targetId = targetId,
                            payloadJson = payload.toString()
                        )
                    }
                }
                JSONObject().apply {
                    put("ok", true)
                    put("offline", true)
                }
            } else {
                throw e
            }
        }
    }

    suspend fun fetchFormConfig(formId: String): FormConfigPayload {
        return try {
            val body = requestJson("/api/forms/$formId/config")
            AuditGridJsonParsers.parseFormConfig(JSONObject(body))
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val uid = currentAuditorId()
                val cached = if (uid.isNotBlank()) mobileDb.loadFormConfigsForUser(uid)[formId] else null
                cached ?: FormConfigPayload(formId = formId)
            } else {
                throw e
            }
        }
    }

    suspend fun fetchDistributorReports(): List<DistributorProductAuditReport> {
        val body = requestJson("/api/reports/distributor")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseDistributorReport(it) }
        }
    }

    suspend fun fetchRetailReports(): List<RetailMarketShopAuditReport> {
        val body = requestJson("/api/reports/retail")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseRetailReport(it) }
        }
    }

    suspend fun fetchCustomSubmissions(): List<CustomAuditSubmission> {
        val body = requestJson("/api/submissions")
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditGridJsonParsers.parseCustomSubmission(it) }
        }
    }

    suspend fun uploadPhotoDataUrl(dataUrl: String): String = withContext(Dispatchers.IO) {
        if (dataUrl.startsWith("/api/photos/")) {
            return@withContext dataUrl.substringAfterLast('/')
        }
        val (mime, bytes) = dataUrlToBytes(dataUrl)
        val boundary = "----AuditGridBoundary${UUID.randomUUID()}"
        val base = getApiBase()

        fun doUpload(): Pair<Int, String> {
            val url = URL("$base/api/photos")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                doOutput = true
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                val audId = currentAuditorId()
                if (audId.isNotBlank()) {
                    setRequestProperty("X-Auditor-Id", audId)
                }
                val audUser = currentAuditorUsername()
                if (audUser.isNotBlank()) {
                    setRequestProperty("X-Auditor-Username", audUser)
                }
            }
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return status to text
        }

        var (status, body) = try {
            doUpload()
        } catch (_: Exception) {
            throw ApiError("Could not upload photo. Check your network connection.", 0)
        }
        if (status == 401) {
            val s = runCatching { refreshSession() }.getOrNull()
            if (s != null) {
                val retry = doUpload()
                status = retry.first
                body = retry.second
            }
        }
        if (status !in 200..299) throw readError(status, body)
        JSONObject(body).optString("id")
    }

    suspend fun submitDistributorReport(report: DistributorProductAuditReport): DistributorProductAuditReport {
        val photosArr = JSONArray()
        for (img in report.capturedImages) {
            val photoId = uploadPhotoDataUrl(img.url)
            photosArr.put(JSONObject().apply {
                put("photoId", photoId)
                put("caption", img.caption)
            })
        }
        val itemsArr = JSONArray()
        for (item in report.items) {
            itemsArr.put(JSONObject().apply {
                put("productId", item.productId)
                put("qtyInCtn", item.qtyInCtn)
                put("qtyInBox", item.qtyInBox)
                put("dispatchDate", item.dispatchDate)
                put("stacking", item.stacking)
                put("remarks", item.remarks)
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", report.id)
            put("distributorId", report.distributorId)
            put("items", itemsArr)
            put("mobileLat", report.mobileLat)
            put("mobileLng", report.mobileLng)
            put("mobileAccuracyMeters", report.mobileAccuracyMeters)
            put("photos", photosArr)
            report.overallComments?.let { put("overallComments", it) }
            report.planId?.let { put("planId", it) }
            report.planItemId?.let { put("planItemId", it) }
        }
        val res = requestJson("/api/reports/distributor", "POST", payload)
        return AuditGridJsonParsers.parseDistributorReport(JSONObject(res))
    }

    suspend fun submitRetailShopReport(report: RetailMarketShopAuditReport): RetailMarketShopAuditReport {
        val shopAuditsArr = JSONArray()
        for (sa in report.shopAudits) {
            val photosArr = JSONArray()
            for (img in sa.capturedImages) {
                val photoId = uploadPhotoDataUrl(img.url)
                photosArr.put(JSONObject().apply {
                    put("photoId", photoId)
                    put("caption", img.caption)
                })
            }
            shopAuditsArr.put(JSONObject().apply {
                put("shopId", sa.shopId)
                put("shopStatus", sa.shopStatus.ifBlank { "Open" })
                put("productACount", sa.productACount)
                put("productBCount", sa.productBCount)
                put("displayRating", sa.displayRating)
                put("packedProductQty", sa.packedProductQty)
                put("regularity", sa.regularity)
                if (sa.salesFloTagged.isNotBlank()) put("salesFloTagged", sa.salesFloTagged)
                if (sa.doubleTag.isNotBlank()) put("doubleTag", sa.doubleTag)
                if (sa.obVisit.isNotBlank()) put("obVisit", sa.obVisit)
                if (sa.computerizedBill.isNotBlank()) put("computerizedBill", sa.computerizedBill)
                put("remarks", sa.remarks)
                put("auditedAt", sa.auditedAt)
                put("mobileLat", sa.mobileLat)
                put("mobileLng", sa.mobileLng)
                put("mobileAccuracyMeters", sa.mobileAccuracyMeters)
                put("photos", photosArr)
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", report.id)
            put("subLocationId", report.subLocationId)
            put("shopAudits", shopAuditsArr)
            report.planId?.let { put("planId", it) }
            report.planItemId?.let { put("planItemId", it) }
        }
        val res = requestJson("/api/reports/retail", "POST", payload)
        return AuditGridJsonParsers.parseRetailReport(JSONObject(res))
    }

    suspend fun submitCustomAudit(
        clientRequestId: String,
        formId: String,
        targetType: String,
        targetId: String,
        answers: Map<String, Any?>,
        photos: List<AuditCapturedImage>,
        mobileLat: Double,
        mobileLng: Double,
        mobileAccuracyMeters: Double,
        planLink: PlanLink?,
        fallbackMeta: CustomAuditSubmission? = null
    ): CustomAuditSubmission {
        val stringAnswers = mutableMapOf<String, String>()
        for ((k, v) in answers) {
            if (v != null && v != "") {
                stringAnswers[k] = v.toString()
            }
        }
        val photosArr = JSONArray()
        for (img in photos) {
            val photoId = uploadPhotoDataUrl(img.url)
            photosArr.put(JSONObject().apply {
                put("photoId", photoId)
                put("caption", img.caption)
            })
        }
        val ansObj = JSONObject()
        for ((k, v) in answers) {
            if (v != null && v != "") {
                ansObj.put(k, v)
            }
        }
        val payload = JSONObject().apply {
            put("clientRequestId", clientRequestId)
            put("formId", formId)
            put("targetType", targetType)
            put("targetId", targetId)
            put("answers", ansObj)
            put("photos", photosArr)
            put("mobileLat", mobileLat)
            put("mobileLng", mobileLng)
            put("mobileAccuracyMeters", mobileAccuracyMeters)
            planLink?.let {
                put("planId", it.planId)
                put("planItemId", it.itemId)
            }
        }
        val res = requestJson("/api/submissions", "POST", payload)
        val parsed = AuditGridJsonParsers.parseCustomSubmission(JSONObject(res))
        return parsed.copy(
            formId = parsed.formId.ifEmpty { formId },
            formCode = parsed.formCode.ifEmpty { fallbackMeta?.formCode ?: "" },
            formTitle = parsed.formTitle.ifEmpty { fallbackMeta?.formTitle ?: "" },
            targetType = parsed.targetType.ifEmpty { targetType },
            targetId = parsed.targetId.ifEmpty { targetId },
            targetCode = parsed.targetCode.ifEmpty { fallbackMeta?.targetCode ?: "" },
            targetName = parsed.targetName.ifEmpty { fallbackMeta?.targetName ?: "" },
            cityName = parsed.cityName.ifEmpty { fallbackMeta?.cityName ?: "" },
            territoryCode = parsed.territoryCode.ifEmpty { fallbackMeta?.territoryCode ?: "" },
            territoryName = parsed.territoryName.ifEmpty { fallbackMeta?.territoryName ?: "" },
            submittedDate = parsed.submittedDate.ifEmpty { fallbackMeta?.submittedDate ?: "" },
            submittedTime = parsed.submittedTime.ifEmpty { fallbackMeta?.submittedTime ?: "" },
            mobileLat = if (parsed.mobileLat != 0.0) parsed.mobileLat else mobileLat,
            mobileLng = if (parsed.mobileLng != 0.0) parsed.mobileLng else mobileLng,
            mobileAccuracyMeters = if (parsed.mobileAccuracyMeters != 0) parsed.mobileAccuracyMeters else mobileAccuracyMeters.toInt(),
            distanceMeters = if (parsed.distanceMeters != 0) parsed.distanceMeters else (fallbackMeta?.distanceMeters ?: 0),
            allowedRadiusMeters = if (parsed.allowedRadiusMeters != 0) parsed.allowedRadiusMeters else (fallbackMeta?.allowedRadiusMeters ?: 150),
            answers = if (parsed.answers.isNotEmpty()) parsed.answers else stringAnswers,
            capturedImages = if (parsed.capturedImages.isNotEmpty()) parsed.capturedImages else photos
        )
    }

    suspend fun fetchPlans(inProgressOnly: Boolean = false): List<AuditPlan> {
        val uid = currentAuditorId()
        return try {
            if (uid.isNotBlank()) {
                runCatching { flushPendingSyncActions(uid) }
            }
            val path = if (inProgressOnly) "/api/plans?status=In%20Progress" else "/api/plans"
            val body = requestJson(path)
            val arr = JSONArray(body)
            val parsed = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { AuditGridJsonParsers.parsePlan(it) }
            }
            if (uid.isNotBlank()) {
                val merged = withContext(Dispatchers.IO) {
                    val localPlans = mobileDb.loadPlansForUser(uid)
                    val pendingActions = mobileDb.loadPendingSyncActions(uid)
                    val outboxItems = mobileDb.loadReportItemsForUser(uid, "outbox", AuditGridJsonParsers::parsePendingOutboxItem)
                    val pendingPlanTargets = pendingActions.map { it.targetId }.toSet()
                    val deletedPlanTargets = pendingActions.filter { it.actionType == "delete_plan" }.map { it.targetId }.toSet()

                    // Map of planItemId -> (reportKind, reportId, reportCode) from queued outbox reports
                    val outboxDoneMap = mutableMapOf<String, Triple<String, String, String>>()
                    for (ob in outboxItems) {
                        ob.distributorReport?.let { dr ->
                            if (!dr.planItemId.isNullOrBlank()) {
                                outboxDoneMap[dr.planItemId] = Triple("distributor", dr.id, dr.reportCode)
                            }
                        }
                        ob.retailReport?.let { rr ->
                            if (!rr.planItemId.isNullOrBlank()) {
                                outboxDoneMap[rr.planItemId] = Triple("retail", rr.id, rr.reportCode)
                            }
                        }
                        ob.customSubmission?.let { cs ->
                            if (!cs.planItemId.isNullOrBlank()) {
                                outboxDoneMap[cs.planItemId] = Triple("custom", cs.id, cs.reportCode)
                            }
                        }
                    }

                    val localById = localPlans.associateBy { it.id }
                    val serverMerged = parsed.filterNot { it.id in deletedPlanTargets }.map { srvPlan ->
                        val locPlan = localById[srvPlan.id]
                        val hasPending = srvPlan.id in pendingPlanTargets || srvPlan.items.any { it.id in outboxDoneMap }
                        if (!hasPending || locPlan == null) {
                            srvPlan
                        } else {
                            val nextItems = srvPlan.items.map { srvItem ->
                                val locItem = locPlan.items.find { it.id == srvItem.id }
                                val obDone = outboxDoneMap[srvItem.id]
                                when {
                                    obDone != null && srvItem.status == "Pending" -> srvItem.copy(
                                        status = "Done",
                                        reportKind = obDone.first,
                                        reportId = obDone.second,
                                        reportCode = obDone.third,
                                        doneAt = locItem?.doneAt ?: currentIsoTimestamp()
                                    )
                                    locItem != null && locItem.status != "Pending" && srvItem.status == "Pending" -> locItem
                                    else -> srvItem
                                }
                            }
                            val allDone = nextItems.isNotEmpty() && nextItems.all { it.status == "Done" || it.status == "Skipped" }
                            val nextStatus = when {
                                allDone -> "Completed"
                                locPlan.status == "Cancelled" -> "Cancelled"
                                locPlan.status == "In Progress" && srvPlan.status == "Planned" -> "In Progress"
                                else -> srvPlan.status
                            }
                            srvPlan.copy(
                                status = nextStatus,
                                startedAt = srvPlan.startedAt ?: locPlan.startedAt,
                                items = nextItems
                            )
                        }
                    }
                    val serverIds = serverMerged.map { it.id }.toSet()
                    val unsyncedLocalPlans = localPlans.filter {
                        (it.id.startsWith("pln-local-") || it.id in pendingPlanTargets) &&
                            it.id !in serverIds &&
                            it.id !in deletedPlanTargets
                    }
                    val combined = unsyncedLocalPlans + serverMerged
                    if (!inProgressOnly) {
                        mobileDb.savePlansForUser(uid, combined)
                    }
                    if (inProgressOnly) combined.filter { it.status == "In Progress" } else combined
                }
                merged
            } else {
                parsed
            }
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                val all = withContext(Dispatchers.IO) { mobileDb.loadPlansForUser(uid) }
                if (inProgressOnly) all.filter { it.status == "In Progress" } else all
            } else {
                throw e
            }
        }
    }

    suspend fun createPlan(
        clientRequestId: String,
        planDate: String,
        title: String?,
        items: List<PlanItem>
    ): AuditPlan {
        val uid = currentAuditorId()
        val itemsArr = JSONArray()
        for (i in items) {
            itemsArr.put(JSONObject().apply {
                put("formId", i.formId)
                put("targetType", i.targetType)
                put("targetId", i.targetId)
                put("shopIds", JSONArray(i.shopIds))
            })
        }
        val payload = JSONObject().apply {
            put("clientRequestId", clientRequestId)
            put("planDate", planDate)
            if (!title.isNullOrBlank()) put("title", title.trim())
            put("items", itemsArr)
        }
        return try {
            val res = requestJson("/api/plans", "POST", payload)
            val created = AuditGridJsonParsers.parsePlan(JSONObject(res))
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, listOf(created) + existing.filter { it.id != created.id })
                }
            }
            created
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val cachedForms = mobileDb.loadFormsForUser(uid).associateBy { it.id }
                    val localPlanId = "pln-local-$clientRequestId"
                    val resolvedItems = items.mapIndexed { idx, item ->
                        val resolvedFormTitle = item.formTitle.ifBlank {
                            when (item.formId) {
                                "distributor" -> "Distributor Product Audit"
                                "retail" -> "Retail Market Shop Audit"
                                else -> cachedForms[item.formId]?.title ?: item.formId
                            }
                        }
                        item.copy(
                            id = item.id.ifBlank { "pli-local-$clientRequestId-${idx + 1}" },
                            formTitle = resolvedFormTitle,
                            status = "Pending"
                        )
                    }
                    val localPlan = AuditPlan(
                        id = localPlanId,
                        auditorId = uid,
                        auditorName = currentAuditorUsername(),
                        planDate = planDate,
                        title = title?.trim()?.ifBlank { "Daily Plan — $planDate" } ?: "Daily Plan — $planDate",
                        status = "Planned",
                        startedAt = null,
                        items = resolvedItems
                    )
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, listOf(localPlan) + existing.filter { it.id != localPlan.id })
                    val actionPayload = JSONObject(payload.toString()).apply {
                        put("localPlanId", localPlanId)
                    }
                    mobileDb.enqueueSyncAction(
                        userId = uid,
                        actionType = "create_plan",
                        targetId = localPlanId,
                        payloadJson = actionPayload.toString()
                    )
                    localPlan
                }
            } else {
                throw e
            }
        }
    }

    suspend fun startPlan(planId: String): AuditPlan {
        val uid = currentAuditorId()
        var effectivePlanId = planId
        if (effectivePlanId.startsWith("pln-local-") && uid.isNotBlank()) {
            runCatching { flushPendingSyncActions(uid) }
        }
        return try {
            if (effectivePlanId.startsWith("pln-local-")) {
                throw ApiError("Offline local plan", 0)
            }
            val res = requestJson("/api/plans/$effectivePlanId/start", "POST", JSONObject())
            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == effectivePlanId || it.id == planId) updated else it })
                }
            }
            updated
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    val target = existing.find { it.id == effectivePlanId || it.id == planId }
                        ?: throw e
                    val nowIso = currentIsoTimestamp()
                    val updated = target.copy(
                        status = "In Progress",
                        startedAt = target.startedAt ?: nowIso
                    )
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == target.id) updated else it })
                    mobileDb.enqueueSyncAction(
                        userId = uid,
                        actionType = "start_plan",
                        targetId = target.id,
                        payloadJson = JSONObject().apply {
                            put("planId", target.id)
                            put("startedAt", nowIso)
                        }.toString()
                    )
                    updated
                }
            } else {
                throw e
            }
        }
    }

    suspend fun cancelPlan(planId: String): AuditPlan {
        val uid = currentAuditorId()
        if (planId.startsWith("pln-local-") && uid.isNotBlank()) {
            runCatching { flushPendingSyncActions(uid) }
        }
        return try {
            if (planId.startsWith("pln-local-")) {
                throw ApiError("Offline local plan", 0)
            }
            val res = requestJson("/api/plans/$planId/cancel", "POST", JSONObject())
            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == planId) updated else it })
                }
            }
            updated
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    val target = existing.find { it.id == planId } ?: throw e
                    val updated = target.copy(
                        status = "Cancelled"
                    )
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == target.id) updated else it })
                    mobileDb.enqueueSyncAction(
                        userId = uid,
                        actionType = "cancel_plan",
                        targetId = target.id,
                        payloadJson = JSONObject().apply {
                            put("planId", target.id)
                        }.toString()
                    )
                    updated
                }
            } else {
                throw e
            }
        }
    }

    suspend fun deletePlan(planId: String) {
        val uid = currentAuditorId()
        if (planId.startsWith("pln-local-")) {
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.filterNot { it.id == planId })
                    mobileDb.removePendingSyncActionsForTarget(uid, planId)
                }
            }
            return
        }
        try {
            requestJson("/api/plans/$planId", "DELETE")
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.filterNot { it.id == planId })
                }
            }
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.filterNot { it.id == planId })
                    mobileDb.enqueueSyncAction(
                        userId = uid,
                        actionType = "delete_plan",
                        targetId = planId,
                        payloadJson = JSONObject().apply { put("planId", planId) }.toString()
                    )
                }
            } else {
                throw e
            }
        }
    }

    suspend fun skipPlanItem(planId: String, itemId: String, reason: String): AuditPlan {
        val uid = currentAuditorId()
        val payload = JSONObject().apply {
            put("status", "Skipped")
            put("reason", reason.trim())
        }
        if ((planId.startsWith("pln-local-") || itemId.startsWith("pli-local-")) && uid.isNotBlank()) {
            runCatching { flushPendingSyncActions(uid) }
        }
        return try {
            if (planId.startsWith("pln-local-") || itemId.startsWith("pli-local-")) {
                throw ApiError("Offline local plan item", 0)
            }
            val res = requestJson("/api/plans/$planId/items/$itemId", "PATCH", payload)
            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
            if (uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == planId) updated else it })
                }
            }
            updated
        } catch (e: ApiError) {
            if (e.isNetwork && uid.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val existing = mobileDb.loadPlansForUser(uid)
                    val target = existing.find { it.id == planId } ?: throw e
                    val nowIso = currentIsoTimestamp()
                    val nextItems = target.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                status = "Skipped",
                                skipReason = reason.trim(),
                                doneAt = nowIso
                            )
                        } else {
                            item
                        }
                    }
                    val allFinished = nextItems.isNotEmpty() && nextItems.all { it.status == "Done" || it.status == "Skipped" }
                    val nextStatus = if (allFinished) "Completed" else target.status
                    val updated = target.copy(
                        status = nextStatus,
                        items = nextItems
                    )
                    mobileDb.savePlansForUser(uid, existing.map { if (it.id == target.id) updated else it })
                    mobileDb.enqueueSyncAction(
                        userId = uid,
                        actionType = "skip_plan_item",
                        targetId = target.id,
                        payloadJson = JSONObject().apply {
                            put("planId", target.id)
                            put("itemId", itemId)
                            put("reason", reason.trim())
                        }.toString()
                    )
                    updated
                }
            } else {
                throw e
            }
        }
    }

    fun loadPendingSyncActionsCount(auditorId: String): Int {
        val uid = auditorId.ifBlank { currentAuditorId() }
        if (uid.isBlank()) return 0
        return mobileDb.loadPendingSyncActions(uid).size
    }

    suspend fun flushPendingSyncActions(auditorId: String): Int = withContext(Dispatchers.IO) {
        val uid = auditorId.ifBlank { currentAuditorId() }
        if (uid.isBlank()) return@withContext 0
        var flushed = 0
        while (true) {
            val actions = mobileDb.loadPendingSyncActions(uid)
            val act = actions.firstOrNull() ?: break
            try {
                val obj = JSONObject(act.payloadJson)
                when (act.actionType) {
                    "create_location" -> {
                        val res = requestJson("/api/locations", "POST", obj)
                        val saved = AuditGridJsonParsers.parseLocation(JSONObject(res))
                        mobileDb.upsertLocation(saved)
                        mobileDb.removePendingSyncAction(act.id)
                        if (act.targetId != saved.id) {
                            mobileDb.remapLocalPlaceId(uid, "location", act.targetId, saved.id)
                        }
                        flushed++
                    }
                    "create_sub_location" -> {
                        val res = requestJson("/api/sub-locations", "POST", obj)
                        val saved = AuditGridJsonParsers.parseSubLocation(JSONObject(res))
                        mobileDb.upsertSubLocation(saved)
                        mobileDb.removePendingSyncAction(act.id)
                        if (act.targetId != saved.id) {
                            mobileDb.remapLocalPlaceId(uid, "subLocation", act.targetId, saved.id)
                        }
                        flushed++
                    }
                    "create_shop" -> {
                        val res = requestJson("/api/shops", "POST", obj)
                        val saved = AuditGridJsonParsers.parseShop(JSONObject(res))
                        mobileDb.upsertShop(saved)
                        mobileDb.removePendingSyncAction(act.id)
                        if (act.targetId != saved.id) {
                            mobileDb.remapLocalPlaceId(uid, "shop", act.targetId, saved.id)
                        }
                        flushed++
                    }
                    "update_place_gps" -> {
                        requestJson("/api/mobile/gps", "PATCH", obj)
                        mobileDb.removePendingSyncAction(act.id)
                        flushed++
                    }
                    "create_plan" -> {
                        val localPlanId = obj.optString("localPlanId").ifBlank { act.targetId }
                        val reqPayload = JSONObject(obj.toString()).apply { remove("localPlanId") }
                        val res = requestJson("/api/plans", "POST", reqPayload)
                        val created = AuditGridJsonParsers.parsePlan(JSONObject(res))
                        mobileDb.removePendingSyncAction(act.id)
                        mobileDb.remapLocalPlanToServerPlan(uid, localPlanId, created)
                        flushed++
                    }
                    "start_plan" -> {
                        val pid = obj.optString("planId").ifBlank { act.targetId }
                        if (!pid.startsWith("pln-local-")) {
                            val res = requestJson("/api/plans/$pid/start", "POST", JSONObject())
                            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
                            val existing = mobileDb.loadPlansForUser(uid)
                            mobileDb.savePlansForUser(uid, existing.map { if (it.id == pid) updated else it })
                        }
                        mobileDb.removePendingSyncAction(act.id)
                        flushed++
                    }
                    "skip_plan_item" -> {
                        val pid = obj.optString("planId").ifBlank { act.targetId }
                        val itemId = obj.optString("itemId")
                        val reason = obj.optString("reason", "Skipped")
                        if (!pid.startsWith("pln-local-") && !itemId.startsWith("pli-local-") && itemId.isNotBlank()) {
                            val skipPayload = JSONObject().apply {
                                put("status", "Skipped")
                                put("reason", reason)
                            }
                            val res = requestJson("/api/plans/$pid/items/$itemId", "PATCH", skipPayload)
                            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
                            val existing = mobileDb.loadPlansForUser(uid)
                            mobileDb.savePlansForUser(uid, existing.map { if (it.id == pid) updated else it })
                        }
                        mobileDb.removePendingSyncAction(act.id)
                        flushed++
                    }
                    "cancel_plan" -> {
                        val pid = obj.optString("planId").ifBlank { act.targetId }
                        if (!pid.startsWith("pln-local-")) {
                            val res = requestJson("/api/plans/$pid/cancel", "POST", JSONObject())
                            val updated = AuditGridJsonParsers.parsePlan(JSONObject(res))
                            val existing = mobileDb.loadPlansForUser(uid)
                            mobileDb.savePlansForUser(uid, existing.map { if (it.id == pid) updated else it })
                        }
                        mobileDb.removePendingSyncAction(act.id)
                        flushed++
                    }
                    "delete_plan" -> {
                        val pid = obj.optString("planId").ifBlank { act.targetId }
                        if (!pid.startsWith("pln-local-")) {
                            requestJson("/api/plans/$pid", "DELETE")
                        }
                        mobileDb.removePendingSyncAction(act.id)
                        flushed++
                    }
                    else -> {
                        mobileDb.removePendingSyncAction(act.id)
                    }
                }
            } catch (e: ApiError) {
                if (e.isNetwork) {
                    // Server/network still unreachable: stop flushing and retry later
                    break
                } else {
                    // Non-recoverable client error (e.g., 400/404 already deleted or completed): drop action so queue doesn't block
                    mobileDb.removePendingSyncAction(act.id)
                }
            } catch (_: Exception) {
                break
            }
        }
        flushed
    }

    suspend fun sendTrackingPing(
        lat: Double,
        lng: Double,
        accuracy: Double,
        context: String? = null,
        dutyId: String? = null,
        planId: String? = null
    ) {
        val uid = currentAuditorId()
        val uname = currentAuditorUsername()
        val payload = JSONObject().apply {
            if (uid.isNotBlank()) put("auditorId", uid)
            if (uname.isNotBlank()) put("username", uname)
            put("lat", lat)
            put("lng", lng)
            put("accuracy", accuracy)
            if (!context.isNullOrBlank()) put("context", context)
            if (!dutyId.isNullOrBlank()) put("dutyId", dutyId)
            if (!planId.isNullOrBlank()) put("planId", planId)
        }
        val (status, body) = sendRaw("/api/tracking/ping", "POST", payload)
        if (status !in 200..299) {
            throw readError(status, body)
        }
    }

    suspend fun sendBatchBreadcrumbs(breadcrumbs: List<Map<String, Any?>>) {
        if (breadcrumbs.isEmpty()) return
        val uid = currentAuditorId()
        val uname = currentAuditorUsername()
        val arr = JSONArray()
        for (b in breadcrumbs) {
            val o = JSONObject().apply {
                if (uid.isNotBlank()) put("auditorId", uid)
                if (uname.isNotBlank()) put("username", uname)
                put("lat", b["lat"] as? Double ?: 0.0)
                put("lng", b["lng"] as? Double ?: 0.0)
                put("accuracy", b["accuracy"] as? Double ?: 20.0)
                b["context"]?.let { put("context", it.toString()) }
                b["dutyId"]?.let { put("dutyId", it.toString()) }
                b["planId"]?.let { put("planId", it.toString()) }
                b["recordedAt"]?.let { put("recordedAt", it.toString()) }
            }
            arr.put(o)
        }
        val payload = JSONObject().apply {
            if (uid.isNotBlank()) put("auditorId", uid)
            if (uname.isNotBlank()) put("username", uname)
            put("pings", arr)
        }
        val (status, body) = sendRaw("/api/tracking/batch", "POST", payload)
        if (status !in 200..299) {
            throw readError(status, body)
        }
    }

    // Local draft storage (mirrors localStorage in browser app)
    fun saveDraftString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getDraftString(key: String): String? = prefs.getString(key, null)

    fun removeDraft(key: String) {
        prefs.edit().remove(key).apply()
    }

    // Offline Outbox Queue & Read-Only Completed Audits in Mobile SQLite Database
    fun loadOutboxItems(auditorId: String): List<PendingOutboxItem> =
        mobileDb.loadReportItemsForUser(auditorId, "outbox", AuditGridJsonParsers::parsePendingOutboxItem)

    fun saveOutboxItems(auditorId: String, items: List<PendingOutboxItem>) {
        mobileDb.saveReportItemsForUser(
            auditorId,
            "outbox",
            items.map { it.id to AuditGridJsonParsers.serializePendingOutboxItem(it) }
        )
    }

    fun loadCachedDistributorReports(auditorId: String): List<DistributorProductAuditReport> =
        mobileDb.loadReportItemsForUser(auditorId, "distributor", AuditGridJsonParsers::parseDistributorReport)

    fun saveCachedDistributorReports(auditorId: String, items: List<DistributorProductAuditReport>) {
        mobileDb.saveReportItemsForUser(
            auditorId,
            "distributor",
            items.take(100).map { it.id to AuditGridJsonParsers.serializeDistributorReport(it) }
        )
    }

    fun loadCachedRetailReports(auditorId: String): List<RetailMarketShopAuditReport> =
        mobileDb.loadReportItemsForUser(auditorId, "retail", AuditGridJsonParsers::parseRetailReport)

    fun saveCachedRetailReports(auditorId: String, items: List<RetailMarketShopAuditReport>) {
        mobileDb.saveReportItemsForUser(
            auditorId,
            "retail",
            items.take(100).map { it.id to AuditGridJsonParsers.serializeRetailReport(it) }
        )
    }

    fun loadCachedCustomSubmissions(auditorId: String): List<CustomAuditSubmission> =
        mobileDb.loadReportItemsForUser(auditorId, "custom", AuditGridJsonParsers::parseCustomSubmission)

    fun saveCachedCustomSubmissions(auditorId: String, items: List<CustomAuditSubmission>) {
        mobileDb.saveReportItemsForUser(
            auditorId,
            "custom",
            items.take(100).map { it.id to AuditGridJsonParsers.serializeCustomSubmission(it) }
        )
    }

    // On-device SQLite persistence for Synced Audit Forms, Field Configs & Master Data
    fun hasSyncedFormsOnce(auditorId: String): Boolean =
        mobileDb.hasSyncedFormsOnce(auditorId)

    fun setFormsSyncedOnce(auditorId: String, synced: Boolean) {
        mobileDb.setFormsSyncedOnce(auditorId, synced)
    }

    fun loadCachedForms(auditorId: String): List<AuditFormDefinition> =
        mobileDb.loadFormsForUser(auditorId)

    fun saveCachedForms(auditorId: String, forms: List<AuditFormDefinition>) {
        val configs = mobileDb.loadFormConfigsForUser(auditorId)
        mobileDb.saveServerFormsForUser(auditorId, forms, configs)
    }

    fun loadCachedFormConfigs(auditorId: String): FormConfigMap =
        mobileDb.loadFormConfigsForUser(auditorId)

    fun saveCachedFormConfigs(auditorId: String, configs: FormConfigMap) {
        val forms = mobileDb.loadFormsForUser(auditorId)
        mobileDb.saveServerFormsForUser(auditorId, forms, configs)
    }

    fun loadCachedBootstrap(auditorId: String, session: Session): BootstrapPayload? =
        mobileDb.loadBootstrapForUser(session)

    // Duties and Notifications Methods
    suspend fun fetchDuties(): List<AuditorDuty> {
        val res = requestJson("/api/mobile/duties", "GET")
        val arr = JSONArray(res)
        val duties = mutableListOf<AuditorDuty>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            duties.add(AuditGridJsonParsers.parseDuty(obj))
        }
        val uid = currentAuditorId()
        if (uid.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mobileDb.saveDutiesForUser(uid, duties)
            }
        }
        return duties
    }

    suspend fun fetchNotifications(): List<AuditorNotification> {
        val res = requestJson("/api/mobile/notifications", "GET")
        val arr = JSONArray(res)
        val list = mutableListOf<AuditorNotification>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(AuditGridJsonParsers.parseNotification(obj))
        }
        val uid = currentAuditorId()
        if (uid.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mobileDb.saveNotificationsForUser(uid, list)
            }
        }
        return list
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        val payload = JSONObject().apply { put("read", true) }
        try {
            requestJson("/api/mobile/notifications/$notificationId/read", "POST", payload)
        } catch (_: Exception) {
            // Best effort network call
        }
        val uid = currentAuditorId()
        if (uid.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mobileDb.markNotificationRead(uid, notificationId)
            }
        }
    }

    suspend fun updateDutyStatus(dutyId: String, status: String): AuditorDuty? {
        val payload = JSONObject().apply { put("status", status) }
        val res = try {
            requestJson("/api/mobile/duties/$dutyId/status", "PATCH", payload)
        } catch (_: Exception) {
            null
        }
        val uid = currentAuditorId()
        if (uid.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mobileDb.updateDutyStatus(uid, dutyId, status)
            }
        }
        return if (res != null) {
            AuditGridJsonParsers.parseDuty(JSONObject(res))
        } else null
    }

    suspend fun syncDutiesAndNotifications(auditorId: String): DutySyncResult {
        val effectiveAuditorId = auditorId.ifBlank { currentAuditorId() }
        val uname = currentAuditorUsername()
        return try {
            val encodedId = java.net.URLEncoder.encode(effectiveAuditorId, "UTF-8")
            val encodedUser = java.net.URLEncoder.encode(uname, "UTF-8")
            val (status, res) = sendRaw("/api/mobile/sync?auditorId=$encodedId&username=$encodedUser", "GET")
            if (status !in 200..299) {
                throw readError(status, res)
            }
            val json = JSONObject(res)
            val result = AuditGridJsonParsers.parseDutySyncResult(json)
            val resolvedId = result.auditorId.ifBlank { effectiveAuditorId }
            withContext(Dispatchers.IO) {
                mobileDb.saveDutiesForUser(resolvedId, result.duties)
                mobileDb.saveNotificationsForUser(resolvedId, result.notifications)
                result.bootstrap?.let { boot ->
                    val existingSession = mobileDb.loadSessionByUsername(uname)
                        ?: Session(
                            user = result.user ?: AppUser(
                                id = resolvedId,
                                code = "",
                                username = uname,
                                fullName = uname,
                                roleId = "",
                                assignedCityIds = result.assignedCityIds,
                                assignedTerritoryIds = result.assignedTerritoryIds,
                                assignedGroupIds = result.assignedGroupIds
                            ),
                            role = boot.roles.firstOrNull(),
                            surface = "mobile",
                            isGlobal = false
                        )
                    val updatedUser = (result.user ?: existingSession.user).copy(
                        assignedCityIds = result.assignedCityIds,
                        assignedTerritoryIds = result.assignedTerritoryIds,
                        assignedGroupIds = result.assignedGroupIds.ifEmpty { existingSession.user.assignedGroupIds }
                    )
                    mobileDb.saveBootstrapForUser(existingSession.copy(user = updatedUser), boot)
                    if (boot.forms.isNotEmpty()) {
                        mobileDb.saveServerFormsForUser(
                            userId = resolvedId,
                            serverForms = boot.forms,
                            serverConfigs = boot.formConfigs,
                            bundleHash = boot.formsBundleHash
                        )
                    }
                }
            }
            result
        } catch (e: Exception) {
            // Offline fallback: load from on-device SQLite
            withContext(Dispatchers.IO) {
                val cachedDuties = mobileDb.loadDutiesForUser(effectiveAuditorId)
                val cachedNotifs = mobileDb.loadNotificationsForUser(effectiveAuditorId)
                val cachedSession = mobileDb.loadSessionByUsername(uname)
                val cachedBoot = cachedSession?.let { mobileDb.loadBootstrapForUser(it) }
                val unread = cachedNotifs.count { !it.isRead }
                DutySyncResult(
                    auditorId = effectiveAuditorId,
                    user = cachedSession?.user,
                    duties = cachedDuties,
                    notifications = cachedNotifs,
                    unreadCount = unread,
                    assignedCityIds = cachedSession?.user?.assignedCityIds ?: emptyList(),
                    assignedTerritoryIds = cachedSession?.user?.assignedTerritoryIds ?: emptyList(),
                    assignedGroupIds = cachedSession?.user?.assignedGroupIds ?: emptyList(),
                    bootstrap = cachedBoot,
                    syncedAt = ""
                )
            }
        }
    }

    fun loadCachedDuties(auditorId: String): List<AuditorDuty> =
        mobileDb.loadDutiesForUser(auditorId)

    fun loadCachedNotifications(auditorId: String): List<AuditorNotification> =
        mobileDb.loadNotificationsForUser(auditorId)
}

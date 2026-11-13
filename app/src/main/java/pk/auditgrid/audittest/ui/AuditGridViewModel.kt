package pk.auditgrid.audittest.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import pk.auditgrid.audittest.api.ApiError
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.util.*
import java.util.UUID

enum class MobileSection {
    AUDIT, DUTIES, COMPLETED, PLANS, CITIES, TERRITORIES, LOCATIONS, SUBLOCATIONS, SHOPS, DISTRIBUTORS, ACCOUNT
}

data class LiveSharingState(
    val sharing: Boolean = false,
    val position: DevicePosition? = null,
    val problem: String = ""
)

data class AppUiState(
    val booting: Boolean = true,
    val session: Session? = null,
    val loadError: String? = null,
    val toast: String? = null,
    val online: Boolean = true,
    val syncing: Boolean = false,
    val lastSyncedAt: String = "",
    val plansRefreshVersion: Int = 0,
    val liveSharing: LiveSharingState = LiveSharingState(),
    val activeSection: MobileSection = MobileSection.AUDIT,
    val cities: List<City> = emptyList(),
    val territories: List<Territory> = emptyList(),
    val locations: List<LocationItem> = emptyList(),
    val subLocations: List<SubLocation> = emptyList(),
    val shops: List<Shop> = emptyList(),
    val distributors: List<Distributor> = emptyList(),
    val groups: List<ProductGroup> = emptyList(),
    val products: List<Product> = emptyList(),
    val users: List<AppUser> = emptyList(),
    val roles: List<UserRole> = emptyList(),
    val auditForms: List<AuditFormDefinition> = emptyList(),
    val formConfigs: FormConfigMap = emptyMap(),
    val formsBundleHash: String = "",
    val formsSyncedOnce: Boolean = false,
    val newlySyncedFormIds: Set<String> = emptySet(),
    val updatedFormIds: Set<String> = emptySet(),
    val mapsApiKey: String = "",
    val auditReports: List<DistributorProductAuditReport> = emptyList(),
    val retailReports: List<RetailMarketShopAuditReport> = emptyList(),
    val customSubmissions: List<CustomAuditSubmission> = emptyList(),
    val pendingOutbox: List<PendingOutboxItem> = emptyList(),
    val pendingSyncActionsCount: Int = 0,
    val duties: List<AuditorDuty> = emptyList(),
    val notifications: List<AuditorNotification> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val showNotificationsDialog: Boolean = false,
    // Geography drill-down filters
    val filterCityId: String = "",
    val filterTerritoryId: String = "",
    val filterLocationId: String = "",
    val filterSubLocationId: String = "",
    val selectedShop: Shop? = null,
    val selectedDistributor: Distributor? = null,
    val searchQuery: String = ""
)

class AuditGridViewModel(application: Application) : AndroidViewModel(application) {

    val apiClient = AuditGridApiClient(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val syncMutex = Mutex()
    private var toastJob: Job? = null
    private var periodicJob: Job? = null
    private var liveLocationListener: LocationListener? = null
    private var lastPingTimeMs: Long = 0L
    private var lastPingLat: Double = 0.0
    private var lastPingLng: Double = 0.0

    init {
        apiClient.onSessionEnded = {
            viewModelScope.launch {
                stopLiveTracking()
                _uiState.update {
                    AppUiState(
                        booting = false,
                        session = null,
                        online = it.online
                    )
                }
                showToast("Your session ended. Please sign in again.")
            }
        }
        apiClient.onConnectivityChanged = { isOnline ->
            _uiState.update { state ->
                if (state.online == isOnline) state else state.copy(online = isOnline)
            }
        }
        WarmLocationManager.onFixCaptured = { pos ->
            onWarmLocationCaptured(pos)
        }
        AuditNotificationHelper.initChannel(application)
        setupNetworkMonitoring()
        restoreInitialSession()
    }

    fun onNotificationPermissionGranted() {
        val current = _uiState.value
        val uid = current.session?.user?.id ?: apiClient.currentAuditorId()
        if (uid.isNotBlank()) {
            val generated = AuditNotificationHelper.processDutiesAndNotifications(
                context = getApplication(),
                auditorId = uid,
                duties = current.duties,
                notifications = current.notifications
            )
            if (generated.isNotEmpty()) {
                val mergedNotifs = generated + current.notifications
                apiClient.mobileDb.saveNotificationsForUser(uid, mergedNotifs)
                _uiState.update {
                    it.copy(
                        notifications = mergedNotifs,
                        unreadNotificationsCount = mergedNotifs.count { n -> !n.isRead }
                    )
                }
            }
        }
    }

    private fun onWarmLocationCaptured(pos: DevicePosition) {
        if (!hasCoords(pos.lat, pos.lng)) return
        _uiState.update {
            it.copy(
                liveSharing = it.liveSharing.copy(
                    position = pos,
                    problem = "",
                    sharing = it.session != null
                )
            )
        }
        if (_uiState.value.session != null) {
            val now = System.currentTimeMillis()
            val movedSignificant =
                lastPingLat == 0.0 || haversineMeters(lastPingLat, lastPingLng, pos.lat, pos.lng) >= 15.0
            val intervalElapsed = lastPingTimeMs == 0L || (now - lastPingTimeMs) >= 45_000L
            if (intervalElapsed || movedSignificant) {
                dispatchLocationPing(pos, now)
            }
        }
    }

    private fun setupNetworkMonitoring() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val activeNet = cm.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        val initialOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(online = initialOnline) }

        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _uiState.update { it.copy(online = true) }
                    if (_uiState.value.session != null) {
                        syncAllNow(manual = false)
                    } else {
                        viewModelScope.launch {
                            runCatching { apiClient.syncMobileUsersPreLogin() }
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _uiState.update { it.copy(online = false) }
                }
            })
        }
    }

    private fun restoreInitialSession() {
        viewModelScope.launch {
            // Priority #1 on app startup: auto-sync mobile users from MySQL database into mobile SQLite DB
            runCatching { apiClient.syncMobileUsersPreLogin() }

            val s = apiClient.restoreSession()
            if (s != null) {
                val syncedSession = apiClient.getUpdatedSessionFromMobileDb(s)
                loadLocalCacheAndOutbox(syncedSession)
                _uiState.update { it.copy(session = syncedSession, booting = false) }
                DutyBackgroundSyncService.start(getApplication())
                startLiveTracking()
                loadAllData(syncedSession)
                startPeriodicSync()
                if (_uiState.value.online && _uiState.value.pendingOutbox.isNotEmpty()) {
                    syncAllNow(manual = false)
                }
            } else {
                _uiState.update { it.copy(booting = false) }
            }
        }
    }

    private fun loadLocalCacheAndOutbox(session: Session) {
        val auditorId = session.user.id
        val outbox = apiClient.loadOutboxItems(auditorId)
        val cachedDist = apiClient.loadCachedDistributorReports(auditorId)
        val cachedRetail = apiClient.loadCachedRetailReports(auditorId)
        val cachedCustom = apiClient.loadCachedCustomSubmissions(auditorId)
        val cachedBoot = apiClient.loadCachedBootstrap(auditorId, session)
        val cachedForms = apiClient.loadCachedForms(auditorId)
        val cachedConfigs = apiClient.loadCachedFormConfigs(auditorId)
        val cachedHash = apiClient.loadCachedBundleHash(auditorId)
        val cachedDuties = apiClient.loadCachedDuties(auditorId)
        val cachedNotifs = apiClient.loadCachedNotifications(auditorId)
        val unreadNotifsCount = cachedNotifs.count { !it.isRead }
        val pendingActionsCount = apiClient.loadPendingSyncActionsCount(auditorId)

        _uiState.update {
            it.copy(
                pendingOutbox = outbox,
                pendingSyncActionsCount = pendingActionsCount,
                auditReports = cachedDist,
                retailReports = cachedRetail,
                customSubmissions = cachedCustom,
                formsSyncedOnce = cachedForms.isNotEmpty() || apiClient.hasSyncedFormsOnce(auditorId),
                auditForms = cachedForms,
                formConfigs = cachedConfigs,
                formsBundleHash = cachedHash,
                cities = cachedBoot?.cities ?: it.cities,
                territories = cachedBoot?.territories ?: it.territories,
                locations = cachedBoot?.locations ?: it.locations,
                subLocations = cachedBoot?.subLocations ?: it.subLocations,
                shops = cachedBoot?.shops ?: it.shops,
                distributors = cachedBoot?.distributors ?: it.distributors,
                groups = cachedBoot?.groups ?: it.groups,
                products = cachedBoot?.products ?: it.products,
                users = cachedBoot?.users ?: it.users,
                roles = cachedBoot?.roles ?: it.roles,
                duties = cachedDuties,
                notifications = cachedNotifs,
                unreadNotificationsCount = unreadNotifsCount
            )
        }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(4000L)
            _uiState.update { state ->
                if (state.toast == message) state.copy(toast = null) else state
            }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = null) }
    }

    fun onSignedIn(session: Session) {
        viewModelScope.launch {
            loadLocalCacheAndOutbox(session)
            _uiState.update { it.copy(session = session, booting = false, loadError = null) }
            DutyBackgroundSyncService.start(getApplication())
            loadAllData(session)
            showToast("Welcome, ${session.user.fullName}")
            startPeriodicSync()
            startLiveTracking()
            if (_uiState.value.online && _uiState.value.pendingOutbox.isNotEmpty()) {
                syncAllNow(manual = false)
            }
        }
    }

    fun retryLoadAll() {
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(booting = true, loadError = null) }
            loadAllData(s)
            _uiState.update { it.copy(booting = false) }
        }
    }

    private fun isFormModifiedOnWeb(
        prevForm: AuditFormDefinition,
        newForm: AuditFormDefinition,
        prevCfg: FormConfigPayload?,
        newCfg: FormConfigPayload?
    ): Boolean {
        if (prevForm.version != newForm.version && newForm.version > 0) return true
        if (prevForm.updatedAt != newForm.updatedAt && newForm.updatedAt.isNotBlank()) return true
        if (prevForm.title != newForm.title || prevForm.status != newForm.status || prevForm.target != newForm.target) return true
        if (prevForm.description != newForm.description || prevForm.fieldsCount != newForm.fieldsCount) return true
        if (prevCfg != null && newCfg != null && prevCfg != newCfg) return true
        return false
    }

    private suspend fun loadAllData(s: Session) {
        try {
            // Sync duty assignments first so server recomputes assignedCityIds, assignedTerritoryIds & assignedGroupIds
            val dutySync = runCatching { apiClient.syncDutiesAndNotifications(s.user.id) }.getOrNull()
            val boot = runCatching { apiClient.fetchBootstrap(s) }.getOrNull() ?: dutySync?.bootstrap
                ?: throw ApiError("Could not load your data.", 0)
            val updatedSession = apiClient.getUpdatedSessionFromMobileDb(s)
            val bundle = runCatching { apiClient.fetchFormBundle() }.getOrNull()
            runCatching { apiClient.fetchPlans(inProgressOnly = false) }
            val dr = runCatching { apiClient.fetchDistributorReports() }.getOrNull()
            val rr = runCatching { apiClient.fetchRetailReports() }.getOrNull()
            val cs = runCatching { apiClient.fetchCustomSubmissions() }.getOrNull()

            val mergedDist = mergeById(dr, _uiState.value.auditReports) { it.id }
            val mergedRetail = mergeById(rr, _uiState.value.retailReports) { it.id }
            val mergedCustom = mergeById(cs, _uiState.value.customSubmissions) { it.id }

            apiClient.saveCachedDistributorReports(updatedSession.user.id, mergedDist)
            apiClient.saveCachedRetailReports(updatedSession.user.id, mergedRetail)
            apiClient.saveCachedCustomSubmissions(updatedSession.user.id, mergedCustom)

            val cachedForms = apiClient.loadCachedForms(updatedSession.user.id)
            val cachedConfigs = apiClient.loadCachedFormConfigs(updatedSession.user.id)

            // When online against the server, use the server bundle/bootstrap as authoritative so web edits & deletions sync cleanly
            val serverForms = when {
                bundle != null && bundle.forms.isNotEmpty() -> bundle.forms
                boot.forms.isNotEmpty() -> boot.forms
                dutySync?.bootstrap?.forms?.isNotEmpty() == true -> dutySync.bootstrap.forms
                else -> emptyList()
            }
            val serverConfigs: FormConfigMap = when {
                bundle != null && bundle.formConfigs.isNotEmpty() -> bundle.formConfigs
                boot.formConfigs.isNotEmpty() -> boot.formConfigs
                dutySync?.bootstrap?.formConfigs?.isNotEmpty() == true -> dutySync.bootstrap.formConfigs
                else -> emptyMap()
            }
            val effectiveHash = bundle?.bundleHash?.ifBlank { boot.formsBundleHash } ?: boot.formsBundleHash

            val (authoritativeForms, authoritativeConfigs) = if (serverForms.isNotEmpty()) {
                apiClient.saveServerSyncedForms(
                    auditorId = updatedSession.user.id,
                    serverForms = serverForms,
                    serverConfigs = serverConfigs,
                    bundleHash = effectiveHash
                )
            } else {
                cachedForms to cachedConfigs
            }

            val effectiveUser = if (dutySync != null && dutySync.syncedAt.isNotBlank()) {
                updatedSession.user.copy(
                    assignedCityIds = dutySync.assignedCityIds,
                    assignedTerritoryIds = dutySync.assignedTerritoryIds,
                    assignedGroupIds = dutySync.assignedGroupIds.ifEmpty { updatedSession.user.assignedGroupIds }
                )
            } else {
                updatedSession.user
            }
            val effectiveSession = updatedSession.copy(user = effectiveUser)
            val effectiveUsers = boot.users.map { u ->
                if (u.id == effectiveUser.id) effectiveUser else u
            }.let { list ->
                if (list.any { it.id == effectiveUser.id }) list else list + effectiveUser
            }

            val latestDuties = dutySync?.duties?.takeIf { it.isNotEmpty() }
                ?: runCatching { apiClient.fetchDuties() }.getOrNull()
                ?: _uiState.value.duties
            val baseNotifications = dutySync?.notifications?.takeIf { it.isNotEmpty() }
                ?: runCatching { apiClient.fetchNotifications() }.getOrNull()
                ?: _uiState.value.notifications

            // Fire Android system notifications for any unread duty notifications or changes in duties
            val generatedDutyNotifs = AuditNotificationHelper.processDutiesAndNotifications(
                context = getApplication(),
                auditorId = effectiveUser.id,
                duties = latestDuties,
                notifications = baseNotifications
            )
            val mergedNotifications = if (generatedDutyNotifs.isNotEmpty()) {
                val existingIds = baseNotifications.map { it.id }.toSet()
                val combined = generatedDutyNotifs.filter { it.id !in existingIds } + baseNotifications
                apiClient.mobileDb.saveNotificationsForUser(effectiveUser.id, combined)
                combined
            } else {
                baseNotifications
            }

            val cityIdsSet = boot.cities.map { it.id }.toSet()
            val terIdsSet = boot.territories.map { it.id }.toSet()
            val locIdsSet = boot.locations.map { it.id }.toSet()
            val subIdsSet = boot.subLocations.map { it.id }.toSet()

            val nowStr = currentTimePkt()
            _uiState.update {
                it.copy(
                    session = effectiveSession,
                    loadError = null,
                    lastSyncedAt = nowStr,
                    plansRefreshVersion = it.plansRefreshVersion + 1,
                    cities = boot.cities,
                    territories = boot.territories,
                    locations = boot.locations,
                    subLocations = boot.subLocations,
                    shops = boot.shops,
                    distributors = boot.distributors,
                    groups = boot.groups,
                    products = boot.products,
                    users = effectiveUsers,
                    roles = boot.roles,
                    formsSyncedOnce = authoritativeForms.isNotEmpty(),
                    auditForms = authoritativeForms,
                    formConfigs = authoritativeConfigs,
                    formsBundleHash = effectiveHash.ifBlank { it.formsBundleHash },
                    mapsApiKey = boot.mapsApiKey,
                    auditReports = mergedDist,
                    retailReports = mergedRetail,
                    customSubmissions = mergedCustom,
                    duties = latestDuties,
                    notifications = mergedNotifications,
                    unreadNotificationsCount = mergedNotifications.count { n -> !n.isRead },
                    filterCityId = if (it.filterCityId in cityIdsSet) it.filterCityId else "",
                    filterTerritoryId = if (it.filterTerritoryId in terIdsSet) it.filterTerritoryId else "",
                    filterLocationId = if (it.filterLocationId in locIdsSet) it.filterLocationId else "",
                    filterSubLocationId = if (it.filterSubLocationId in subIdsSet) it.filterSubLocationId else ""
                )
            }
            checkLiveSharingStatus()
        } catch (e: Exception) {
            val hasOfflineCache = _uiState.value.cities.isNotEmpty() ||
                _uiState.value.territories.isNotEmpty() ||
                _uiState.value.auditForms.isNotEmpty()
            if (hasOfflineCache) {
                _uiState.update { it.copy(loadError = null) }
            } else {
                val msg = if (e is ApiError) e.message else "Could not load your data."
                _uiState.update { it.copy(loadError = msg) }
            }
        }
    }

    private fun <T> mergeById(
        remote: List<T>?,
        local: List<T>,
        idSelector: (T) -> String
    ): List<T> {
        if (remote == null) return local
        val remoteIds = remote.map(idSelector).toSet()
        return remote + local.filterNot { remoteIds.contains(idSelector(it)) }
    }

    /**
     * Full two-way sync:
     * 1. Uploads any queued offline audit reports from `pendingOutbox`.
     * 2. Refreshes users, roles, cities, territories, locations, sub-locations, shops, distributors, groups, and SKUs (`/api/bootstrap`)
     *    and updates the mobile SQLite database (`AuditGridMobileDatabase`) and the logged-in user's session/assignments.
     * 3. Refreshes submitted reports (`/api/reports/distributor`, `/api/reports/retail`, `/api/submissions`) and daily plans (`/api/plans`).
     * 4. Fetches the authoritative Form Bundle (`/api/forms/bundle`) from the web application:
     *    - Inserts any newly created web forms into the mobile database
     *    - Updates any existing forms whose fields, options, settings, or versions changed on the web application
     *    - Removes any server forms deleted on the web application while preserving manually imported package forms
     *    - Saves all synced forms & configs on the phone for offline & online use.
     */
    fun syncAllNow(manual: Boolean = false) {
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            if (!syncMutex.tryLock()) {
                if (manual) showToast("Sync is already in progress…")
                return@launch
            }
            _uiState.update { it.copy(syncing = true) }
            try {
                val previousFormsById = _uiState.value.auditForms.associateBy { it.id }
                val previousConfigsById = _uiState.value.formConfigs
                var flushedCount = 0
                var failedCount = 0

                // 1. Flush any queued offline plan creations/starts/skips/cancels and place/GPS actions first
                //    so local IDs (pln-local-*, pli-local-*, loc-mob-*, sub-mob-*, shop-mob-*) are remapped in outbox reports!
                val flushedActions = runCatching { apiClient.flushPendingSyncActions(s.user.id) }.getOrDefault(0)
                flushedCount += flushedActions

                // Reload outbox from SQLite after ID remapping
                val currentOutbox = apiClient.loadOutboxItems(s.user.id).toMutableList()
                val remainingOutbox = mutableListOf<PendingOutboxItem>()

                for (item in currentOutbox) {
                    try {
                        when {
                            item.distributorReport != null -> {
                                val r = item.distributorReport
                                if (r.planId?.startsWith("pln-local-") == true) {
                                    throw ApiError("Waiting for offline plan to sync first.", 0)
                                }
                                val saved = apiClient.submitDistributorReport(r)
                                prependDistributorReport(s.user.id, saved)
                                flushedCount++
                            }
                            item.retailReport != null -> {
                                val r = item.retailReport
                                if (r.planId?.startsWith("pln-local-") == true) {
                                    throw ApiError("Waiting for offline plan to sync first.", 0)
                                }
                                val saved = apiClient.submitRetailShopReport(r)
                                prependRetailReport(s.user.id, saved)
                                flushedCount++
                            }
                            item.customSubmission != null -> {
                                val c = item.customSubmission
                                if (c.planId?.startsWith("pln-local-") == true) {
                                    throw ApiError("Waiting for offline plan to sync first.", 0)
                                }
                                val saved = apiClient.submitCustomAudit(
                                    clientRequestId = c.id,
                                    formId = c.formId,
                                    targetType = c.targetType,
                                    targetId = c.targetId,
                                    answers = c.answers,
                                    photos = c.capturedImages,
                                    mobileLat = c.mobileLat,
                                    mobileLng = c.mobileLng,
                                    mobileAccuracyMeters = c.mobileAccuracyMeters.toDouble(),
                                    planLink = if (!c.planId.isNullOrBlank() && !c.planItemId.isNullOrBlank()) {
                                        PlanLink(c.planId, c.planItemId)
                                    } else null,
                                    fallbackMeta = c
                                )
                                prependCustomSubmission(s.user.id, saved)
                                flushedCount++
                            }
                        }
                    } catch (e: Exception) {
                        failedCount++
                        val errMsg = if (e is ApiError) e.message else "Waiting for server connection"
                        remainingOutbox.add(item.copy(lastError = errMsg))
                    }
                }

                apiClient.saveOutboxItems(s.user.id, remainingOutbox)
                val remainingActionsCount = apiClient.loadPendingSyncActionsCount(s.user.id)
                _uiState.update {
                    it.copy(
                        pendingOutbox = remainingOutbox,
                        pendingSyncActionsCount = remainingActionsCount
                    )
                }

                // Flush queued offline location breadcrumbs to web server
                runCatching {
                    val queuedBreadcrumbs = apiClient.mobileDb.loadQueuedBreadcrumbs(s.user.id)
                    if (queuedBreadcrumbs.isNotEmpty()) {
                        apiClient.sendBatchBreadcrumbs(queuedBreadcrumbs)
                        val breadcrumbIds = queuedBreadcrumbs.mapNotNull { (it["id"] as? Number)?.toLong() }
                        apiClient.mobileDb.removeQueuedBreadcrumbs(breadcrumbIds)
                    }
                }

                // Sync duty assignments and notification alerts from web portal first so server recomputes user scope
                val previousNotifIds = _uiState.value.notifications.map { it.id }.toSet()
                val dutySync = runCatching { apiClient.syncDutiesAndNotifications(s.user.id) }.getOrNull()

                // Refresh bootstrap master data (users, roles, cities, territories, locations, subLocations, shops, distributors, products)
                val boot = runCatching { apiClient.fetchBootstrap(s) }.getOrNull() ?: dutySync?.bootstrap
                    ?: throw ApiError("Could not reach server.", 0)
                val updatedSession = apiClient.getUpdatedSessionFromMobileDb(s)
                runCatching { apiClient.fetchPlans(inProgressOnly = false) }
                val dr = runCatching { apiClient.fetchDistributorReports() }.getOrNull()
                val rr = runCatching { apiClient.fetchRetailReports() }.getOrNull()
                val cs = runCatching { apiClient.fetchCustomSubmissions() }.getOrNull()

                // Fetch authoritative Form Bundle from web application
                val remoteBundle = runCatching { apiClient.fetchFormBundle() }.getOrNull()
                val remoteForms = when {
                    remoteBundle != null && remoteBundle.forms.isNotEmpty() -> remoteBundle.forms
                    boot.forms.isNotEmpty() -> boot.forms
                    else -> runCatching { apiClient.fetchForms() }.getOrDefault(boot.forms)
                }

                val rawConfigs = mutableMapOf<String, FormConfigPayload>()
                if (remoteBundle != null && remoteBundle.formConfigs.isNotEmpty()) {
                    rawConfigs.putAll(remoteBundle.formConfigs)
                } else {
                    rawConfigs.putAll(boot.formConfigs)
                    for (f in remoteForms) {
                        runCatching { apiClient.fetchFormConfig(f.id) }.getOrNull()?.let { cfg ->
                            rawConfigs[f.id] = cfg
                        }
                    }
                }
                val newBundleHash = remoteBundle?.bundleHash?.ifBlank { boot.formsBundleHash } ?: boot.formsBundleHash

                // Persist synced server forms & field configs in mobile SQLite DB while preserving manually imported package forms
                val (syncedForms, syncedConfigs) = if (remoteForms.isNotEmpty()) {
                    apiClient.saveServerSyncedForms(
                        auditorId = updatedSession.user.id,
                        serverForms = remoteForms,
                        serverConfigs = rawConfigs,
                        bundleHash = newBundleHash
                    )
                } else {
                    apiClient.loadCachedForms(updatedSession.user.id) to apiClient.loadCachedFormConfigs(updatedSession.user.id)
                }

                val newlyInsertedForms = syncedForms.filter { it.id !in previousFormsById }
                val updatedWebForms = syncedForms.filter { f ->
                    val prev = previousFormsById[f.id]
                    prev != null && isFormModifiedOnWeb(prev, f, previousConfigsById[f.id], syncedConfigs[f.id])
                }
                val newlyInsertedIds = newlyInsertedForms.map { it.id }.toSet()
                val updatedIds = updatedWebForms.map { it.id }.toSet()

                val mergedDist = mergeById(dr, _uiState.value.auditReports) { it.id }
                val mergedRetail = mergeById(rr, _uiState.value.retailReports) { it.id }
                val mergedCustom = mergeById(cs, _uiState.value.customSubmissions) { it.id }

                apiClient.saveCachedDistributorReports(updatedSession.user.id, mergedDist)
                apiClient.saveCachedRetailReports(updatedSession.user.id, mergedRetail)
                apiClient.saveCachedCustomSubmissions(updatedSession.user.id, mergedCustom)

                val effectiveUser = if (dutySync != null && dutySync.syncedAt.isNotBlank()) {
                    updatedSession.user.copy(
                        assignedCityIds = dutySync.assignedCityIds,
                        assignedTerritoryIds = dutySync.assignedTerritoryIds,
                        assignedGroupIds = dutySync.assignedGroupIds.ifEmpty { updatedSession.user.assignedGroupIds }
                    )
                } else {
                    updatedSession.user
                }
                val effectiveSession = updatedSession.copy(user = effectiveUser)
                val effectiveUsers = boot.users.map { u ->
                    if (u.id == effectiveUser.id) effectiveUser else u
                }.let { list ->
                    if (list.any { it.id == effectiveUser.id }) list else list + effectiveUser
                }

                val latestDuties = dutySync?.duties?.takeIf { it.isNotEmpty() }
                    ?: runCatching { apiClient.fetchDuties() }.getOrNull()
                    ?: _uiState.value.duties
                val baseNotifications = dutySync?.notifications?.takeIf { it.isNotEmpty() }
                    ?: runCatching { apiClient.fetchNotifications() }.getOrNull()
                    ?: _uiState.value.notifications

                val newUnreadNotifs = baseNotifications.filter { !it.isRead && it.id !in previousNotifIds }
                val generatedDutyNotifs = AuditNotificationHelper.processDutiesAndNotifications(
                    context = getApplication(),
                    auditorId = effectiveUser.id,
                    duties = latestDuties,
                    notifications = baseNotifications
                )
                val mergedNotifications = if (generatedDutyNotifs.isNotEmpty()) {
                    val existingIds = baseNotifications.map { it.id }.toSet()
                    val combined = generatedDutyNotifs.filter { it.id !in existingIds } + baseNotifications
                    apiClient.mobileDb.saveNotificationsForUser(effectiveUser.id, combined)
                    combined
                } else {
                    baseNotifications
                }

                if (generatedDutyNotifs.isNotEmpty()) {
                    showToast(generatedDutyNotifs.first().title)
                } else if (newUnreadNotifs.isNotEmpty()) {
                    showToast("${newUnreadNotifs.first().title} — Saved to mobile database!")
                }

                val cityIdsSet = boot.cities.map { it.id }.toSet()
                val terIdsSet = boot.territories.map { it.id }.toSet()
                val locIdsSet = boot.locations.map { it.id }.toSet()
                val subIdsSet = boot.subLocations.map { it.id }.toSet()

                val syncedTime = currentTimePkt()
                _uiState.update {
                    it.copy(
                        session = effectiveSession,
                        online = true,
                        loadError = null,
                        lastSyncedAt = syncedTime,
                        plansRefreshVersion = it.plansRefreshVersion + 1,
                        cities = boot.cities,
                        territories = boot.territories,
                        locations = boot.locations,
                        subLocations = boot.subLocations,
                        shops = boot.shops,
                        distributors = boot.distributors,
                        groups = boot.groups,
                        products = boot.products,
                        users = effectiveUsers,
                        roles = boot.roles,
                        formsSyncedOnce = syncedForms.isNotEmpty(),
                        auditForms = syncedForms,
                        formConfigs = syncedConfigs,
                        formsBundleHash = newBundleHash.ifBlank { it.formsBundleHash },
                        newlySyncedFormIds = if (newlyInsertedIds.isNotEmpty()) newlyInsertedIds else it.newlySyncedFormIds,
                        updatedFormIds = if (updatedIds.isNotEmpty()) updatedIds else it.updatedFormIds,
                        mapsApiKey = boot.mapsApiKey,
                        auditReports = mergedDist,
                        retailReports = mergedRetail,
                        customSubmissions = mergedCustom,
                        duties = latestDuties,
                        notifications = mergedNotifications,
                        unreadNotificationsCount = mergedNotifications.count { n -> !n.isRead },
                        filterCityId = if (it.filterCityId in cityIdsSet) it.filterCityId else "",
                        filterTerritoryId = if (it.filterTerritoryId in terIdsSet) it.filterTerritoryId else "",
                        filterLocationId = if (it.filterLocationId in locIdsSet) it.filterLocationId else "",
                        filterSubLocationId = if (it.filterSubLocationId in subIdsSet) it.filterSubLocationId else "",
                        selectedShop = it.selectedShop?.let { sel -> boot.shops.find { sh -> sh.id == sel.id } },
                        selectedDistributor = it.selectedDistributor?.let { sel -> boot.distributors.find { d -> d.id == sel.id } }
                    )
                }
                checkLiveSharingStatus()

                if (flushedCount > 0 && failedCount == 0) {
                    showToast("Synced $flushedCount queued audit(s), user data & ${syncedForms.size} form(s) to mobile DB.")
                } else if (flushedCount > 0 && failedCount > 0) {
                    showToast("Uploaded $flushedCount audit(s); $failedCount still in outbox.")
                } else if (newlyInsertedForms.isNotEmpty() || updatedWebForms.isNotEmpty()) {
                    if (previousFormsById.isEmpty()) {
                        showToast("Synced ${syncedForms.size} audit form(s) & user data from server to mobile DB.")
                    } else {
                        val parts = mutableListOf<String>()
                        if (newlyInsertedForms.isNotEmpty()) {
                            parts.add("${newlyInsertedForms.size} new (${newlyInsertedForms.joinToString(", ") { it.code }})")
                        }
                        if (updatedWebForms.isNotEmpty()) {
                            parts.add("${updatedWebForms.size} updated (${updatedWebForms.joinToString(", ") { it.code }})")
                        }
                        showToast("Synced server changes: ${parts.joinToString(" & ")} saved to mobile DB.")
                    }
                } else if (manual) {
                    if (failedCount > 0) {
                        showToast("$failedCount queued audit(s) could not be uploaded yet.")
                    } else {
                        showToast("Synced user profile, roles, cities, territories, places, SKUs & ${syncedForms.size} forms ($syncedTime).")
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    val msg = if (e is ApiError) e.message else "Cannot reach server right now. Saved mobile DB data remains available offline."
                    showToast(msg)
                }
            } finally {
                _uiState.update { it.copy(syncing = false) }
                syncMutex.unlock()
            }
        }
    }

    /**
     * Imports a downloadable Form Package (.json) exported from the Web Portal (`GET /api/forms/bundle`)
     * directly into the mobile database so forms and web edits are available offline & online.
     */
    fun importFormPackageFromJson(rawJson: String) {
        val s = _uiState.value.session ?: return
        try {
            val previousById = _uiState.value.auditForms.associateBy { it.id }
            val previousCfgs = _uiState.value.formConfigs
            val bundle = apiClient.importFormBundleJson(s.user.id, rawJson)
            if (bundle.forms.isEmpty()) {
                showToast("This JSON file does not contain any valid AuditGrid forms.")
                return
            }
            val newIds = bundle.forms.filter { it.id !in previousById }.map { it.id }.toSet()
            val updatedIds = bundle.forms.filter { f ->
                val prev = previousById[f.id]
                prev != null && isFormModifiedOnWeb(prev, f, previousCfgs[f.id], bundle.formConfigs[f.id])
            }.map { it.id }.toSet()

            _uiState.update {
                it.copy(
                    formsSyncedOnce = true,
                    auditForms = bundle.forms,
                    formConfigs = bundle.formConfigs,
                    formsBundleHash = bundle.bundleHash.ifBlank { it.formsBundleHash },
                    newlySyncedFormIds = newIds,
                    updatedFormIds = updatedIds,
                    lastSyncedAt = currentTimePkt()
                )
            }
            showToast("Imported Form Package (${bundle.forms.size} forms, ${newIds.size} new, ${updatedIds.size} updated) into phone storage.")
        } catch (e: Exception) {
            showToast("Invalid Form Package JSON: ${e.message ?: "Could not parse file."}")
        }
    }

    /**
     * Field creation of Location, Sub-location, Shop, and GPS updates that sync directly to the Web Portal.
     */
    suspend fun createLocationFromMobile(
        name: String,
        code: String,
        territoryId: String,
        lat: Double,
        lng: Double
    ): LocationItem {
        val ter = _uiState.value.territories.find { it.id == territoryId }
            ?: throw ApiError("Please select an assigned territory.", 400)
        val city = _uiState.value.cities.find { it.id == ter.cityId }
        val payload = LocationItem(
            id = "loc-mob-${UUID.randomUUID().toString().take(8)}",
            code = code.trim().uppercase().ifBlank { "LOC-${(100..999).random()}" },
            name = name.trim(),
            territoryId = ter.id,
            territoryName = ter.name,
            cityId = ter.cityId,
            cityName = city?.name ?: ter.cityName,
            subLocationsCount = 0,
            shopsCount = 0,
            gpsStatus = "GPS Verified",
            status = "Active",
            lat = lat,
            lng = lng
        )
        val saved = apiClient.createLocation(payload)
        val pendingCount = apiClient.loadPendingSyncActionsCount(_uiState.value.session?.user?.id ?: "")
        _uiState.update { state ->
            state.copy(
                locations = listOf(saved) + state.locations.filter { it.id != saved.id },
                pendingSyncActionsCount = pendingCount
            )
        }
        val syncNote = if (_uiState.value.online) "synced to Web Portal" else "saved to mobile DB (will auto-sync when online)"
        showToast("Created location '${saved.name}' with GPS (${String.format("%.5f", saved.lat)}, ${String.format("%.5f", saved.lng)}) — $syncNote.")
        return saved
    }

    suspend fun createSubLocationFromMobile(
        name: String,
        code: String,
        locationId: String,
        lat: Double,
        lng: Double,
        shopsCount: Int = 0
    ): SubLocation {
        val loc = _uiState.value.locations.find { it.id == locationId }
            ?: throw ApiError("Please select a parent location.", 400)
        val payload = SubLocation(
            id = "sub-mob-${UUID.randomUUID().toString().take(8)}",
            code = code.trim().uppercase().ifBlank { "SUB-${(100..999).random()}" },
            name = name.trim(),
            locationId = loc.id,
            locationName = loc.name,
            territoryId = loc.territoryId,
            territoryName = loc.territoryName,
            cityId = loc.cityId,
            cityName = loc.cityName,
            shopsCount = shopsCount,
            status = "Verified",
            lat = lat,
            lng = lng
        )
        val saved = apiClient.createSubLocation(payload)
        val pendingCount = apiClient.loadPendingSyncActionsCount(_uiState.value.session?.user?.id ?: "")
        _uiState.update { state ->
            state.copy(
                subLocations = listOf(saved) + state.subLocations.filter { it.id != saved.id },
                pendingSyncActionsCount = pendingCount
            )
        }
        val syncNote = if (_uiState.value.online) "synced to Web Portal" else "saved to mobile DB (will auto-sync when online)"
        showToast("Created sub-location '${saved.name}' — $syncNote.")
        return saved
    }

    suspend fun createShopFromMobile(
        name: String,
        code: String,
        subLocationId: String,
        ownerName: String,
        phone: String,
        shopType: String,
        address: String,
        lat: Double,
        lng: Double
    ): Shop {
        val sub = _uiState.value.subLocations.find { it.id == subLocationId }
            ?: throw ApiError("Please select a sub-location.", 400)
        val payload = Shop(
            id = "shop-mob-${UUID.randomUUID().toString().take(8)}",
            code = code.trim().uppercase().ifBlank { "SHP-${(1000..9999).random()}" },
            name = name.trim(),
            subLocationId = sub.id,
            subLocationName = sub.name,
            locationId = sub.locationId,
            locationName = sub.locationName,
            territoryId = sub.territoryId,
            territoryName = sub.territoryName,
            cityId = sub.cityId,
            cityName = sub.cityName,
            ownerName = ownerName.trim().ifBlank { "Shop Keeper" },
            phone = phone.trim(),
            shopType = shopType.trim().ifBlank { "General Store" },
            address = address.trim().ifBlank { "${sub.name}, ${sub.cityName}" },
            gpsStatus = "GPS Verified",
            lat = lat,
            lng = lng,
            lastAudit = "Never",
            status = "Active"
        )
        val saved = apiClient.createShop(payload)
        val pendingCount = apiClient.loadPendingSyncActionsCount(_uiState.value.session?.user?.id ?: "")
        _uiState.update { state ->
            state.copy(
                shops = listOf(saved) + state.shops.filter { it.id != saved.id },
                pendingSyncActionsCount = pendingCount
            )
        }
        val syncNote = if (_uiState.value.online) "viewable on Web Portal" else "saved to mobile DB (will auto-sync when online)"
        showToast("Registered shop '${saved.name}' with live GPS — $syncNote.")
        return saved
    }

    suspend fun updatePlaceGpsFromMobile(
        targetType: String,
        targetId: String,
        lat: Double,
        lng: Double,
        accuracy: Double
    ) {
        apiClient.updatePlaceGps(targetType, targetId, lat, lng, accuracy)
        val pendingCount = apiClient.loadPendingSyncActionsCount(_uiState.value.session?.user?.id ?: "")
        _uiState.update { state ->
            val nextState = when (targetType) {
                "location" -> {
                    val updatedList = state.locations.map {
                        if (it.id == targetId) {
                            val updated = it.copy(lat = lat, lng = lng, gpsStatus = "GPS Verified")
                            apiClient.mobileDb.upsertLocation(updated)
                            updated
                        } else it
                    }
                    state.copy(locations = updatedList)
                }
                "subLocation" -> {
                    val updatedList = state.subLocations.map {
                        if (it.id == targetId) {
                            val updated = it.copy(lat = lat, lng = lng, status = "Verified")
                            apiClient.mobileDb.upsertSubLocation(updated)
                            updated
                        } else it
                    }
                    state.copy(subLocations = updatedList)
                }
                "shop" -> {
                    val updatedList = state.shops.map {
                        if (it.id == targetId) {
                            val updated = it.copy(lat = lat, lng = lng, gpsStatus = "GPS Verified")
                            apiClient.mobileDb.upsertShop(updated)
                            updated
                        } else it
                    }
                    state.copy(
                        shops = updatedList,
                        selectedShop = state.selectedShop?.let {
                            if (it.id == targetId) it.copy(lat = lat, lng = lng, gpsStatus = "GPS Verified") else it
                        }
                    )
                }
                else -> state
            }
            nextState.copy(pendingSyncActionsCount = pendingCount)
        }
        val syncNote = if (_uiState.value.online) "synced to Web Portal map" else "saved to mobile DB (will auto-sync when online)"
        showToast("Updated GPS coordinates (${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}) — $syncNote.")
    }

    fun refreshFormsSilently() {
        syncAllNow(manual = false)
    }

    private fun startPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val s = _uiState.value.session
                if (s != null) {
                    val serverOk = apiClient.checkServer(apiClient.getConfiguredServerUrl())
                    if (serverOk) {
                        if (!_uiState.value.online) {
                            _uiState.update { it.copy(online = true) }
                        }
                        syncAllNow(manual = false)
                    } else {
                        val pendingActions = apiClient.loadPendingSyncActionsCount(s.user.id)
                        _uiState.update {
                            it.copy(
                                online = false,
                                pendingSyncActionsCount = pendingActions
                            )
                        }
                    }
                }
            }
        }
    }

    fun notifyPlansChanged() {
        viewModelScope.launch {
            val uid = _uiState.value.session?.user?.id ?: ""
            val pendingCount = if (uid.isNotBlank()) apiClient.loadPendingSyncActionsCount(uid) else 0
            _uiState.update { it.copy(pendingSyncActionsCount = pendingCount) }
            checkLiveSharingStatus()
            if (_uiState.value.online && pendingCount > 0) {
                syncAllNow(manual = false)
            }
        }
    }

    private fun getAuditTrackingContext(): String {
        val dutyInProgress = _uiState.value.duties.find { it.status.equals("In Progress", ignoreCase = true) }
        return when {
            _uiState.value.selectedDistributor != null -> "Distributor Check: ${_uiState.value.selectedDistributor?.setupName}"
            _uiState.value.selectedShop != null -> "Retail Shop Audit: ${_uiState.value.selectedShop?.name}"
            dutyInProgress != null -> "Duty: ${dutyInProgress.title}"
            _uiState.value.activeSection == MobileSection.AUDIT -> "Conducting Audit"
            _uiState.value.activeSection == MobileSection.DUTIES -> "Reviewing Duties"
            else -> "Field Auditor Active"
        }
    }

    private suspend fun checkLiveSharingStatus() {
        if (_uiState.value.session == null) return
        _uiState.update {
            it.copy(liveSharing = it.liveSharing.copy(sharing = true))
        }
        if (liveLocationListener == null) {
            startLiveTracking()
        }
    }

    private var locationPingJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startLiveTracking() {
        val ctx = getApplication<Application>()
        if (!hasLocationPermission(ctx)) {
            _uiState.update {
                it.copy(
                    liveSharing = it.liveSharing.copy(
                        problem = "Location permission needed to share your field audit location with web portal."
                    )
                )
            }
            return
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        stopLiveTracking()

        // Start background multi-provider warming as well
        runCatching { WarmLocationManager.startWarming(ctx) }

        // Immediately seed from WarmLocationManager or lastKnownLocation across all providers and send first ping
        val initialPos = WarmLocationManager.latestPosition.value ?: run {
            val candidates = listOfNotNull(
                runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
                runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
            )
            candidates.maxByOrNull { it.time }?.let { loc ->
                DevicePosition(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 20.0,
                    at = currentTimePkt()
                )
            }
        }

        if (initialPos != null && hasCoords(initialPos.lat, initialPos.lng)) {
            _uiState.update {
                it.copy(liveSharing = it.liveSharing.copy(position = initialPos, problem = "", sharing = true))
            }
            dispatchLocationPing(initialPos, System.currentTimeMillis())
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val pos = DevicePosition(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 20.0,
                    at = currentTimePkt()
                )
                WarmLocationManager.recordFix(pos)
                _uiState.update {
                    it.copy(liveSharing = it.liveSharing.copy(position = pos, problem = "", sharing = true))
                }

                val now = System.currentTimeMillis()
                val movedSignificant = lastPingLat == 0.0 || haversineMeters(lastPingLat, lastPingLng, pos.lat, pos.lng) >= 15.0
                val oneMinuteElapsed = lastPingTimeMs == 0L || (now - lastPingTimeMs) >= 60_000L

                if (oneMinuteElapsed || movedSignificant) {
                    dispatchLocationPing(pos, now)
                }
            }
        }
        liveLocationListener = listener

        runCatching {
            val providers = buildList {
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
            for (provider in providers) {
                runCatching {
                    lm.requestLocationUpdates(provider, 5000L, 0f, listener, Looper.getMainLooper())
                }
            }
            _uiState.update { it.copy(liveSharing = it.liveSharing.copy(sharing = true, problem = "")) }
        }.onFailure {
            _uiState.update {
                it.copy(liveSharing = it.liveSharing.copy(problem = "Waiting for a GPS signal…"))
            }
        }

        // Periodic 60-second timer to ensure location updates are captured and sent every 1 minute even if stationary
        locationPingJob?.cancel()
        locationPingJob = viewModelScope.launch {
            // If no initial position was available at 0s, actively request one after 2 seconds
            if (_uiState.value.liveSharing.position == null && _uiState.value.session != null) {
                delay(2_000L)
                val firstFix = WarmLocationManager.latestPosition.value
                    ?: runCatching { getDevicePosition(ctx, timeoutMs = 6000L) }.getOrNull()
                if (firstFix != null && hasCoords(firstFix.lat, firstFix.lng)) {
                    _uiState.update {
                        it.copy(liveSharing = it.liveSharing.copy(position = firstFix, problem = "", sharing = true))
                    }
                    dispatchLocationPing(firstFix, System.currentTimeMillis())
                }
            }
            while (isActive) {
                delay(60_000L) // 1 minute gap
                if (_uiState.value.session != null) {
                    val curPos = WarmLocationManager.latestPosition.value
                        ?: _uiState.value.liveSharing.position
                        ?: runCatching { getDevicePosition(ctx, timeoutMs = 5000L) }.getOrNull()
                    if (curPos != null && hasCoords(curPos.lat, curPos.lng)) {
                        _uiState.update {
                            it.copy(liveSharing = it.liveSharing.copy(position = curPos, problem = "", sharing = true))
                        }
                        dispatchLocationPing(curPos, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    private fun dispatchLocationPing(pos: DevicePosition, nowMs: Long) {
        val s = _uiState.value.session ?: return
        if (!hasCoords(pos.lat, pos.lng)) return
        lastPingTimeMs = nowMs
        lastPingLat = pos.lat
        lastPingLng = pos.lng
        val ctxStr = getAuditTrackingContext()
        val dutyInProgress = _uiState.value.duties.find {
            it.status.equals("In Progress", ignoreCase = true) || it.status.equals("Assigned", ignoreCase = true)
        }

        viewModelScope.launch {
            try {
                apiClient.sendTrackingPing(
                    lat = pos.lat,
                    lng = pos.lng,
                    accuracy = pos.accuracy,
                    context = ctxStr,
                    dutyId = dutyInProgress?.id,
                    planId = null
                )
            } catch (_: Exception) {
                // Network unavailable or offline: save breadcrumb locally in SQLite so full day record is preserved
                apiClient.mobileDb.queueBreadcrumb(
                    userId = s.user.id,
                    lat = pos.lat,
                    lng = pos.lng,
                    accuracy = pos.accuracy,
                    context = ctxStr,
                    dutyId = dutyInProgress?.id,
                    planId = null,
                    recordedAt = currentSqlUtcTimestamp()
                )
            }
        }
    }

    private fun stopLiveTracking() {
        locationPingJob?.cancel()
        locationPingJob = null
        val listener = liveLocationListener ?: return
        liveLocationListener = null
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching { lm?.removeUpdates(listener) }
    }

    fun handleLogout() {
        viewModelScope.launch {
            periodicJob?.cancel()
            stopLiveTracking()
            DutyBackgroundSyncService.stop(getApplication())
            apiClient.logout()
            _uiState.update {
                AppUiState(
                    booting = false,
                    session = null,
                    online = it.online
                )
            }
        }
    }

    fun setSection(section: MobileSection) {
        _uiState.update {
            it.copy(
                activeSection = section,
                selectedShop = null,
                selectedDistributor = null
            )
        }
    }

    fun toggleNotificationsDialog(show: Boolean) {
        _uiState.update { it.copy(showNotificationsDialog = show) }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            apiClient.markNotificationAsRead(notificationId)
            _uiState.update { state ->
                val updatedNotifs = state.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
                state.copy(
                    notifications = updatedNotifs,
                    unreadNotificationsCount = updatedNotifs.count { !it.isRead }
                )
            }
        }
    }

    fun updateDutyStatus(dutyId: String, newStatus: String) {
        viewModelScope.launch {
            apiClient.updateDutyStatus(dutyId, newStatus)
            _uiState.update { state ->
                val updatedList = state.duties.map {
                    if (it.id == dutyId) it.copy(status = newStatus) else it
                }
                state.copy(duties = updatedList)
            }
        }
    }

    fun startDutyAudit(duty: AuditorDuty) {
        if (duty.status.equals("Assigned", ignoreCase = true)) {
            updateDutyStatus(duty.id, "In Progress")
        }
        _uiState.update { state ->
            state.copy(
                filterCityId = duty.cityId.ifBlank { duty.cityIds.firstOrNull() ?: state.filterCityId },
                filterTerritoryId = if (duty.territoryIds.size == 1) duty.territoryIds.first() else "",
                activeSection = MobileSection.AUDIT,
                selectedShop = null,
                selectedDistributor = null,
                showNotificationsDialog = false
            )
        }
        showToast("Started duty ${duty.code} (${duty.cityName} · ${duty.territoryName})")
    }

    fun setGeographyFilter(
        cityId: String = _uiState.value.filterCityId,
        territoryId: String = _uiState.value.filterTerritoryId,
        locationId: String = _uiState.value.filterLocationId,
        subLocationId: String = _uiState.value.filterSubLocationId,
        targetSection: MobileSection? = null
    ) {
        _uiState.update {
            it.copy(
                filterCityId = cityId,
                filterTerritoryId = territoryId,
                filterLocationId = locationId,
                filterSubLocationId = subLocationId,
                activeSection = targetSection ?: it.activeSection,
                selectedShop = null,
                selectedDistributor = null
            )
        }
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                filterCityId = "",
                filterTerritoryId = "",
                filterLocationId = "",
                filterSubLocationId = "",
                selectedShop = null,
                selectedDistributor = null,
                searchQuery = ""
            )
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun selectShop(shop: Shop?) {
        _uiState.update { it.copy(selectedShop = shop) }
    }

    fun selectDistributor(distributor: Distributor?) {
        _uiState.update { it.copy(selectedDistributor = distributor) }
    }

    private fun prependDistributorReport(auditorId: String, saved: DistributorProductAuditReport) {
        val updated = listOf(saved) + _uiState.value.auditReports.filter { it.id != saved.id }
        apiClient.saveCachedDistributorReports(auditorId, updated)
        _uiState.update { it.copy(auditReports = updated) }
    }

    private fun prependRetailReport(auditorId: String, saved: RetailMarketShopAuditReport) {
        val updated = listOf(saved) + _uiState.value.retailReports.filter { it.id != saved.id }
        apiClient.saveCachedRetailReports(auditorId, updated)
        _uiState.update { it.copy(retailReports = updated) }
    }

    private fun prependCustomSubmission(auditorId: String, saved: CustomAuditSubmission) {
        val updated = listOf(saved) + _uiState.value.customSubmissions.filter { it.id != saved.id }
        apiClient.saveCachedCustomSubmissions(auditorId, updated)
        _uiState.update { it.copy(customSubmissions = updated) }
    }

    private fun enqueueOutboxItem(auditorId: String, item: PendingOutboxItem) {
        val updated = listOf(item) + _uiState.value.pendingOutbox.filter { it.id != item.id }
        apiClient.saveOutboxItems(auditorId, updated)
        _uiState.update { it.copy(pendingOutbox = updated) }
    }

    suspend fun submitDistributorReport(report: DistributorProductAuditReport): DistributorProductAuditReport {
        val auditorId = _uiState.value.session?.user?.id ?: report.auditorId
        if (!report.planId.isNullOrBlank() && !report.planItemId.isNullOrBlank()) {
            apiClient.mobileDb.markPlanItemDoneLocally(
                userId = auditorId,
                planId = report.planId,
                itemId = report.planItemId,
                reportKind = "distributor",
                reportId = report.id,
                reportCode = report.reportCode
            )
            _uiState.update { it.copy(plansRefreshVersion = it.plansRefreshVersion + 1) }
        }
        return try {
            if (report.planId?.startsWith("pln-local-") == true) {
                runCatching { apiClient.flushPendingSyncActions(auditorId) }
            }
            val latestPlanId = if (report.planId?.startsWith("pln-local-") == true) {
                // Check if local plan was remapped or still offline
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.find { p -> p.items.any { it.reportId == report.id } }
                matched?.id ?: report.planId
            } else report.planId
            val latestItemId = if (report.planItemId?.startsWith("pli-local-") == true) {
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.flatMap { it.items }.find { it.reportId == report.id }
                matched?.id ?: report.planItemId
            } else report.planItemId
            if (latestPlanId?.startsWith("pln-local-") == true) {
                throw ApiError("Offline plan queued for sync", 0)
            }
            val effectiveReport = report.copy(planId = latestPlanId, planItemId = latestItemId)
            val saved = apiClient.submitDistributorReport(effectiveReport)
            prependDistributorReport(auditorId, saved)
            showToast("Submitted ${saved.reportCode} for ${saved.distributorSetupName}.")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val outboxItem = PendingOutboxItem(
                    id = report.id,
                    kind = "distributor",
                    reportCode = report.reportCode,
                    formId = report.formId,
                    formTitle = "Distributor Product Stock Audit",
                    targetName = report.distributorSetupName,
                    territoryCode = report.territoryCode,
                    cityName = report.cityName,
                    createdAtDisplay = "${report.submittedDate} · ${report.submittedTime}",
                    createdAtISO = report.submittedAtISO,
                    locationMatched = report.locationMatched,
                    distributorReport = report
                )
                enqueueOutboxItem(auditorId, outboxItem)
                prependDistributorReport(auditorId, report)
                showToast("Saved ${report.reportCode} to mobile database & Offline Outbox. Will auto-sync when online.")
                report
            } else {
                throw e
            }
        }
    }

    suspend fun submitRetailShopReport(report: RetailMarketShopAuditReport): RetailMarketShopAuditReport {
        val auditorId = _uiState.value.session?.user?.id ?: report.auditorId
        if (!report.planId.isNullOrBlank() && !report.planItemId.isNullOrBlank()) {
            apiClient.mobileDb.markPlanItemDoneLocally(
                userId = auditorId,
                planId = report.planId,
                itemId = report.planItemId,
                reportKind = "retail",
                reportId = report.id,
                reportCode = report.reportCode
            )
            _uiState.update { it.copy(plansRefreshVersion = it.plansRefreshVersion + 1) }
        }
        return try {
            if (report.planId?.startsWith("pln-local-") == true) {
                runCatching { apiClient.flushPendingSyncActions(auditorId) }
            }
            val latestPlanId = if (report.planId?.startsWith("pln-local-") == true) {
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.find { p -> p.items.any { it.reportId == report.id } }
                matched?.id ?: report.planId
            } else report.planId
            val latestItemId = if (report.planItemId?.startsWith("pli-local-") == true) {
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.flatMap { it.items }.find { it.reportId == report.id }
                matched?.id ?: report.planItemId
            } else report.planItemId
            if (latestPlanId?.startsWith("pln-local-") == true) {
                throw ApiError("Offline plan queued for sync", 0)
            }
            val effectiveReport = report.copy(planId = latestPlanId, planItemId = latestItemId)
            val saved = apiClient.submitRetailShopReport(effectiveReport)
            prependRetailReport(auditorId, saved)
            showToast("Submitted ${saved.reportCode} for ${saved.subLocationName}.")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val outboxItem = PendingOutboxItem(
                    id = report.id,
                    kind = "retail",
                    reportCode = report.reportCode,
                    formId = report.formId,
                    formTitle = "Retail Market Shop Audit",
                    targetName = "${report.subLocationName} (${report.auditedShopsCount} shops)",
                    territoryCode = report.territoryCode,
                    cityName = report.cityName,
                    createdAtDisplay = "${report.submittedDate} · ${report.submittedTime}",
                    createdAtISO = report.submittedAtISO,
                    locationMatched = report.auditedShopsCount > 0 && report.matchedLocationShopsCount == report.auditedShopsCount,
                    retailReport = report
                )
                enqueueOutboxItem(auditorId, outboxItem)
                prependRetailReport(auditorId, report)
                showToast("Saved ${report.reportCode} to mobile database & Offline Outbox. Will auto-sync when online.")
                report
            } else {
                throw e
            }
        }
    }

    suspend fun submitCustomAuditReport(
        localRecord: CustomAuditSubmission,
        rawAnswers: Map<String, Any?>,
        planLink: PlanLink?
    ): CustomAuditSubmission {
        val auditorId = _uiState.value.session?.user?.id ?: localRecord.auditorId
        val effPlanId = planLink?.planId ?: localRecord.planId
        val effItemId = planLink?.itemId ?: localRecord.planItemId
        if (!effPlanId.isNullOrBlank() && !effItemId.isNullOrBlank()) {
            apiClient.mobileDb.markPlanItemDoneLocally(
                userId = auditorId,
                planId = effPlanId,
                itemId = effItemId,
                reportKind = "custom",
                reportId = localRecord.id,
                reportCode = localRecord.reportCode
            )
            _uiState.update { it.copy(plansRefreshVersion = it.plansRefreshVersion + 1) }
        }
        return try {
            if (effPlanId?.startsWith("pln-local-") == true) {
                runCatching { apiClient.flushPendingSyncActions(auditorId) }
            }
            val latestPlanId = if (effPlanId?.startsWith("pln-local-") == true) {
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.find { p -> p.items.any { it.reportId == localRecord.id } }
                matched?.id ?: effPlanId
            } else effPlanId
            val latestItemId = if (effItemId?.startsWith("pli-local-") == true) {
                val plans = apiClient.mobileDb.loadPlansForUser(auditorId)
                val matched = plans.flatMap { it.items }.find { it.reportId == localRecord.id }
                matched?.id ?: effItemId
            } else effItemId
            if (latestPlanId?.startsWith("pln-local-") == true) {
                throw ApiError("Offline plan queued for sync", 0)
            }
            val effectiveLink = if (!latestPlanId.isNullOrBlank() && !latestItemId.isNullOrBlank()) {
                PlanLink(latestPlanId, latestItemId)
            } else null
            val saved = apiClient.submitCustomAudit(
                clientRequestId = localRecord.id,
                formId = localRecord.formId,
                targetType = localRecord.targetType,
                targetId = localRecord.targetId,
                answers = rawAnswers,
                photos = localRecord.capturedImages,
                mobileLat = localRecord.mobileLat,
                mobileLng = localRecord.mobileLng,
                mobileAccuracyMeters = localRecord.mobileAccuracyMeters.toDouble(),
                planLink = effectiveLink,
                fallbackMeta = localRecord
            )
            prependCustomSubmission(auditorId, saved)
            showToast("Submitted ${saved.reportCode} (${saved.formTitle}).")
            saved
        } catch (e: ApiError) {
            if (e.isNetwork) {
                val recordWithLink = localRecord.copy(
                    planId = effPlanId,
                    planItemId = effItemId
                )
                val outboxItem = PendingOutboxItem(
                    id = recordWithLink.id,
                    kind = "custom",
                    reportCode = recordWithLink.reportCode,
                    formId = recordWithLink.formId,
                    formTitle = recordWithLink.formTitle,
                    targetName = recordWithLink.targetName,
                    territoryCode = recordWithLink.territoryCode,
                    cityName = recordWithLink.cityName,
                    createdAtDisplay = "${recordWithLink.submittedDate} · ${recordWithLink.submittedTime}",
                    createdAtISO = recordWithLink.submittedAtISO,
                    locationMatched = recordWithLink.locationMatched,
                    customSubmission = recordWithLink
                )
                enqueueOutboxItem(auditorId, outboxItem)
                prependCustomSubmission(auditorId, recordWithLink)
                showToast("Saved ${recordWithLink.reportCode} to mobile database & Offline Outbox. Will auto-sync when online.")
                recordWithLink
            } else {
                throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveTracking()
    }
}

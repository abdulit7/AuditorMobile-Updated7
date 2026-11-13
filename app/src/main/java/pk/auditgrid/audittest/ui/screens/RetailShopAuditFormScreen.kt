package pk.auditgrid.audittest.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.api.AuditGridJsonParsers
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*
import kotlin.math.max
import kotlin.math.roundToInt

private val DISPLAY_OPTIONS = listOf("N/A", "Poor", "Average", "Good", "Excellent")
private val SHOP_STATUS_OPTIONS = listOf("Open", "Close")
private val YES_NO_OPTIONS = listOf("Yes", "No")
private val OB_VISIT_OPTIONS = listOf("Regular", "Not Regular", "Never")
private val OB_TOKEN_REGEX = Regex("""\b(OB[-_\s]?\d+)\b""", RegexOption.IGNORE_CASE)

private data class SynthesizedOrderBookerOption(
    val id: String,
    val code: String,
    val name: String,
    val territoryIds: List<String>,
    val shopsCount: Int
)

private fun Territory.isLinkedToDistributor(dist: Distributor): Boolean {
    if (dist.territoryId == id) return true
    if (distributorId.isNotBlank() && distributorId == dist.id) return true
    if (distributorCode.isNotBlank() && dist.code.isNotBlank() &&
        distributorCode.trim().equals(dist.code.trim(), ignoreCase = true)
    ) {
        return true
    }
    if (distributorName.isNotBlank() && dist.setupName.isNotBlank() &&
        distributorName.trim().equals(dist.setupName.trim(), ignoreCase = true)
    ) {
        return true
    }
    return false
}

fun hasSavedRetailDraft(apiClient: AuditGridApiClient, auditorId: String): Boolean {
    return try {
        val raw = apiClient.getDraftString("auditgrid_retail_shop_audit_draft_v1_$auditorId") ?: return false
        val obj = JSONObject(raw)
        val audits = obj.optJSONObject("auditsBySubLocation")
        val queued = obj.optJSONArray("queuedShopIds")
        (audits != null && audits.length() > 0) || (queued != null && queued.length() > 0)
    } catch (_: Exception) {
        false
    }
}

@Composable
fun RetailShopAuditFormScreen(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    locations: List<LocationItem>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    distributors: List<Distributor> = emptyList(),
    formConfigs: FormConfigMap,
    apiClient: AuditGridApiClient,
    preset: AuditPreset?,
    planLink: PlanLink?,
    onSubmitReport: suspend (RetailMarketShopAuditReport) -> RetailMarketShopAuditReport,
    onFinished: () -> Unit,
    onBackToForms: (() -> Unit)?,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageKey = "auditgrid_retail_shop_audit_draft_v1_${activeAuditor.id}"
    val fh = remember(formConfigs) { makeFormHelper(formConfigs[RETAIL_FORM_ID]) }

    val assignedTerritoryIdsSet = remember(territories, activeAuditor) {
        activeAuditor.linkedTerritoryIds(territories)
    }
    val assignedTerritories = remember(territories, assignedTerritoryIdsSet) {
        val matched = territories.filter { assignedTerritoryIdsSet.contains(it.id) && it.status == "Active" }
        matched.ifEmpty { territories.filter { it.status == "Active" } }
    }
    val effectiveTerritoryIdsSet = remember(assignedTerritories) {
        assignedTerritories.map { it.id }.toSet()
    }

    val scopedDistributors = remember(distributors, effectiveTerritoryIdsSet, assignedTerritories) {
        val activeDists = distributors.filter { it.status == "Active" }
        if (activeDists.isNotEmpty()) {
            val matched = activeDists.filter { it.isLinkedToScope(effectiveTerritoryIdsSet, assignedTerritories) }
            matched.ifEmpty { activeDists }
        } else {
            val map = LinkedHashMap<String, Distributor>()
            assignedTerritories.forEach { t ->
                val distId = t.distributorId.ifBlank { t.distributorCode.ifBlank { "dist-${t.cityId}" } }
                if (!map.containsKey(distId)) {
                    map[distId] = Distributor(
                        id = distId,
                        code = t.distributorCode.ifBlank { "DST-001" },
                        setupName = t.distributorName.ifBlank { "Assigned Distributor" },
                        distributorName = t.distributorName.ifBlank { "Assigned Distributor" },
                        fatherName = "",
                        cnic = "",
                        phone = "",
                        cityId = t.cityId,
                        territoryId = t.id,
                        completeAddress = "",
                        groupIds = emptyList(),
                        productStatuses = emptyMap(),
                        lat = t.lat,
                        lng = t.lng,
                        status = "Active"
                    )
                }
            }
            map.values.toList()
        }
    }

    val assignedCityIdsSet = remember(cities, territories, activeAuditor, scopedDistributors) {
        (activeAuditor.linkedCityIds(cities, territories) +
            assignedTerritories.map { it.cityId } +
            scopedDistributors.map { it.cityId }).filter { it.isNotBlank() }.toSet()
    }
    val assignedCities = remember(cities, assignedCityIdsSet) {
        val matched = cities.filter { assignedCityIdsSet.contains(it.id) && it.status == "Active" }
        matched.ifEmpty { cities.filter { it.status == "Active" } }
    }
    val assignedSubLocations = remember(subLocations, effectiveTerritoryIdsSet) {
        subLocations.filter { effectiveTerritoryIdsSet.contains(it.territoryId) && it.status != "Inactive" }
    }
    val assignedShops = remember(shops, effectiveTerritoryIdsSet) {
        shops.filter { effectiveTerritoryIdsSet.contains(it.territoryId) && it.status == "Active" }
    }

    var selectedCityId by remember { mutableStateOf("") }
    var selectedDistributorId by remember { mutableStateOf("") }
    var selectedOrderBookerId by remember { mutableStateOf("") }
    var markedTerritoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var markedSubLocationIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // Persistent Audit List (queuedShopIds) that stays intact across Locality / Sub-Locality / OB / Distributor navigation
    var queuedShopIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var auditsBySubLocation by remember {
        mutableStateOf<Map<String, Map<String, RetailShopAuditItem>>>(emptyMap())
    }
    var statusBannerText by remember { mutableStateOf<String?>(null) }

    // Selection Mode ("hierarchy" vs "quickSearch") & Audit List filter tabs
    var selectionMode by remember { mutableStateOf("hierarchy") }
    var globalQuickQuery by remember { mutableStateOf("") }
    var auditListTab by remember { mutableStateOf("all") }
    var auditListSearch by remember { mutableStateOf("") }
    var savingAsPlan by remember { mutableStateOf(false) }

    val retailLocPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        }
    }

    DisposableEffect(Unit) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        } else {
            retailLocPermLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        onDispose {
            WarmLocationManager.stopWarming(context)
        }
    }

    LaunchedEffect(markedSubLocationIds, queuedShopIds) {
        if ((markedSubLocationIds.isNotEmpty() || queuedShopIds.isNotEmpty()) && hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
            runCatching { getDevicePosition(context, timeoutMs = 5000L) }
        }
    }

    // Load saved draft on mount
    LaunchedEffect(activeAuditor.id) {
        try {
            val raw = apiClient.getDraftString(storageKey)
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                selectedCityId = obj.optString("selectedCityId")
                selectedDistributorId = obj.optString("selectedDistributorId")
                selectedOrderBookerId = obj.optString("selectedOrderBookerId")

                val markedTerrArr = obj.optJSONArray("markedTerritoryIds")
                val parsedTerrs = if (markedTerrArr != null) {
                    (0 until markedTerrArr.length()).mapNotNull { idx ->
                        markedTerrArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                } else emptyList()
                val legacyTerId = obj.optString("selectedTerritoryId")
                markedTerritoryIds = when {
                    parsedTerrs.isNotEmpty() -> parsedTerrs
                    legacyTerId.isNotBlank() -> listOf(legacyTerId)
                    else -> emptyList()
                }

                val markedSubArr = obj.optJSONArray("markedSubLocationIds")
                val parsedSubs = if (markedSubArr != null) {
                    (0 until markedSubArr.length()).mapNotNull { idx ->
                        markedSubArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                } else emptyList()
                val legacySubId = obj.optString("selectedSubLocationId")
                markedSubLocationIds = when {
                    parsedSubs.isNotEmpty() -> parsedSubs
                    legacySubId.isNotBlank() -> listOf(legacySubId)
                    else -> emptyList()
                }

                val queuedArr = obj.optJSONArray("queuedShopIds")
                val parsedQueue = if (queuedArr != null) {
                    (0 until queuedArr.length()).mapNotNull { idx ->
                        queuedArr.optString(idx).takeIf { it.isNotBlank() }
                    }
                } else emptyList()

                val auditsObj = obj.optJSONObject("auditsBySubLocation")
                val auditedIds = LinkedHashSet<String>()
                if (auditsObj != null) {
                    val map = mutableMapOf<String, Map<String, RetailShopAuditItem>>()
                    val subKeys = auditsObj.keys()
                    while (subKeys.hasNext()) {
                        val subId = subKeys.next()
                        val shopMapObj = auditsObj.optJSONObject(subId) ?: continue
                        val shopMap = mutableMapOf<String, RetailShopAuditItem>()
                        val shopKeys = shopMapObj.keys()
                        while (shopKeys.hasNext()) {
                            val shId = shopKeys.next()
                            shopMapObj.optJSONObject(shId)?.let { itemJson ->
                                shopMap[shId] = AuditGridJsonParsers.parseRetailShopAuditItem(itemJson)
                                auditedIds.add(shId)
                            }
                        }
                        if (shopMap.isNotEmpty()) map[subId] = shopMap
                    }
                    auditsBySubLocation = map
                }
                val mergedQueue = (parsedQueue + auditedIds).distinct()
                queuedShopIds = mergedQueue
                if (auditsBySubLocation.isNotEmpty() || mergedQueue.isNotEmpty()) {
                    statusBannerText = "Draft & Audit List restored automatically from device!"
                }
            }
        } catch (_: Exception) {
        }
        // Apply preset from plan if provided
        preset?.subLocationId?.let { preSubId ->
            assignedSubLocations.find { it.id == preSubId }?.let { sl ->
                val ter = assignedTerritories.find { it.id == sl.territoryId }
                val matchingDist = (preset.distributorId?.let { dId -> scopedDistributors.find { it.id == dId } })
                    ?: (ter?.let { tObj -> scopedDistributors.find { tObj.isLinkedToDistributor(it) } })
                    ?: scopedDistributors.find { it.cityId == sl.cityId }

                selectedCityId = sl.cityId
                if (matchingDist != null) {
                    selectedDistributorId = matchingDist.id
                }
                selectedOrderBookerId = "ALL_OB"
                markedTerritoryIds = listOf(sl.territoryId)
                markedSubLocationIds = listOf(sl.id)

                val areaShopIds = shops.filter { sh ->
                    sh.subLocationId == sl.id &&
                        sh.status == "Active" &&
                        (preset.shopIds.isEmpty() || preset.shopIds.contains(sh.id))
                }.map { it.id }
                if (areaShopIds.isNotEmpty()) {
                    queuedShopIds = (queuedShopIds + areaShopIds).distinct()
                }
            }
        }
    }

    // Persist draft changes
    fun saveDraftToPrefs(
        cId: String = selectedCityId,
        dId: String = selectedDistributorId,
        obId: String = selectedOrderBookerId,
        tIds: List<String> = markedTerritoryIds,
        sIds: List<String> = markedSubLocationIds,
        qIds: List<String> = queuedShopIds,
        audits: Map<String, Map<String, RetailShopAuditItem>> = auditsBySubLocation
    ) {
        try {
            val auditsObj = JSONObject()
            for ((subId, shopMap) in audits) {
                val sObj = JSONObject()
                for ((shId, item) in shopMap) {
                    val imgsArr = JSONArray()
                    item.capturedImages.forEach { img ->
                        imgsArr.put(JSONObject().apply {
                            put("id", img.id)
                            put("url", img.url)
                            put("caption", img.caption)
                            put("capturedAt", img.capturedAt)
                        })
                    }
                    sObj.put(shId, JSONObject().apply {
                        put("shopId", item.shopId)
                        put("shopCode", item.shopCode)
                        put("shopName", item.shopName)
                        put("ownerName", item.ownerName)
                        put("phone", item.phone)
                        put("addressLine", item.addressLine)
                        put("shopCategory", item.shopCategory)
                        put("shopStatus", item.shopStatus.ifBlank { "Open" })
                        put("productACount", item.productACount)
                        put("productBCount", item.productBCount)
                        put("displayRating", item.displayRating)
                        put("packedProductQty", item.packedProductQty)
                        put("regularity", item.regularity)
                        put("salesFloTagged", item.salesFloTagged.ifBlank { "No" })
                        put("doubleTag", item.doubleTag.ifBlank { "No" })
                        put("obVisit", item.obVisit.ifBlank { if (item.regularity == "Yes") "Regular" else "Not Regular" })
                        put("computerizedBill", item.computerizedBill.ifBlank { "No" })
                        put("remarks", item.remarks)
                        put("capturedImages", imgsArr)
                        put("auditStatus", item.auditStatus)
                        put("auditedAt", item.auditedAt)
                        put("mobileLat", item.mobileLat)
                        put("mobileLng", item.mobileLng)
                        put("mobileAccuracyMeters", item.mobileAccuracyMeters)
                        put("shopLat", item.shopLat)
                        put("shopLng", item.shopLng)
                        put("distanceMeters", item.distanceMeters)
                        put("allowedRadiusMeters", item.allowedRadiusMeters)
                        put("locationMatched", item.locationMatched)
                    })
                }
                auditsObj.put(subId, sObj)
            }
            val primarySubId = sIds.firstOrNull() ?: ""
            val primarySub = assignedSubLocations.find { it.id == primarySubId }
            val payload = JSONObject().apply {
                put("selectedCityId", cId)
                put("selectedDistributorId", dId)
                put("selectedOrderBookerId", obId)
                put("markedTerritoryIds", JSONArray(tIds))
                put("markedSubLocationIds", JSONArray(sIds))
                put("queuedShopIds", JSONArray(qIds))
                put("selectedTerritoryId", tIds.firstOrNull() ?: primarySub?.territoryId ?: "")
                put("selectedLocationId", primarySub?.locationId ?: "")
                put("selectedSubLocationId", primarySubId)
                put("auditsBySubLocation", auditsObj)
                put("updatedAt", currentIsoTimestamp())
            }
            apiClient.saveDraftString(storageKey, payload.toString())
        } catch (_: Exception) {
        }
    }

    var shopFilterQuery by remember { mutableStateOf("") }
    var activePopupShop by remember { mutableStateOf<Shop?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitKey by remember { mutableStateOf(newSubmitKey()) }
    var completedSummaryReport by remember { mutableStateOf<RetailMarketShopAuditReport?>(null) }

    BackHandler(enabled = onBackToForms != null) {
        onBackToForms?.invoke()
    }

    val summary = completedSummaryReport
    if (summary != null) {
        RetailAuditSummaryView(
            report = summary,
            onDone = {
                completedSummaryReport = null
                onFinished()
            }
        )
        return
    }

    // Step 2: Distributors of Selected Town linked to auditor
    val distributorsInSelectedTown = remember(selectedCityId, scopedDistributors, assignedTerritories) {
        if (selectedCityId.isBlank()) emptyList()
        else {
            val direct = scopedDistributors.filter { it.cityId == selectedCityId }
            if (direct.isNotEmpty()) direct
            else {
                val townTerrs = assignedTerritories.filter { it.cityId == selectedCityId }
                scopedDistributors.filter { d -> townTerrs.any { t -> t.isLinkedToDistributor(d) } }
            }
        }
    }

    val selectedDistributor = remember(scopedDistributors, selectedDistributorId) {
        scopedDistributors.find { it.id == selectedDistributorId }
    }

    // Localities linked to Selected Distributor (or All Distributors in Town when ALL_DIST is selected)
    val distributorLocalities = remember(selectedDistributorId, selectedDistributor, assignedTerritories, selectedCityId) {
        if (selectedCityId.isBlank() || selectedDistributorId.isBlank()) emptyList()
        else {
            val townTerrs = assignedTerritories.filter { it.cityId == selectedCityId }
            if (selectedDistributorId == "ALL_DIST") townTerrs
            else if (selectedDistributor == null) emptyList()
            else {
                val linked = townTerrs.filter { it.isLinkedToDistributor(selectedDistributor) }
                linked.ifEmpty { townTerrs }
            }
        }
    }

    // Step 3: Order Bookers of Selected Distributor
    val distributorOrderBookers = remember(
        selectedDistributorId,
        selectedDistributor,
        distributorLocalities,
        assignedSubLocations,
        shops
    ) {
        if (selectedDistributorId.isBlank() || distributorLocalities.isEmpty()) emptyList()
        else {
            val obTerrMap = LinkedHashMap<String, Pair<String, MutableSet<String>>>()
            val distTerrIdsSet = distributorLocalities.map { it.id }.toSet()

            distributorLocalities.forEach { t ->
                var obCode = t.obCode.trim()
                var obName = t.obName.trim()
                if (obCode.isBlank()) {
                    val m = OB_TOKEN_REGEX.find(t.name)
                    if (m != null) {
                        val token = m.groupValues[1].replace(Regex("\\s+"), "").uppercase()
                        obCode = "${selectedDistributor?.code ?: "DIST"}-$token"
                        obName = "$token (${selectedDistributor?.setupName ?: "Distributor"})"
                    }
                }
                if (obCode.isNotBlank()) {
                    val key = obCode.uppercase()
                    val entry = obTerrMap.getOrPut(key) { (obName.ifBlank { obCode }) to LinkedHashSet() }
                    entry.second.add(t.id)
                }
            }

            assignedSubLocations
                .filter { distTerrIdsSet.contains(it.territoryId) && it.obCode.isNotBlank() }
                .forEach { s ->
                    val code = s.obCode.trim()
                    val key = code.uppercase()
                    val entry = obTerrMap.getOrPut(key) { (s.obName.trim().ifBlank { code }) to LinkedHashSet() }
                    entry.second.add(s.territoryId)
                }

            shops
                .filter { distTerrIdsSet.contains(it.territoryId) && it.obCode.isNotBlank() && it.status == "Active" }
                .forEach { sh ->
                    val code = sh.obCode.trim()
                    val key = code.uppercase()
                    val entry = obTerrMap.getOrPut(key) { (sh.obName.trim().ifBlank { code }) to LinkedHashSet() }
                    entry.second.add(sh.territoryId)
                }

            val synthesized = obTerrMap.entries.map { (key, pair) ->
                val terIds = pair.second.toList()
                val terSet = terIds.toSet()
                val shCount = shops.count { it.status == "Active" && terSet.contains(it.territoryId) }
                SynthesizedOrderBookerOption(
                    id = "ob-synth-$key",
                    code = key,
                    name = pair.first,
                    territoryIds = terIds,
                    shopsCount = shCount
                )
            }.sortedBy { it.name }

            val allTerIds = distributorLocalities.map { it.id }
            val totalShops = shops.count { it.status == "Active" && distTerrIdsSet.contains(it.territoryId) }

            listOf(
                SynthesizedOrderBookerOption(
                    id = "ALL_OB",
                    code = "ALL",
                    name = "All Order Bookers (${distributorLocalities.size} Localities)",
                    territoryIds = allTerIds,
                    shopsCount = totalShops
                )
            ) + synthesized
        }
    }

    val selectedOrderBooker = remember(distributorOrderBookers, selectedOrderBookerId) {
        distributorOrderBookers.find { it.id == selectedOrderBookerId }
    }

    // Step 4: Localities of Selected Order Booker
    val orderBookerLocalities = remember(selectedDistributorId, selectedOrderBooker, distributorLocalities) {
        if (selectedDistributorId.isBlank() || selectedOrderBooker == null) emptyList()
        else if (selectedOrderBooker.id == "ALL_OB") distributorLocalities
        else {
            val obTerSet = selectedOrderBooker.territoryIds.toSet()
            distributorLocalities.filter { obTerSet.contains(it.id) }
        }
    }

    // Step 5: Sub-Localities belonging to Marked Localities
    val markedTerritoryIdsSet = remember(markedTerritoryIds) { markedTerritoryIds.toSet() }
    val availableSubLocationsForMarkedLocalities = remember(assignedSubLocations, markedTerritoryIdsSet) {
        if (markedTerritoryIdsSet.isEmpty()) emptyList()
        else assignedSubLocations.filter { markedTerritoryIdsSet.contains(it.territoryId) }
    }

    // Step 6: Shops belonging to Marked Sub-Localities (for marking to Audit List)
    val markedSubLocationIdsSet = remember(markedSubLocationIds) { markedSubLocationIds.toSet() }

    val subLocationShops = remember(shops, markedSubLocationIdsSet) {
        if (markedSubLocationIdsSet.isEmpty()) emptyList()
        else shops.filter { sh ->
            markedSubLocationIdsSet.contains(sh.subLocationId) && sh.status == "Active"
        }
    }

    val visibleSubLocationShops = remember(subLocationShops, shopFilterQuery) {
        subLocationShops.filter { sh ->
            if (shopFilterQuery.isBlank()) true
            else {
                val q = shopFilterQuery.lowercase()
                sh.name.lowercase().contains(q) ||
                    sh.code.lowercase().contains(q) ||
                    sh.ownerName.lowercase().contains(q) ||
                    sh.addressLine.lowercase().contains(q)
            }
        }
    }

    fun getSavedShopAudit(shop: Shop): RetailShopAuditItem? {
        return auditsBySubLocation[shop.subLocationId]?.get(shop.id)
    }

    // Persistent Audit List (queuedShopIds + any shop that already has a Done audit)
    val queuedShopIdsSet = remember(queuedShopIds, auditsBySubLocation) {
        val set = LinkedHashSet<String>(queuedShopIds)
        auditsBySubLocation.values.forEach { subMap ->
            subMap.keys.forEach { shId -> set.add(shId) }
        }
        set
    }

    val auditListShops = remember(shops, queuedShopIdsSet) {
        val byId = shops.associateBy { it.id }
        queuedShopIdsSet.mapNotNull { byId[it] }
    }

    val doneAuditListShops = remember(auditListShops, auditsBySubLocation) {
        auditListShops.filter { getSavedShopAudit(it)?.auditStatus == "Done" }
    }

    val pendingAuditListShops = remember(auditListShops, auditsBySubLocation) {
        auditListShops.filter { getSavedShopAudit(it)?.auditStatus != "Done" }
    }

    val filteredAuditListShops = remember(
        auditListTab,
        auditListShops,
        doneAuditListShops,
        pendingAuditListShops,
        auditListSearch,
        subLocations,
        territories
    ) {
        val base = when (auditListTab) {
            "done" -> doneAuditListShops
            "pending" -> pendingAuditListShops
            else -> auditListShops
        }
        if (auditListSearch.isBlank()) base
        else {
            val q = auditListSearch.lowercase()
            base.filter { sh ->
                val subObj = subLocations.find { it.id == sh.subLocationId }
                val terObj = territories.find { it.id == sh.territoryId }
                sh.name.lowercase().contains(q) ||
                    sh.code.lowercase().contains(q) ||
                    sh.ownerName.lowercase().contains(q) ||
                    (subObj?.name ?: "").lowercase().contains(q) ||
                    (terObj?.name ?: "").lowercase().contains(q)
            }
        }
    }

    // Quick Search matching Sub-Localities & Shops across all assigned scope
    val quickSearchSubLocations = remember(globalQuickQuery, assignedSubLocations, territories) {
        val q = globalQuickQuery.trim().lowercase()
        if (q.isEmpty()) assignedSubLocations.take(8)
        else assignedSubLocations.filter { s ->
            val ter = territories.find { it.id == s.territoryId }
            s.name.lowercase().contains(q) ||
                s.code.lowercase().contains(q) ||
                (ter?.name ?: "").lowercase().contains(q)
        }.take(10)
    }

    val quickSearchShops = remember(globalQuickQuery, assignedShops, subLocations, territories) {
        val q = globalQuickQuery.trim().lowercase()
        if (q.isEmpty()) assignedShops.take(15)
        else assignedShops.filter { sh ->
            val sub = subLocations.find { it.id == sh.subLocationId }
            val ter = territories.find { it.id == sh.territoryId }
            sh.name.lowercase().contains(q) ||
                sh.code.lowercase().contains(q) ||
                sh.ownerName.lowercase().contains(q) ||
                sh.phone.lowercase().contains(q) ||
                sh.addressLine.lowercase().contains(q) ||
                (sub?.name ?: "").lowercase().contains(q) ||
                (ter?.name ?: "").lowercase().contains(q)
        }.take(25)
    }

    fun toggleShopInAuditList(shop: Shop) {
        val isQueued = queuedShopIdsSet.contains(shop.id)
        val isDone = getSavedShopAudit(shop)?.auditStatus == "Done"
        if (isQueued) {
            if (isDone) {
                onToast("\"${shop.name}\" is already audited (Done). Keep it in the list to submit.")
                return
            }
            val next = queuedShopIds.filterNot { it == shop.id }
            queuedShopIds = next
            saveDraftToPrefs(qIds = next)
            onToast("Removed \"${shop.name}\" from Audit List.")
        } else {
            val next = (queuedShopIds + shop.id).distinct()
            queuedShopIds = next
            saveDraftToPrefs(qIds = next)
            onToast("Added \"${shop.name}\" to Audit List (${next.size} shops).")
        }
    }

    fun openShopForAudit(shop: Shop) {
        if (!isMobileLocationReadyForAudit(context)) {
            onToast("Mobile location is OFF. Turn on mobile location to start an audit.")
            onBackToForms?.invoke()
            return
        }
        if (!queuedShopIdsSet.contains(shop.id)) {
            val next = (queuedShopIds + shop.id).distinct()
            queuedShopIds = next
            saveDraftToPrefs(qIds = next)
        }
        activePopupShop = shop
    }

    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Royal Blue Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgRoyalBlue)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBackToForms != null) {
                        IconButton(
                            onClick = onBackToForms,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Audit Forms",
                                tint = Color.White
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Retail Audit",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mark shops from any locality into your Audit List",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (auditListShops.isNotEmpty()) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "List: ${doneAuditListShops.size}/${auditListShops.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (onBackToForms != null) {
                        IconButton(
                            onClick = onBackToForms,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                statusBannerText?.let { banner ->
                    Surface(
                        color = AgAccentGreenBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AgAccentGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = banner,
                                    color = AgAccentGreenDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { statusBannerText = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = AgAccentGreenDark,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Mode Switcher: Browse by Locality vs Quick Search & Add
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isHierarchy = selectionMode == "hierarchy"
                        Surface(
                            color = if (isHierarchy) AgRoyalBlue else AgBackground,
                            contentColor = if (isHierarchy) Color.White else AgForeground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clickable { selectionMode = "hierarchy" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Browse by Locality", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val isQuick = selectionMode == "quickSearch"
                        Surface(
                            color = if (isQuick) AgRoyalBlue else AgBackground,
                            contentColor = if (isQuick) Color.White else AgForeground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clickable { selectionMode = "quickSearch" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quick Search & Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ================================================================
                // WAY 2: QUICK SEARCH ANY SHOP / SUB-LOCALITY / LOCALITY
                // ================================================================
                if (selectionMode == "quickSearch") {
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AgLightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Quick Find & Mark Shops or Sub-Localities",
                                    color = AgForeground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                CodeBadge("Instant Add", bg = AgSoftBlueBg, fg = AgActionBlue)
                            }

                            OutlinedTextField(
                                value = globalQuickQuery,
                                onValueChange = { globalQuickQuery = it },
                                placeholder = { Text("Search shop name, code, phone, sub-locality or locality...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (globalQuickQuery.isNotBlank()) {
                                        IconButton(onClick = { globalQuickQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (quickSearchSubLocations.isNotEmpty()) {
                                var showAllQuickSubs by remember(globalQuickQuery) { mutableStateOf(false) }
                                val visibleQuickSubs = if (showAllQuickSubs) quickSearchSubLocations else quickSearchSubLocations.take(4)
                                Text(
                                    text = "Sub-Localities (${quickSearchSubLocations.size}) — Tap + All Shops to add",
                                    color = AgMutedText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Column(
                                    modifier = Modifier.animateContentSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    visibleQuickSubs.forEach { sub ->
                                        val ter = territories.find { it.id == sub.territoryId }
                                        val areaShops = shops.filter { it.subLocationId == sub.id && it.status == "Active" }
                                        Surface(
                                            color = AgBackground,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, AgBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sub.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("Locality: ${ter?.name ?: sub.code} • ${areaShops.size} Shops", color = AgMutedText, fontSize = 10.sp)
                                                }
                                                TextButton(
                                                    onClick = {
                                                        val ids = areaShops.map { it.id }
                                                        if (ids.isEmpty()) {
                                                            onToast("No active shops in ${sub.name}.")
                                                        } else {
                                                            val next = (queuedShopIds + ids).distinct()
                                                            queuedShopIds = next
                                                            saveDraftToPrefs(qIds = next)
                                                            onToast("Added ${ids.size} shops from \"${sub.name}\" to Audit List.")
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("+ Add ${areaShops.size} Shops", color = AgActionBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    if (quickSearchSubLocations.size > 4) {
                                        ShowMoreListToggle(
                                            totalCount = quickSearchSubLocations.size,
                                            collapsedLimit = 4,
                                            expanded = showAllQuickSubs,
                                            itemLabel = "Sub-Localities",
                                            onToggle = { showAllQuickSubs = !showAllQuickSubs }
                                        )
                                    }
                                }
                            }

                            var showAllQuickShops by remember(globalQuickQuery) { mutableStateOf(false) }
                            val visibleQuickShops = if (showAllQuickShops) quickSearchShops else quickSearchShops.take(5)
                            Text(
                                text = "Matching Shops (${quickSearchShops.size}) — Tap checkbox to add to Audit List",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Column(
                                modifier = Modifier.animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                visibleQuickShops.forEach { shop ->
                                    val isQueued = queuedShopIdsSet.contains(shop.id)
                                    val isDone = getSavedShopAudit(shop)?.auditStatus == "Done"
                                    val subObj = subLocations.find { it.id == shop.subLocationId }
                                    val terObj = territories.find { it.id == shop.territoryId }
                                    Surface(
                                        color = if (isQueued) AgSoftBlueBg else AgBackground,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isQueued) AgActionBlue else AgBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { toggleShopInAuditList(shop) }
                                            ) {
                                                Icon(
                                                    imageVector = if (isQueued) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = null,
                                                    tint = if (isQueued) AgActionBlue else AgMutedText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text("${shop.name} (${shop.code})", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${terObj?.name ?: "Locality"} → ${subObj?.name ?: "Sub-Locality"} • ${shop.ownerName}", color = AgMutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            TextButton(
                                                onClick = { openShopForAudit(shop) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isDone) "Done (Edit)" else "Audit Now",
                                                    color = if (isDone) AgAccentGreenDark else AgActionBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                if (quickSearchShops.size > 5) {
                                    ShowMoreListToggle(
                                        totalCount = quickSearchShops.size,
                                        collapsedLimit = 5,
                                        expanded = showAllQuickShops,
                                        itemLabel = "Shops",
                                        onToggle = { showAllQuickShops = !showAllQuickShops }
                                    )
                                }
                            }
                        }
                    }
                }

                // ================================================================
                // WAY 1: CASCADING HIERARCHY SELECTION
                // 1. Select Town -> 2. Select Distributor -> 3. Select Order Booker ->
                // 4. Mark Localities -> 5. Mark Sub-Localities -> 6. Mark Shops to Add to Audit List
                // ================================================================
                if (selectionMode == "hierarchy") {
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AgLightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (auditListShops.isNotEmpty()) {
                                Surface(
                                    color = AgAccentGreenBg,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Switching locality or sub-locality keeps your ${auditListShops.size} shop(s) in the Audit List below.",
                                        color = AgAccentGreenDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // 1. Select Town *
                            GeographyDropdown(
                                label = "1. Select Town *",
                                selectedLabel = assignedCities.find { it.id == selectedCityId }?.name ?: "Select Town...",
                                enabled = true,
                                options = assignedCities.map { it.id to it.name },
                                onSelect = { cId ->
                                    selectedCityId = cId
                                    selectedDistributorId = ""
                                    selectedOrderBookerId = ""
                                    markedTerritoryIds = emptyList()
                                    markedSubLocationIds = emptyList()
                                    saveDraftToPrefs(cId = cId, dId = "", obId = "", tIds = emptyList(), sIds = emptyList())
                                }
                            )

                            // 2. Select Distributor *
                            val distDropdownOptions = remember(selectedCityId, distributorsInSelectedTown) {
                                val base = distributorsInSelectedTown.map { it.id to "${it.setupName} (${it.code})" }
                                if (selectedCityId.isNotBlank() && distributorsInSelectedTown.size > 1) {
                                    listOf("ALL_DIST" to "All Distributors in Town (${distributorsInSelectedTown.size})") + base
                                } else base
                            }
                            GeographyDropdown(
                                label = "2. Select Distributor *",
                                selectedLabel = when {
                                    selectedCityId.isBlank() -> "Select Town First..."
                                    selectedDistributorId == "ALL_DIST" -> "All Distributors in Town (${distributorsInSelectedTown.size})"
                                    selectedDistributor != null -> "${selectedDistributor.setupName} (${selectedDistributor.code})"
                                    else -> "Select Distributor (${distributorsInSelectedTown.size} Available)..."
                                },
                                enabled = selectedCityId.isNotBlank(),
                                options = distDropdownOptions,
                                onSelect = { dId ->
                                    selectedDistributorId = dId
                                    selectedOrderBookerId = "ALL_OB"
                                    markedTerritoryIds = emptyList()
                                    markedSubLocationIds = emptyList()
                                    saveDraftToPrefs(dId = dId, obId = "ALL_OB", tIds = emptyList(), sIds = emptyList())
                                }
                            )

                            // 3. Select Order Booker (OB) *
                            GeographyDropdown(
                                label = "3. Select Order Booker (OB) *",
                                selectedLabel = when {
                                    selectedDistributorId.isBlank() -> "Select Distributor First..."
                                    selectedOrderBooker != null -> {
                                        if (selectedOrderBooker.id == "ALL_OB") selectedOrderBooker.name
                                        else "${selectedOrderBooker.name} (${selectedOrderBooker.territoryIds.size} Localities · ${selectedOrderBooker.shopsCount} Shops)"
                                    }
                                    else -> "Select Order Booker..."
                                },
                                enabled = selectedDistributorId.isNotBlank(),
                                options = distributorOrderBookers.map { ob ->
                                    val label = if (ob.id == "ALL_OB") ob.name
                                    else "${ob.name} (${ob.territoryIds.size} Localities · ${ob.shopsCount} Shops)"
                                    ob.id to label
                                },
                                onSelect = { obId ->
                                    selectedOrderBookerId = obId
                                    markedTerritoryIds = emptyList()
                                    markedSubLocationIds = emptyList()
                                    saveDraftToPrefs(obId = obId, tIds = emptyList(), sIds = emptyList())
                                }
                            )

                            // 4. Mark Localities (shown when Order Booker is selected)
                            if (selectedOrderBooker != null) {
                                HorizontalDivider(color = AgLightBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "4. Mark Localities (${markedTerritoryIds.size}/${orderBookerLocalities.size}) *",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (orderBookerLocalities.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                val allIds = orderBookerLocalities.map { it.id }
                                                if (markedTerritoryIds.size == allIds.size) {
                                                    markedTerritoryIds = emptyList()
                                                    markedSubLocationIds = emptyList()
                                                    saveDraftToPrefs(tIds = emptyList(), sIds = emptyList())
                                                } else {
                                                    markedTerritoryIds = allIds
                                                    saveDraftToPrefs(tIds = allIds)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (markedTerritoryIds.size == orderBookerLocalities.size) "Clear All" else "Mark All Localities",
                                                color = AgActionBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (orderBookerLocalities.isEmpty()) {
                                    Text(
                                        text = "No localities linked to this Order Booker.",
                                        color = AgMutedText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    var showAllLocalities by remember(selectedOrderBookerId) { mutableStateOf(false) }
                                    val visibleLocalities = if (showAllLocalities) orderBookerLocalities else orderBookerLocalities.take(4)
                                    Column(
                                        modifier = Modifier.animateContentSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        visibleLocalities.forEach { ter ->
                                            val isMarked = markedTerritoryIdsSet.contains(ter.id)
                                            val subCount = assignedSubLocations.count { it.territoryId == ter.id }
                                            val shCount = shops.count { it.territoryId == ter.id && it.status == "Active" }
                                            val queuedInTer = auditListShops.count { it.territoryId == ter.id }
                                            Surface(
                                                color = if (isMarked) AgSoftBlueBg else AgBackground,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, if (isMarked) AgActionBlue else AgBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val nextTerrs = if (isMarked) {
                                                            markedTerritoryIds - ter.id
                                                        } else {
                                                            markedTerritoryIds + ter.id
                                                        }
                                                        val nextTerSet = nextTerrs.toSet()
                                                        val nextSubs = markedSubLocationIds.filter { sId ->
                                                            val sl = assignedSubLocations.find { it.id == sId }
                                                            sl != null && nextTerSet.contains(sl.territoryId)
                                                        }
                                                        markedTerritoryIds = nextTerrs
                                                        markedSubLocationIds = nextSubs
                                                        saveDraftToPrefs(tIds = nextTerrs, sIds = nextSubs)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isMarked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                            contentDescription = null,
                                                            tint = if (isMarked) AgActionBlue else AgMutedText,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = ter.name,
                                                                color = AgForeground,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${ter.code} · $subCount Sub-Localities" +
                                                                    if (queuedInTer > 0) " · $queuedInTer in Audit List" else "",
                                                                color = AgMutedText,
                                                                fontSize = 10.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                    CodeBadge("$shCount Shops", bg = AgCardWhite, fg = AgActionBlue)
                                                }
                                            }
                                        }
                                        if (orderBookerLocalities.size > 4) {
                                            ShowMoreListToggle(
                                                totalCount = orderBookerLocalities.size,
                                                collapsedLimit = 4,
                                                expanded = showAllLocalities,
                                                itemLabel = "Localities",
                                                onToggle = { showAllLocalities = !showAllLocalities }
                                            )
                                        }
                                    }
                                }
                            }

                            // 5. Mark Sub-Localities (shown when Localities are marked)
                            if (markedTerritoryIds.isNotEmpty()) {
                                HorizontalDivider(color = AgLightBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "5. Mark Sub-Localities (${markedSubLocationIds.size}/${availableSubLocationsForMarkedLocalities.size}) *",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (availableSubLocationsForMarkedLocalities.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                val allSubIds = availableSubLocationsForMarkedLocalities.map { it.id }
                                                val nextSubs = if (markedSubLocationIds.size == allSubIds.size) {
                                                    emptyList()
                                                } else {
                                                    allSubIds
                                                }
                                                markedSubLocationIds = nextSubs
                                                saveDraftToPrefs(sIds = nextSubs)
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (markedSubLocationIds.size == availableSubLocationsForMarkedLocalities.size) "Clear All" else "Mark All Sub-Localities",
                                                color = AgActionBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (availableSubLocationsForMarkedLocalities.isEmpty()) {
                                    Text(
                                        text = "No sub-localities found in the marked localities.",
                                        color = AgMutedText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    var showAllSubLocalities by remember(markedTerritoryIds) { mutableStateOf(false) }
                                    val visibleSubs = if (showAllSubLocalities) availableSubLocationsForMarkedLocalities else availableSubLocationsForMarkedLocalities.take(4)
                                    Column(
                                        modifier = Modifier.animateContentSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        visibleSubs.forEach { sub ->
                                            val isMarked = markedSubLocationIdsSet.contains(sub.id)
                                            val parentTer = territories.find { it.id == sub.territoryId }
                                            val areaShops = shops.filter { it.subLocationId == sub.id && it.status == "Active" }
                                            val shCount = areaShops.size
                                            val queuedInSub = auditListShops.count { it.subLocationId == sub.id }
                                            Surface(
                                                color = if (isMarked) AgAccentGreenBg else AgBackground,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, if (isMarked) AgAccentGreen else AgBorder),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                val nextSubs = if (isMarked) {
                                                                    markedSubLocationIds - sub.id
                                                                } else {
                                                                    markedSubLocationIds + sub.id
                                                                }
                                                                markedSubLocationIds = nextSubs
                                                                saveDraftToPrefs(sIds = nextSubs)
                                                            }
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isMarked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                            contentDescription = null,
                                                            tint = if (isMarked) AgAccentGreenDark else AgMutedText,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = sub.name,
                                                                color = AgForeground,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "Locality: ${parentTer?.name ?: sub.territoryName}" +
                                                                    if (queuedInSub > 0) " • $queuedInSub/$shCount in List" else "",
                                                                color = AgMutedText,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val ids = areaShops.map { it.id }
                                                            if (ids.isNotEmpty()) {
                                                                val next = (queuedShopIds + ids).distinct()
                                                                queuedShopIds = next
                                                                saveDraftToPrefs(qIds = next)
                                                                onToast("Added $shCount shops from \"${sub.name}\" to Audit List.")
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "+ All ($shCount)",
                                                            color = AgAccentGreenDark,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (availableSubLocationsForMarkedLocalities.size > 4) {
                                            ShowMoreListToggle(
                                                totalCount = availableSubLocationsForMarkedLocalities.size,
                                                collapsedLimit = 4,
                                                expanded = showAllSubLocalities,
                                                itemLabel = "Sub-Localities",
                                                onToggle = { showAllSubLocalities = !showAllSubLocalities }
                                            )
                                        }
                                    }
                                }
                            }

                            // 6. Mark Shops in Marked Sub-Localities to Add to Audit List
                            if (markedSubLocationIds.isNotEmpty()) {
                                HorizontalDivider(color = AgLightBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "6. Mark Shops to Add to Audit List (${visibleSubLocationShops.size})",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (visibleSubLocationShops.isNotEmpty()) {
                                        val allCurrentQueued = visibleSubLocationShops.all { queuedShopIdsSet.contains(it.id) }
                                        TextButton(
                                            onClick = {
                                                val currentIds = visibleSubLocationShops.map { it.id }
                                                if (allCurrentQueued) {
                                                    val doneIds = doneAuditListShops.map { it.id }.toSet()
                                                    val removeIds = currentIds.filterNot { doneIds.contains(it) }.toSet()
                                                    val next = queuedShopIds.filterNot { removeIds.contains(it) }
                                                    queuedShopIds = next
                                                    saveDraftToPrefs(qIds = next)
                                                    onToast("Removed un-audited shops of this sub-locality from Audit List.")
                                                } else {
                                                    val next = (queuedShopIds + currentIds).distinct()
                                                    queuedShopIds = next
                                                    saveDraftToPrefs(qIds = next)
                                                    onToast("Added ${currentIds.size} shops to Audit List!")
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (allCurrentQueued) "Unmark Un-audited" else "+ Mark All Shops",
                                                color = AgActionBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = shopFilterQuery,
                                    onValueChange = { shopFilterQuery = it },
                                    placeholder = { Text("Filter shops in marked sub-locality...", fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (visibleSubLocationShops.isEmpty()) {
                                    Text(
                                        text = "No shops found in the marked sub-localities.",
                                        color = AgMutedText,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                } else {
                                    var showAllStep6Shops by remember(markedSubLocationIds, shopFilterQuery) { mutableStateOf(false) }
                                    val visibleStep6Shops = if (showAllStep6Shops || shopFilterQuery.isNotBlank()) {
                                        visibleSubLocationShops
                                    } else {
                                        visibleSubLocationShops.take(5)
                                    }
                                    Column(
                                        modifier = Modifier.animateContentSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        visibleStep6Shops.forEach { shop ->
                                            val isQueued = queuedShopIdsSet.contains(shop.id)
                                            val isDone = getSavedShopAudit(shop)?.auditStatus == "Done"
                                            val subObj = assignedSubLocations.find { it.id == shop.subLocationId }
                                            val terObj = territories.find { it.id == shop.territoryId }
                                            var step6More by remember(shop.id) { mutableStateOf(false) }
                                            Surface(
                                                color = when {
                                                    isDone -> AgAccentGreenBg
                                                    isQueued -> AgSoftBlueBg
                                                    else -> AgBackground
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    when {
                                                        isDone -> AgAccentGreen
                                                        isQueued -> AgActionBlue
                                                        else -> AgBorder
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                        .animateContentSize(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable { toggleShopInAuditList(shop) }
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isQueued) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                                contentDescription = null,
                                                                tint = if (isQueued) AgActionBlue else AgMutedText,
                                                                modifier = Modifier.size(19.dp)
                                                            )
                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text(
                                                                    text = shop.name,
                                                                    color = AgForeground,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "${terObj?.name ?: "Locality"} → ${subObj?.name ?: shop.code}",
                                                                    color = AgRoyalBlue,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        Button(
                                                            onClick = { openShopForAudit(shop) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isDone) AgAccentGreenDark else AgActionBlue
                                                            ),
                                                            modifier = Modifier.heightIn(min = 34.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isDone) "Done" else "Audit",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${shop.code} · ${shop.ownerName}",
                                                            color = AgMutedText,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        CardMoreDetailsToggle(
                                                            expanded = step6More,
                                                            labelCollapsed = "More",
                                                            labelExpanded = "Less",
                                                            onToggle = { step6More = !step6More }
                                                        )
                                                    }
                                                    if (step6More) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                StructuredMetaGridItem("Phone", shop.phone.ifBlank { "—" }, Modifier.weight(1f))
                                                                StructuredMetaGridItem("Owner", shop.ownerName.ifBlank { "—" }, Modifier.weight(1f))
                                                            }
                                                            if (shop.addressLine.isNotBlank()) {
                                                                StructuredMetaGridItem("Address", shop.addressLine, Modifier.fillMaxWidth())
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (visibleSubLocationShops.size > 5 && shopFilterQuery.isBlank()) {
                                            ShowMoreListToggle(
                                                totalCount = visibleSubLocationShops.size,
                                                collapsedLimit = 5,
                                                expanded = showAllStep6Shops,
                                                itemLabel = "Shops",
                                                onToggle = { showAllStep6Shops = !showAllStep6Shops }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ================================================================
                // PERSISTENT AUDIT LIST (SHOPS ADDED ACROSS ANY LOCALITY / SUB-LOCALITY)
                // ================================================================
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Checklist, contentDescription = null, tint = AgRoyalBlue, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Audit List (${auditListShops.size} Shops)",
                                    color = AgForeground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CodeBadge("Done: ${doneAuditListShops.size}", bg = AgAccentGreenBg, fg = AgAccentGreenDark)
                                CodeBadge("Pending: ${pendingAuditListShops.size}", bg = AgSoftBlueBg, fg = AgActionBlue)
                            }
                        }

                        if (auditListShops.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = AgActionBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Your Audit List is Empty",
                                    color = AgForeground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mark a Locality → Mark a Sub-Locality → Mark Shop(s) above (or use Quick Search) to add shops here. You can switch to another locality/sub-locality anytime and keep adding shops before submitting!",
                                    color = AgMutedText,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Filter Tabs inside Audit List
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "all" to "All (${auditListShops.size})",
                                    "pending" to "Pending (${pendingAuditListShops.size})",
                                    "done" to "Done (${doneAuditListShops.size})"
                                ).forEach { (tabKey, tabLabel) ->
                                    val active = auditListTab == tabKey
                                    Surface(
                                        color = if (active) AgRoyalBlue else AgBackground,
                                        contentColor = if (active) Color.White else AgForeground,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (active) AgRoyalBlue else AgBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { auditListTab = tabKey }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                                        ) {
                                            Text(tabLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (auditListShops.size > 4) {
                                OutlinedTextField(
                                    value = auditListSearch,
                                    onValueChange = { auditListSearch = it },
                                    placeholder = { Text("Search inside Audit List...", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = AgMutedText, modifier = Modifier.size(16.dp))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Audit List Cards (Mobile-first layout with progressive disclosure)
                            var showAllAuditListShops by remember(auditListTab, auditListSearch) { mutableStateOf(false) }
                            val visibleAuditListShops = if (showAllAuditListShops || auditListSearch.isNotBlank()) {
                                filteredAuditListShops
                            } else {
                                filteredAuditListShops.take(5)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleAuditListShops.forEachIndexed { idx, shop ->
                                    val savedAudit = getSavedShopAudit(shop)
                                    val isDone = savedAudit?.auditStatus == "Done"
                                    val subObj = subLocations.find { it.id == shop.subLocationId }
                                    val terObj = territories.find { it.id == shop.territoryId }
                                    var itemMoreOpen by remember(shop.id) { mutableStateOf(false) }

                                    Surface(
                                        color = if (isDone) AgAccentGreenBg.copy(alpha = 0.45f) else AgBackground,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDone) AgAccentGreen.copy(alpha = 0.45f) else AgSoftBlueBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openShopForAudit(shop) }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .animateContentSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        CodeBadge("#${idx + 1}", bg = AgMutedBg, fg = AgPrimary)
                                                        CodeBadge(shop.code, bg = AgSoftBlueBg, fg = AgRoyalBlue)
                                                        Surface(
                                                            color = if (isDone) AgAccentGreenBg else AgWarningBg,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isDone) "Done" else "Pending",
                                                                color = if (isDone) AgAccentGreenDark else AgWarningDark,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = shop.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = AgForeground,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${terObj?.name ?: "Locality"} → ${subObj?.name ?: "Sub-Locality"}",
                                                        fontSize = 11.sp,
                                                        color = AgActionBlue,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val nextQueue = queuedShopIds.filterNot { it == shop.id }
                                                        val nextAudits = if (isDone) {
                                                            auditsBySubLocation.toMutableMap().apply {
                                                                val subMap = get(shop.subLocationId)?.toMutableMap()
                                                                if (subMap != null) {
                                                                    subMap.remove(shop.id)
                                                                    if (subMap.isEmpty()) remove(shop.subLocationId)
                                                                    else put(shop.subLocationId, subMap)
                                                                }
                                                            }
                                                        } else auditsBySubLocation
                                                        queuedShopIds = nextQueue
                                                        auditsBySubLocation = nextAudits
                                                        saveDraftToPrefs(qIds = nextQueue, audits = nextAudits)
                                                        onToast("Removed \"${shop.name}\" from Audit List.")
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove from Audit List",
                                                        tint = AgDestructive,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CardMoreDetailsToggle(
                                                    expanded = itemMoreOpen,
                                                    labelCollapsed = "More Details",
                                                    labelExpanded = "Hide Details",
                                                    onToggle = { itemMoreOpen = !itemMoreOpen }
                                                )
                                                Button(
                                                    onClick = { openShopForAudit(shop) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isDone) AgAccentGreenDark else AgRoyalBlue
                                                    ),
                                                    modifier = Modifier.heightIn(min = 34.dp)
                                                ) {
                                                    Text(
                                                        text = if (isDone) "Edit Audit" else "Audit Shop",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (itemMoreOpen) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        StructuredMetaGridItem("Owner", shop.ownerName.ifBlank { "—" }, Modifier.weight(1f))
                                                        StructuredMetaGridItem("Phone", shop.phone.ifBlank { "—" }, Modifier.weight(1f))
                                                    }
                                                    if (shop.addressLine.isNotBlank()) {
                                                        StructuredMetaGridItem("Address", shop.addressLine, Modifier.fillMaxWidth())
                                                    }
                                                    if (isDone && savedAudit != null) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            StructuredMetaGridItem("Status", savedAudit.shopStatus, Modifier.weight(1f))
                                                            StructuredMetaGridItem("Display", savedAudit.displayRating, Modifier.weight(1f))
                                                            StructuredMetaGridItem("Packed", savedAudit.packedProductQty.toString(), Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (filteredAuditListShops.size > 5 && auditListSearch.isBlank()) {
                                    ShowMoreListToggle(
                                        totalCount = filteredAuditListShops.size,
                                        collapsedLimit = 5,
                                        expanded = showAllAuditListShops,
                                        itemLabel = "Shops in Audit List",
                                        onToggle = { showAllAuditListShops = !showAllAuditListShops }
                                    )
                                }
                            }

                            // Audited Progress Bar
                            Surface(
                                color = AgSoftBlueBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgSoftBlueBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AgAccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${doneAuditListShops.size} / ${auditListShops.size} Shops Audited in List",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Start / Audit Next Pending Shop Button
                            if (pendingAuditListShops.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val nextShop = pendingAuditListShops.firstOrNull() ?: auditListShops.firstOrNull()
                                        if (nextShop != null) openShopForAudit(nextShop)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("start_retail_audit_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (doneAuditListShops.isEmpty()) {
                                            "Start Audit (${pendingAuditListShops.size} Pending)"
                                        } else {
                                            "Audit Next Pending Shop (${pendingAuditListShops.size} Left)"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 1-Tap Save Selected Audit List as Plan
                            OutlinedButton(
                                onClick = {
                                    if (savingAsPlan || auditListShops.isEmpty()) return@OutlinedButton
                                    savingAsPlan = true
                                    scope.launch {
                                        try {
                                            val bySub = auditListShops.groupBy { it.subLocationId }
                                            val planItems = bySub.map { (subId, shList) ->
                                                val subObj = subLocations.find { it.id == subId }
                                                val terObj = territories.find { it.id == subObj?.territoryId }
                                                PlanItem(
                                                    id = "",
                                                    formId = RETAIL_FORM_ID,
                                                    formTitle = "Retail Market Shop Audit",
                                                    targetType = "subLocation",
                                                    targetId = subId,
                                                    targetLabel = "${subObj?.name ?: "Sub-Locality"} (${terObj?.name ?: "Locality"})",
                                                    shopIds = shList.map { it.id }
                                                )
                                            }
                                            apiClient.createPlan(
                                                clientRequestId = newSubmitKey(),
                                                planDate = currentIsoDate(),
                                                title = "Retail Shop Audit Plan (${auditListShops.size} Shops)",
                                                items = planItems
                                            )
                                            statusBannerText = "Saved ${auditListShops.size} selected shop(s) as today's Audit Plan!"
                                            onToast("Saved Audit List as an Audit Plan!")
                                        } catch (e: Exception) {
                                            onToast(e.message ?: "Could not save plan.")
                                        } finally {
                                            savingAsPlan = false
                                        }
                                    }
                                },
                                enabled = !savingAsPlan,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgActionBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.EventNote, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (savingAsPlan) "Saving Plan…" else "Save List as Plan (${auditListShops.size} Shops)",
                                    color = AgActionBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Submit All Audited (Done) Shops Across All Localities & Sub-Localities in Audit List
                            fun triggerSubmitRetailAudit(
                                overrideAudits: Map<String, Map<String, RetailShopAuditItem>> = auditsBySubLocation,
                                overrideQueue: List<String> = queuedShopIds
                            ) {
                                if (submitting) return
                                if (!isMobileLocationReadyForAudit(context)) {
                                    onToast("Mobile location is OFF. Turn on mobile location to submit this audit.")
                                    return
                                }
                                fun lookupAudit(sh: Shop): RetailShopAuditItem? =
                                    overrideAudits[sh.subLocationId]?.get(sh.id)

                                val effectiveQueueIds = LinkedHashSet<String>().apply {
                                    addAll(overrideQueue)
                                    overrideAudits.values.forEach { subMap -> addAll(subMap.keys) }
                                }
                                val shopById = shops.associateBy { it.id }
                                val effectiveListShops = effectiveQueueIds.mapNotNull { shopById[it] }
                                val auditedShops = effectiveListShops.filter { sh ->
                                    lookupAudit(sh)?.auditStatus == "Done"
                                }
                                val auditedItems = auditedShops.mapNotNull { sh ->
                                    lookupAudit(sh)?.takeIf { it.auditStatus == "Done" }
                                }
                                if (auditedItems.isEmpty()) {
                                    onToast("Please audit at least one shop before submitting.")
                                    return
                                }

                                // Group audited shops by their own subLocationId so every POST /api/reports/retail
                                // contains only shops belonging to that subLocationId
                                val shopsBySubLocation = LinkedHashMap<String, MutableList<Shop>>()
                                auditedShops.forEach { sh ->
                                    val subId = sh.subLocationId.ifBlank { markedSubLocationIds.firstOrNull() ?: "" }
                                    if (subId.isNotBlank()) {
                                        shopsBySubLocation.getOrPut(subId) { mutableListOf() }.add(sh)
                                    }
                                }

                                if (shopsBySubLocation.isEmpty()) {
                                    onToast("Could not determine sub-locality for the audited shops.")
                                    return
                                }

                                val reportDateDisplay = currentReportDateDisplay()
                                val submittedDate = currentIsoDate()
                                val submittedTime = currentTimePkt()
                                val submittedAtISO = currentIsoTimestamp()

                                submitting = true
                                scope.launch {
                                    try {
                                        val savedReports = mutableListOf<RetailMarketShopAuditReport>()
                                        val groupEntries = shopsBySubLocation.entries.toList()
                                        var workingAudits = overrideAudits
                                        var workingQueue = overrideQueue

                                        for ((subId, subShopsGroup) in groupEntries) {
                                            val subObj = subLocations.find { it.id == subId }
                                                ?: assignedSubLocations.find { it.id == subId }
                                                ?: continue

                                            val groupAuditedItems = subShopsGroup.mapNotNull { sh ->
                                                lookupAudit(sh)?.takeIf { it.auditStatus == "Done" }
                                            }
                                            if (groupAuditedItems.isEmpty()) continue

                                            val cityObj = cities.find { it.id == subObj.cityId }
                                            val terObj = territories.find { it.id == subObj.territoryId }
                                            val locObj = locations.find { it.id == subObj.locationId }
                                            val totalShopsInThisSub = shops.count {
                                                it.subLocationId == subObj.id && it.status == "Active"
                                            }

                                            val requestId = if (groupEntries.size == 1) {
                                                submitKey
                                            } else {
                                                "$submitKey-${subObj.id}".take(64)
                                            }

                                            val attachPlanLink = planLink != null &&
                                                (preset?.subLocationId.isNullOrBlank() || preset?.subLocationId == subObj.id)

                                            val subReport = RetailMarketShopAuditReport(
                                                id = requestId,
                                                reportCode = makeReportCode("AUD-SHP"),
                                                formId = RETAIL_FORM_ID,
                                                cityId = subObj.cityId,
                                                cityName = cityObj?.name ?: "",
                                                territoryId = subObj.territoryId,
                                                territoryCode = terObj?.code ?: "",
                                                territoryName = terObj?.name ?: "",
                                                locationId = subObj.locationId,
                                                locationCode = locObj?.code ?: "",
                                                locationName = locObj?.name ?: "",
                                                subLocationId = subObj.id,
                                                subLocationCode = subObj.code,
                                                subLocationName = subObj.name,
                                                auditorId = activeAuditor.id,
                                                auditorCode = activeAuditor.code,
                                                auditorName = activeAuditor.fullName,
                                                auditorUsername = activeAuditor.username,
                                                reportDateDisplay = reportDateDisplay,
                                                submittedDate = submittedDate,
                                                submittedTime = submittedTime,
                                                submittedAtISO = submittedAtISO,
                                                totalShopsInSubLocation = if (totalShopsInThisSub > 0) totalShopsInThisSub else subShopsGroup.size,
                                                auditedShopsCount = groupAuditedItems.size,
                                                totalProductACount = groupAuditedItems.sumOf { it.productACount },
                                                totalProductBCount = groupAuditedItems.sumOf { it.productBCount },
                                                totalPackedProductQty = groupAuditedItems.sumOf { it.packedProductQty },
                                                matchedLocationShopsCount = groupAuditedItems.count { it.locationMatched },
                                                shopAudits = groupAuditedItems,
                                                planId = if (attachPlanLink) planLink?.planId else null,
                                                planItemId = if (attachPlanLink) planLink?.itemId else null
                                            )

                                            val saved = onSubmitReport(subReport)
                                            savedReports.add(saved)

                                            // Immediately clear this sub-locality's submitted shops from draft & queue
                                            val groupIdsSet = subShopsGroup.map { it.id }.toSet()
                                            val nextAudits = mutableMapOf<String, Map<String, RetailShopAuditItem>>()
                                            workingAudits.forEach { (kSubId, shopMap) ->
                                                val remaining = shopMap.filterKeys { !groupIdsSet.contains(it) }
                                                if (remaining.isNotEmpty()) {
                                                    nextAudits[kSubId] = remaining
                                                }
                                            }
                                            workingAudits = nextAudits
                                            workingQueue = workingQueue.filterNot { groupIdsSet.contains(it) }
                                            auditsBySubLocation = workingAudits
                                            queuedShopIds = workingQueue
                                            saveDraftToPrefs(qIds = workingQueue, audits = workingAudits)
                                        }

                                        val auditedSubIds = shopsBySubLocation.keys.toList()
                                        val auditedSubObjs = auditedSubIds.mapNotNull { id -> subLocations.find { it.id == id } }
                                        val auditedTerIds = auditedShops.map { it.territoryId }.distinct()
                                        val auditedTerObjs = auditedTerIds.mapNotNull { id -> territories.find { it.id == id } }

                                        val primaryReport = savedReports.first()
                                        val combinedSummary = primaryReport.copy(
                                            territoryName = if (auditedTerObjs.size > 1) {
                                                auditedTerObjs.joinToString(", ") { it.name }
                                            } else {
                                                primaryReport.territoryName
                                            },
                                            subLocationName = if (auditedSubObjs.size > 1) {
                                                auditedSubObjs.joinToString(", ") { it.name }
                                            } else {
                                                primaryReport.subLocationName
                                            },
                                            totalShopsInSubLocation = effectiveListShops.size,
                                            auditedShopsCount = auditedItems.size,
                                            totalProductACount = auditedItems.sumOf { it.productACount },
                                            totalProductBCount = auditedItems.sumOf { it.productBCount },
                                            totalPackedProductQty = auditedItems.sumOf { it.packedProductQty },
                                            matchedLocationShopsCount = auditedItems.count { it.locationMatched },
                                            shopAudits = auditedItems
                                        )

                                        submitKey = newSubmitKey()
                                        statusBannerText = null
                                        completedSummaryReport = combinedSummary
                                    } catch (e: Exception) {
                                        onToast(e.message ?: "Could not submit. Your answers are saved on this phone; try again.")
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }

                            if (doneAuditListShops.isNotEmpty()) {
                                Button(
                                    onClick = { triggerSubmitRetailAudit() },
                                    enabled = !submitting,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgAccentGreenDark),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("submit_retail_audit_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (submitting) "Submitting…" else "Submit Audit (${doneAuditListShops.size} Audited Shop${if (doneAuditListShops.size == 1) "" else "s"})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val popupShop = activePopupShop
    if (popupShop != null) {
        val targetSubId = popupShop.subLocationId.ifBlank { markedSubLocationIds.firstOrNull() ?: "" }
        ShopAuditPopupDialog(
            shop = popupShop,
            existingAudit = getSavedShopAudit(popupShop),
            fh = fh,
            onDismiss = { activePopupShop = null },
            onSaved = { savedItem, submitImmediately ->
                val nextQueue = (queuedShopIds + popupShop.id).distinct()
                queuedShopIds = nextQueue
                val nextAudits = if (targetSubId.isNotBlank()) {
                    val updatedSubMap = (auditsBySubLocation[targetSubId] ?: emptyMap()) + (popupShop.id to savedItem)
                    auditsBySubLocation + (targetSubId to updatedSubMap)
                } else {
                    auditsBySubLocation
                }
                auditsBySubLocation = nextAudits
                saveDraftToPrefs(qIds = nextQueue, audits = nextAudits)
                activePopupShop = null

                if (submitImmediately) {
                    onToast("Submitting \"${popupShop.name}\" as Closed without audit…")
                    // Trigger immediate submission with the newly saved Closed shop
                    if (!submitting && isMobileLocationReadyForAudit(context)) {
                        val reportDateDisplay = currentReportDateDisplay()
                        val submittedDate = currentIsoDate()
                        val submittedTime = currentTimePkt()
                        val submittedAtISO = currentIsoTimestamp()

                        fun lookupAudit(sh: Shop): RetailShopAuditItem? =
                            nextAudits[sh.subLocationId]?.get(sh.id)

                        val effectiveQueueIds = LinkedHashSet<String>().apply {
                            addAll(nextQueue)
                            nextAudits.values.forEach { subMap -> addAll(subMap.keys) }
                        }
                        val shopById = shops.associateBy { it.id }
                        val effectiveListShops = effectiveQueueIds.mapNotNull { shopById[it] }
                        val auditedShops = effectiveListShops.filter { sh ->
                            lookupAudit(sh)?.auditStatus == "Done"
                        }
                        val auditedItems = auditedShops.mapNotNull { sh ->
                            lookupAudit(sh)?.takeIf { it.auditStatus == "Done" }
                        }
                        val shopsBySubLocation = LinkedHashMap<String, MutableList<Shop>>()
                        auditedShops.forEach { sh ->
                            val subId = sh.subLocationId.ifBlank { markedSubLocationIds.firstOrNull() ?: "" }
                            if (subId.isNotBlank()) {
                                shopsBySubLocation.getOrPut(subId) { mutableListOf() }.add(sh)
                            }
                        }

                        if (shopsBySubLocation.isNotEmpty() && auditedItems.isNotEmpty()) {
                            submitting = true
                            scope.launch {
                                try {
                                    val savedReports = mutableListOf<RetailMarketShopAuditReport>()
                                    val groupEntries = shopsBySubLocation.entries.toList()
                                    var workingAudits = nextAudits
                                    var workingQueue = nextQueue

                                    for ((subId, subShopsGroup) in groupEntries) {
                                        val subObj = subLocations.find { it.id == subId }
                                            ?: assignedSubLocations.find { it.id == subId }
                                            ?: continue

                                        val groupAuditedItems = subShopsGroup.mapNotNull { sh ->
                                            lookupAudit(sh)?.takeIf { it.auditStatus == "Done" }
                                        }
                                        if (groupAuditedItems.isEmpty()) continue

                                        val cityObj = cities.find { it.id == subObj.cityId }
                                        val terObj = territories.find { it.id == subObj.territoryId }
                                        val locObj = locations.find { it.id == subObj.locationId }
                                        val totalShopsInThisSub = shops.count {
                                            it.subLocationId == subObj.id && it.status == "Active"
                                        }

                                        val requestId = if (groupEntries.size == 1) {
                                            submitKey
                                        } else {
                                            "$submitKey-${subObj.id}".take(64)
                                        }

                                        val attachPlanLink = planLink != null &&
                                            (preset?.subLocationId.isNullOrBlank() || preset?.subLocationId == subObj.id)

                                        val subReport = RetailMarketShopAuditReport(
                                            id = requestId,
                                            reportCode = makeReportCode("AUD-SHP"),
                                            formId = RETAIL_FORM_ID,
                                            cityId = subObj.cityId,
                                            cityName = cityObj?.name ?: "",
                                            territoryId = subObj.territoryId,
                                            territoryCode = terObj?.code ?: "",
                                            territoryName = terObj?.name ?: "",
                                            locationId = subObj.locationId,
                                            locationCode = locObj?.code ?: "",
                                            locationName = locObj?.name ?: "",
                                            subLocationId = subObj.id,
                                            subLocationCode = subObj.code,
                                            subLocationName = subObj.name,
                                            auditorId = activeAuditor.id,
                                            auditorCode = activeAuditor.code,
                                            auditorName = activeAuditor.fullName,
                                            auditorUsername = activeAuditor.username,
                                            reportDateDisplay = reportDateDisplay,
                                            submittedDate = submittedDate,
                                            submittedTime = submittedTime,
                                            submittedAtISO = submittedAtISO,
                                            totalShopsInSubLocation = if (totalShopsInThisSub > 0) totalShopsInThisSub else subShopsGroup.size,
                                            auditedShopsCount = groupAuditedItems.size,
                                            totalProductACount = groupAuditedItems.sumOf { it.productACount },
                                            totalProductBCount = groupAuditedItems.sumOf { it.productBCount },
                                            totalPackedProductQty = groupAuditedItems.sumOf { it.packedProductQty },
                                            matchedLocationShopsCount = groupAuditedItems.count { it.locationMatched },
                                            shopAudits = groupAuditedItems,
                                            planId = if (attachPlanLink) planLink?.planId else null,
                                            planItemId = if (attachPlanLink) planLink?.itemId else null
                                        )

                                        val saved = onSubmitReport(subReport)
                                        savedReports.add(saved)

                                        val groupIdsSet = subShopsGroup.map { it.id }.toSet()
                                        val remAudits = mutableMapOf<String, Map<String, RetailShopAuditItem>>()
                                        workingAudits.forEach { (kSubId, shopMap) ->
                                            val remaining = shopMap.filterKeys { !groupIdsSet.contains(it) }
                                            if (remaining.isNotEmpty()) {
                                                remAudits[kSubId] = remaining
                                            }
                                        }
                                        workingAudits = remAudits
                                        workingQueue = workingQueue.filterNot { groupIdsSet.contains(it) }
                                        auditsBySubLocation = workingAudits
                                        queuedShopIds = workingQueue
                                        saveDraftToPrefs(qIds = workingQueue, audits = workingAudits)
                                    }

                                    val auditedSubIds = shopsBySubLocation.keys.toList()
                                    val auditedSubObjs = auditedSubIds.mapNotNull { id -> subLocations.find { it.id == id } }
                                    val auditedTerIds = auditedShops.map { it.territoryId }.distinct()
                                    val auditedTerObjs = auditedTerIds.mapNotNull { id -> territories.find { it.id == id } }

                                    val primaryReport = savedReports.first()
                                    val combinedSummary = primaryReport.copy(
                                        territoryName = if (auditedTerObjs.size > 1) {
                                            auditedTerObjs.joinToString(", ") { it.name }
                                        } else {
                                            primaryReport.territoryName
                                        },
                                        subLocationName = if (auditedSubObjs.size > 1) {
                                            auditedSubObjs.joinToString(", ") { it.name }
                                        } else {
                                            primaryReport.subLocationName
                                        },
                                        totalShopsInSubLocation = effectiveListShops.size,
                                        auditedShopsCount = auditedItems.size,
                                        totalProductACount = auditedItems.sumOf { it.productACount },
                                        totalProductBCount = auditedItems.sumOf { it.productBCount },
                                        totalPackedProductQty = auditedItems.sumOf { it.packedProductQty },
                                        matchedLocationShopsCount = auditedItems.count { it.locationMatched },
                                        shopAudits = auditedItems
                                    )

                                    submitKey = newSubmitKey()
                                    statusBannerText = null
                                    completedSummaryReport = combinedSummary
                                } catch (e: Exception) {
                                    onToast(e.message ?: "Could not submit. Your answers are saved on this phone; try again.")
                                } finally {
                                    submitting = false
                                }
                            }
                        }
                    }
                } else {
                    val isClosed = savedItem.shopStatus.equals("Close", ignoreCase = true)
                    statusBannerText = if (isClosed) {
                        "Marked \"${popupShop.name}\" as Closed (Done)! You can submit now or audit more shops."
                    } else {
                        "Saved \"${popupShop.name}\" (Done)! You can audit more shops or select another locality/sub-locality before submitting."
                    }
                    onToast(if (isClosed) "Saved \"${popupShop.name}\" as Closed." else "Audit saved for \"${popupShop.name}\" (Status: Done).")
                }
            },
            onToast = onToast
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeographyDropdown(
    label: String,
    selectedLabel: String,
    enabled: Boolean,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 13.sp) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopAuditPopupDialog(
    shop: Shop,
    existingAudit: RetailShopAuditItem?,
    fh: FormHelper,
    onDismiss: () -> Unit,
    onSaved: (RetailShopAuditItem, Boolean) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shopStatus by remember { mutableStateOf(existingAudit?.shopStatus?.ifBlank { "Open" } ?: "Open") }
    var productACount by remember { mutableStateOf(existingAudit?.productACount?.toString() ?: "") }
    var productBCount by remember { mutableStateOf(existingAudit?.productBCount?.toString() ?: "") }
    var displayRating by remember { mutableStateOf(existingAudit?.displayRating ?: "") }
    var packedQty by remember { mutableStateOf(existingAudit?.packedProductQty?.toString() ?: "") }
    var salesFloTagged by remember { mutableStateOf(existingAudit?.salesFloTagged ?: "") }
    var doubleTag by remember { mutableStateOf(existingAudit?.doubleTag ?: "") }
    var obVisit by remember {
        mutableStateOf(
            existingAudit?.obVisit?.ifBlank {
                when (existingAudit.regularity) {
                    "Yes" -> "Regular"
                    "No" -> "Not Regular"
                    else -> ""
                }
            } ?: ""
        )
    }
    var computerizedBill by remember { mutableStateOf(existingAudit?.computerizedBill ?: "") }
    var remarks by remember { mutableStateOf(existingAudit?.remarks ?: "") }
    var shopImages by remember { mutableStateOf(existingAudit?.capturedImages ?: emptyList()) }
    var isSavingWithGps by remember { mutableStateOf(false) }
    var formProblem by remember { mutableStateOf<String?>(null) }
    var shopGpsError by remember { mutableStateOf<String?>(null) }
    val warmStreamPos by WarmLocationManager.latestPosition.collectAsState()
    var preFetchedShopPos by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
    var isPreFetchingGps by remember { mutableStateOf(false) }

    LaunchedEffect(shop.id) {
        if (hasLocationPermission(context)) {
            isPreFetchingGps = true
            WarmLocationManager.startWarming(context)
            try {
                preFetchedShopPos = getDevicePosition(context, timeoutMs = 5000L)
                shopGpsError = null
            } catch (_: Exception) {
            } finally {
                isPreFetchingGps = false
            }
        }
    }

    val liveShopGps = warmStreamPos ?: preFetchedShopPos

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (shopImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                val url = compressBitmapToDataUrl(bmp)
                shopImages = shopImages + AuditCapturedImage(
                    id = "shp-img-${System.currentTimeMillis()}",
                    url = url,
                    caption = "${shop.name} — Shop Display Photo",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is required to capture shop photos.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (shopImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val url = compressUriToDataUrl(context, uri)
                    if (url != null) {
                        shopImages = shopImages + AuditCapturedImage(
                            id = "shp-img-${System.currentTimeMillis()}",
                            url = url,
                            caption = "${shop.name} — Shop Display Photo",
                            capturedAt = currentTimePkt()
                        )
                    }
                }
            }
        }
    }

    fun performSaveShopAudit(submitImmediately: Boolean) {
        val isClosedShop = shopStatus.equals("Close", ignoreCase = true)

        if (!isClosedShop) {
            fun need(key: String, empty: Boolean, fallback: String): String? =
                if (fh.visible(key) && fh.required(key, true) && empty) "${fh.label(key, fallback)} is required." else null

            val problem = need("productACount", productACount.isBlank(), "No. of Product-A")
                ?: need("productBCount", productBCount.isBlank(), "No. of Product-B")
                ?: need("displayRating", displayRating.isBlank(), "Display")
                ?: need("packedProductQty", packedQty.isBlank(), "Packed qty")
                ?: need("salesFloTagged", salesFloTagged.isBlank(), "Shop Tagged in SalesFlo")
                ?: need("doubleTag", doubleTag.isBlank(), "Double Tag")
                ?: need("obVisit", obVisit.isBlank(), "OB Visit")
                ?: need("computerizedBill", computerizedBill.isBlank(), "Computerized Bill")
                ?: if (fh.visible("remarks") && fh.required("remarks") && remarks.isBlank()) "${fh.label("remarks", "Remarks")} is required." else null
                ?: if (fh.settings.allowPhotoCapture && fh.settings.requirePhoto && shopImages.isEmpty()) "A photo of the shop is required." else null

            if (problem != null) {
                formProblem = problem
                shopGpsError = null
                return
            }
        }
        formProblem = null
        isSavingWithGps = true
        scope.launch {
            try {
                val pos = liveShopGps ?: WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                val mLat = (pos.lat * 1_000_000).roundToInt() / 1_000_000.0
                val mLng = (pos.lng * 1_000_000).roundToInt() / 1_000_000.0
                val acc = pos.accuracy.roundToInt()
                val distMeters = calculateDistanceMeters(mLat, mLng, shop.lat, shop.lng)
                val allowedRadius = shop.allowedRadiusMeters.takeIf { it > 0 } ?: DEFAULT_ALLOWED_RADIUS_M
                val matched = distMeters <= allowedRadius && acc <= fh.settings.maxAccuracyMeters

                val effectiveObVisit = if (isClosedShop) "Never" else obVisit.ifBlank { "Regular" }
                val derivedRegularity = if (!isClosedShop && effectiveObVisit == "Regular") "Yes" else "No"

                val item = RetailShopAuditItem(
                    shopId = shop.id,
                    shopCode = shop.code,
                    shopName = shop.name,
                    ownerName = shop.ownerName,
                    phone = shop.phone,
                    addressLine = shop.addressLine,
                    shopCategory = shop.shopCategory,
                    shopStatus = if (isClosedShop) "Close" else "Open",
                    productACount = if (isClosedShop) 0 else if (fh.visible("productACount")) (productACount.toIntOrNull() ?: 0) else 0,
                    productBCount = if (isClosedShop) 0 else if (fh.visible("productBCount")) (productBCount.toIntOrNull() ?: 0) else 0,
                    displayRating = if (isClosedShop) "N/A" else if (fh.visible("displayRating") && displayRating.isNotEmpty()) displayRating else "N/A",
                    packedProductQty = if (isClosedShop) 0 else if (fh.visible("packedProductQty")) (packedQty.toIntOrNull() ?: 0) else 0,
                    regularity = derivedRegularity,
                    salesFloTagged = if (isClosedShop) "No" else salesFloTagged.ifBlank { "No" },
                    doubleTag = if (isClosedShop) "No" else doubleTag.ifBlank { "No" },
                    obVisit = effectiveObVisit,
                    computerizedBill = if (isClosedShop) "No" else computerizedBill.ifBlank { "No" },
                    remarks = if (isClosedShop) {
                        remarks.trim().ifBlank { "Shop Closed" }
                    } else if (fh.visible("remarks")) {
                        remarks.trim()
                    } else "",
                    capturedImages = shopImages,
                    auditStatus = "Done",
                    auditedAt = "${currentIsoDate()} ${currentTimePkt().removeSuffix(" PKT")}",
                    mobileLat = mLat,
                    mobileLng = mLng,
                    mobileAccuracyMeters = acc,
                    shopLat = shop.lat,
                    shopLng = shop.lng,
                    distanceMeters = distMeters,
                    allowedRadiusMeters = allowedRadius,
                    locationMatched = matched
                )
                onSaved(item, submitImmediately)
            } catch (e: Exception) {
                val msg = if (e is GpsException) e.message ?: "Could not read your location."
                else "Could not read your location."
                shopGpsError = msg
                onToast(msg)
            } finally {
                isSavingWithGps = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgSoftBlueBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = AgRoyalBlue)
                        Text("Shop Audit", color = AgRoyalBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = AgLightBorder)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Compact Collapsible Shop Metadata Card
                    var showFullShopMeta by remember(shop.id) { mutableStateOf(false) }
                    Surface(
                        color = AgFormBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = shop.name,
                                        color = AgForeground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${shop.code} · ${shop.phone.ifBlank { "No Phone" }}",
                                        color = AgMutedText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                CardMoreDetailsToggle(
                                    expanded = showFullShopMeta,
                                    labelCollapsed = "More Info",
                                    labelExpanded = "Less Info",
                                    onToggle = { showFullShopMeta = !showFullShopMeta }
                                )
                            }
                            if (showFullShopMeta) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StructuredMetaGridItem("Owner Name", shop.ownerName.ifBlank { "—" }, Modifier.fillMaxWidth())
                                    StructuredMetaGridItem("Address", shop.addressLine.ifBlank { "—" }, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Live Background GPS Pre-fetch Badge
                    Surface(
                        color = if (liveShopGps != null) AgAccentGreenBg else AgSoftBlueBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (liveShopGps != null) AgAccentGreen.copy(alpha = 0.4f) else AgSoftBlueBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = if (liveShopGps != null) AgAccentGreenDark else AgActionBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                val distPreview = if (liveShopGps != null && (shop.lat != 0.0 || shop.lng != 0.0)) {
                                    calculateDistanceMeters(liveShopGps.lat, liveShopGps.lng, shop.lat, shop.lng)
                                } else null
                                Text(
                                    text = when {
                                        liveShopGps != null && distPreview != null ->
                                            "GPS Locked in Background (±${liveShopGps.accuracy.roundToInt()}m · ${distPreview}m from shop)"
                                        liveShopGps != null ->
                                            "GPS Locked in Background (±${liveShopGps.accuracy.roundToInt()}m)"
                                        isPreFetchingGps ->
                                            "Pre-fetching GPS location in background…"
                                        else ->
                                            "Warming up GPS in background…"
                                    },
                                    color = if (liveShopGps != null) AgAccentGreenDark else AgForeground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = AgLightBorder)

                    // Shop Status (Default: Open; if Close -> Submit as Closed without Audit)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fh.label("shopStatus", "Shop Status")} *",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(AgInputFill, RoundedCornerShape(10.dp))
                                .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                                .padding(4.dp)
                        ) {
                            SHOP_STATUS_OPTIONS.forEach { st ->
                                val active = shopStatus.equals(st, ignoreCase = true)
                                val activeBg = if (st == "Open") AgAccentGreenDark else AgDestructive
                                Surface(
                                    color = if (active) activeBg else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        shopStatus = st
                                        formProblem = null
                                    }
                                ) {
                                    Text(
                                        text = st,
                                        color = if (active) Color.White else AgMutedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (shopStatus.equals("Close", ignoreCase = true)) {
                        Surface(
                            color = AgWarningBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgWarning)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Shop is Marked as Closed",
                                    color = AgWarningDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Product and visit audit fields are skipped when a shop is closed. Tap 'Submit as Closed without Audit' below to submit directly, or 'Save Closed in List' to keep auditing other shops.",
                                    color = AgWarningDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        if (fh.visible("productACount")) {
                            StepperInputRow(
                                label = fh.label("productACount", "No. of Product-A"),
                                required = fh.required("productACount", true),
                                value = productACount,
                                onChange = { productACount = it }
                            )
                        }
                        if (fh.visible("productBCount")) {
                            StepperInputRow(
                                label = fh.label("productBCount", "No. of Product-B"),
                                required = fh.required("productBCount", true),
                                value = productBCount,
                                onChange = { productBCount = it }
                            )
                        }
                        if (fh.visible("displayRating")) {
                            SelectOptionRow(
                                label = fh.label("displayRating", "Display"),
                                required = fh.required("displayRating", true),
                                value = displayRating,
                                options = fh.options("displayRating", DISPLAY_OPTIONS),
                                onChange = { displayRating = it }
                            )
                        }
                        if (fh.visible("packedProductQty")) {
                            StepperInputRow(
                                label = fh.label("packedProductQty", "Packed Product Qty (Not in Display)"),
                                required = fh.required("packedProductQty", true),
                                value = packedQty,
                                onChange = { packedQty = it }
                            )
                        }
                        if (fh.visible("salesFloTagged")) {
                            SelectOptionRow(
                                label = fh.label("salesFloTagged", "Shop Tagged in SalesFlo"),
                                required = fh.required("salesFloTagged", true),
                                value = salesFloTagged,
                                options = fh.options("salesFloTagged", YES_NO_OPTIONS),
                                onChange = { salesFloTagged = it }
                            )
                        }
                        if (fh.visible("doubleTag")) {
                            SelectOptionRow(
                                label = fh.label("doubleTag", "Double Tag"),
                                required = fh.required("doubleTag", true),
                                value = doubleTag,
                                options = fh.options("doubleTag", YES_NO_OPTIONS),
                                onChange = { doubleTag = it }
                            )
                        }
                        if (fh.visible("obVisit")) {
                            SelectOptionRow(
                                label = fh.label("obVisit", "OB Visit"),
                                required = fh.required("obVisit", true),
                                value = obVisit,
                                options = fh.options("obVisit", OB_VISIT_OPTIONS),
                                onChange = { obVisit = it }
                            )
                        }
                        if (fh.visible("computerizedBill")) {
                            SelectOptionRow(
                                label = fh.label("computerizedBill", "Computerized Bill"),
                                required = fh.required("computerizedBill", true),
                                value = computerizedBill,
                                options = fh.options("computerizedBill", YES_NO_OPTIONS),
                                onChange = { computerizedBill = it }
                            )
                        }
                    }

                    if (fh.visible("remarks")) {
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = {
                                Text(
                                    fh.label("remarks", "Remarks") +
                                        if (!shopStatus.equals("Close", ignoreCase = true) && fh.required("remarks")) " *" else ""
                                )
                            },
                            placeholder = {
                                Text(
                                    if (shopStatus.equals("Close", ignoreCase = true)) "Optional note for closed shop"
                                    else fh.placeholder("remarks", "Optional notes")
                                )
                            },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (fh.settings.allowPhotoCapture && fh.visible("photos")) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${fh.label("photos", "Capture Image")} ${if (!shopStatus.equals("Close", ignoreCase = true) && fh.settings.requirePhoto) "(Required)" else "(Optional)"} ${shopImages.size}/${fh.settings.maxPhotos}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) cameraLauncher.launch(null)
                                        else cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Camera", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Gallery", fontSize = 12.sp)
                                }
                            }
                            if (shopImages.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    shopImages.forEach { img ->
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                                        ) {
                                            DataUrlImage(img.url, img.caption, Modifier.fillMaxSize())
                                            IconButton(
                                                onClick = { shopImages = shopImages.filterNot { it.id == img.id } },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    formProblem?.let {
                        Surface(
                            color = AgDestructiveBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgDestructive)
                        ) {
                            Text(it, color = AgDestructiveDark, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }

                    shopGpsError?.let {
                        Surface(
                            color = AgDestructiveBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgDestructive)
                        ) {
                            Text("$it Tap Save to try again.", color = AgDestructiveDark, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }

                    if (shopStatus.equals("Close", ignoreCase = true)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { performSaveShopAudit(submitImmediately = true) },
                                enabled = !isSavingWithGps,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgDestructive),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("submit_closed_shop_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isSavingWithGps) "Submitting as Closed..." else "Submit as Closed without Audit",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(2.dp, AgActionBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                ) {
                                    Text("Cancel", color = AgActionBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { performSaveShopAudit(submitImmediately = false) },
                                    enabled = !isSavingWithGps,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .testTag("save_shop_audit_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isSavingWithGps) "Saving..." else "Save Closed in List", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text("Cancel", color = AgActionBlue, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { performSaveShopAudit(submitImmediately = false) },
                                enabled = !isSavingWithGps,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("save_shop_audit_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isSavingWithGps) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AgMutedText, fontSize = 11.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = AgForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun StepperInputRow(
    label: String,
    required: Boolean,
    value: String,
    onChange: (String) -> Unit
) {
    fun bump(delta: Int) {
        val cur = value.toIntOrNull() ?: 0
        onChange(max(0, cur + delta).toString())
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (required) "$label *" else label,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, AgBorder, RoundedCornerShape(10.dp))
                .background(AgBackground, RoundedCornerShape(10.dp))
        ) {
            IconButton(onClick = { bump(-1) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = AgActionBlue, modifier = Modifier.size(16.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
                placeholder = { Text("0", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(64.dp)
            )
            IconButton(onClick = { bump(1) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = AgActionBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectOptionRow(
    label: String,
    required: Boolean,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (required) "$label *" else label,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(150.dp)
        ) {
            OutlinedTextField(
                value = value.ifEmpty { "Select…" },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 12.sp) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RetailAuditSummaryView(
    report: RetailMarketShopAuditReport,
    onDone: () -> Unit
) {
    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgRoyalBlue)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onDone, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Submit Audit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Audit Summary",
                            color = AgRoyalBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        HorizontalDivider(color = AgLightBorder)
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgLightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetaRow("Town:", report.cityName)
                                MetaRow("Localities:", report.territoryName)
                                MetaRow("Sub-Localities:", report.subLocationName)
                                MetaRow("Audit List Shops:", report.totalShopsInSubLocation.toString())
                                MetaRow("Audited & Submitted:", report.auditedShopsCount.toString())
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(AgAccentGreenBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AgAccentGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Audit Completed Successfully!",
                            color = AgAccentGreenDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Your multi-locality shop audit data has been saved with GPS location.",
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Text("View Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
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
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistributorAuditFormScreen(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    locations: List<LocationItem>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    distributors: List<Distributor>,
    products: List<Product>,
    auditForms: List<AuditFormDefinition>,
    formConfigs: FormConfigMap,
    apiClient: AuditGridApiClient,
    initialFormId: String? = null,
    preset: AuditPreset? = null,
    planLink: PlanLink? = null,
    onExit: (() -> Unit)? = null,
    completedTotalCount: Int = 0,
    pendingOutboxCount: Int = 0,
    onOpenCompletedAudits: (() -> Unit)? = null,
    onOpenDuties: (() -> Unit)? = null,
    onOpenLocalities: (() -> Unit)? = null,
    onOpenShops: (() -> Unit)? = null,
    syncing: Boolean = false,
    online: Boolean = true,
    lastSyncedAt: String = "",
    newlySyncedFormIds: Set<String> = emptySet(),
    updatedFormIds: Set<String> = emptySet(),
    formsBundleHash: String = "",
    onSyncFormsNow: (() -> Unit)? = null,
    onImportFormPackageJson: ((String) -> Unit)? = null,
    onSubmitReport: suspend (DistributorProductAuditReport) -> DistributorProductAuditReport,
    onSubmitRetailShopReport: suspend (RetailMarketShopAuditReport) -> RetailMarketShopAuditReport,
    onSubmitCustomAudit: suspend (CustomAuditSubmission, Map<String, Any?>, PlanLink?) -> CustomAuditSubmission,
    onCustomSubmitted: (CustomAuditSubmission) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distDraftKey = "auditgrid_dist_product_audit_draft_v1_${activeAuditor.id}"
    val locationStatus by rememberMobileLocationStatus(context)

    var openedFormId by remember {
        mutableStateOf<String?>(
            when {
                !isMobileLocationReadyForAudit(context) -> null
                !initialFormId.isNullOrBlank() -> initialFormId
                auditForms.any { it.id == RETAIL_FORM_ID } && hasSavedRetailDraft(apiClient, activeAuditor.id) -> RETAIL_FORM_ID
                else -> null
            }
        )
    }
    var pendingFormIdToOpen by remember {
        mutableStateOf<String?>(
            if (!isMobileLocationReadyForAudit(context) && !initialFormId.isNullOrBlank()) initialFormId else null
        )
    }
    var showLocationRequiredDialog by remember {
        mutableStateOf(!isMobileLocationReadyForAudit(context) && !initialFormId.isNullOrBlank())
    }

    val gatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (isDeviceLocationEnabled(context)) {
                showLocationRequiredDialog = false
                pendingFormIdToOpen?.let { targetFormId ->
                    pendingFormIdToOpen = null
                    openedFormId = targetFormId
                }
            } else {
                showLocationRequiredDialog = true
                onToast("Mobile location is OFF. Turn on mobile location to start an audit.")
            }
        } else {
            showLocationRequiredDialog = true
            onToast("Location permission is required to open any audit form.")
        }
    }

    fun tryOpenAuditForm(formId: String) {
        val status = getMobileLocationStatus(context)
        if (status.isReady) {
            showLocationRequiredDialog = false
            pendingFormIdToOpen = null
            openedFormId = formId
        } else {
            pendingFormIdToOpen = formId
            openedFormId = null
            showLocationRequiredDialog = true
            if (!status.hasPermission) {
                onToast("Location permission is off. Allow location to open any audit form.")
            } else {
                onToast("Mobile location is OFF. Turn on mobile location to start an audit.")
            }
        }
    }

    LaunchedEffect(initialFormId) {
        if (!initialFormId.isNullOrBlank()) {
            tryOpenAuditForm(initialFormId)
        }
    }

    // If mobile location is turned OFF while any audit form is open, immediately close the form and block audit
    LaunchedEffect(locationStatus.isReady, openedFormId) {
        if (openedFormId != null && !locationStatus.isReady) {
            pendingFormIdToOpen = openedFormId
            openedFormId = null
            showLocationRequiredDialog = true
            onToast("Mobile location is OFF. Turn on mobile location to open or continue an audit.")
        } else if (locationStatus.isReady && showLocationRequiredDialog) {
            showLocationRequiredDialog = false
            pendingFormIdToOpen?.let { targetFormId ->
                pendingFormIdToOpen = null
                openedFormId = targetFormId
            }
        }
    }

    fun closeForm() {
        if (onExit != null) onExit() else openedFormId = null
    }

    if (showLocationRequiredDialog) {
        MobileLocationRequiredDialog(
            status = locationStatus,
            onTurnOnLocation = { openDeviceLocationSettings(context) },
            onGrantPermission = {
                gatePermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onCheckAgain = {
                val fresh = getMobileLocationStatus(context)
                if (fresh.isReady) {
                    showLocationRequiredDialog = false
                    pendingFormIdToOpen?.let { targetFormId ->
                        pendingFormIdToOpen = null
                        openedFormId = targetFormId
                    }
                } else {
                    onToast("Mobile location is still OFF. Please turn on mobile location first.")
                }
            },
            onDismiss = {
                showLocationRequiredDialog = false
                if (!initialFormId.isNullOrBlank() && openedFormId == null) {
                    onExit?.invoke()
                }
            }
        )
    }

    val assignedTerritoryIdsSet = remember(activeAuditor, territories) {
        activeAuditor.linkedTerritoryIds(territories)
    }
    val assignedCityIdsSet = remember(activeAuditor, cities, territories) {
        activeAuditor.linkedCityIds(cities, territories)
    }
    val scopedCities = remember(cities, assignedCityIdsSet) {
        cities.filter { it.status == "Active" && assignedCityIdsSet.contains(it.id) }
    }
    val scopedTerritories = remember(territories, assignedTerritoryIdsSet) {
        territories.filter { it.status == "Active" && assignedTerritoryIdsSet.contains(it.id) }
    }
    val scopedDistributors = remember(distributors, assignedTerritoryIdsSet, scopedTerritories) {
        distributors.filter { it.isLinkedToScope(assignedTerritoryIdsSet, scopedTerritories) }
    }
    val scopedShops = remember(shops, assignedTerritoryIdsSet) {
        shops.filter { it.status == "Active" && assignedTerritoryIdsSet.contains(it.territoryId) }
    }
    val scopedSubLocations = remember(subLocations, assignedTerritoryIdsSet) {
        subLocations.filter { it.status != "Inactive" && assignedTerritoryIdsSet.contains(it.territoryId) }
    }

    // VIEW 1: AUDIT FORMS LIST DASHBOARD (Matches the Left Phone in AuditGrid Mockup)
    val currentOpenedId = openedFormId?.takeIf { locationStatus.isReady && isMobileLocationReadyForAudit(context) }
    if (currentOpenedId == null) {
        val activeForms = remember(auditForms) {
            val syncedActive = auditForms.filter { it.status == "Active" }
            if (syncedActive.isNotEmpty()) {
                syncedActive
            } else {
                listOf(
                    AuditFormDefinition(
                        id = DISTRIBUTOR_FORM_ID,
                        code = "FRM-AUD-01",
                        title = "Distributor Product Audit Form",
                        shortTitle = "Distributor Audit",
                        category = "Distributor",
                        kind = "builtin",
                        target = "distributor",
                        status = "Active"
                    ),
                    AuditFormDefinition(
                        id = RETAIL_FORM_ID,
                        code = "FRM-AUD-02",
                        title = "Retail Market Shop Audit Form",
                        shortTitle = "Retail Shop Audit",
                        category = "Retail",
                        kind = "builtin",
                        target = "subLocation",
                        status = "Active"
                    )
                )
            }
        }

        val todayStr = remember { pktToday() }
        val townsDisplay = remember(scopedCities, scopedTerritories) {
            when {
                scopedCities.isNotEmpty() -> scopedCities.joinToString(", ") { it.name }
                scopedTerritories.any { it.cityName.isNotBlank() } ->
                    scopedTerritories.mapNotNull { it.cityName.takeIf(String::isNotBlank) }.distinct().joinToString(", ")
                else -> "Assigned Towns"
            }
        }
        val primaryDistributorAndTownSubtitle = remember(scopedDistributors, scopedTerritories, townsDisplay) {
            val distPart = when {
                scopedDistributors.isNotEmpty() -> {
                    val d = scopedDistributors.first()
                    if (d.code.isNotBlank()) "${d.setupName} (${d.code})" else d.setupName
                }
                scopedTerritories.any { it.distributorName.isNotBlank() } -> {
                    val t = scopedTerritories.first { it.distributorName.isNotBlank() }
                    if (t.distributorCode.isNotBlank()) "${t.distributorName} (${t.distributorCode})" else t.distributorName
                }
                else -> "Assigned Distributor"
            }
            "$distPart • $townsDisplay"
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. Hero Card: "Mobile Field Audit Forms"
            Surface(
                color = AgRoyalBlue,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Mobile Field Audit Forms",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Select a synced form to continue",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (onSyncFormsNow != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.80f)),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clickable(enabled = !syncing) { onSyncFormsNow() }
                                .testTag("header_sync_forms_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync forms from web server",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (syncing) "Syncing…" else "Sync Forms",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Prominent Mobile Location OFF Banner when location is disabled
            MobileLocationOffBanner(
                status = locationStatus,
                onTurnOnLocation = { openDeviceLocationSettings(context) },
                onGrantPermission = {
                    gatePermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            // 2. Card: "Your Assigned Duty Scope"
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgBorder),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row: Target icon + Title left, Synced pill right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(AgSoftBlueBg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = AgRoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Assigned Duty Scope",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgForeground
                            )
                        }
                        Surface(
                            color = Color(0xFFD1FAE5),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "SYNCED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    // Towns Progressive Disclosure Chip Group
                    val townChips = remember(townsDisplay) {
                        townsDisplay.split("•", ",", "\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AgRoyalBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "ASSIGNED TOWNS (${townChips.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = AgMutedText
                            )
                        }
                        ExpandableChipGroup(
                            items = townChips,
                            initialCount = 2,
                            emptyText = townsDisplay
                        )
                    }

                    HorizontalDivider(color = AgLightBorder)

                    // Bottom 2-column structured KPI stat tiles: Localities | Shops
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = AgFormBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = onOpenLocalities != null) { onOpenLocalities?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(AgSoftBlueBg, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apartment,
                                        contentDescription = null,
                                        tint = AgRoyalBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${scopedTerritories.size}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AgForeground
                                    )
                                    Text(
                                        text = "Localities",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AgMutedText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = AgMutedText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Surface(
                            color = AgFormBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = onOpenShops != null) { onOpenShops?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(AgAccentGreenBg, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = AgAccentGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${scopedShops.size}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AgForeground
                                    )
                                    Text(
                                        text = "Retail Shops",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AgMutedText
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = AgMutedText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Date Row Card: "Today – YYYY-MM-DD"
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AgBorder),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onOpenDuties != null) { onOpenDuties?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = AgDutyBannerNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Today – $todayStr",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgForeground
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Today's Duties",
                        tint = AgDutyBannerNavy,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 4. Audit Form Cards (FRM-AUD-01, FRM-AUD-02, and any custom synced forms)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                activeForms.forEach { form ->
                    val isRetailForm = form.id == RETAIL_FORM_ID || form.code.equals("FRM-AUD-02", ignoreCase = true)
                    val savedDraftExists = isRetailForm && hasSavedRetailDraft(apiClient, activeAuditor.id)
                    val isNewlyInserted = newlySyncedFormIds.contains(form.id)
                    val isUpdatedFromWeb = updatedFormIds.contains(form.id)

                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 1.dp,
                        border = BorderStroke(
                            width = if (isNewlyInserted) 1.5.dp else 1.dp,
                            color = if (!locationStatus.isReady) {
                                AgDestructive.copy(alpha = 0.45f)
                            } else if (isNewlyInserted) {
                                AgAccentGreen
                            } else {
                                AgBorder
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tryOpenAuditForm(form.id) }
                            .testTag("open_form_${form.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            // Left Rounded Blue Icon Square
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(AgRoyalBlue, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRetailForm) Icons.Default.Storefront else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Middle Content Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Top Row: Code Pill + Assigned/Status Pill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = AgSoftBlueBg,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = form.code.ifBlank { if (isRetailForm) "FRM-AUD-02" else "FRM-AUD-01" },
                                                color = AgRoyalBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (isUpdatedFromWeb && !isNewlyInserted) {
                                            Surface(
                                                color = Color(0xFFDBEAFE),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    text = "UPDATED",
                                                    color = AgRoyalBlue,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    when {
                                        !locationStatus.isReady -> {
                                            Surface(
                                                color = AgDestructiveBg,
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOff,
                                                        contentDescription = null,
                                                        tint = AgDestructive,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = "Location OFF",
                                                        color = AgDestructiveDark,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        savedDraftExists -> {
                                            Surface(
                                                color = AgWarningBg,
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    text = "Draft Saved",
                                                    color = AgWarning,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        else -> {
                                            Surface(
                                                color = Color(0xFFD1FAE5),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(Color(0xFF10B981), CircleShape)
                                                    )
                                                    Text(
                                                        text = "Assigned",
                                                        color = Color(0xFF047857),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Form Title
                                Text(
                                    text = when {
                                        form.id == DISTRIBUTOR_FORM_ID -> "Distributor Product Audit Form"
                                        form.id == RETAIL_FORM_ID -> "Retail Market Shop Audit Form"
                                        else -> form.title
                                    },
                                    color = AgForeground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 19.sp
                                )

                                // Subtitle with Building Icon + Distributor & Town (Progressive Disclosure)
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apartment,
                                        contentDescription = null,
                                        tint = AgDutyBannerNavy,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(14.dp)
                                    )
                                    ExpandableTextWithMore(
                                        text = primaryDistributorAndTownSubtitle,
                                        maxLinesCollapsed = 1,
                                        thresholdChars = 44,
                                        textColor = AgForeground.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // Right Chevron Arrow
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open ${form.title}",
                                tint = AgDutyBannerNavy,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 5. Bottom Soft Blue GPS Info Banner: "GPS will be captured when you submit the form"
            Surface(
                color = Color(0xFFDCEBFF),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFC5DCFA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AgRoyalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPS will be captured when you submit the form",
                        color = AgDutyBannerNavy,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    // VIEW 2A: CUSTOM AUDIT FORM
    val customForm = auditForms.find { it.id == currentOpenedId && it.kind == "custom" }
    if (customForm != null) {
        CustomAuditFormScreen(
            form = customForm,
            config = formConfigs[customForm.id],
            activeAuditor = activeAuditor,
            cities = cities,
            territories = territories,
            distributors = distributors,
            subLocations = subLocations,
            shops = shops,
            presetTargetId = preset?.targetId,
            planLink = planLink,
            apiClient = apiClient,
            onBack = ::closeForm,
            onSubmitCustomAudit = onSubmitCustomAudit,
            onSubmitted = onCustomSubmitted,
            onToast = onToast
        )
        return
    }

    // VIEW 2B: RETAIL MARKET SHOP AUDIT FORM
    if (currentOpenedId == RETAIL_FORM_ID) {
        RetailShopAuditFormScreen(
            activeAuditor = activeAuditor,
            cities = cities,
            territories = territories,
            locations = locations,
            subLocations = subLocations,
            shops = shops,
            distributors = scopedDistributors.ifEmpty { distributors },
            formConfigs = formConfigs,
            apiClient = apiClient,
            preset = preset,
            planLink = planLink,
            onSubmitReport = onSubmitRetailShopReport,
            onFinished = ::closeForm,
            onBackToForms = ::closeForm,
            onToast = onToast
        )
        return
    }

    // VIEW 2C: DISTRIBUTOR PRODUCT AUDIT FORM
    BackHandler { closeForm() }

    val fh = remember(formConfigs) { makeFormHelper(formConfigs[DISTRIBUTOR_FORM_ID]) }
    var selectedDistributorId by remember { mutableStateOf(preset?.distributorId ?: "") }
    var productSearchText by remember { mutableStateOf("") }
    var productDropdownOpen by remember { mutableStateOf(false) }
    var selectedProductId by remember { mutableStateOf("") }

    var qtyInCtn by remember { mutableStateOf("") }
    var qtyInBox by remember { mutableStateOf("") }
    var dispatchDate by remember { mutableStateOf("") }
    var stacking by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    var savedItems by remember { mutableStateOf<List<DistributorAuditLineItem>>(emptyList()) }
    var capturedImages by remember { mutableStateOf<List<AuditCapturedImage>>(emptyList()) }

    val warmDistributorPos by WarmLocationManager.latestPosition.collectAsState()
    var silentCoords by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
    var gpsError by remember { mutableStateOf<String?>(null) }
    var gpsBusy by remember { mutableStateOf(false) }

    LaunchedEffect(warmDistributorPos) {
        if (warmDistributorPos != null) {
            silentCoords = warmDistributorPos
            gpsError = null
        }
    }

    DisposableEffect(currentOpenedId) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        }
        onDispose {
            WarmLocationManager.stopWarming(context)
        }
    }
    var submitting by remember { mutableStateOf(false) }
    var submitKey by remember { mutableStateOf(newSubmitKey()) }

    // Load saved distributor draft if exists
    LaunchedEffect(distDraftKey) {
        try {
            val raw = apiClient.getDraftString(distDraftKey)
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                val distId = obj.optString("selectedDistributorId")
                if (distId.isNotEmpty() && preset?.distributorId == null) {
                    selectedDistributorId = distId
                }
                val itemsArr = obj.optJSONArray("savedItems")
                if (itemsArr != null) {
                    savedItems = (0 until itemsArr.length()).mapNotNull { idx ->
                        itemsArr.optJSONObject(idx)?.let { AuditGridJsonParsers.parseDistributorLineItem(it) }
                    }
                }
                val imgsArr = obj.optJSONArray("capturedImages")
                if (imgsArr != null) {
                    capturedImages = (0 until imgsArr.length()).mapNotNull { idx ->
                        imgsArr.optJSONObject(idx)?.let { AuditGridJsonParsers.parseCapturedImage(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun captureRealLocation(): DevicePosition? {
        gpsBusy = true
        gpsError = null
        return try {
            val pos = getDevicePosition(context)
            silentCoords = pos
            pos
        } catch (e: Exception) {
            silentCoords = null
            gpsError = if (e is GpsException) e.message else "Could not read your location. Tap \"Try again\"."
            null
        } finally {
            gpsBusy = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { captureRealLocation() }
    }

    LaunchedEffect(selectedDistributorId, currentOpenedId) {
        if (!hasLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            captureRealLocation()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (capturedImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                val dataUrl = compressBitmapToDataUrl(bmp)
                capturedImages = capturedImages + AuditCapturedImage(
                    id = "img-${System.currentTimeMillis()}",
                    url = dataUrl,
                    caption = "Warehouse Stock Photo",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is needed to capture warehouse photos.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (capturedImages.size >= fh.settings.maxPhotos) {
                onToast("You can add up to ${fh.settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val dataUrl = compressUriToDataUrl(context, uri)
                    if (dataUrl != null) {
                        capturedImages = capturedImages + AuditCapturedImage(
                            id = "img-${System.currentTimeMillis()}",
                            url = dataUrl,
                            caption = "Warehouse Stock Photo",
                            capturedAt = currentTimePkt()
                        )
                    } else {
                        onToast("Could not read that photo. Try again.")
                    }
                }
            }
        }
    }

    val selectedDistributor = scopedDistributors.find { it.id == selectedDistributorId }
    val distributorProducts = remember(selectedDistributor, products) {
        if (selectedDistributor == null) emptyList()
        else products.filter { p ->
            p.status == "Active" &&
                selectedDistributor.groupIds.contains(p.groupId) &&
                selectedDistributor.productStatuses[p.id] != "Inactive"
        }
    }
    val matchingSearchProducts = distributorProducts.filter { p ->
        p.name.contains(productSearchText, ignoreCase = true) ||
            p.sku.contains(productSearchText, ignoreCase = true)
    }
    val selectedProduct = distributorProducts.find { it.id == selectedProductId }

    val parsedCtn = max(0, qtyInCtn.toIntOrNull() ?: 0)
    val parsedBox = max(0, qtyInBox.toIntOrNull() ?: 0)
    val liveTotalCtns = if (selectedProduct != null && selectedProduct.boxesPerCtn > 0) {
        parsedCtn.toDouble() + parsedBox.toDouble() / selectedProduct.boxesPerCtn.toDouble()
    } else 0.0
    val liveTotalBoxes = if (selectedProduct != null) {
        parsedCtn * selectedProduct.boxesPerCtn + parsedBox
    } else 0
    val liveStockValueRs = if (selectedProduct != null) {
        liveTotalBoxes * selectedProduct.pricePerBoxRs
    } else 0.0

    val grandTotalCtns = savedItems.sumOf { it.totalCtns }
    val grandTotalValueRs = savedItems.sumOf { it.stockValueRs }

    val activeStep = when {
        selectedDistributor == null -> 1
        savedItems.isEmpty() -> 2
        else -> 3
    }

    Surface(
        color = AgFormBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Royal Blue App Header ("New Audit")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = ::closeForm, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("New Audit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = ::closeForm, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live GPS Status Bar
                val coords = silentCoords
                val (gpsBg, gpsBorder, gpsFg) = when {
                    gpsError != null -> Triple(AgDestructiveBg, AgDestructive, AgDestructiveDark)
                    coords != null && coords.accuracy <= fh.settings.maxAccuracyMeters ->
                        Triple(AgAccentGreenBg, AgAccentGreen.copy(alpha = 0.4f), AgAccentGreenDark)
                    coords != null -> Triple(AgWarningBg, AgWarning.copy(alpha = 0.4f), AgWarningDark)
                    else -> Triple(AgCardWhite, AgBorder, AgMutedText)
                }
                Surface(
                    color = gpsBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, gpsBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val statusText = when {
                            gpsBusy -> "Getting your location…"
                            gpsError != null -> gpsError!!
                            coords != null -> {
                                val acc = coords.accuracy.roundToInt()
                                val warn = if (acc > fh.settings.maxAccuracyMeters) " — low accuracy, audit will be flagged for review" else ""
                                "Location captured (±$acc m)$warn"
                            }
                            else -> "Location not captured yet"
                        }
                        Text(
                            text = statusText,
                            color = gpsFg,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { scope.launch { captureRealLocation() } },
                            enabled = !gpsBusy,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, gpsFg),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Text(
                                text = if (gpsError != null) "Try again" else "Refresh",
                                color = gpsFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3-Step Progress Indicator Bar
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StepPill(1, "Audit Details", activeStep >= 1)
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), color = AgBorder)
                        StepPill(2, "Products", activeStep >= 2)
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), color = AgBorder)
                        StepPill(3, "Photos & Submit", activeStep >= 3)
                    }
                }

                // Main Card: Step 1 Select Town -> Step 2 Select Distributor -> Step 3 Start Audit of Product
                val initialPresetDist = remember(preset?.distributorId, scopedDistributors) {
                    scopedDistributors.find { it.id == preset?.distributorId }
                }
                val linkedTowns = remember(scopedCities, scopedDistributors, cities) {
                    val distTownIds = scopedDistributors.map { it.cityId }.toSet()
                    val combined = (scopedCities.map { it.id } + distTownIds).toSet()
                    val matched = cities.filter { it.status == "Active" && combined.contains(it.id) }
                    matched.ifEmpty { cities.filter { it.status == "Active" } }
                }
                var selectedDistCityId by remember(linkedTowns, initialPresetDist) {
                    mutableStateOf(
                        initialPresetDist?.cityId
                            ?: if (linkedTowns.size == 1) linkedTowns.first().id else ""
                    )
                }
                LaunchedEffect(selectedDistributor) {
                    if (selectedDistributor != null && selectedDistributor.cityId.isNotBlank()) {
                        selectedDistCityId = selectedDistributor.cityId
                    }
                }
                val filteredDistributorsForSelection = remember(
                    scopedDistributors,
                    selectedDistCityId
                ) {
                    if (selectedDistCityId.isBlank()) emptyList()
                    else scopedDistributors.filter { d -> d.cityId == selectedDistCityId }
                }

                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Select Town (linked to auditor)
                        var cityExpanded by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Select Town *", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            ExposedDropdownMenuBox(
                                expanded = cityExpanded,
                                onExpandedChange = { cityExpanded = !cityExpanded }
                            ) {
                                val selectedCityObj = linkedTowns.find { it.id == selectedDistCityId }
                                val cityLabel = selectedCityObj?.let { town ->
                                    val cnt = scopedDistributors.count { it.cityId == town.id }
                                    "${town.name} ($cnt Distributor${if (cnt == 1) "" else "s"})"
                                } ?: "Select Town..."
                                OutlinedTextField(
                                    value = cityLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = cityExpanded,
                                    onDismissRequest = { cityExpanded = false }
                                ) {
                                    linkedTowns.forEach { c ->
                                        val cnt = scopedDistributors.count { it.cityId == c.id }
                                        DropdownMenuItem(
                                            text = { Text("${c.name} ($cnt Distributor${if (cnt == 1) "" else "s"})", fontSize = 13.sp) },
                                            onClick = {
                                                selectedDistCityId = c.id
                                                if (selectedDistributor != null && selectedDistributor.cityId != c.id) {
                                                    selectedDistributorId = ""
                                                    selectedProductId = ""
                                                    productSearchText = ""
                                                }
                                                cityExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Select Distributor of Selected Town (linked to auditor)
                        var distExpanded by remember { mutableStateOf(false) }
                        val distDropdownEnabled = selectedDistCityId.isNotBlank()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Select Distributor *",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ExposedDropdownMenuBox(
                                expanded = distExpanded && distDropdownEnabled,
                                onExpandedChange = { if (distDropdownEnabled) distExpanded = !distExpanded }
                            ) {
                                val distLabel = when {
                                    !distDropdownEnabled -> "Select Town First..."
                                    selectedDistributor != null -> {
                                        val c = cities.find { it.id == selectedDistributor.cityId }
                                        "${selectedDistributor.setupName} (${selectedDistributor.code}) – ${c?.name ?: ""}"
                                    }
                                    else -> "Select Distributor (${filteredDistributorsForSelection.size} Available)..."
                                }
                                OutlinedTextField(
                                    value = distLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = distDropdownEnabled,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = distExpanded && distDropdownEnabled) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("select_distributor_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = distExpanded && distDropdownEnabled,
                                    onDismissRequest = { distExpanded = false }
                                ) {
                                    filteredDistributorsForSelection.forEach { dist ->
                                        val c = cities.find { it.id == dist.cityId }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${dist.setupName} (${dist.code}) – ${c?.name ?: ""}",
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedDistributorId = dist.id
                                                selectedDistCityId = dist.cityId
                                                selectedProductId = ""
                                                productSearchText = ""
                                                productDropdownOpen = false
                                                distExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Start Audit of Product (shown once Distributor is selected)
                        if (selectedDistributor != null) {
                            HorizontalDivider(color = AgLightBorder)
                            Surface(
                                color = AgSoftBlueBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgSoftBlueBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = AgActionBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Start Product Audit — ${selectedDistributor.setupName}",
                                            color = AgRoyalBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    CodeBadge("${distributorProducts.size} Products", bg = AgCardWhite, fg = AgActionBlue)
                                }
                            }

                            // Select Product Search & Dropdown
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Select Product *", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = productSearchText,
                                    onValueChange = {
                                        productSearchText = it
                                        productDropdownOpen = true
                                        if (selectedProductId.isNotEmpty()) selectedProductId = ""
                                    },
                                    placeholder = {
                                        Text(
                                            "Search Product by Name or SKU...",
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                    },
                                trailingIcon = {
                                    if (productSearchText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            productSearchText = ""
                                            selectedProductId = ""
                                            productDropdownOpen = true
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    } else if (selectedDistributor != null) {
                                        IconButton(onClick = { productDropdownOpen = !productDropdownOpen }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Toggle products")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_search_input")
                            )

                            if (selectedDistributor != null && productDropdownOpen) {
                                var showAllDropdownProducts by remember(productSearchText, selectedDistributorId) {
                                    mutableStateOf(false)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AgBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.animateContentSize()) {
                                        if (matchingSearchProducts.isEmpty()) {
                                            Text(
                                                text = "No matching products found",
                                                color = AgMutedText,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp)
                                            )
                                        } else {
                                            val visibleProducts = if (showAllDropdownProducts) {
                                                matchingSearchProducts
                                            } else {
                                                matchingSearchProducts.take(5)
                                            }
                                            visibleProducts.forEachIndexed { idx, prd ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (idx == 0) AgSoftBlueBg else AgCardWhite)
                                                        .clickable {
                                                            selectedProductId = prd.id
                                                            productSearchText = prd.name
                                                            productDropdownOpen = false
                                                            qtyInCtn = ""
                                                            qtyInBox = ""
                                                            dispatchDate = ""
                                                            stacking = ""
                                                            remarks = ""
                                                        }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(AgActionBlue, RoundedCornerShape(8.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(prd.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            text = "Box/CTN: ${prd.boxesPerCtn} · Unit/Box: ${prd.unitsCountPerBox.takeIf { it > 0 } ?: prd.unitPerBox} · Rs. ${prd.pricePerBoxRs.roundToInt()}",
                                                            color = AgMutedText,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                                if (idx < visibleProducts.lastIndex) {
                                                    HorizontalDivider(color = AgLightBorder)
                                                }
                                            }
                                            if (matchingSearchProducts.size > 5) {
                                                HorizontalDivider(color = AgLightBorder)
                                                Box(modifier = Modifier.padding(8.dp)) {
                                                    ShowMoreListToggle(
                                                        totalCount = matchingSearchProducts.size,
                                                        collapsedLimit = 5,
                                                        expanded = showAllDropdownProducts,
                                                        itemLabel = "Products",
                                                        onToggle = { showAllDropdownProducts = !showAllDropdownProducts }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Locked Product Spec Banner + Inputs
                        if (selectedProduct != null) {
                            Surface(
                                color = AgSoftBlueBg,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFC9DCFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .background(AgCardWhite, RoundedCornerShape(10.dp))
                                                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(19.dp))
                                            }
                                            Column {
                                                Text(
                                                    selectedProduct.name,
                                                    color = AgForeground,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                if (selectedProduct.sku.isNotBlank()) {
                                                    Text(
                                                        "SKU: ${selectedProduct.sku}",
                                                        color = AgRoyalBlue,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Icon(Icons.Default.Lock, contentDescription = "Locked Master Specs", tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StructuredMetaGridItem(
                                            label = "Box / CTN",
                                            value = "${selectedProduct.boxesPerCtn}",
                                            modifier = Modifier.weight(1f)
                                        )
                                        StructuredMetaGridItem(
                                            label = "Unit / Box",
                                            value = "${selectedProduct.unitsCountPerBox.takeIf { it > 0 } ?: selectedProduct.unitPerBox}",
                                            modifier = Modifier.weight(1f)
                                        )
                                        StructuredMetaGridItem(
                                            label = "Price / Box",
                                            value = "Rs. ${selectedProduct.pricePerBoxRs.roundToInt()}",
                                            valueColor = AgRoyalBlue,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = qtyInCtn,
                                    onValueChange = { qtyInCtn = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("${fh.label("qtyInCtn", "Qty in CTN")} *", fontSize = 11.sp) },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = qtyInBox,
                                    onValueChange = { qtyInBox = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("${fh.label("qtyInBox", "Qty in Box")} *", fontSize = 11.sp) },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (fh.visible("dispatchDate")) {
                                OutlinedTextField(
                                    value = dispatchDate,
                                    onValueChange = { dispatchDate = it },
                                    label = {
                                        Text(
                                            fh.label("dispatchDate", "Dispatch Date") +
                                                if (fh.required("dispatchDate", true)) " *" else "",
                                            fontSize = 11.sp
                                        )
                                    },
                                    placeholder = { Text(fh.placeholder("dispatchDate", "e.g. 30/10/2026 or JUL-26"), fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (fh.visible("stacking")) {
                                    var stExpanded by remember { mutableStateOf(false) }
                                    val stOptions = fh.options("stacking", listOf("Yes", "No"))
                                    ExposedDropdownMenuBox(
                                        expanded = stExpanded,
                                        onExpandedChange = { stExpanded = !stExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = stacking.ifEmpty { "Select…" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = {
                                                Text(
                                                    fh.label("stacking", "Stacking") +
                                                        if (fh.required("stacking", true)) " *" else "",
                                                    fontSize = 11.sp
                                                )
                                            },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stExpanded) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = stExpanded,
                                            onDismissRequest = { stExpanded = false }
                                        ) {
                                            stOptions.forEach { o ->
                                                DropdownMenuItem(
                                                    text = { Text(o) },
                                                    onClick = {
                                                        stacking = o
                                                        stExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (fh.visible("remarks")) {
                                    OutlinedTextField(
                                        value = remarks,
                                        onValueChange = { remarks = it },
                                        label = {
                                            Text(
                                                fh.label("remarks", "Remarks") +
                                                    if (fh.required("remarks")) " *" else "",
                                                fontSize = 11.sp
                                            )
                                        },
                                        placeholder = { Text(fh.placeholder("remarks", "Optional notes"), fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "Calc: %.2f CTN · %s", liveTotalCtns, formatPKR(liveStockValueRs)),
                                    color = AgMutedText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Button(
                                    onClick = {
                                        if (qtyInCtn.isBlank() && qtyInBox.isBlank()) {
                                            onToast("Please enter Qty in CTN or Qty in Box before adding (enter 0 if none).")
                                            return@Button
                                        }
                                        if (!fh.settings.allowMultipleEntriesPerProduct && savedItems.any { it.productId == selectedProduct.id }) {
                                            onToast("This product is already in the list. Remove it first to enter it again.")
                                            return@Button
                                        }
                                        if (fh.visible("dispatchDate") && fh.required("dispatchDate", true) && dispatchDate.isBlank()) {
                                            onToast("${fh.label("dispatchDate", "Dispatch date")} is required.")
                                            return@Button
                                        }
                                        if (fh.visible("stacking") && fh.required("stacking", true) && stacking.isBlank()) {
                                            onToast("Please choose ${fh.label("stacking", "stacking").lowercase()}.")
                                            return@Button
                                        }
                                        if (fh.visible("remarks") && fh.required("remarks") && remarks.isBlank()) {
                                            onToast("${fh.label("remarks", "Remarks")} is required.")
                                            return@Button
                                        }

                                        val newItem = DistributorAuditLineItem(
                                            id = "line-${System.currentTimeMillis()}",
                                            productId = selectedProduct.id,
                                            sku = selectedProduct.sku,
                                            productName = selectedProduct.name,
                                            boxesPerCtn = selectedProduct.boxesPerCtn,
                                            unitPerBox = (selectedProduct.unitsCountPerBox.takeIf { it > 0 } ?: selectedProduct.unitPerBox).toString(),
                                            unitsCountPerBox = selectedProduct.unitsCountPerBox,
                                            pricePerBoxRs = selectedProduct.pricePerBoxRs,
                                            qtyInCtn = parsedCtn,
                                            qtyInBox = parsedBox,
                                            totalCtns = liveTotalCtns,
                                            dispatchDate = if (fh.visible("dispatchDate")) dispatchDate.trim() else "",
                                            stacking = if (fh.visible("stacking")) stacking.trim() else "",
                                            remarks = if (fh.visible("remarks")) remarks.trim() else "",
                                            stockValueRs = liveStockValueRs
                                        )
                                        savedItems = savedItems + newItem
                                        selectedProductId = ""
                                        productSearchText = ""
                                        qtyInCtn = ""
                                        qtyInBox = ""
                                        dispatchDate = ""
                                        stacking = ""
                                        remarks = ""
                                        onToast(
                                            String.format(
                                                Locale.US,
                                                "Added %s (%.2f CTN · %s)",
                                                selectedProduct.name,
                                                liveTotalCtns,
                                                formatPKR(liveStockValueRs)
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                    modifier = Modifier.testTag("add_product_line_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Product", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        } else {
                            Surface(
                                color = AgBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (selectedDistCityId.isBlank())
                                        "1. Select a Town above to view its linked Distributors."
                                    else
                                        "2. Select a Distributor above to start auditing its products.",
                                    color = AgMutedText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Added Products Card with Progressive Disclosure
                var showAllAddedProducts by remember { mutableStateOf(false) }
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AgLightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(18.dp))
                                Text("Added Products", color = AgPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            CodeBadge("${savedItems.size} SKUs", bg = AgSoftBlueBg, fg = AgRoyalBlue)
                        }
                        HorizontalDivider(color = AgLightBorder)

                        if (savedItems.isEmpty()) {
                            Text(
                                text = "No products added yet. Select a distributor and product above, then tap + Add Product.",
                                color = AgMutedText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            )
                        } else {
                            val visibleAddedItems = if (showAllAddedProducts) savedItems else savedItems.take(3)
                            visibleAddedItems.forEachIndexed { idx, item ->
                                var itemExpanded by remember(item.id) { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CodeBadge("#${idx + 1}", bg = AgMutedBg, fg = AgPrimary)
                                                if (item.sku.isNotBlank()) {
                                                    CodeBadge(item.sku, bg = AgSoftBlueBg, fg = AgRoyalBlue)
                                                }
                                                Surface(
                                                    color = AgAccentGreenBg,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = formatPKR(item.stockValueRs),
                                                        color = AgAccentGreenDark,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                item.productName,
                                                color = AgForeground,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        IconButton(
                                            onClick = { savedItems = savedItems.filterNot { it.id == item.id } },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.productName}", tint = AgDestructive, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StructuredMetaGridItem("Physical CTN", item.qtyInCtn.toString(), Modifier.weight(1f))
                                        StructuredMetaGridItem("Loose Box", item.qtyInBox.toString(), Modifier.weight(1f))
                                        StructuredMetaGridItem(
                                            "Total CTNs",
                                            String.format(Locale.US, "%.2f", item.totalCtns),
                                            Modifier.weight(1f),
                                            valueColor = AgRoyalBlue
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        CardMoreDetailsToggle(
                                            expanded = itemExpanded,
                                            labelCollapsed = "More Details",
                                            labelExpanded = "Hide Details",
                                            onToggle = { itemExpanded = !itemExpanded }
                                        )
                                    }

                                    if (itemExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StructuredMetaGridItem("Dispatch Date", item.dispatchDate.ifEmpty { "—" }, Modifier.weight(1f))
                                                StructuredMetaGridItem("Stacking", item.stacking.ifEmpty { "—" }, Modifier.weight(1f))
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StructuredMetaGridItem("Box / CTN", "${item.boxesPerCtn}", Modifier.weight(1f))
                                                StructuredMetaGridItem("Price / Box", formatPKR(item.pricePerBoxRs), Modifier.weight(1f))
                                            }
                                            if (item.remarks.isNotEmpty()) {
                                                Surface(
                                                    color = AgFormBg,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, AgBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text("REMARKS", color = AgMutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(item.remarks, color = AgForeground, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (idx < visibleAddedItems.lastIndex) {
                                    HorizontalDivider(color = AgLightBorder)
                                }
                            }
                            if (savedItems.size > 3) {
                                HorizontalDivider(color = AgLightBorder)
                                Box(modifier = Modifier.padding(12.dp)) {
                                    ShowMoreListToggle(
                                        totalCount = savedItems.size,
                                        collapsedLimit = 3,
                                        expanded = showAllAddedProducts,
                                        itemLabel = "Added Products",
                                        onToggle = { showAllAddedProducts = !showAllAddedProducts }
                                    )
                                }
                            }
                        }

                        // Totals Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgSoftBlueBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "Total CTN: %.2f", grandTotalCtns),
                                color = AgPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Total Value: ${formatPKR(grandTotalValueRs)}",
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Capture Images Card
                if (fh.settings.allowPhotoCapture && fh.visible("photos")) {
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${fh.label("photos", "Capture Images")} ${if (fh.settings.requirePhoto) "(Required)" else "(Optional)"} ${capturedImages.size}/${fh.settings.maxPhotos}",
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (capturedImages.size < fh.settings.maxPhotos) {
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
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tap to Capture", color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            galleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Upload Gallery", color = AgActionBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (capturedImages.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    capturedImages.forEach { img ->
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, AgBorder, RoundedCornerShape(12.dp))
                                        ) {
                                            DataUrlImage(img.url, img.caption, Modifier.fillMaxSize())
                                            IconButton(
                                                onClick = { capturedImages = capturedImages.filterNot { it.id == img.id } },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(AgForeground.copy(alpha = 0.8f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar: Save Draft / Submit Audit
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%d product%s · %.2f CTN",
                                    savedItems.size,
                                    if (savedItems.size == 1) "" else "s",
                                    grandTotalCtns
                                ),
                                color = AgMutedText,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatPKR(grandTotalValueRs),
                                color = AgForeground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val itemsArr = JSONArray()
                                        savedItems.forEach { item ->
                                            itemsArr.put(JSONObject().apply {
                                                put("id", item.id)
                                                put("productId", item.productId)
                                                put("sku", item.sku)
                                                put("productName", item.productName)
                                                put("boxesPerCtn", item.boxesPerCtn)
                                                put("unitPerBox", item.unitPerBox)
                                                put("unitsCountPerBox", item.unitsCountPerBox)
                                                put("pricePerBoxRs", item.pricePerBoxRs)
                                                put("qtyInCtn", item.qtyInCtn)
                                                put("qtyInBox", item.qtyInBox)
                                                put("totalCtns", item.totalCtns)
                                                put("dispatchDate", item.dispatchDate)
                                                put("stacking", item.stacking)
                                                put("remarks", item.remarks)
                                                put("stockValueRs", item.stockValueRs)
                                            })
                                        }
                                        val imgsArr = JSONArray()
                                        capturedImages.forEach { img ->
                                            imgsArr.put(JSONObject().apply {
                                                put("id", img.id)
                                                put("url", img.url)
                                                put("caption", img.caption)
                                                put("capturedAt", img.capturedAt)
                                            })
                                        }
                                        val draft = JSONObject().apply {
                                            put("selectedDistributorId", selectedDistributorId)
                                            put("savedItems", itemsArr)
                                            put("capturedImages", imgsArr)
                                            put("updatedAt", currentIsoTimestamp())
                                        }
                                        apiClient.saveDraftString(distDraftKey, draft.toString())
                                        onToast("Audit draft saved to mobile device.")
                                    } catch (_: Exception) {
                                        onToast("Could not save draft.")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_distributor_draft_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Draft", color = AgActionBlue, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (selectedDistributor == null) {
                                        onToast("Please select a distributor before submitting.")
                                        return@Button
                                    }
                                    if (submitting) return@Button
                                    if (fh.settings.allowPhotoCapture && fh.settings.requirePhoto && capturedImages.isEmpty()) {
                                        onToast("At least one photo is required before submitting.")
                                        return@Button
                                    }
                                    if (savedItems.isEmpty()) {
                                        onToast("Please add at least one product before submitting.")
                                        return@Button
                                    }

                                    submitting = true
                                    scope.launch {
                                        val fix = captureRealLocation()
                                        if (fix == null) {
                                            onToast("Location is required to submit this audit. Allow location and tap \"Try again\".")
                                            submitting = false
                                            return@launch
                                        }
                                        val mLat = (fix.lat * 1_000_000).roundToInt() / 1_000_000.0
                                        val mLng = (fix.lng * 1_000_000).roundToInt() / 1_000_000.0
                                        val mAcc = fix.accuracy.roundToInt()
                                        val distMeters = calculateDistanceMeters(
                                            mLat,
                                            mLng,
                                            selectedDistributor.lat,
                                            selectedDistributor.lng
                                        )
                                        val allowedRadius = fh.settings.allowedRadiusMeters
                                        val matched = distMeters <= allowedRadius && mAcc <= fh.settings.maxAccuracyMeters

                                        val city = cities.find { it.id == selectedDistributor.cityId }
                                        val territory = territories.find { it.id == selectedDistributor.territoryId }
                                        val primaryGroupCode = if (selectedDistributor.groupIds.contains("grp-b")) "B" else "A"
                                        val primaryGroupName = if (selectedDistributor.groupIds.contains("grp-b")) "Group B" else "Group A"

                                        val newReport = DistributorProductAuditReport(
                                            id = submitKey,
                                            reportCode = makeReportCode("AUD-DST"),
                                            formId = DISTRIBUTOR_FORM_ID,
                                            distributorId = selectedDistributor.id,
                                            distributorCode = selectedDistributor.code,
                                            distributorSetupName = selectedDistributor.setupName.uppercase(Locale.US),
                                            distributorOwnerName = selectedDistributor.distributorName,
                                            groupCode = primaryGroupCode,
                                            groupName = primaryGroupName,
                                            cityId = selectedDistributor.cityId,
                                            cityName = (city?.name ?: "PUNJAB").uppercase(Locale.US),
                                            territoryId = selectedDistributor.territoryId,
                                            territoryCode = territory?.code ?: "",
                                            territoryName = territory?.name ?: "",
                                            auditorId = activeAuditor.id,
                                            auditorCode = activeAuditor.code,
                                            auditorName = activeAuditor.fullName,
                                            auditorUsername = activeAuditor.username,
                                            reportDateDisplay = currentReportDateDisplay(),
                                            submittedDate = currentIsoDate(),
                                            submittedTime = currentTimePkt(),
                                            submittedAtISO = currentIsoTimestamp(),
                                            mobileLat = mLat,
                                            mobileLng = mLng,
                                            mobileAccuracyMeters = mAcc,
                                            distributorLat = selectedDistributor.lat,
                                            distributorLng = selectedDistributor.lng,
                                            distanceMeters = distMeters,
                                            allowedRadiusMeters = allowedRadius,
                                            locationMatched = matched,
                                            items = savedItems,
                                            totalCtns = grandTotalCtns,
                                            totalStockValueRs = grandTotalValueRs,
                                            capturedImages = capturedImages,
                                            overallComments = "Submitted from Mobile App by ${activeAuditor.fullName} (@${activeAuditor.username}).",
                                            planId = planLink?.planId,
                                            planItemId = planLink?.itemId
                                        )

                                        try {
                                            onSubmitReport(newReport)
                                            submitKey = newSubmitKey()
                                            apiClient.removeDraft(distDraftKey)
                                            savedItems = emptyList()
                                            capturedImages = emptyList()
                                            selectedProductId = ""
                                            productSearchText = ""
                                            closeForm()
                                        } catch (e: Exception) {
                                            onToast(e.message ?: "Could not submit. Your entries are kept on this phone; try again.")
                                        } finally {
                                            submitting = false
                                        }
                                    }
                                },
                                enabled = savedItems.isNotEmpty() && !submitting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("submit_distributor_audit_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (submitting) "Submitting…" else "Submit Audit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPill(number: Int, label: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(if (active) AgActionBlue else AgBackground, CircleShape)
                .border(1.dp, if (active) AgActionBlue else AgBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = if (active) Color.White else AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = if (active) AgActionBlue else AgMutedText,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun SmallKv(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = AgMutedText, fontSize = 10.sp)
        Text(value, color = AgForeground, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

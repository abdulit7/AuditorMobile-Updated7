package pk.auditgrid.audittest

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pk.auditgrid.audittest.model.AuditPreset
import pk.auditgrid.audittest.model.PlanLink
import pk.auditgrid.audittest.model.isLinkedToScope
import pk.auditgrid.audittest.model.linkedCityIds
import pk.auditgrid.audittest.model.linkedTerritoryIds
import pk.auditgrid.audittest.ui.AppUiState
import pk.auditgrid.audittest.ui.AuditGridViewModel
import pk.auditgrid.audittest.ui.MobileSection
import pk.auditgrid.audittest.ui.screens.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.hasLocationPermission

class MainActivity : ComponentActivity() {

    private val viewModel: AuditGridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)

        setContent {
            AuditGridTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.liveSharing.sharing) {
                    if (uiState.liveSharing.sharing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                AuditGridRoot(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra("open_section") == "DUTIES") {
            viewModel.setSection(MobileSection.DUTIES)
            viewModel.syncAllNow(manual = false)
        }
    }
}

@Composable
fun AuditGridRoot(
    uiState: AppUiState,
    viewModel: AuditGridViewModel
) {
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && uiState.liveSharing.sharing) {
            viewModel.startLiveTracking()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onNotificationPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.session?.user?.id) {
        if (uiState.session != null) {
            if (!hasLocationPermission(context)) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgBackground)
    ) {
        when {
            uiState.booting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AgRoyalBlue,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading…",
                            color = AgMutedText,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            uiState.session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    NativeLoginScreen(
                        apiClient = viewModel.apiClient,
                        onSignedIn = viewModel::onSignedIn
                    )
                }
            }

            uiState.loadError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.loadError,
                        color = AgForeground,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = viewModel::retryLoadAll,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgRoyalBlue),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("retry_load_button")
                        ) {
                            Text(
                                text = "Try again",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::handleLogout,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("error_sign_out_button")
                        ) {
                            Text(
                                text = "Sign out",
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            else -> {
                AuditorMobileShell(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        // Toast overlay
        uiState.toast?.let { toastText ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 76.dp)
                    .widthIn(max = 460.dp)
            ) {
                Surface(
                    color = AgNavyHeader,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = toastText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = viewModel::dismissToast,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditorMobileShell(
    uiState: AppUiState,
    viewModel: AuditGridViewModel
) {
    val session = uiState.session ?: return
    val activeAuditor = uiState.users.find {
        it.id == session.user.id ||
            (it.username.isNotBlank() && it.username.equals(session.user.username, ignoreCase = true))
    } ?: session.user
    val activeRole = uiState.roles.find { it.id == activeAuditor.roleId } ?: session.role

    val assignedTerritoryIdsSet = remember(activeAuditor, uiState.territories, session.isGlobal) {
        activeAuditor.linkedTerritoryIds(uiState.territories, session.isGlobal)
    }
    val assignedTerritories = remember(uiState.territories, assignedTerritoryIdsSet) {
        uiState.territories.filter {
            assignedTerritoryIdsSet.contains(it.id) && it.status == "Active"
        }
    }
    val assignedCityIdsSet = remember(activeAuditor, uiState.cities, uiState.territories, session.isGlobal) {
        activeAuditor.linkedCityIds(uiState.cities, uiState.territories, session.isGlobal)
    }
    val assignedCities = remember(uiState.cities, assignedCityIdsSet) {
        uiState.cities.filter {
            assignedCityIdsSet.contains(it.id) && it.status == "Active"
        }
    }
    val assignedLocations = remember(uiState.locations, assignedTerritoryIdsSet) {
        uiState.locations.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }
    val assignedSubLocations = remember(uiState.subLocations, assignedTerritoryIdsSet) {
        uiState.subLocations.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status != "Inactive"
        }
    }
    val assignedShops = remember(uiState.shops, assignedTerritoryIdsSet) {
        uiState.shops.filter {
            assignedTerritoryIdsSet.contains(it.territoryId) && it.status == "Active"
        }
    }
    val assignedDistributors = remember(uiState.distributors, assignedTerritoryIdsSet, assignedTerritories) {
        uiState.distributors.filter {
            it.isLinkedToScope(assignedTerritoryIdsSet, assignedTerritories)
        }
    }

    val visibleTerritories = remember(assignedTerritories, uiState.filterCityId, uiState.searchQuery) {
        assignedTerritories.filter { t ->
            (uiState.filterCityId.isBlank() || t.cityId == uiState.filterCityId) &&
                (uiState.searchQuery.isBlank() ||
                    t.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    t.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleLocations = remember(assignedLocations, uiState.filterTerritoryId, uiState.searchQuery) {
        assignedLocations.filter { l ->
            (uiState.filterTerritoryId.isBlank() || l.territoryId == uiState.filterTerritoryId) &&
                (uiState.searchQuery.isBlank() ||
                    l.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    l.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleSubLocations = remember(
        assignedSubLocations,
        uiState.filterTerritoryId,
        uiState.filterLocationId,
        uiState.searchQuery
    ) {
        assignedSubLocations.filter { s ->
            (uiState.filterTerritoryId.isBlank() || s.territoryId == uiState.filterTerritoryId) &&
                (uiState.filterLocationId.isBlank() || s.locationId == uiState.filterLocationId) &&
                (uiState.searchQuery.isBlank() ||
                    s.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    s.code.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleShops = remember(
        assignedShops,
        uiState.filterTerritoryId,
        uiState.filterSubLocationId,
        uiState.searchQuery
    ) {
        assignedShops.filter { sh ->
            (uiState.filterTerritoryId.isBlank() || sh.territoryId == uiState.filterTerritoryId) &&
                (uiState.filterSubLocationId.isBlank() || sh.subLocationId == uiState.filterSubLocationId) &&
                (uiState.searchQuery.isBlank() ||
                    sh.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    sh.code.contains(uiState.searchQuery, ignoreCase = true) ||
                    sh.ownerName.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val visibleDistributors = remember(
        assignedDistributors,
        assignedTerritories,
        uiState.filterCityId,
        uiState.filterTerritoryId,
        uiState.searchQuery
    ) {
        assignedDistributors.filter { d ->
            val matchesTerritory = uiState.filterTerritoryId.isBlank() ||
                d.territoryId == uiState.filterTerritoryId ||
                assignedTerritories.any { t ->
                    t.id == uiState.filterTerritoryId &&
                        ((t.distributorId.isNotBlank() && t.distributorId == d.id) ||
                            (t.distributorCode.isNotBlank() && t.distributorCode.equals(d.code, ignoreCase = true)) ||
                            (t.distributorName.isNotBlank() && t.distributorName.equals(d.setupName, ignoreCase = true)))
                }
            (uiState.filterCityId.isBlank() || d.cityId == uiState.filterCityId) &&
                matchesTerritory &&
                (uiState.searchQuery.isBlank() ||
                    d.setupName.contains(uiState.searchQuery, ignoreCase = true) ||
                    d.code.contains(uiState.searchQuery, ignoreCase = true) ||
                    d.distributorName.contains(uiState.searchQuery, ignoreCase = true))
        }
    }

    val isGeoSection = uiState.activeSection in listOf(
        MobileSection.CITIES,
        MobileSection.TERRITORIES,
        MobileSection.LOCATIONS,
        MobileSection.SUBLOCATIONS,
        MobileSection.SHOPS
    )

    val isSecondarySection = isGeoSection ||
        uiState.activeSection == MobileSection.DISTRIBUTORS ||
        uiState.activeSection == MobileSection.COMPLETED

    // Back handler on non-Audit top-level sections when no detail item is open
    if (uiState.activeSection != MobileSection.AUDIT &&
        uiState.selectedShop == null &&
        uiState.selectedDistributor == null
    ) {
        BackHandler {
            viewModel.setSection(MobileSection.AUDIT)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgBackground)
    ) {
        // Top App Bar matching the AuditGrid Mobile Dashboard Mockup (#0A2246)
        Surface(
            color = AgNavyHeader,
            contentColor = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.selectedShop != null || uiState.selectedDistributor != null || isSecondarySection) {
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    if (uiState.selectedShop != null || uiState.selectedDistributor != null) {
                                        viewModel.selectShop(null)
                                        viewModel.selectDistributor(null)
                                    } else {
                                        viewModel.setSection(MobileSection.AUDIT)
                                    }
                                }
                                .testTag("top_bar_back_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(modifier = Modifier.padding(4.dp)) {
                            AuditGridShieldLogo(size = 26.dp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "AuditGrid",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sectionSubtitle = when {
                            uiState.selectedShop != null -> "Shop Profile"
                            uiState.selectedDistributor != null -> "Distributor Profile"
                            uiState.activeSection == MobileSection.AUDIT -> "Field Audit Workspace"
                            uiState.activeSection == MobileSection.DUTIES -> "Assigned Duties"
                            uiState.activeSection == MobileSection.PLANS -> "Daily Audit Plans"
                            uiState.activeSection == MobileSection.COMPLETED -> "Completed Audits"
                            uiState.activeSection == MobileSection.DISTRIBUTORS -> "Assigned Distributors"
                            uiState.activeSection == MobileSection.ACCOUNT -> "Auditor Account"
                            else -> "Assigned Geography"
                        }
                        Text(
                            text = sectionSubtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Duty Notifications Bell Button with Red Badge
                    val badgeNumber = when {
                        uiState.unreadNotificationsCount > 0 -> uiState.unreadNotificationsCount
                        uiState.notifications.isNotEmpty() -> uiState.notifications.size
                        else -> uiState.duties.count {
                            it.status.equals("Assigned", ignoreCase = true) ||
                                it.status.equals("In Progress", ignoreCase = true)
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.toggleNotificationsDialog(true) }
                            .testTag("top_bar_notifications_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Duty Notifications",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(21.dp)
                            )
                            if (badgeNumber > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-3).dp, y = 3.dp)
                                        .size(17.dp)
                                        .background(Color(0xFFEF4444), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (badgeNumber > 9) "9+" else badgeNumber.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Sync Icon Button in Top App Bar
                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !uiState.syncing) {
                                viewModel.syncAllNow(manual = true)
                            }
                            .testTag("top_bar_sync_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync with main server",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Offline Banner
        if (!uiState.online) {
            Surface(
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = AgWarningDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Offline Mode — Data is saved locally and will auto-sync when online.",
                        color = AgWarningDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Pending Outbox & Offline Actions Banner when items are waiting to upload
        if (uiState.pendingOutbox.isNotEmpty() || uiState.pendingSyncActionsCount > 0) {
            Surface(
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = AgWarningDark,
                            modifier = Modifier.size(15.dp)
                        )
                        val queueSummary = buildList {
                            if (uiState.pendingOutbox.isNotEmpty()) {
                                add("${uiState.pendingOutbox.size} audit(s)")
                            }
                            if (uiState.pendingSyncActionsCount > 0) {
                                add("${uiState.pendingSyncActionsCount} update(s)")
                            }
                        }.joinToString(" • ")
                        Text(
                            text = "$queueSummary queued for sync",
                            color = AgWarningDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!uiState.syncing) {
                            Surface(
                                color = AgActionBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.syncAllNow(manual = true) }
                            ) {
                                Text(
                                    text = "Sync",
                                    color = AgActionBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (uiState.pendingOutbox.isNotEmpty()) {
                            Surface(
                                color = AgPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setSection(MobileSection.COMPLETED) }
                            ) {
                                Text(
                                    text = "View",
                                    color = AgPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Geography Drill-Down Sub-Tabs
        if (isGeoSection) {
            Surface(
                color = AgCardWhite,
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val geoTabs = listOf(
                        Triple(MobileSection.CITIES, "Towns", assignedCities.size),
                        Triple(MobileSection.TERRITORIES, "Localities", assignedTerritories.size),
                        Triple(MobileSection.SUBLOCATIONS, "Sub-Localities", assignedSubLocations.size),
                        Triple(MobileSection.SHOPS, "Shops", assignedShops.size)
                    )
                    geoTabs.forEach { (sec, label, count) ->
                        val active = uiState.activeSection == sec
                        Surface(
                            color = if (active) AgRoyalBlue else AgFormBg,
                            contentColor = if (active) Color.White else AgMutedText,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (active) AgRoyalBlue else AgBorder
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setSection(sec) }
                                .testTag("geo_tab_${sec.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (active) Color.White.copy(alpha = 0.22f) else AgMutedBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = if (active) Color.White else AgPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.filterCityId.isNotBlank() ||
                        uiState.filterTerritoryId.isNotBlank() ||
                        uiState.filterLocationId.isNotBlank() ||
                        uiState.filterSubLocationId.isNotBlank()
                    ) {
                        Surface(
                            color = AgDestructiveBg,
                            contentColor = AgDestructiveDark,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.resetFilters() }
                                .testTag("clear_geo_filters_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Reset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Scope Filter Chips & Search Bar (for Geography & Distributors list views)
        if ((isGeoSection || uiState.activeSection == MobileSection.DISTRIBUTORS) &&
            uiState.selectedShop == null &&
            uiState.selectedDistributor == null
        ) {
            Surface(
                color = AgCardWhite,
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.filterCityId.isNotBlank() ||
                        uiState.filterTerritoryId.isNotBlank() ||
                        uiState.filterLocationId.isNotBlank() ||
                        uiState.filterSubLocationId.isNotBlank()
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.filterCityId.isNotBlank()) {
                                val cityName = uiState.cities.find { it.id == uiState.filterCityId }?.name ?: ""
                                FilterScopeChip("Town: $cityName")
                            }
                            if (uiState.filterTerritoryId.isNotBlank()) {
                                val terCode = uiState.territories.find { it.id == uiState.filterTerritoryId }?.code ?: ""
                                FilterScopeChip("Locality: $terCode")
                            }
                            if (uiState.filterSubLocationId.isNotBlank()) {
                                val subName = uiState.subLocations.find { it.id == uiState.filterSubLocationId }?.name ?: ""
                                FilterScopeChip("Sub-Locality: $subName")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = {
                            Text(
                                text = "Search ${uiState.activeSection.name.lowercase()} within assigned scope...",
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AgMutedText,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scope_search_input")
                    )
                }
            }
        }

        // Scrollable Main Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                val totalCompletedCount =
                    uiState.auditReports.size + uiState.retailReports.size + uiState.customSubmissions.size

                when (uiState.activeSection) {
                    MobileSection.AUDIT -> {
                        DistributorAuditFormScreen(
                            activeAuditor = activeAuditor,
                            cities = uiState.cities,
                            territories = uiState.territories,
                            locations = uiState.locations,
                            subLocations = uiState.subLocations,
                            shops = uiState.shops,
                            distributors = uiState.distributors,
                            products = uiState.products,
                            auditForms = uiState.auditForms,
                            formConfigs = uiState.formConfigs,
                            apiClient = viewModel.apiClient,
                            completedTotalCount = totalCompletedCount,
                            pendingOutboxCount = uiState.pendingOutbox.size,
                            onOpenCompletedAudits = { viewModel.setSection(MobileSection.COMPLETED) },
                            onOpenDuties = { viewModel.setSection(MobileSection.DUTIES) },
                            onOpenLocalities = { viewModel.setSection(MobileSection.TERRITORIES) },
                            onOpenShops = { viewModel.setSection(MobileSection.SHOPS) },
                            syncing = uiState.syncing,
                            online = uiState.online,
                            lastSyncedAt = uiState.lastSyncedAt,
                            newlySyncedFormIds = uiState.newlySyncedFormIds,
                            updatedFormIds = uiState.updatedFormIds,
                            formsBundleHash = uiState.formsBundleHash,
                            onSyncFormsNow = { viewModel.syncAllNow(manual = true) },
                            onSubmitReport = viewModel::submitDistributorReport,
                            onSubmitRetailShopReport = viewModel::submitRetailShopReport,
                            onSubmitCustomAudit = viewModel::submitCustomAuditReport,
                            onCustomSubmitted = {},
                            onToast = viewModel::showToast
                        )
                    }

                    MobileSection.DUTIES -> {
                        DutiesScreen(
                            duties = uiState.duties,
                            territories = assignedTerritories,
                            subLocations = assignedSubLocations,
                            shops = assignedShops,
                            distributors = assignedDistributors,
                            syncing = uiState.syncing,
                            online = uiState.online,
                            lastSyncedAt = uiState.lastSyncedAt,
                            onSyncNow = { viewModel.syncAllNow(manual = true) },
                            onStartDutyAudit = viewModel::startDutyAudit,
                            onBrowseDutyPlaces = { duty ->
                                viewModel.setGeographyFilter(
                                    cityId = duty.cityId.ifBlank { duty.cityIds.firstOrNull() ?: "" },
                                    territoryId = if (duty.territoryIds.size == 1) duty.territoryIds.first() else "",
                                    locationId = "",
                                    subLocationId = "",
                                    targetSection = MobileSection.TERRITORIES
                                )
                            },
                            onUpdateDutyStatus = viewModel::updateDutyStatus,
                            onToast = viewModel::showToast
                        )
                    }

                    MobileSection.COMPLETED -> {
                        CompletedAuditsScreen(
                            distributorReports = uiState.auditReports,
                            retailReports = uiState.retailReports,
                            customSubmissions = uiState.customSubmissions,
                            pendingOutbox = uiState.pendingOutbox,
                            formConfigs = uiState.formConfigs,
                            syncing = uiState.syncing,
                            lastSyncedAt = uiState.lastSyncedAt,
                            online = uiState.online,
                            onSyncNow = { viewModel.syncAllNow(manual = true) }
                        )
                    }

                    MobileSection.PLANS -> {
                        MobilePlansScreen(
                            activeAuditor = activeAuditor,
                            cities = assignedCities.ifEmpty { uiState.cities },
                            territories = assignedTerritories.ifEmpty { uiState.territories },
                            duties = uiState.duties,
                            auditForms = uiState.auditForms,
                            distributors = assignedDistributors,
                            subLocations = assignedSubLocations,
                            shops = assignedShops,
                            apiClient = viewModel.apiClient,
                            mePosition = uiState.liveSharing.position,
                            refreshVersion = uiState.plansRefreshVersion,
                            onPlansChanged = viewModel::notifyPlansChanged,
                            onToast = viewModel::showToast,
                            renderAuditForm = { plan, item, onExit ->
                                DistributorAuditFormScreen(
                                    activeAuditor = activeAuditor,
                                    cities = uiState.cities,
                                    territories = uiState.territories,
                                    locations = uiState.locations,
                                    subLocations = uiState.subLocations,
                                    shops = uiState.shops,
                                    distributors = uiState.distributors,
                                    products = uiState.products,
                                    auditForms = uiState.auditForms,
                                    formConfigs = uiState.formConfigs,
                                    apiClient = viewModel.apiClient,
                                    initialFormId = item.formId,
                                    preset = AuditPreset(
                                        distributorId = item.targetId,
                                        subLocationId = item.targetId,
                                        targetId = item.targetId,
                                        shopIds = item.shopIds
                                    ),
                                    planLink = PlanLink(planId = plan.id, itemId = item.id),
                                    onExit = onExit,
                                    onSubmitReport = viewModel::submitDistributorReport,
                                    onSubmitRetailShopReport = viewModel::submitRetailShopReport,
                                    onSubmitCustomAudit = viewModel::submitCustomAuditReport,
                                    onCustomSubmitted = {},
                                    onToast = viewModel::showToast
                                )
                            }
                        )
                    }

                    MobileSection.CITIES -> {
                        CitiesListSection(
                            assignedCities = assignedCities,
                            assignedTerritories = assignedTerritories,
                            assignedDistributors = assignedDistributors,
                            searchQuery = uiState.searchQuery,
                            onSelectCity = { city ->
                                viewModel.setGeographyFilter(
                                    cityId = city.id,
                                    territoryId = "",
                                    locationId = "",
                                    subLocationId = "",
                                    targetSection = MobileSection.TERRITORIES
                                )
                            }
                        )
                    }

                    MobileSection.TERRITORIES -> {
                        TerritoriesListSection(
                            visibleTerritories = visibleTerritories,
                            cities = uiState.cities,
                            assignedLocations = assignedLocations,
                            assignedSubLocations = assignedSubLocations,
                            assignedShops = assignedShops,
                            assignedDistributors = assignedDistributors,
                            onNavigateSection = { cityId, territoryId, target ->
                                viewModel.setGeographyFilter(
                                    cityId = cityId,
                                    territoryId = territoryId,
                                    locationId = "",
                                    subLocationId = "",
                                    targetSection = target
                                )
                            }
                        )
                    }

                    MobileSection.LOCATIONS -> {
                        LocationsListSection(
                            visibleLocations = visibleLocations,
                            assignedTerritories = assignedTerritories,
                            assignedSubLocations = assignedSubLocations,
                            assignedShops = assignedShops,
                            defaultTerritoryId = uiState.filterTerritoryId,
                            onSelectLocation = { loc ->
                                viewModel.setGeographyFilter(
                                    territoryId = loc.territoryId,
                                    locationId = loc.id,
                                    subLocationId = "",
                                    targetSection = MobileSection.SUBLOCATIONS
                                )
                            },
                            onCreateLocation = viewModel::createLocationFromMobile,
                            onUpdatePlaceGps = viewModel::updatePlaceGpsFromMobile
                        )
                    }

                    MobileSection.SUBLOCATIONS -> {
                        SubLocationsListSection(
                            visibleSubLocations = visibleSubLocations,
                            assignedLocations = assignedLocations,
                            assignedTerritories = assignedTerritories,
                            assignedShops = assignedShops,
                            defaultLocationId = uiState.filterLocationId,
                            onSelectSubLocation = { sub ->
                                viewModel.setGeographyFilter(
                                    territoryId = sub.territoryId,
                                    locationId = sub.locationId,
                                    subLocationId = sub.id,
                                    targetSection = MobileSection.SHOPS
                                )
                            },
                            onCreateSubLocation = viewModel::createSubLocationFromMobile,
                            onUpdatePlaceGps = viewModel::updatePlaceGpsFromMobile
                        )
                    }

                    MobileSection.SHOPS -> {
                        ShopsSection(
                            visibleShops = visibleShops,
                            selectedShop = uiState.selectedShop,
                            assignedTerritories = assignedTerritories,
                            assignedSubLocations = assignedSubLocations,
                            defaultSubLocationId = uiState.filterSubLocationId,
                            onSelectShop = viewModel::selectShop,
                            onCreateShop = viewModel::createShopFromMobile,
                            onUpdatePlaceGps = viewModel::updatePlaceGpsFromMobile
                        )
                    }

                    MobileSection.DISTRIBUTORS -> {
                        DistributorsSection(
                            visibleDistributors = visibleDistributors,
                            selectedDistributor = uiState.selectedDistributor,
                            cities = uiState.cities,
                            territories = uiState.territories,
                            products = uiState.products,
                            onSelectDistributor = viewModel::selectDistributor
                        )
                    }

                    MobileSection.ACCOUNT -> {
                        AccountSection(
                            activeAuditor = activeAuditor,
                            activeRole = activeRole,
                            citiesCount = assignedCities.size,
                            territoriesCount = assignedTerritories.size,
                            shopsCount = assignedShops.size,
                            distributorsCount = assignedDistributors.size,
                            completedAuditsCount = totalCompletedCount,
                            pendingOutboxCount = uiState.pendingOutbox.size,
                            syncing = uiState.syncing,
                            lastSyncedAt = uiState.lastSyncedAt,
                            online = uiState.online,
                            apiClient = viewModel.apiClient,
                            onSyncNow = { viewModel.syncAllNow(manual = true) },
                            onOpenCompletedAudits = { viewModel.setSection(MobileSection.COMPLETED) },
                            onOpenPlaces = { viewModel.setSection(MobileSection.TERRITORIES) },
                            onOpenDistributors = { viewModel.setSection(MobileSection.DISTRIBUTORS) },
                            onImportFormPackageJson = viewModel::importFormPackageFromJson,
                            onToast = viewModel::showToast,
                            onLogout = viewModel::handleLogout
                        )
                    }
                }
            }
        }

        // Bottom Navigation Bar matching the 4-Tab AuditGrid Mockup (Audit, Duties, Plans, Account)
        Surface(
            color = AgCardWhite,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFDCE6F4)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "Audit",
                    icon = Icons.Default.AssignmentTurnedIn,
                    active = uiState.activeSection == MobileSection.AUDIT,
                    testTag = "nav_tab_audit",
                    onClick = { viewModel.setSection(MobileSection.AUDIT) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Duties",
                    icon = Icons.Default.DateRange,
                    active = uiState.activeSection == MobileSection.DUTIES,
                    testTag = "nav_tab_duties",
                    onClick = { viewModel.setSection(MobileSection.DUTIES) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Plans",
                    icon = Icons.Default.LocationOn,
                    active = uiState.activeSection == MobileSection.PLANS || isGeoSection,
                    testTag = "nav_tab_plans",
                    onClick = { viewModel.setSection(MobileSection.PLANS) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "Account",
                    icon = Icons.Default.Person,
                    active = uiState.activeSection == MobileSection.ACCOUNT ||
                        uiState.activeSection == MobileSection.COMPLETED ||
                        uiState.activeSection == MobileSection.DISTRIBUTORS,
                    testTag = "nav_tab_account",
                    onClick = { viewModel.setSection(MobileSection.ACCOUNT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Duty Assignment Notifications Dialog Overlay
        if (uiState.showNotificationsDialog) {
            DutyNotificationsDialog(
                notifications = uiState.notifications,
                onDismiss = { viewModel.toggleNotificationsDialog(false) },
                onMarkRead = viewModel::markNotificationRead,
                onOpenDuty = {
                    viewModel.toggleNotificationsDialog(false)
                    viewModel.setSection(MobileSection.DUTIES)
                }
            )
        }
    }
}

@Composable
private fun FilterScopeChip(text: String) {
    Surface(
        color = AgSelectedCardBg,
        contentColor = AgRoyalBlue,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AgRoyalBlue.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = AgRoyalBlue
    val inactiveColor = AgMutedText
    val tint = if (active) activeColor else inactiveColor

    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(top = 5.dp, bottom = 5.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) AgRoyalBlue.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

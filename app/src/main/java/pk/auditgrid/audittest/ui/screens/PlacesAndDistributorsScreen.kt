package pk.auditgrid.audittest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.ApiError
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.MobileSection
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*
import kotlin.math.roundToInt

@Composable
fun CitiesListSection(
    assignedCities: List<City>,
    assignedTerritories: List<Territory>,
    assignedDistributors: List<Distributor>,
    searchQuery: String,
    onSelectCity: (City) -> Unit
) {
    val filtered = assignedCities.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
    }
    var showAllCities by remember(filtered.size) { mutableStateOf(false) }
    val shownCities = if (showAllCities || filtered.size <= 6) filtered else filtered.take(6)
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ASSIGNED TOWNS (${assignedCities.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        shownCities.forEach { city ->
            val cityTerritories = assignedTerritories.count { it.cityId == city.id }
            val cityDistributors = assignedDistributors.count { it.cityId == city.id }
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCity(city) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CodeBadge(city.code)
                            Text(
                                text = city.name,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${city.province} · ${city.region}",
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$cityTerritories Assigned Localities · $cityDistributors Distributors",
                            color = AgPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${city.name}",
                        tint = AgMutedText
                    )
                }
            }
        }
        ShowMoreListToggle(
            totalCount = filtered.size,
            collapsedLimit = 6,
            expanded = showAllCities,
            itemLabel = "Towns",
            onToggle = { showAllCities = !showAllCities }
        )
    }
}

@Composable
fun TerritoriesListSection(
    visibleTerritories: List<Territory>,
    cities: List<City>,
    assignedLocations: List<LocationItem>,
    assignedSubLocations: List<SubLocation> = emptyList(),
    assignedShops: List<Shop>,
    assignedDistributors: List<Distributor>,
    onNavigateSection: (cityId: String, territoryId: String, target: MobileSection) -> Unit
) {
    var showAllTerritories by remember(visibleTerritories.size) { mutableStateOf(false) }
    val shownTerritories = if (showAllTerritories || visibleTerritories.size <= 6) {
        visibleTerritories
    } else {
        visibleTerritories.take(6)
    }
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ASSIGNED LOCALITIES (${visibleTerritories.size})",
            color = AgMutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        shownTerritories.forEach { ter ->
            val city = cities.find { it.id == ter.cityId }
            val terSubLocations = if (assignedSubLocations.isNotEmpty()) {
                assignedSubLocations.count { it.territoryId == ter.id }
            } else {
                assignedLocations.count { it.territoryId == ter.id }
            }
            val terShops = assignedShops.count { it.territoryId == ter.id }
            val terDistributors = assignedDistributors.count { it.territoryId == ter.id }

            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
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
                        CodeBadge(ter.code)
                        Text(
                            text = city?.name ?: "",
                            color = AgMutedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = ter.name,
                        color = AgForeground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ExpandableTextWithMore(
                        text = "Locality Distributor: ${ter.distributorName} (${ter.distributorCode})",
                        maxLinesCollapsed = 1,
                        thresholdChars = 54,
                        textColor = AgMutedText,
                        fontSize = 12.sp
                    )
                    HorizontalDivider(color = AgBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.SUBLOCATIONS) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AgBackground,
                                contentColor = AgPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text(
                                text = "Sub-Localities ($terSubLocations)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.DISTRIBUTORS) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AgBackground,
                                contentColor = AgPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text(
                                text = "Distributors ($terDistributors)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$terShops Scoped Shops in Locality",
                            color = AgMutedText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        TextButton(
                            onClick = { onNavigateSection(ter.cityId, ter.id, MobileSection.SHOPS) }
                        ) {
                            Text(
                                text = "View Shops →",
                                color = AgSecondaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        ShowMoreListToggle(
            totalCount = visibleTerritories.size,
            collapsedLimit = 6,
            expanded = showAllTerritories,
            itemLabel = "Localities",
            onToggle = { showAllTerritories = !showAllTerritories }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsListSection(
    visibleLocations: List<LocationItem>,
    assignedTerritories: List<Territory>,
    assignedSubLocations: List<SubLocation>,
    assignedShops: List<Shop>,
    defaultTerritoryId: String = "",
    onSelectLocation: (LocationItem) -> Unit,
    onCreateLocation: (suspend (String, String, String, Double, Double) -> LocationItem)? = null,
    onUpdatePlaceGps: (suspend (String, String, Double, Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateModal by remember { mutableStateOf(false) }
    var updatingGpsId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        }
        onDispose { }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SELECTED LOCALITY LOCATIONS (${visibleLocations.size})",
                color = AgMutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (onCreateLocation != null && assignedTerritories.isNotEmpty()) {
                Button(
                    onClick = { showCreateModal = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 34.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("+ Add Location (GPS)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        var showAllLocations by remember(visibleLocations.size) { mutableStateOf(false) }
        val shownLocations = if (showAllLocations || visibleLocations.size <= 6) {
            visibleLocations
        } else {
            visibleLocations.take(6)
        }
        shownLocations.forEach { loc ->
            val ter = assignedTerritories.find { it.id == loc.territoryId }
            val subCount = assignedSubLocations.count { it.locationId == loc.id }
            val shopCount = assignedShops.count { it.locationId == loc.id }
            val hasCoords = loc.lat != 0.0 || loc.lng != 0.0
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLocation(loc) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CodeBadge(loc.code)
                                GPSStatusChip(if (hasCoords) "GPS Verified" else loc.gpsStatus)
                            }
                            Text(
                                text = loc.name,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${ter?.code ?: ""} · $subCount Sub-Localities · $shopCount Shops",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (hasCoords) {
                                Text(
                                    text = "GPS: ${String.format("%.5f", loc.lat)}, ${String.format("%.5f", loc.lng)} (Viewable on Web Map)",
                                    color = AgAccentGreenDark,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open ${loc.name}",
                            tint = AgMutedText
                        )
                    }
                    if (onUpdatePlaceGps != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = {
                                    updatingGpsId = loc.id
                                    scope.launch {
                                        try {
                                            val pos = WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                                            onUpdatePlaceGps("location", loc.id, pos.lat, pos.lng, pos.accuracy)
                                        } catch (_: Exception) {
                                        } finally {
                                            updatingGpsId = null
                                        }
                                    }
                                },
                                enabled = updatingGpsId != loc.id,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (updatingGpsId == loc.id) "Capturing GPS…" else "Capture GPS Point Here",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
        ShowMoreListToggle(
            totalCount = visibleLocations.size,
            collapsedLimit = 6,
            expanded = showAllLocations,
            itemLabel = "Locations",
            onToggle = { showAllLocations = !showAllLocations }
        )
    }

    if (showCreateModal && onCreateLocation != null) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("LOC-${(100..999).random()}") }
        var territoryId by remember {
            mutableStateOf(defaultTerritoryId.ifBlank { assignedTerritories.firstOrNull()?.id ?: "" })
        }
        var terExpanded by remember { mutableStateOf(false) }
        val warmPos by WarmLocationManager.latestPosition.collectAsState()
        var capturedPos by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
        var saving by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            WarmLocationManager.startWarming(context)
            runCatching { getDevicePosition(context, timeoutMs = 5000L) }.getOrNull()?.let {
                capturedPos = it
            }
        }
        val activePos = warmPos ?: capturedPos

        Dialog(onDismissRequest = { if (!saving) showCreateModal = false }) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgSoftBlueBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Create Field Location (Syncs to Web)", color = AgPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = terExpanded,
                        onExpandedChange = { terExpanded = !terExpanded }
                    ) {
                        OutlinedTextField(
                            value = assignedTerritories.find { it.id == territoryId }?.let { "${it.code} · ${it.name}" } ?: "Select Locality",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Locality *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = terExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = terExpanded, onDismissRequest = { terExpanded = false }) {
                            assignedTerritories.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text("${t.code} · ${t.name}") },
                                    onClick = { territoryId = t.id; terExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Location Name * (e.g. Gulberg Commercial)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Location Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        color = if (activePos != null) AgAccentGreenBg else AgSoftBlueBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activePos != null) {
                                "Live GPS Ready: ${String.format("%.5f", activePos.lat)}, ${String.format("%.5f", activePos.lng)} (±${activePos.accuracy.roundToInt()}m)"
                            } else {
                                "Pre-fetching GPS coordinates in background…"
                            },
                            color = if (activePos != null) AgAccentGreenDark else AgPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    err?.let { Text(it, color = AgDestructiveDark, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCreateModal = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (name.isBlank()) { err = "Enter location name."; return@Button }
                                saving = true
                                err = null
                                scope.launch {
                                    try {
                                        val pos = activePos ?: getDevicePosition(context)
                                        onCreateLocation(name, code, territoryId, pos.lat, pos.lng)
                                        showCreateModal = false
                                    } catch (e: Exception) {
                                        err = e.message ?: "Could not save location."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (saving) "Saving…" else "Save & Sync") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubLocationsListSection(
    visibleSubLocations: List<SubLocation>,
    assignedLocations: List<LocationItem>,
    assignedTerritories: List<Territory>,
    assignedShops: List<Shop>,
    defaultLocationId: String = "",
    onSelectSubLocation: (SubLocation) -> Unit,
    onCreateSubLocation: (suspend (String, String, String, Double, Double, Int) -> SubLocation)? = null,
    onUpdatePlaceGps: (suspend (String, String, Double, Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateModal by remember { mutableStateOf(false) }
    var updatingGpsId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SELECTED LOCALITY SUB-LOCALITIES (${visibleSubLocations.size})",
                color = AgMutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (onCreateSubLocation != null && assignedLocations.isNotEmpty()) {
                Button(
                    onClick = { showCreateModal = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 34.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("+ Add Sub-Locality", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        var showAllSubLocations by remember(visibleSubLocations.size) { mutableStateOf(false) }
        val shownSubLocations = if (showAllSubLocations || visibleSubLocations.size <= 6) {
            visibleSubLocations
        } else {
            visibleSubLocations.take(6)
        }
        shownSubLocations.forEach { sub ->
            val loc = assignedLocations.find { it.id == sub.locationId }
            val ter = assignedTerritories.find { it.id == sub.territoryId }
            val shopCount = assignedShops.count { it.subLocationId == sub.id }
            val hasCoords = sub.lat != 0.0 || sub.lng != 0.0
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSubLocation(sub) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CodeBadge(sub.code)
                                Text(
                                    text = "${ter?.name ?: ter?.code ?: ""}",
                                    color = AgMutedText,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = sub.name,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${sub.marketType} · $shopCount Shops",
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (hasCoords) {
                                Text(
                                    text = "GPS: ${String.format("%.5f", sub.lat)}, ${String.format("%.5f", sub.lng)} (Viewable on Web Map)",
                                    color = AgAccentGreenDark,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open ${sub.name}",
                            tint = AgMutedText
                        )
                    }
                    if (onUpdatePlaceGps != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = {
                                    updatingGpsId = sub.id
                                    scope.launch {
                                        try {
                                            val pos = WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                                            onUpdatePlaceGps("subLocation", sub.id, pos.lat, pos.lng, pos.accuracy)
                                        } catch (_: Exception) {
                                        } finally {
                                            updatingGpsId = null
                                        }
                                    }
                                },
                                enabled = updatingGpsId != sub.id,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (updatingGpsId == sub.id) "Capturing GPS…" else "Capture GPS Point Here",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
        ShowMoreListToggle(
            totalCount = visibleSubLocations.size,
            collapsedLimit = 6,
            expanded = showAllSubLocations,
            itemLabel = "Sub-Localities",
            onToggle = { showAllSubLocations = !showAllSubLocations }
        )
    }

    if (showCreateModal && onCreateSubLocation != null) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("SUB-${(100..999).random()}") }
        var locationId by remember {
            mutableStateOf(defaultLocationId.ifBlank { assignedLocations.firstOrNull()?.id ?: "" })
        }
        var locExpanded by remember { mutableStateOf(false) }
        val warmPos by WarmLocationManager.latestPosition.collectAsState()
        var capturedPos by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
        var saving by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            WarmLocationManager.startWarming(context)
            runCatching { getDevicePosition(context, timeoutMs = 5000L) }.getOrNull()?.let {
                capturedPos = it
            }
        }
        val activePos = warmPos ?: capturedPos

        Dialog(onDismissRequest = { if (!saving) showCreateModal = false }) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgSoftBlueBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Create Sub-Locality (Syncs to Web)", color = AgPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = locExpanded,
                        onExpandedChange = { locExpanded = !locExpanded }
                    ) {
                        OutlinedTextField(
                            value = assignedLocations.find { it.id == locationId }?.let { "${it.code} · ${it.name}" } ?: "Select Locality",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Locality *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = locExpanded, onDismissRequest = { locExpanded = false }) {
                            assignedLocations.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("${l.code} · ${l.name}") },
                                    onClick = { locationId = l.id; locExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Sub-Locality / Market Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Sub-Locality Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        color = if (activePos != null) AgAccentGreenBg else AgSoftBlueBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activePos != null) {
                                "Live GPS Ready: ${String.format("%.5f", activePos.lat)}, ${String.format("%.5f", activePos.lng)} (±${activePos.accuracy.roundToInt()}m)"
                            } else {
                                "Pre-fetching GPS coordinates in background…"
                            },
                            color = if (activePos != null) AgAccentGreenDark else AgPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    err?.let { Text(it, color = AgDestructiveDark, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCreateModal = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (name.isBlank()) { err = "Enter sub-locality name."; return@Button }
                                saving = true
                                err = null
                                scope.launch {
                                    try {
                                        val pos = activePos ?: getDevicePosition(context)
                                        onCreateSubLocation(name, code, locationId, pos.lat, pos.lng, 0)
                                        showCreateModal = false
                                    } catch (e: Exception) {
                                        err = e.message ?: "Could not save sub-locality."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (saving) "Saving…" else "Save & Sync") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsSection(
    visibleShops: List<Shop>,
    selectedShop: Shop?,
    assignedTerritories: List<Territory>,
    assignedSubLocations: List<SubLocation>,
    defaultSubLocationId: String = "",
    onSelectShop: (Shop?) -> Unit,
    onCreateShop: (suspend (String, String, String, String, String, String, String, Double, Double) -> Shop)? = null,
    onUpdatePlaceGps: (suspend (String, String, Double, Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var distanceBusy by remember { mutableStateOf(false) }
    var gpsUpdateBusy by remember { mutableStateOf(false) }
    var showCreateShopModal by remember { mutableStateOf(false) }
    var distanceResult by remember(selectedShop?.id) { mutableStateOf<Pair<Boolean, String>?>(null) }

    DisposableEffect(selectedShop?.id) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        }
        onDispose { }
    }

    if (selectedShop != null) {
        BackHandler { onSelectShop(null) }
        val ter = assignedTerritories.find { it.id == selectedShop.territoryId }
        val sub = assignedSubLocations.find { it.id == selectedShop.subLocationId }
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        CodeBadge(selectedShop.code)
                        Text(
                            text = selectedShop.name,
                            color = AgForeground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedShop.addressLine,
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                    }
                    GPSStatusChip(selectedShop.gpsStatus)
                }
                HorizontalDivider(color = AgBorder)

                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Retailer Owner", selectedShop.ownerName, Modifier.weight(1f))
                            InfoCell("Contact Phone", selectedShop.phone, Modifier.weight(1f), mono = true)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Locality", ter?.name ?: "", Modifier.weight(1f), mono = true)
                            InfoCell("Sub-Locality", sub?.name ?: "", Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell(
                                "Registered GPS",
                                String.format("%.5f, %.5f", selectedShop.lat, selectedShop.lng),
                                Modifier.weight(1f),
                                mono = true
                            )
                            InfoCell(
                                "Allowed Radius",
                                "${selectedShop.allowedRadiusMeters} meters",
                                Modifier.weight(1f),
                                mono = true
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        distanceBusy = true
                        scope.launch {
                            try {
                                val pos = WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                                val d = haversineMeters(pos.lat, pos.lng, selectedShop.lat, selectedShop.lng).roundToInt()
                                val radius = selectedShop.allowedRadiusMeters.takeIf { it > 0 } ?: DEFAULT_ALLOWED_RADIUS_M
                                val lowAcc = pos.accuracy > MAX_ACCEPTABLE_ACCURACY_M
                                val ok = d <= radius && !lowAcc
                                val msg = when {
                                    lowAcc -> "GPS accuracy is low (±${pos.accuracy.roundToInt()} m). Move to an open area and try again."
                                    d <= radius -> "You are $d m from the registered point (allowed $radius m)."
                                    else -> "You are $d m from the registered point — outside the allowed $radius m."
                                }
                                distanceResult = ok to msg
                            } catch (e: Exception) {
                                val msg = if (e is GpsException) e.message ?: "Could not read your location."
                                else "Could not read your location."
                                distanceResult = false to msg
                            } finally {
                                distanceBusy = false
                            }
                        }
                    },
                    enabled = !distanceBusy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_distance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (distanceBusy) "Getting location…" else "Check my distance from this shop",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (onUpdatePlaceGps != null) {
                    OutlinedButton(
                        onClick = {
                            gpsUpdateBusy = true
                            scope.launch {
                                try {
                                    val pos = WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                                    onUpdatePlaceGps("shop", selectedShop.id, pos.lat, pos.lng, pos.accuracy)
                                } catch (_: Exception) {
                                } finally {
                                    gpsUpdateBusy = false
                                }
                            }
                        },
                        enabled = !gpsUpdateBusy,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, AgActionBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = AgActionBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (gpsUpdateBusy) "Saving GPS to Web Server…" else "Update Shop GPS Point Here (Syncs to Web Map)",
                            color = AgActionBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                distanceResult?.let { (ok, text) ->
                    Surface(
                        color = if (ok) AgAccentGreenBg else AgDestructiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (ok) AgAccentGreen.copy(alpha = 0.4f) else AgDestructive.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = text,
                            color = if (ok) AgAccentGreenDark else AgDestructiveDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onSelectShop(null) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text(
                        text = "← Back to Locality Shops List",
                        color = AgForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECTED LOCALITY SHOPS (${visibleShops.size})",
                    color = AgMutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (onCreateShop != null && assignedSubLocations.isNotEmpty()) {
                    Button(
                        onClick = { showCreateShopModal = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("+ Register Shop (GPS)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            var showAllShops by remember(visibleShops.size) { mutableStateOf(false) }
            val shownShops = if (showAllShops || visibleShops.size <= 6) {
                visibleShops
            } else {
                visibleShops.take(6)
            }
            shownShops.forEach { sh ->
                val ter = assignedTerritories.find { it.id == sh.territoryId }
                val sub = assignedSubLocations.find { it.id == sh.subLocationId }
                var shopCardExpanded by remember(sh.id) { mutableStateOf(false) }
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable { onSelectShop(sh) }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CodeBadge(sh.code)
                            GPSStatusChip(sh.gpsStatus)
                        }
                        Text(
                            text = sh.name,
                            color = AgForeground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${sh.ownerName} · ${sh.phone} · ${sh.shopCategory}",
                            color = AgMutedText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider(color = AgBorder.copy(alpha = 0.6f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${ter?.code ?: ""} · ${sub?.name ?: ""}",
                                color = AgPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CardMoreDetailsToggle(
                                expanded = shopCardExpanded,
                                labelCollapsed = "More",
                                labelExpanded = "Less",
                                onToggle = { shopCardExpanded = !shopCardExpanded }
                            )
                        }
                        if (shopCardExpanded) {
                            Surface(
                                color = AgFormBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AgBorderHairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StructuredMetaGridItem(
                                        label = "Address",
                                        value = sh.addressLine.ifBlank { "No street address on file" }
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StructuredMetaGridItem(
                                            label = "GPS Coords",
                                            value = if (sh.lat != 0.0 || sh.lng != 0.0) {
                                                "${String.format("%.5f", sh.lat)}, ${String.format("%.5f", sh.lng)}"
                                            } else {
                                                "Unpinned"
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        StructuredMetaGridItem(
                                            label = "Allowed Radius",
                                            value = "${sh.allowedRadiusMeters} m",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ShowMoreListToggle(
                totalCount = visibleShops.size,
                collapsedLimit = 6,
                expanded = showAllShops,
                itemLabel = "Shops",
                onToggle = { showAllShops = !showAllShops }
            )
        }
    }

    if (showCreateShopModal && onCreateShop != null) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("SHP-${(1000..9999).random()}") }
        var subLocationId by remember {
            mutableStateOf(defaultSubLocationId.ifBlank { assignedSubLocations.firstOrNull()?.id ?: "" })
        }
        var ownerName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var subExpanded by remember { mutableStateOf(false) }
        val warmPos by WarmLocationManager.latestPosition.collectAsState()
        var capturedPos by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
        var saving by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            WarmLocationManager.startWarming(context)
            runCatching { getDevicePosition(context, timeoutMs = 5000L) }.getOrNull()?.let {
                capturedPos = it
            }
        }
        val activePos = warmPos ?: capturedPos

        Dialog(onDismissRequest = { if (!saving) showCreateShopModal = false }) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgSoftBlueBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Register Shop with Live GPS (Syncs to Web)", color = AgPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = subExpanded,
                        onExpandedChange = { subExpanded = !subExpanded }
                    ) {
                        OutlinedTextField(
                            value = assignedSubLocations.find { it.id == subLocationId }?.let { "${it.code} · ${it.name}" } ?: "Select Sub-Locality",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sub-Locality *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = subExpanded, onDismissRequest = { subExpanded = false }) {
                            assignedSubLocations.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.code} · ${s.name}") },
                                    onClick = { subLocationId = s.id; subExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Shop Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Street / Market Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        color = if (activePos != null) AgAccentGreenBg else AgSoftBlueBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activePos != null) {
                                "Live GPS Ready: ${String.format("%.5f", activePos.lat)}, ${String.format("%.5f", activePos.lng)} (±${activePos.accuracy.roundToInt()}m)"
                            } else {
                                "Pre-fetching GPS coordinates in background…"
                            },
                            color = if (activePos != null) AgAccentGreenDark else AgPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    err?.let { Text(it, color = AgDestructiveDark, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCreateShopModal = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (name.isBlank()) { err = "Enter shop name."; return@Button }
                                saving = true
                                err = null
                                scope.launch {
                                    try {
                                        val pos = activePos ?: getDevicePosition(context)
                                        onCreateShop(name, code, subLocationId, ownerName, phone, "General Store", address, pos.lat, pos.lng)
                                        showCreateShopModal = false
                                    } catch (e: Exception) {
                                        err = e.message ?: "Could not register shop."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (saving) "Saving…" else "Save & Sync") }
                    }
                }
            }
        }
    }
}

@Composable
fun DistributorsSection(
    visibleDistributors: List<Distributor>,
    selectedDistributor: Distributor?,
    cities: List<City>,
    territories: List<Territory>,
    products: List<Product>,
    onSelectDistributor: (Distributor?) -> Unit
) {

    if (selectedDistributor != null) {
        BackHandler { onSelectDistributor(null) }
        val city = cities.find { it.id == selectedDistributor.cityId }
        val ter = territories.find { it.id == selectedDistributor.territoryId }
        val activeProducts = products.filter { p ->
            p.status == "Active" &&
                selectedDistributor.groupIds.contains(p.groupId) &&
                selectedDistributor.productStatuses[p.id] != "Inactive"
        }

        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CodeBadge(selectedDistributor.code)
                            selectedDistributor.groupIds.forEach { gid ->
                                ProductGroupTag(if (gid == "grp-a") "Group A" else "Group B")
                            }
                        }
                        Text(
                            text = selectedDistributor.setupName,
                            color = AgForeground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedDistributor.completeAddress,
                            color = AgMutedText,
                            fontSize = 12.sp
                        )
                    }
                    EntityStatusIndicator(selectedDistributor.status)
                }
                HorizontalDivider(color = AgBorder)

                Surface(
                    color = AgBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Distributor Name", selectedDistributor.distributorName, Modifier.weight(1f))
                            InfoCell("Father Name", selectedDistributor.fatherName, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("CNIC Number", selectedDistributor.cnic, Modifier.weight(1f), mono = true)
                            InfoCell("Contact Number", selectedDistributor.phone, Modifier.weight(1f), mono = true)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell("Town", city?.name ?: "", Modifier.weight(1f))
                            InfoCell("Linked Locality", ter?.name ?: "", Modifier.weight(1f), mono = true)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell(
                                "Warehouse GPS",
                                String.format("%.5f, %.5f", selectedDistributor.lat, selectedDistributor.lng),
                                Modifier.weight(1f),
                                mono = true
                            )
                            InfoCell("GPS Status", selectedDistributor.gpsStatus, Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    text = "Active Dealing Products (${activeProducts.size})",
                    color = AgForeground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                var showAllActiveProducts by remember(activeProducts.size) { mutableStateOf(false) }
                val shownActiveProducts = if (showAllActiveProducts || activeProducts.size <= 4) {
                    activeProducts
                } else {
                    activeProducts.take(4)
                }
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shownActiveProducts.forEach { prd ->
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prd.sku} · ${prd.name}",
                                        color = AgForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prd.boxesPerCtn} Boxes/CTN · ${prd.unitPerBox}",
                                        color = AgMutedText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = formatPKR(prd.pricePerBoxRs),
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    ShowMoreListToggle(
                        totalCount = activeProducts.size,
                        collapsedLimit = 4,
                        expanded = showAllActiveProducts,
                        itemLabel = "Products",
                        onToggle = { showAllActiveProducts = !showAllActiveProducts }
                    )
                }

                OutlinedButton(
                    onClick = { onSelectDistributor(null) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text(
                        text = "← Back to Locality Distributors",
                        color = AgForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SELECTED LOCALITY DISTRIBUTORS (${visibleDistributors.size})",
                color = AgMutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (visibleDistributors.isEmpty()) {
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No locality distributors matched the current filter.",
                        color = AgMutedText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                var showAllDistributors by remember(visibleDistributors.size) { mutableStateOf(false) }
                val shownDistributors = if (showAllDistributors || visibleDistributors.size <= 6) {
                    visibleDistributors
                } else {
                    visibleDistributors.take(6)
                }
                shownDistributors.forEach { dist ->
                    val city = cities.find { it.id == dist.cityId }
                    val ter = territories.find { it.id == dist.territoryId }
                    val activeSkuCount = products.count { p ->
                        p.status == "Active" &&
                            dist.groupIds.contains(p.groupId) &&
                            dist.productStatuses[p.id] != "Inactive"
                    }
                    var distCardExpanded by remember(dist.id) { mutableStateOf(false) }
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable { onSelectDistributor(dist) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CodeBadge(dist.code)
                                    dist.groupIds.forEach { gid ->
                                        ProductGroupTag(if (gid == "grp-a") "Group A" else "Group B")
                                    }
                                }
                                EntityStatusIndicator(dist.status)
                            }
                            Text(
                                text = dist.setupName,
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Owner: ${dist.distributorName} · ${dist.phone}",
                                color = AgMutedText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            HorizontalDivider(color = AgBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${city?.name ?: ""} → ${ter?.code ?: ""}",
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$activeSkuCount Active SKUs →",
                                        color = AgAccentGreen,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    CardMoreDetailsToggle(
                                        expanded = distCardExpanded,
                                        labelCollapsed = "More",
                                        labelExpanded = "Less",
                                        onToggle = { distCardExpanded = !distCardExpanded }
                                    )
                                }
                            }
                            if (distCardExpanded) {
                                Surface(
                                    color = AgFormBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AgBorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            StructuredMetaGridItem(
                                                label = "Father Name",
                                                value = dist.fatherName.ifBlank { "—" },
                                                modifier = Modifier.weight(1f)
                                            )
                                            StructuredMetaGridItem(
                                                label = "CNIC",
                                                value = dist.cnic.ifBlank { "—" },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        StructuredMetaGridItem(
                                            label = "Complete Address",
                                            value = dist.completeAddress.ifBlank { "No address on file" }
                                        )
                                        StructuredMetaGridItem(
                                            label = "GPS Status",
                                            value = dist.gpsStatus.ifBlank { "Pending" }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ShowMoreListToggle(
                    totalCount = visibleDistributors.size,
                    collapsedLimit = 6,
                    expanded = showAllDistributors,
                    itemLabel = "Distributors",
                    onToggle = { showAllDistributors = !showAllDistributors }
                )
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = AgMutedText, fontSize = 11.sp)
        Text(
            text = value,
            color = AgForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun AccountSection(
    activeAuditor: AppUser,
    activeRole: UserRole?,
    citiesCount: Int,
    territoriesCount: Int,
    shopsCount: Int,
    distributorsCount: Int,
    completedAuditsCount: Int = 0,
    pendingOutboxCount: Int = 0,
    syncing: Boolean = false,
    lastSyncedAt: String = "",
    online: Boolean,
    apiClient: AuditGridApiClient,
    onSyncNow: () -> Unit = {},
    onOpenCompletedAudits: () -> Unit = {},
    onOpenPlaces: () -> Unit = {},
    onOpenDistributors: () -> Unit = {},
    onImportFormPackageJson: ((String) -> Unit)? = null,
    onToast: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showManualPackageDialog by remember { mutableStateOf(false) }
    var manualPackageJsonText by remember { mutableStateOf("") }

    val importPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && onImportFormPackageJson != null) {
            scope.launch {
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!raw.isNullOrBlank()) {
                        showManualPackageDialog = false
                        manualPackageJsonText = ""
                        onImportFormPackageJson(raw)
                    } else {
                        onToast("Selected Form Package file is empty.")
                    }
                } catch (e: Exception) {
                    onToast("Could not read Form Package file: ${e.message ?: "Error"}")
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero Profile Header Card matching the AuditGrid Mockup
        Surface(
            color = AgDutyBannerNavy,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
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
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
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
                            text = activeAuditor.fullName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "@${activeAuditor.username}" +
                                (if (activeAuditor.phone.isNotBlank()) " • ${activeAuditor.phone}" else "") +
                                (if (activeRole != null) " • ${activeRole.name}" else ""),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.80f)),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clickable(enabled = !syncing) { onSyncNow() }
                        .testTag("account_sync_button")
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
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (syncing) "Syncing…" else "Sync",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val stats = listOf(
            "Towns" to citiesCount,
            "Localities" to territoriesCount,
            "Shops" to shopsCount,
            "Distributors" to distributorsCount
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stats[0].first, stats[0].second, Modifier.weight(1f))
                StatCard(stats[1].first, stats[1].second, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stats[2].first, stats[2].second, Modifier.weight(1f))
                StatCard(stats[3].first, stats[3].second, Modifier.weight(1f))
            }
        }

        // Quick Scope & Completed Audits Navigation Card
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgBorder),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audits, Places & Distributors",
                            color = AgForeground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (lastSyncedAt.isNotBlank()) "Last synced at $lastSyncedAt PKT" else "Synced to device database",
                            color = AgMutedText,
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        color = if (pendingOutboxCount > 0) AgWarningBg else AgAccentGreenBg,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (pendingOutboxCount > 0) "$pendingOutboxCount Pending" else "✓ SYNCED",
                            color = if (pendingOutboxCount > 0) AgWarningDark else AgAccentGreenDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                Button(
                    onClick = onOpenCompletedAudits,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgDutyBannerNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .testTag("account_open_completed_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Completed Audits ($completedAuditsCount)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenPlaces,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, AgDutyBannerNavy),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("account_open_places_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AgDutyBannerNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Places ($territoriesCount)",
                            color = AgDutyBannerNavy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenDistributors,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, AgDutyBannerNavy),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("account_open_distributors_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = AgDutyBannerNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Distributors ($distributorsCount)",
                            color = AgDutyBannerNavy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Surface(
            color = if (online) AgAccentGreenBg else AgWarningBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (online) AgAccentGreen.copy(alpha = 0.4f) else AgWarning.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (online) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (online) AgAccentGreenDark else AgWarningDark,
                    modifier = Modifier.size(16.dp)
                )
                ExpandableTextWithMore(
                    text = if (online) {
                        "Connected to Main Server · ${apiClient.getApiBase()}"
                    } else {
                        "Offline — submitted audits queue in Outbox & auto-sync when online"
                    },
                    maxLinesCollapsed = 1,
                    thresholdChars = 48,
                    textColor = if (online) AgAccentGreenDark else AgWarningDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (onImportFormPackageJson != null) {
            OutlinedButton(
                onClick = { showManualPackageDialog = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, AgRoyalBlue),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AgCardWhite,
                    contentColor = AgRoyalBlue
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("account_add_form_package_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = AgRoyalBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Form via Package Manually (.json)",
                    color = AgRoyalBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedButton(
            onClick = { showPasswordDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.2.dp, AgDutyBannerNavy),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AgCardWhite,
                contentColor = AgDutyBannerNavy
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("change_password_button")
        ) {
            Text(text = "Change password", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AgDestructiveBg.copy(alpha = 0.6f),
                contentColor = AgDestructive
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("logout_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Log out", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showManualPackageDialog && onImportFormPackageJson != null) {
        Dialog(onDismissRequest = { showManualPackageDialog = false }) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgSoftBlueBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Form via Package Manually",
                            color = AgPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showManualPackageDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = "Select a Form Package (.json) file from your phone or paste the Form Package JSON below to save the form directly into your mobile database.",
                        color = AgMutedText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = { importPackageLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("dialog_pick_package_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select .json Package File from Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = manualPackageJsonText,
                        onValueChange = { manualPackageJsonText = it },
                        label = { Text("Or paste Form Package JSON here") },
                        placeholder = { Text("{\"forms\": [...], \"formConfigs\": {...}}") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_paste_package_json_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showManualPackageDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val raw = manualPackageJsonText.trim()
                                if (raw.isBlank()) {
                                    onToast("Paste Form Package JSON or select a .json file.")
                                    return@Button
                                }
                                onImportFormPackageJson(raw)
                                showManualPackageDialog = false
                                manualPackageJsonText = ""
                            },
                            enabled = manualPackageJsonText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgRoyalBlue),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_import_pasted_package_button")
                        ) {
                            Text("Save Form Package", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordModal(
            apiClient = apiClient,
            onClose = { showPasswordDialog = false },
            onDone = {
                showPasswordDialog = false
                onToast(it)
            }
        )
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = AgMutedText, fontSize = 11.sp)
            Text(
                text = count.toString(),
                color = AgForeground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChangePasswordModal(
    apiClient: AuditGridApiClient,
    onClose: () -> Unit,
    onDone: (String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change password",
                        color = AgForeground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
                error?.let {
                    Surface(
                        color = AgDestructiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AgDestructive)
                    ) {
                        Text(
                            text = it,
                            color = AgDestructiveDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "At least 8 characters with an uppercase letter, a lowercase letter and a number.",
                    color = AgMutedText,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Repeat new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        error = null
                        if (next != confirm) {
                            error = "The new passwords do not match."
                            return@Button
                        }
                        if (next.length < 8) {
                            error = "Password must be at least 8 characters."
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            try {
                                apiClient.changePassword(current, next)
                                onDone("Password changed. Other devices were signed out.")
                            } catch (e: Exception) {
                                error = if (e is ApiError) e.message else "Could not change the password."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (busy) "Saving…" else "Change password",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

package pk.auditgrid.audittest.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pk.auditgrid.audittest.model.AuditorDuty
import pk.auditgrid.audittest.model.Distributor
import pk.auditgrid.audittest.model.Shop
import pk.auditgrid.audittest.model.SubLocation
import pk.auditgrid.audittest.model.Territory
import pk.auditgrid.audittest.model.isLinkedToScope
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.pktToday

@Composable
fun DutiesScreen(
    duties: List<AuditorDuty>,
    territories: List<Territory> = emptyList(),
    subLocations: List<SubLocation> = emptyList(),
    shops: List<Shop> = emptyList(),
    distributors: List<Distributor> = emptyList(),
    syncing: Boolean,
    online: Boolean,
    lastSyncedAt: String,
    onSyncNow: () -> Unit,
    onStartDutyAudit: (AuditorDuty) -> Unit,
    onBrowseDutyPlaces: (AuditorDuty) -> Unit = {},
    onUpdateDutyStatus: (dutyId: String, newStatus: String) -> Unit,
    onToast: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDuties = remember(duties, selectedFilter, searchQuery) {
        duties.filter { d ->
            val matchesFilter = when (selectedFilter) {
                "Assigned" -> d.status.equals("Assigned", ignoreCase = true)
                "In Progress" -> d.status.equals("In Progress", ignoreCase = true)
                "Completed" -> d.status.equals("Completed", ignoreCase = true)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                d.code.contains(searchQuery, ignoreCase = true) ||
                d.title.contains(searchQuery, ignoreCase = true) ||
                d.cityName.contains(searchQuery, ignoreCase = true) ||
                d.cityNames.any { it.contains(searchQuery, ignoreCase = true) } ||
                d.territoryName.contains(searchQuery, ignoreCase = true) ||
                d.territoryNames.any { it.contains(searchQuery, ignoreCase = true) } ||
                d.distributorNames.any { it.contains(searchQuery, ignoreCase = true) } ||
                d.notes.contains(searchQuery, ignoreCase = true) ||
                d.scheduledDate.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    val dutiesByDate = remember(filteredDuties) {
        filteredDuties
            .groupBy { it.scheduledDate.ifBlank { "Unscheduled" } }
            .entries
            .sortedByDescending { it.key }
    }

    val assignedCount = remember(duties) { duties.count { it.status.equals("Assigned", ignoreCase = true) } }
    val inProgressCount = remember(duties) { duties.count { it.status.equals("In Progress", ignoreCase = true) } }
    val completedCount = remember(duties) { duties.count { it.status.equals("Completed", ignoreCase = true) } }
    val todayDate = remember { pktToday() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duties_screen_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Card: "Assigned Duties" (Matches Right Phone in AuditGrid Mockup)
        Surface(
            color = AgDutyBannerNavy,
            contentColor = Color.White,
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
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentTurnedIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Assigned Duties",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${territories.size} Localities • ${subLocations.size} Sub-Localities • ${shops.size} Shops",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        .testTag("duties_sync_button")
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
                                contentDescription = "Sync duties",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (syncing) "Syncing…" else "Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search by date, duty code, distributor...",
                    fontSize = 13.sp,
                    color = AgMutedText
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
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = AgMutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AgCardWhite,
                unfocusedContainerColor = AgCardWhite,
                focusedBorderColor = AgDutyBannerNavy,
                unfocusedBorderColor = AgBorder
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Filter Pills Row ("All (2)", "Assigned (2)", "In Progress", "Completed")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "All" to duties.size,
                "Assigned" to assignedCount,
                "In Progress" to inProgressCount,
                "Completed" to completedCount
            )
            filters.forEach { (label, count) ->
                val active = selectedFilter == label
                val displayLabel = if (count > 0 || label == "All") "$label ($count)" else label
                Surface(
                    color = if (active) AgDutyBannerNavy else AgCardWhite,
                    contentColor = if (active) Color.White else AgForeground,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (active) AgDutyBannerNavy else AgBorder),
                    modifier = Modifier
                        .clickable { selectedFilter = label }
                        .weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = displayLabel,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4. Duty Cards Grouped by Date
        if (dutiesByDate.isEmpty()) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AgBorder),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = AgIceBoxBg,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AssignmentTurnedIn,
                                contentDescription = null,
                                tint = AgDutyBannerNavy,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = if (duties.isEmpty()) "No Duties Assigned Yet" else "No matching duties",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgForeground
                    )
                    Text(
                        text = if (duties.isEmpty()) {
                            "When your manager assigns a duty on the Web Portal, all Distributor, Locality, Sub-Locality, and Shop details will sync here."
                        } else {
                            "Try clearing your search query or switching filters to view your duties."
                        },
                        fontSize = 12.sp,
                        color = AgMutedText,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        lineHeight = 17.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                dutiesByDate.forEach { (dateKey, dateDuties) ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Date Group Header Bar ("Today (2026-11-07)" | "2 Duties")
                        Surface(
                            color = Color(0xFFE3EEFC),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCDE0FA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = AgDutyBannerNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (dateKey == todayDate) "Today ($dateKey)" else dateKey,
                                        color = AgForeground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    color = AgDutyBannerNavy,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "${dateDuties.size} ${if (dateDuties.size == 1) "Duty" else "Duties"}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        var showAllForDate by remember(dateKey, dateDuties.size) { mutableStateOf(false) }
                        val visibleDateDuties = if (showAllForDate) dateDuties else dateDuties.take(4)
                        visibleDateDuties.forEach { duty ->
                            DutyCard(
                                duty = duty,
                                todayDate = todayDate,
                                allTerritories = territories,
                                allSubLocations = subLocations,
                                allShops = shops,
                                allDistributors = distributors,
                                onStartDutyAudit = onStartDutyAudit,
                                onBrowseDutyPlaces = onBrowseDutyPlaces,
                                onUpdateDutyStatus = onUpdateDutyStatus
                            )
                        }
                        if (dateDuties.size > 4) {
                            ShowMoreListToggle(
                                totalCount = dateDuties.size,
                                collapsedLimit = 4,
                                expanded = showAllForDate,
                                itemLabel = "Duties",
                                onToggle = { showAllForDate = !showAllForDate }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DutyCard(
    duty: AuditorDuty,
    todayDate: String,
    allTerritories: List<Territory>,
    allSubLocations: List<SubLocation>,
    allShops: List<Shop>,
    allDistributors: List<Distributor>,
    onStartDutyAudit: (AuditorDuty) -> Unit,
    onBrowseDutyPlaces: (AuditorDuty) -> Unit,
    onUpdateDutyStatus: (dutyId: String, newStatus: String) -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val dutyTerritoryIdSet = remember(duty, allTerritories) {
        when {
            duty.territoryIds.isNotEmpty() -> duty.territoryIds.toSet()
            duty.territoryId.isNotBlank() -> setOf(duty.territoryId)
            else -> allTerritories.map { it.id }.toSet()
        }
    }
    val dutyLocalities = remember(allTerritories, dutyTerritoryIdSet) {
        allTerritories.filter { dutyTerritoryIdSet.contains(it.id) }
    }
    val dutySubLocations = remember(allSubLocations, dutyTerritoryIdSet) {
        allSubLocations.filter { dutyTerritoryIdSet.contains(it.territoryId) }
    }
    val dutyShops = remember(allShops, dutyTerritoryIdSet) {
        allShops.filter { dutyTerritoryIdSet.contains(it.territoryId) }
    }
    val dutyDistributors = remember(allDistributors, dutyTerritoryIdSet, dutyLocalities) {
        allDistributors.filter { it.isLinkedToScope(dutyTerritoryIdSet, dutyLocalities) }
    }

    val orderBookerLabel = remember(duty.notes, dutyLocalities) {
        val fromNotes = Regex("""\[OB:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
            .find(duty.notes)?.groupValues?.getOrNull(1)?.trim() ?: ""
        when {
            fromNotes.isNotBlank() -> fromNotes
            dutyLocalities.any { it.obName.isNotBlank() || it.obCode.isNotBlank() } -> {
                val loc = dutyLocalities.first { it.obName.isNotBlank() || it.obCode.isNotBlank() }
                if (loc.obCode.isNotBlank() && loc.obName.isNotBlank()) "${loc.obCode} : ${loc.obName}"
                else loc.obName.ifBlank { loc.obCode }
            }
            else -> "Assigned Order Booker"
        }
    }
    val noteDistributorLabel = remember(duty.notes) {
        Regex("""\[Distributor:\s*([^\]|]+)""", RegexOption.IGNORE_CASE)
            .find(duty.notes)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    val statusColor = when (duty.status.lowercase()) {
        "completed" -> Color(0xFF047857)
        "in progress" -> Color(0xFFB45309)
        "cancelled" -> Color(0xFF475569)
        else -> AgRoyalBlue
    }

    val statusBg = when (duty.status.lowercase()) {
        "completed" -> Color(0xFFD1FAE5)
        "in progress" -> Color(0xFFFEF3C7)
        "cancelled" -> Color(0xFFF1F5F9)
        else -> Color(0xFFDCEBFF)
    }

    val citiesLabel = when {
        duty.cityNames.isNotEmpty() -> duty.cityNames.joinToString(", ")
        duty.cityName.isNotBlank() -> duty.cityName
        dutyLocalities.any { it.cityName.isNotBlank() } ->
            dutyLocalities.mapNotNull { it.cityName.takeIf(String::isNotBlank) }.distinct().joinToString(", ")
        else -> "Assigned Town"
    }
    val localitiesCount = dutyLocalities.size.coerceAtLeast(duty.territoryIds.size).coerceAtLeast(1)
    val shopsCount = dutyShops.size.coerceAtLeast(duty.shopsCount)
    val territoriesLabel = when {
        dutyLocalities.isNotEmpty() -> dutyLocalities.joinToString(" • ") {
            if (it.code.isNotBlank()) "${it.code} ${it.name}" else it.name
        }
        duty.territoryNames.isNotEmpty() -> duty.territoryNames.joinToString(" • ")
        duty.territoryName.isNotBlank() -> duty.territoryName
        else -> "All Assigned Localities"
    }
    val distributorLabel = when {
        noteDistributorLabel.isNotBlank() -> noteDistributorLabel
        dutyDistributors.isNotEmpty() -> dutyDistributors.joinToString(", ") {
            if (it.code.isNotBlank()) "${it.setupName} (${it.code})" else it.setupName
        }
        duty.distributorNames.isNotEmpty() -> duty.distributorNames.joinToString(", ")
        dutyLocalities.isNotEmpty() && dutyLocalities.first().distributorName.isNotBlank() -> {
            val first = dutyLocalities.first()
            if (first.distributorCode.isNotBlank()) "${first.distributorName} (${first.distributorCode})"
            else first.distributorName
        }
        else -> "Assigned Locality Distributor"
    }
    val titleText = duty.title.ifBlank { "Audit + Visit – $distributorLabel" }

    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AgBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Code Pill + Status Pill + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = AgSoftBlueBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = duty.code,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AgRoyalBlue,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = duty.status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = AgDutyBannerNavy,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = duty.scheduledDate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AgMutedText
                    )
                }
            }

            // Duty Title
            Text(
                text = titleText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AgForeground,
                lineHeight = 20.sp
            )

            // Structured Metadata Section with Progressive Disclosure
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StructuredMetaGridItem(
                        label = "Town",
                        value = citiesLabel,
                        modifier = Modifier.weight(1f)
                    )
                    StructuredMetaGridItem(
                        label = "Order Booker",
                        value = orderBookerLabel,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = AgFormBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = AgRoyalBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "DISTRIBUTOR",
                                color = AgMutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }
                        ExpandableTextWithMore(
                            text = distributorLabel,
                            maxLinesCollapsed = 1,
                            thresholdChars = 42,
                            textColor = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                }

                val localityChips = remember(territoriesLabel) {
                    territoriesLabel.split("•", ",", "\n")
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
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "LOCALITIES ($localitiesCount)",
                            color = AgMutedText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                    ExpandableChipGroup(
                        items = localityChips,
                        initialCount = 2,
                        emptyText = "All Assigned Localities"
                    )
                }
            }

            // 3 Summary Metric Boxes Row (Localities | Shops | Today Visit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DutyMetricBox(
                    icon = Icons.Default.DateRange,
                    topValue = localitiesCount.toString(),
                    bottomLabel = "Localities",
                    modifier = Modifier.weight(1f)
                )
                DutyMetricBox(
                    icon = Icons.Default.Storefront,
                    topValue = shopsCount.toString(),
                    bottomLabel = "Shops",
                    modifier = Modifier.weight(1f)
                )
                DutyMetricBox(
                    icon = Icons.Default.Schedule,
                    topValue = if (duty.scheduledDate == todayDate) "Today" else "Scheduled",
                    bottomLabel = "Visit",
                    modifier = Modifier.weight(1f)
                )
            }

            // Expandable Breakdown when "View Details" is toggled
            if (showDetails) {
                var showAllDutyLocalities by remember(duty.id) { mutableStateOf(false) }
                Surface(
                    color = AgIceBoxBg,
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
                        Text(
                            text = "Assigned Scope Breakdown (${dutyLocalities.size} Localities · ${dutySubLocations.size} Sub-Localities · $shopsCount Shops)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgDutyBannerNavy
                        )
                        if (dutyLocalities.isNotEmpty()) {
                            val visibleLocalities = if (showAllDutyLocalities) dutyLocalities else dutyLocalities.take(4)
                            visibleLocalities.forEach { loc ->
                                val subs = dutySubLocations.filter { it.territoryId == loc.id }
                                val locShops = dutyShops.filter { it.territoryId == loc.id }
                                Surface(
                                    color = AgCardWhite,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AgBorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${loc.code} ${loc.name}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgForeground,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${subs.size} Sub-Loc · ${locShops.size} Shops",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AgMutedText
                                        )
                                    }
                                }
                            }
                            if (dutyLocalities.size > 4) {
                                ShowMoreListToggle(
                                    totalCount = dutyLocalities.size,
                                    collapsedLimit = 4,
                                    expanded = showAllDutyLocalities,
                                    itemLabel = "Localities",
                                    onToggle = { showAllDutyLocalities = !showAllDutyLocalities }
                                )
                            }
                        }
                        if (duty.notes.isNotBlank()) {
                            Surface(
                                color = AgCardWhite,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AgBorderHairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("NOTES", color = AgMutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    ExpandableTextWithMore(
                                        text = duty.notes,
                                        maxLinesCollapsed = 2,
                                        thresholdChars = 80,
                                        textColor = AgForeground,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        if (duty.assignedByName.isNotBlank()) {
                            Text(
                                text = "Assigned by Manager: ${duty.assignedByName}",
                                fontSize = 11.sp,
                                color = AgMutedText
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onStartDutyAudit(duty) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgRoyalBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AssignmentTurnedIn,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start Audit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = { onBrowseDutyPlaces(duty) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AgDutyBannerNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = AgDutyBannerNavy,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Places & Shops",
                                    color = AgDutyBannerNavy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2 Pre-Adjusted Action Buttons Row: "View Details" & "Update"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showDetails = !showDetails },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgDutyBannerNavy),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("start_duty_audit_${duty.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showDetails) "Hide Details" else "View Details",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { showUpdateDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.2.dp, AgDutyBannerNavy),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AgCardWhite,
                        contentColor = AgDutyBannerNavy
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("update_duty_${duty.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = AgDutyBannerNavy,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update",
                        color = AgDutyBannerNavy,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showUpdateDialog) {
        Dialog(onDismissRequest = { showUpdateDialog = false }) {
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Update Duty (${duty.code})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgForeground
                            )
                            Text(
                                text = titleText,
                                fontSize = 12.sp,
                                color = AgMutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showUpdateDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = AgLightBorder)

                    Button(
                        onClick = {
                            showUpdateDialog = false
                            onStartDutyAudit(duty)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgRoyalBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start & Open Audit Form", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showUpdateDialog = false
                            onBrowseDutyPlaces(duty)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgDutyBannerNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = AgDutyBannerNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Browse Assigned Places & Shops",
                            color = AgDutyBannerNavy,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Change Duty Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgMutedText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Assigned", "In Progress", "Completed").forEach { statusOpt ->
                            val isCurrent = duty.status.equals(statusOpt, ignoreCase = true)
                            Surface(
                                color = if (isCurrent) AgDutyBannerNavy else AgIceBoxBg,
                                contentColor = if (isCurrent) Color.White else AgForeground,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isCurrent) AgDutyBannerNavy else AgBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateDutyStatus(duty.id, statusOpt)
                                        showUpdateDialog = false
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = statusOpt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DutyMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AgDutyBannerNavy,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = AgForeground.copy(alpha = 0.85f))) {
                    append(label)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AgForeground)) {
                    append(value)
                }
            },
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun DutyMetricBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    topValue: String,
    bottomLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AgIceBoxBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE7F7)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AgDutyBannerNavy,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = topValue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AgForeground,
                    maxLines = 1
                )
                Text(
                    text = bottomLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AgMutedText,
                    maxLines = 1
                )
            }
        }
    }
}

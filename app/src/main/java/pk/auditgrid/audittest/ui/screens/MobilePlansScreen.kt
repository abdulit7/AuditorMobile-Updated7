package pk.auditgrid.audittest.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*

private sealed class PlansViewState {
    data object ListView : PlansViewState()
    data object NewPlan : PlansViewState()
    data class Detail(val planId: String) : PlansViewState()
    data class RunAudit(val planId: String, val itemId: String) : PlansViewState()
}

private data class DraftPlanItem(
    val formId: String,
    val targetType: String,
    val targetId: String,
    val label: String,
    val meta: String = "",
    val shopIds: List<String>
)

private fun territoryMatchesDistributor(ter: Territory, dist: Distributor): Boolean {
    if (dist.territoryId == ter.id) return true
    if (ter.distributorId.isNotBlank() && ter.distributorId == dist.id) return true
    if (ter.distributorCode.isNotBlank() && dist.code.isNotBlank() &&
        ter.distributorCode.trim().equals(dist.code.trim(), ignoreCase = true)
    ) return true
    if (ter.distributorName.isNotBlank() && dist.setupName.isNotBlank() &&
        ter.distributorName.trim().equals(dist.setupName.trim(), ignoreCase = true)
    ) return true
    return false
}

@Composable
fun MobilePlansScreen(
    activeAuditor: AppUser,
    cities: List<City> = emptyList(),
    territories: List<Territory> = emptyList(),
    duties: List<AuditorDuty> = emptyList(),
    auditForms: List<AuditFormDefinition>,
    distributors: List<Distributor>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    apiClient: AuditGridApiClient,
    mePosition: DevicePosition?,
    refreshVersion: Int = 0,
    onPlansChanged: () -> Unit,
    onToast: (String) -> Unit,
    renderAuditForm: @Composable (plan: AuditPlan, item: PlanItem, onExit: () -> Unit) -> Unit
) {
    var plans by remember { mutableStateOf<List<AuditPlan>?>(null) }
    var viewState by remember { mutableStateOf<PlansViewState>(PlansViewState.ListView) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadPlans() {
        scope.launch {
            try {
                plans = apiClient.fetchPlans(inProgressOnly = false)
                error = ""
            } catch (e: Exception) {
                error = e.message ?: "Could not load plans."
            }
        }
    }

    LaunchedEffect(refreshVersion) {
        loadPlans()
    }

    fun coordsOf(item: PlanItem): Pair<Double, Double>? {
        return when (item.targetType) {
            "distributor" -> distributors.find { it.id == item.targetId }?.let {
                if (hasCoords(it.lat, it.lng)) it.lat to it.lng else null
            }
            "subLocation" -> subLocations.find { it.id == item.targetId }?.let {
                if (hasCoords(it.lat, it.lng)) it.lat to it.lng else null
            }
            else -> shops.find { it.id == item.targetId }?.let {
                if (hasCoords(it.lat, it.lng)) it.lat to it.lng else null
            }
        }
    }

    when (val current = viewState) {
        is PlansViewState.NewPlan -> {
            BackHandler { viewState = PlansViewState.ListView }
            PlanBuilderView(
                activeAuditor = activeAuditor,
                cities = cities,
                territories = territories,
                duties = duties,
                auditForms = auditForms,
                distributors = distributors,
                subLocations = subLocations,
                shops = shops,
                apiClient = apiClient,
                onCancel = { viewState = PlansViewState.ListView },
                onSaved = { savedPlan ->
                    plans = listOf(savedPlan) + (plans ?: emptyList())
                    viewState = PlansViewState.Detail(savedPlan.id)
                    onToast("Plan saved.")
                },
                onToast = onToast
            )
        }
        is PlansViewState.RunAudit -> {
            val plan = plans?.find { it.id == current.planId }
            val item = plan?.items?.find { it.id == current.itemId }
            if (plan == null || item == null) {
                viewState = PlansViewState.ListView
            } else {
                BackHandler {
                    loadPlans()
                    onPlansChanged()
                    viewState = PlansViewState.Detail(plan.id)
                }
                renderAuditForm(plan, item) {
                    loadPlans()
                    onPlansChanged()
                    viewState = PlansViewState.Detail(plan.id)
                }
            }
        }
        is PlansViewState.Detail -> {
            BackHandler { viewState = PlansViewState.ListView }
            val plan = plans?.find { it.id == current.planId }
            if (plan == null) {
                Text(
                    text = "Plan not found.",
                    color = AgMutedText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                PlanDetailView(
                    plan = plan,
                    mePosition = mePosition,
                    coordsOf = ::coordsOf,
                    apiClient = apiClient,
                    onBack = { viewState = PlansViewState.ListView },
                    onChanged = { updated ->
                        plans = (plans ?: emptyList()).map { if (it.id == updated.id) updated else it }
                        onPlansChanged()
                    },
                    onRemoved = { id ->
                        plans = (plans ?: emptyList()).filterNot { it.id == id }
                        viewState = PlansViewState.ListView
                        onPlansChanged()
                    },
                    onOpenItem = { itemId ->
                        viewState = PlansViewState.RunAudit(plan.id, itemId)
                    },
                    onToast = onToast
                )
            }
        }
        is PlansViewState.ListView -> {
            val today = pktToday()
            val allPlans = plans ?: emptyList()
            val groups = listOf(
                "TODAY" to allPlans.filter { it.planDate == today && it.status != "Cancelled" },
                "UPCOMING" to allPlans.filter { it.planDate > today && it.status != "Cancelled" }.sortedBy { it.planDate },
                "EARLIER" to allPlans.filter { it.planDate < today || (it.planDate == today && it.status == "Cancelled") }
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.Transparent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AgSectionHeaderGradient)
                            .padding(16.dp),
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
                                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
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
                                    text = "Daily Audit Plans",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${distributors.size} Distributors • ${subLocations.size} Sub-Localities • ${shops.size} Shops",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE)
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
                                .clickable { viewState = PlansViewState.NewPlan }
                                .testTag("create_plan_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "New Plan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Surface(
                        color = AgDestructiveBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = AgDestructiveDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (plans == null && error.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Loading plans…", color = AgMutedText, fontSize = 13.sp)
                    }
                }

                if (plans?.isEmpty() == true) {
                    Surface(
                        color = AgCardWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = AgMutedText,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No plans yet",
                                color = AgForeground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Plan your day: pick the forms and the distributors, areas or shops you will audit, then start the plan when you are out in the field.",
                                color = AgMutedText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                groups.forEach { (label, list) ->
                    if (list.isNotEmpty()) {
                        var showAllGroupPlans by remember(label, list.size) { mutableStateOf(false) }
                        val visibleGroupPlans = if (showAllGroupPlans) list else list.take(4)
                        Column(
                            modifier = Modifier.animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = label,
                                color = AgMutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            visibleGroupPlans.forEach { p ->
                                val done = p.items.count { it.status != "Pending" }
                                val progress = if (p.items.isNotEmpty()) done.toFloat() / p.items.size else 0f
                                Surface(
                                    color = AgCardWhite,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, AgBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewState = PlansViewState.Detail(p.id) }
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
                                            Text(
                                                text = p.title.ifBlank { "Audit plan" },
                                                color = AgForeground,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            PlanStatusBadge(p.status)
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = AgMutedText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = prettyDate(p.planDate),
                                                color = AgMutedText,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (p.items.isNotEmpty()) {
                                            val stopLabels = remember(p.items) { p.items.map { it.targetLabel } }
                                            ExpandableChipGroup(
                                                items = stopLabels,
                                                initialCount = 2,
                                                emptyText = "No stops"
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = AgAccentGreen,
                                            trackColor = AgLightBorder,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$done of ${p.items.size} audits done",
                                                color = AgMutedText,
                                                fontSize = 11.sp
                                            )
                                            val pct = (progress * 100).toInt()
                                            Text(
                                                text = "$pct%",
                                                color = AgRoyalBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (list.size > 4) {
                                ShowMoreListToggle(
                                    totalCount = list.size,
                                    collapsedLimit = 4,
                                    expanded = showAllGroupPlans,
                                    itemLabel = "Plans",
                                    onToggle = { showAllGroupPlans = !showAllGroupPlans }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "In Progress" -> AgWarningBg to AgWarningDark
        "Completed" -> AgAccentGreenBg to AgAccentGreenDark
        "Cancelled" -> Color(0xFFF1F5F9) to AgMutedText
        else -> AgMutedBg to AgPrimary
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PlanBuilderView(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    duties: List<AuditorDuty>,
    auditForms: List<AuditFormDefinition>,
    distributors: List<Distributor>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    apiClient: AuditGridApiClient,
    onCancel: () -> Unit,
    onSaved: (AuditPlan) -> Unit,
    onToast: (String) -> Unit
) {
    var date by remember { mutableStateOf(pktToday()) }
    var title by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<DraftPlanItem>>(emptyList()) }
    var addingForm by remember { mutableStateOf<AuditFormDefinition?>(null) }
    var auditTypeMode by remember { mutableStateOf("distributor") } // "distributor" or "retail"
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val submitKey = remember { newSubmitKey() }
    val scope = rememberCoroutineScope()

    val terrs = remember(activeAuditor) { activeAuditor.assignedTerritoryIds.toSet() }
    val activeForms = remember(auditForms) { auditForms.filter { it.status == "Active" } }

    fun formTarget(f: AuditFormDefinition): String =
        f.target.ifBlank { if (f.id == RETAIL_FORM_ID) "subLocation" else "distributor" }

    val availableTowns = remember(cities, activeAuditor, distributors, territories) {
        val assignedSet = activeAuditor.assignedCityIds.toSet()
        val assigned = cities.filter { assignedSet.contains(it.id) }
        if (assigned.isNotEmpty()) assigned
        else {
            val fromDataIds = (distributors.map { it.cityId } + territories.map { it.cityId }).toSet()
            val fromData = cities.filter { fromDataIds.contains(it.id) }
            if (fromData.isNotEmpty()) fromData else cities
        }
    }

    var selectedTownId by remember(availableTowns) {
        mutableStateOf(availableTowns.firstOrNull()?.id ?: "")
    }
    var townMenuOpen by remember { mutableStateOf(false) }

    val distributorsInTown = remember(distributors, territories, selectedTownId) {
        val activeDists = distributors.filter { it.status == "Active" }
        if (selectedTownId.isBlank()) activeDists
        else {
            val townTers = territories.filter { it.cityId == selectedTownId }
            activeDists.filter { d ->
                d.cityId == selectedTownId || townTers.any { t -> territoryMatchesDistributor(t, d) }
            }
        }
    }

    var selectedDistributorIds by remember(selectedTownId, distributorsInTown) {
        mutableStateOf(if (distributorsInTown.size == 1) setOf(distributorsInTown.first().id) else emptySet())
    }
    var selectedRetailDistributorId by remember(selectedTownId, distributorsInTown) {
        mutableStateOf(distributorsInTown.firstOrNull()?.id ?: "")
    }
    var retailDistMenuOpen by remember { mutableStateOf(false) }

    val activeRetailDistributor = remember(distributorsInTown, selectedRetailDistributorId) {
        distributorsInTown.find { it.id == selectedRetailDistributorId }
    }

    val distributorLocalities = remember(territories, selectedTownId, activeRetailDistributor) {
        if (activeRetailDistributor == null) emptyList()
        else {
            val townTers = if (selectedTownId.isNotBlank()) territories.filter { it.cityId == selectedTownId } else territories
            val matched = townTers.filter { t -> territoryMatchesDistributor(t, activeRetailDistributor) }
            if (matched.isNotEmpty()) matched else townTers
        }
    }

    val auditorAssignedLocalityIds = remember(activeAuditor, duties) {
        val s = activeAuditor.assignedTerritoryIds.toMutableSet()
        duties.filter { it.auditorId == activeAuditor.id && !it.status.equals("Cancelled", ignoreCase = true) }
            .forEach { d ->
                if (d.territoryId.isNotBlank()) s.add(d.territoryId)
                s.addAll(d.territoryIds)
            }
        s.toSet()
    }

    var localitySelectMode by remember { mutableStateOf("auditor") } // "auditor", "all", "choice"
    var selectedLocalityIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(activeRetailDistributor?.id, localitySelectMode, distributorLocalities) {
        if (activeRetailDistributor == null) {
            selectedLocalityIds = emptySet()
        } else if (localitySelectMode == "all") {
            selectedLocalityIds = distributorLocalities.map { it.id }.toSet()
        } else if (localitySelectMode == "auditor") {
            val assignedInDist = distributorLocalities.filter { auditorAssignedLocalityIds.contains(it.id) }.map { it.id }.toSet()
            selectedLocalityIds = if (assignedInDist.isNotEmpty()) assignedInDist else distributorLocalities.map { it.id }.toSet()
        }
    }

    val subLocationsInSelectedLocalities = remember(subLocations, selectedLocalityIds) {
        subLocations.filter { selectedLocalityIds.contains(it.territoryId) && it.status == "Active" }
    }

    var selectedSubLocationIds by remember(subLocationsInSelectedLocalities) {
        mutableStateOf(subLocationsInSelectedLocalities.map { it.id }.toSet())
    }
    var subShopSelections by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var openShopPickerSubId by remember { mutableStateOf("") }

    val currentAdding = addingForm
    if (currentAdding != null) {
        BackHandler { addingForm = null }
        val tType = formTarget(currentAdding)
        val taken = items.filter { it.formId == currentAdding.id }.map { it.targetId }.toSet()
        TargetPickerView(
            form = currentAdding,
            targetType = tType,
            terrs = terrs,
            distributors = distributors,
            subLocations = subLocations,
            shops = shops,
            taken = taken,
            onBack = { addingForm = null },
            onAdd = { picked ->
                items = items + picked.map { p ->
                    DraftPlanItem(
                        formId = currentAdding.id,
                        targetType = tType,
                        targetId = p.first,
                        label = p.second,
                        shopIds = p.third
                    )
                }
                addingForm = null
            }
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header Banner matching AgDutyBannerNavy
        Surface(
            color = AgDutyBannerNavy,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Create My Audit Plan", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Town → Distributor → Localities", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
        }

        // Date & Plan Title Card
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Plan Date (YYYY-MM-DD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(160) },
                    label = { Text("Plan Title (optional)") },
                    placeholder = { Text("e.g. Gujranwala Market & Stock Plan") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Cascading Builder Card: 1. Audit Type -> 2. Town -> 3. Distributor -> 4. Localities (By Auditor / All / By Choice)
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. SELECT AUDIT TYPE",
                    color = AgDutyBannerNavy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isDistMode = auditTypeMode == "distributor"
                    Surface(
                        color = if (isDistMode) AgDutyBannerNavy else AgBackground,
                        contentColor = if (isDistMode) Color.White else AgForeground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isDistMode) AgDutyBannerNavy else AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable { auditTypeMode = "distributor" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                            Text("Distributor Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val isRetailMode = auditTypeMode == "retail"
                    Surface(
                        color = if (isRetailMode) AgRoyalBlue else AgBackground,
                        contentColor = if (isRetailMode) Color.White else AgForeground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isRetailMode) AgRoyalBlue else AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable { auditTypeMode = "retail" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                            Text("Retail Market Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Step 2: Select Town
                if (availableTowns.isNotEmpty()) {
                    Text(
                        text = "2. SELECT TOWN",
                        color = AgDutyBannerNavy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val currentTown = availableTowns.find { it.id == selectedTownId }
                    Box {
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { townMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentTown?.let { "${it.name} (${it.code})" } ?: "Select Town",
                                    color = AgForeground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AgRoyalBlue)
                            }
                        }
                        DropdownMenu(
                            expanded = townMenuOpen,
                            onDismissRequest = { townMenuOpen = false }
                        ) {
                            availableTowns.forEach { town ->
                                DropdownMenuItem(
                                    text = { Text("${town.name} (${town.code})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedTownId = town.id
                                        townMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (auditTypeMode == "distributor") {
                    Text(
                        text = "3. SELECT DISTRIBUTOR FROM TOWN (${distributorsInTown.size})",
                        color = AgDutyBannerNavy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (distributorsInTown.isEmpty()) {
                        Text("No active distributors in this town.", color = AgMutedText, fontSize = 12.sp)
                    } else {
                        var showAllPlanDists by remember(selectedTownId) { mutableStateOf(false) }
                        val visiblePlanDists = if (showAllPlanDists) distributorsInTown else distributorsInTown.take(4)
                        visiblePlanDists.forEach { dist ->
                            val checked = selectedDistributorIds.contains(dist.id)
                            Surface(
                                color = if (checked) AgSoftBlueBg else AgBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (checked) AgRoyalBlue else AgBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDistributorIds = if (checked) selectedDistributorIds - dist.id else selectedDistributorIds + dist.id
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (checked) AgAccentGreen else AgBorder
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dist.setupName, color = AgForeground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${dist.code} • ${dist.distributorName}", color = AgMutedText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        if (distributorsInTown.size > 4) {
                            ShowMoreListToggle(
                                totalCount = distributorsInTown.size,
                                collapsedLimit = 4,
                                expanded = showAllPlanDists,
                                itemLabel = "Distributors",
                                onToggle = { showAllPlanDists = !showAllPlanDists }
                            )
                        }

                        Button(
                            onClick = {
                                if (selectedDistributorIds.isEmpty()) {
                                    error = "Select at least one Distributor from Town."
                                    return@Button
                                }
                                val townName = availableTowns.find { it.id == selectedTownId }?.name ?: "Town"
                                val existing = items.filter { it.targetType == "distributor" }.map { it.targetId }.toSet()
                                val toAdd = selectedDistributorIds.mapNotNull { did ->
                                    if (existing.contains(did)) null
                                    else distributors.find { it.id == did }?.let { d ->
                                        DraftPlanItem(
                                            formId = DISTRIBUTOR_FORM_ID,
                                            targetType = "distributor",
                                            targetId = d.id,
                                            label = "${d.setupName} (${d.code})",
                                            meta = "Town: $townName • Distributor Stock Audit",
                                            shopIds = emptyList()
                                        )
                                    }
                                }
                                if (toAdd.isEmpty()) {
                                    onToast("Selected distributor(s) already in plan.")
                                } else {
                                    items = items + toAdd
                                    error = ""
                                    onToast("Added ${toAdd.size} distributor audit(s) to plan.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgDutyBannerNavy),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Selected Distributor(s) to Plan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Retail Market Audit: Select Distributor by Town -> Select Localities (By Auditor / All / By Choice)
                    Text(
                        text = "3. SELECT DISTRIBUTOR BY TOWN",
                        color = AgDutyBannerNavy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box {
                        Surface(
                            color = AgBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AgBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { retailDistMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeRetailDistributor?.let { "${it.setupName} (${it.code})" } ?: "Select Distributor",
                                    color = AgForeground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AgRoyalBlue)
                            }
                        }
                        DropdownMenu(
                            expanded = retailDistMenuOpen,
                            onDismissRequest = { retailDistMenuOpen = false }
                        ) {
                            distributorsInTown.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text("${d.setupName} (${d.code})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedRetailDistributorId = d.id
                                        retailDistMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    if (activeRetailDistributor != null) {
                        Text(
                            text = "4. SELECT LOCALITIES OF DISTRIBUTOR (${selectedLocalityIds.size}/${distributorLocalities.size})",
                            color = AgDutyBannerNavy,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "auditor" to "By Auditor",
                                "all" to "All (${distributorLocalities.size})",
                                "choice" to "By Choice"
                            ).forEach { (modeKey, modeLabel) ->
                                val active = localitySelectMode == modeKey
                                Surface(
                                    color = if (active) AgRoyalBlue else AgBackground,
                                    contentColor = if (active) Color.White else AgForeground,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (active) AgRoyalBlue else AgBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { localitySelectMode = modeKey }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                        Text(modeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        var showAllPlanLocalities by remember(activeRetailDistributor?.id) { mutableStateOf(false) }
                        val visiblePlanLocalities = if (showAllPlanLocalities) distributorLocalities else distributorLocalities.take(4)
                        visiblePlanLocalities.forEach { ter ->
                            val isSelected = selectedLocalityIds.contains(ter.id)
                            val terSubCount = subLocations.count { it.territoryId == ter.id && it.status == "Active" }
                            val terShopCount = shops.count { it.territoryId == ter.id && it.status == "Active" }
                            Surface(
                                color = if (isSelected) AgSoftBlueBg else AgBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) AgRoyalBlue else AgBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        localitySelectMode = "choice"
                                        selectedLocalityIds = if (isSelected) selectedLocalityIds - ter.id else selectedLocalityIds + ter.id
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) AgAccentGreen else AgBorder,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ter.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${ter.code} • $terSubCount Sub-Localities • $terShopCount Shops", color = AgMutedText, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        if (distributorLocalities.size > 4) {
                            ShowMoreListToggle(
                                totalCount = distributorLocalities.size,
                                collapsedLimit = 4,
                                expanded = showAllPlanLocalities,
                                itemLabel = "Localities",
                                onToggle = { showAllPlanLocalities = !showAllPlanLocalities }
                            )
                        }

                        // Step 5: Sub-Localities & Shops in Selected Localities
                        if (subLocationsInSelectedLocalities.isNotEmpty()) {
                            HorizontalDivider(color = AgLightBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "5. SUB-LOCALITIES & SHOPS (${selectedSubLocationIds.size}/${subLocationsInSelectedLocalities.size})",
                                    color = AgDutyBannerNavy,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                TextButton(
                                    onClick = {
                                        val allIds = subLocationsInSelectedLocalities.map { it.id }.toSet()
                                        selectedSubLocationIds = if (selectedSubLocationIds.size == allIds.size) emptySet() else allIds
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (selectedSubLocationIds.size == subLocationsInSelectedLocalities.size) "Unselect All" else "Select All",
                                        color = AgRoyalBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            var showAllPlanSubs by remember(selectedLocalityIds) { mutableStateOf(false) }
                            val visiblePlanSubs = if (showAllPlanSubs) subLocationsInSelectedLocalities else subLocationsInSelectedLocalities.take(4)
                            Column(
                                modifier = Modifier.animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                visiblePlanSubs.forEach { sub ->
                                    val subChecked = selectedSubLocationIds.contains(sub.id)
                                    val areaShops = shops.filter { it.subLocationId == sub.id && it.status == "Active" }
                                    val chosenShops = subShopSelections[sub.id] ?: emptyList()
                                    val terName = territories.find { it.id == sub.territoryId }?.name ?: "Locality"

                                    Surface(
                                        color = if (subChecked) AgCardWhite else AgBackground,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (subChecked) AgAccentGreen else AgBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedSubLocationIds = if (subChecked) {
                                                            selectedSubLocationIds - sub.id
                                                        } else {
                                                            selectedSubLocationIds + sub.id
                                                        }
                                                    }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (subChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = null,
                                                    tint = if (subChecked) AgAccentGreenDark else AgMutedText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sub.name, color = AgForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = "$terName • ${areaShops.size} shops" +
                                                            if (chosenShops.isNotEmpty()) " • ${chosenShops.size} marked" else "",
                                                        color = AgMutedText,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            if (subChecked && areaShops.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                openShopPickerSubId = if (openShopPickerSubId == sub.id) "" else sub.id
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = (if (chosenShops.isNotEmpty()) "Marked ${chosenShops.size} shops" else "All ${areaShops.size} shops") +
                                                                    " — " + (if (openShopPickerSubId == sub.id) "hide shops" else "mark specific shops"),
                                                                color = AgRoyalBlue,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        if (openShopPickerSubId == sub.id) {
                                                            TextButton(
                                                                onClick = {
                                                                    val allShopIds = areaShops.map { it.id }
                                                                    val nextChosen = if (chosenShops.size == allShopIds.size) emptyList() else allShopIds
                                                                    subShopSelections = subShopSelections + (sub.id to nextChosen)
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (chosenShops.size == areaShops.size) "Clear Shops" else "Mark All",
                                                                    color = AgAccentGreenDark,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (openShopPickerSubId == sub.id) {
                                                        Surface(
                                                            color = AgBackground,
                                                            shape = RoundedCornerShape(8.dp),
                                                            border = BorderStroke(1.dp, AgLightBorder),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column {
                                                                areaShops.forEach { sh ->
                                                                    val isShopChecked = chosenShops.contains(sh.id)
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                val nextList = if (isShopChecked) chosenShops - sh.id else chosenShops + sh.id
                                                                                subShopSelections = subShopSelections + (sub.id to nextList)
                                                                            }
                                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (isShopChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                                            contentDescription = null,
                                                                            tint = if (isShopChecked) AgRoyalBlue else AgMutedText,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(sh.name, color = AgForeground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                                            Text("${sh.code} • ${sh.ownerName}", color = AgMutedText, fontSize = 9.sp)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (subLocationsInSelectedLocalities.size > 4) {
                                    ShowMoreListToggle(
                                        totalCount = subLocationsInSelectedLocalities.size,
                                        collapsedLimit = 4,
                                        expanded = showAllPlanSubs,
                                        itemLabel = "Sub-Localities",
                                        onToggle = { showAllPlanSubs = !showAllPlanSubs }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (selectedSubLocationIds.isEmpty()) {
                                    error = "Select at least one Locality with Sub-Localities to audit."
                                    return@Button
                                }
                                val townName = availableTowns.find { it.id == selectedTownId }?.name ?: "Town"
                                val nextItems = items.toMutableList()
                                var countChanged = 0

                                selectedSubLocationIds.forEach { sid ->
                                    val sub = subLocations.find { it.id == sid } ?: return@forEach
                                    val terName = territories.find { it.id == sub.territoryId }?.name ?: "Locality"
                                    val totalShopsCount = shops.count { it.subLocationId == sub.id && it.status == "Active" }
                                    val chosenShops = subShopSelections[sub.id] ?: emptyList()

                                    val existingIdx = nextItems.indexOfFirst { it.targetType == "subLocation" && it.targetId == sub.id }
                                    if (existingIdx >= 0) {
                                        val existingItem = nextItems[existingIdx]
                                        val mergedShops = if (chosenShops.isNotEmpty()) {
                                            (existingItem.shopIds + chosenShops).distinct()
                                        } else emptyList()
                                        nextItems[existingIdx] = existingItem.copy(
                                            meta = "$townName • ${activeRetailDistributor.setupName} • " +
                                                if (mergedShops.isNotEmpty()) "${mergedShops.size} chosen shops" else "$totalShopsCount shops",
                                            shopIds = mergedShops
                                        )
                                        countChanged++
                                    } else {
                                        nextItems.add(
                                            DraftPlanItem(
                                                formId = RETAIL_FORM_ID,
                                                targetType = "subLocation",
                                                targetId = sub.id,
                                                label = "${sub.name} ($terName)",
                                                meta = "$townName • ${activeRetailDistributor.setupName} • " +
                                                    if (chosenShops.isNotEmpty()) "${chosenShops.size} chosen shops" else "$totalShopsCount shops",
                                                shopIds = chosenShops
                                            )
                                        )
                                        countChanged++
                                    }
                                }

                                if (countChanged == 0) {
                                    onToast("No sub-localities or shops to add.")
                                } else {
                                    items = nextItems
                                    error = ""
                                    onToast("Added/updated $countChanged sub-locality audit(s) in plan! You can select another locality/sub-locality to add more.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgRoyalBlue),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Selected Sub-Localities / Shops (${selectedSubLocationIds.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(
            text = "AUDITS IN THIS PLAN (${items.size})",
            color = AgMutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        var showAllDraftItems by remember { mutableStateOf(false) }
        val visibleDraftItems = if (showAllDraftItems) items else items.take(4)
        visibleDraftItems.forEachIndexed { idx, i ->
            val formName = activeForms.find { it.id == i.formId }?.shortTitle ?: i.formId
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(i.label, color = AgForeground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = i.meta.ifBlank { if (i.shopIds.isNotEmpty()) "$formName · ${i.shopIds.size} shops" else formName },
                            color = AgMutedText,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { items = items.filterIndexed { k, _ -> k != idx } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AgDestructive)
                    }
                }
            }
        }
        if (items.size > 4) {
            ShowMoreListToggle(
                totalCount = items.size,
                collapsedLimit = 4,
                expanded = showAllDraftItems,
                itemLabel = "Planned Audits",
                onToggle = { showAllDraftItems = !showAllDraftItems }
            )
        }

        if (error.isNotEmpty()) {
            Surface(
                color = AgDestructiveBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = AgDestructiveDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = {
                if (items.isEmpty()) {
                    error = "Add at least one audit to the plan."
                    return@Button
                }
                saving = true
                error = ""
                scope.launch {
                    try {
                        val saved = apiClient.createPlan(
                            clientRequestId = submitKey,
                            planDate = date,
                            title = title,
                            items = items.map {
                                PlanItem(
                                    id = "",
                                    formId = it.formId,
                                    formTitle = "",
                                    targetType = it.targetType,
                                    targetId = it.targetId,
                                    targetLabel = it.label,
                                    shopIds = it.shopIds
                                )
                            }
                        )
                        onSaved(saved)
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save the plan."
                        onToast("Plan not saved.")
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AgAccentGreen),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("save_plan_button")
        ) {
            Text(
                text = if (saving) "Saving Plan…" else "Save Audit Plan (${items.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TargetPickerView(
    form: AuditFormDefinition,
    targetType: String,
    terrs: Set<String>,
    distributors: List<Distributor>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    taken: Set<String>,
    onBack: () -> Unit,
    onAdd: (List<Triple<String, String, List<String>>>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var openAreaId by remember { mutableStateOf("") }

    val options = remember(targetType, terrs, distributors, subLocations, shops) {
        when (targetType) {
            "subLocation" -> subLocations
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { s ->
                    val count = shops.count { it.subLocationId == s.id && it.status == "Active" }
                    Triple(s.id, s.name, "$count shops")
                }
            "shop" -> shops
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { Triple(it.id, it.name, it.code) }
            else -> distributors
                .filter { terrs.contains(it.territoryId) && it.status == "Active" }
                .map { Triple(it.id, it.setupName, it.code) }
        }
    }

    val shown = options.filter {
        !taken.contains(it.first) && (it.second + " " + it.third).contains(query, ignoreCase = true)
    }.take(60)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AgPrimary)
            }
            Column {
                Text(
                    text = form.shortTitle.ifBlank { form.title },
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose targets",
                    color = AgMutedText,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        shown.forEach { (id, label, sub) ->
            val isChosen = selected.containsKey(id)
            val areaShops = if (targetType == "subLocation") {
                shops.filter { it.subLocationId == id && it.status == "Active" }
            } else emptyList()

            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isChosen) AgAccentGreen else AgBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (isChosen) selected - id else selected + (id to emptyList())
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isChosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isChosen) AgAccentGreen else AgBorder
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = AgForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val chosenShops = selected[id] ?: emptyList()
                            Text(
                                text = if (isChosen && chosenShops.isNotEmpty()) "$sub · ${chosenShops.size} chosen" else sub,
                                color = AgMutedText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isChosen && targetType == "subLocation" && areaShops.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            val chosenShops = selected[id] ?: emptyList()
                            TextButton(
                                onClick = { openAreaId = if (openAreaId == id) "" else id }
                            ) {
                                Text(
                                    text = (if (chosenShops.isNotEmpty()) "Only ${chosenShops.size} shops" else "All shops") +
                                        " — " + (if (openAreaId == id) "hide list" else "choose specific shops"),
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (openAreaId == id) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AgLightBorder)
                                ) {
                                    Column {
                                        areaShops.forEach { sh ->
                                            val checked = chosenShops.contains(sh.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val nextList = if (checked) chosenShops - sh.id else chosenShops + sh.id
                                                        selected = selected + (id to nextList)
                                                    }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (checked) AgAccentGreen else AgBorder,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(sh.name, fontSize = 12.sp, color = AgForeground)
                                            }
                                        }
                                    }
                                }
                                if (subLocationsInSelectedLocalities.size > 4) {
                                    ShowMoreListToggle(
                                        totalCount = subLocationsInSelectedLocalities.size,
                                        collapsedLimit = 4,
                                        expanded = showAllPlanSubs,
                                        itemLabel = "Sub-Localities",
                                        onToggle = { showAllPlanSubs = !showAllPlanSubs }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val res = selected.map { (id, shopIds) ->
                    val label = options.find { it.first == id }?.second ?: id
                    Triple(id, label, shopIds)
                }
                onAdd(res)
            },
            enabled = selected.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Text(
                text = "Add ${if (selected.isNotEmpty()) selected.size else ""} to plan",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlanDetailView(
    plan: AuditPlan,
    mePosition: DevicePosition?,
    coordsOf: (PlanItem) -> Pair<Double, Double>?,
    apiClient: AuditGridApiClient,
    onBack: () -> Unit,
    onChanged: (AuditPlan) -> Unit,
    onRemoved: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationStatus by rememberMobileLocationStatus(context)
    var busy by remember { mutableStateOf(false) }
    var skippingItemId by remember { mutableStateOf<String?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var showMap by remember { mutableStateOf(false) }
    var pendingItemToOpen by remember { mutableStateOf<String?>(null) }
    var pendingStartPlan by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    val planPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && isDeviceLocationEnabled(context)) {
            showLocationDialog = false
            pendingItemToOpen?.let { targetItem ->
                pendingItemToOpen = null
                onOpenItem(targetItem)
            }
        } else {
            showLocationDialog = true
            onToast("Mobile location must be ON to start an audit.")
        }
    }

    LaunchedEffect(locationStatus.isReady) {
        if (locationStatus.isReady && showLocationDialog) {
            showLocationDialog = false
            val targetItem = pendingItemToOpen
            if (targetItem != null) {
                pendingItemToOpen = null
                onOpenItem(targetItem)
            } else if (pendingStartPlan) {
                pendingStartPlan = false
                busy = true
                try {
                    onChanged(apiClient.startPlan(plan.id))
                } catch (e: Exception) {
                    onToast(e.message ?: "Could not start plan.")
                } finally {
                    busy = false
                }
            }
        }
    }

    if (showLocationDialog) {
        MobileLocationRequiredDialog(
            status = locationStatus,
            onTurnOnLocation = { openDeviceLocationSettings(context) },
            onGrantPermission = {
                planPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onCheckAgain = {
                val fresh = getMobileLocationStatus(context)
                if (fresh.isReady) {
                    showLocationDialog = false
                    val targetItem = pendingItemToOpen
                    if (targetItem != null) {
                        pendingItemToOpen = null
                        onOpenItem(targetItem)
                    } else if (pendingStartPlan) {
                        pendingStartPlan = false
                        busy = true
                        scope.launch {
                            try {
                                onChanged(apiClient.startPlan(plan.id))
                            } catch (e: Exception) {
                                onToast(e.message ?: "Could not start plan.")
                            } finally {
                                busy = false
                            }
                        }
                    }
                } else {
                    onToast("Mobile location is still OFF. Please turn on mobile location first.")
                }
            },
            onDismiss = {
                showLocationDialog = false
                pendingItemToOpen = null
                pendingStartPlan = false
            }
        )
    }

    val done = plan.items.count { it.status != "Pending" }
    val progress = if (plan.items.isNotEmpty()) done.toFloat() / plan.items.size else 0f
    val active = plan.status == "Planned" || plan.status == "In Progress"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AgPrimary)
            }
            Column {
                Text(
                    text = plan.title.ifBlank { "Audit plan" },
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = prettyDate(plan.planDate),
                    color = AgMutedText,
                    fontSize = 12.sp
                )
            }
        }

        MobileLocationOffBanner(
            status = locationStatus,
            onTurnOnLocation = { openDeviceLocationSettings(context) },
            onGrantPermission = {
                planPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgBorder),
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
                    PlanStatusBadge(plan.status)
                    Text("$done of ${plan.items.size} done", color = AgMutedText, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = AgAccentGreen,
                    trackColor = AgLightBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
                if (plan.status == "Planned") {
                    Button(
                        onClick = {
                            if (!isMobileLocationReadyForAudit(context)) {
                                pendingStartPlan = true
                                showLocationDialog = true
                                onToast("Mobile location is OFF. Turn on mobile location to start an audit plan.")
                                return@Button
                            }
                            busy = true
                            scope.launch {
                                try {
                                    onChanged(apiClient.startPlan(plan.id))
                                } catch (e: Exception) {
                                    onToast(e.message ?: "Could not start plan.")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgAccentGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("start_plan_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start plan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Show stops on map button & visual stop map
        OutlinedButton(
            onClick = { showMap = !showMap },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AgCardWhite,
                contentColor = AgPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (showMap) "Hide map" else "Show stops on map", fontWeight = FontWeight.Bold)
        }

        if (showMap) {
            PlanStopsMapCard(
                plan = plan,
                mePosition = mePosition,
                coordsOf = coordsOf,
                onNavigateTo = { lat, lng ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directionsUrl(lat, lng)))
                    runCatching { context.startActivity(intent) }
                }
            )
        }

        // Items Checklist with Progressive Disclosure
        var showAllPlanStops by remember(plan.id) { mutableStateOf(false) }
        val visiblePlanStops = if (showAllPlanStops) plan.items else plan.items.take(5)
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visiblePlanStops.forEach { item ->
                val isDone = item.status == "Done"
                val isSkipped = item.status == "Skipped"
                val coords = coordsOf(item)
                Surface(
                    color = AgCardWhite,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isDone) AgAccentGreen.copy(alpha = 0.5f) else AgBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isDone -> Icons.Default.CheckCircle
                                    isSkipped -> Icons.Default.Cancel
                                    else -> Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = item.status,
                                tint = when {
                                    isDone -> AgAccentGreen
                                    isSkipped -> AgWarning
                                    else -> AgBorder
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.targetLabel,
                                    color = AgForeground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.formTitle + if (item.shopIds.isNotEmpty()) " · ${item.shopIds.size} shops selected" else "",
                                    color = AgMutedText,
                                    fontSize = 11.sp
                                )
                                item.reportCode?.let {
                                    Text(
                                        text = it,
                                        color = AgAccentGreenDark,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                item.skipReason?.let {
                                    Text(
                                        text = "Skipped: $it",
                                        color = AgWarning,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (plan.status == "In Progress" && item.status == "Pending") {
                            if (skippingItemId == item.id) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = skipReason,
                                        onValueChange = { skipReason = it },
                                        placeholder = { Text("Why are you skipping? (min 3 letters)", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                busy = true
                                                scope.launch {
                                                    try {
                                                        val updated = apiClient.skipPlanItem(plan.id, item.id, skipReason)
                                                        onChanged(updated)
                                                        skippingItemId = null
                                                        skipReason = ""
                                                    } catch (e: Exception) {
                                                        onToast(e.message ?: "Could not skip item.")
                                                    } finally {
                                                        busy = false
                                                    }
                                                }
                                            },
                                            enabled = !busy && skipReason.trim().length >= 3,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AgWarning),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Text("Confirm skip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { skippingItemId = null },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (coords != null) {
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(directionsUrl(coords.first, coords.second))
                                                )
                                                runCatching { context.startActivity(intent) }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, AgSecondaryBlue.copy(alpha = 0.4f)),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Navigation,
                                                contentDescription = "Directions to ${item.targetLabel}",
                                                tint = AgSecondaryBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (!isMobileLocationReadyForAudit(context)) {
                                                pendingItemToOpen = item.id
                                                showLocationDialog = true
                                                onToast("Mobile location is OFF. Turn on mobile location to start an audit.")
                                            } else {
                                                onOpenItem(item.id)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("start_audit_item_${item.id}")
                                    ) {
                                        Text("Start audit", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            skippingItemId = item.id
                                            skipReason = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AgBorder),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Skip this audit",
                                            tint = AgMutedText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (plan.items.size > 5) {
                ShowMoreListToggle(
                    totalCount = plan.items.size,
                    collapsedLimit = 5,
                    expanded = showAllPlanStops,
                    itemLabel = "Stops",
                    onToggle = { showAllPlanStops = !showAllPlanStops }
                )
            }
        }

        if (plan.status == "Planned") {
            Text(
                text = "Tap Start plan when you begin your round — each audit then opens already filled with its distributor, area or shops.",
                color = AgMutedText,
                fontSize = 12.sp
            )
        }

        if (active || plan.status == "Cancelled") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.status != "Cancelled") {
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    onChanged(apiClient.cancelPlan(plan.id))
                                } catch (e: Exception) {
                                    onToast(e.message ?: "Could not cancel plan.")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel plan", color = AgMutedText, fontWeight = FontWeight.Bold)
                    }
                }
                if (plan.status != "In Progress") {
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    apiClient.deletePlan(plan.id)
                                    onRemoved(plan.id)
                                } catch (e: Exception) {
                                    onToast(e.message ?: "Could not delete plan.")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Delete", color = AgDestructive, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStopsMapCard(
    plan: AuditPlan,
    mePosition: DevicePosition?,
    coordsOf: (PlanItem) -> Pair<Double, Double>?,
    onNavigateTo: (Double, Double) -> Unit
) {
    val stops = remember(plan.items) {
        plan.items.mapNotNull { item ->
            coordsOf(item)?.let { Triple(item, it.first, it.second) }
        }
    }
    val missing = plan.items.size - stops.size

    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cartographic Canvas Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEDF2F7))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepX = size.width / 6f
                    val stepY = size.height / 4f
                    for (i in 1..5) {
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(stepX * i, 0f),
                            end = Offset(stepX * i, size.height),
                            strokeWidth = 1.5f
                        )
                    }
                    for (j in 1..3) {
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(0f, stepY * j),
                            end = Offset(size.width, stepY * j),
                            strokeWidth = 1.5f
                        )
                    }
                    if (stops.isNotEmpty()) {
                        val minLat = stops.minOf { it.second } - 0.005
                        val maxLat = stops.maxOf { it.second } + 0.005
                        val minLng = stops.minOf { it.third } - 0.005
                        val maxLng = stops.maxOf { it.third } + 0.005

                        stops.forEach { (item, lat, lng) ->
                            val x = ((lng - minLng) / (maxLng - minLng).coerceAtLeast(0.0001)).toFloat() *
                                (size.width * 0.75f) + size.width * 0.12f
                            val y = (1f - ((lat - minLat) / (maxLat - minLat).coerceAtLeast(0.0001)).toFloat()) *
                                (size.height * 0.7f) + size.height * 0.15f
                            val pinColor = if (item.status == "Pending") Color(0xFFDC2626) else Color(0x88DC2626)
                            drawCircle(color = Color.White, radius = 16f, center = Offset(x, y))
                            drawCircle(color = pinColor, radius = 12f, center = Offset(x, y))
                        }
                    }
                    mePosition?.let {
                        drawCircle(
                            color = Color(0x332563EB),
                            radius = 28f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                        drawCircle(
                            color = Color(0xFF2563EB),
                            radius = 10f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    }
                }
            }

            stops.forEach { (item, lat, lng) ->
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
                                .size(10.dp)
                                .background(
                                    if (item.status == "Pending") AgDestructive else AgDestructive.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                        Text(
                            text = "${item.targetLabel} · ${item.status}",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(onClick = { onNavigateTo(lat, lng) }) {
                        Text("Directions ↗", color = AgPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (missing > 0) {
                Text(
                    text = "$missing stop${if (missing == 1) " has" else "s have"} no registered location yet.",
                    color = AgWarningDark,
                    fontSize = 11.sp
                )
            }
        }
    }
}

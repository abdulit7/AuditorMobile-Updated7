package pk.auditgrid.audittest.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.pktToday
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    distributors: List<Distributor>,
    duties: List<AuditorDuty>,
    completedTotalCount: Int,
    pendingOutboxCount: Int,
    syncing: Boolean,
    onOpenAudit: () -> Unit,
    onOpenDuties: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenShops: () -> Unit,
    onStartDutyAudit: (AuditorDuty) -> Unit,
    onSyncNow: () -> Unit
) {
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

    val assignedDutiesCount = duties.size
    val inProgressDutiesCount = duties.count { it.status.equals("In Progress", ignoreCase = true) }
    val pendingDutiesCount = duties.count {
        it.status.equals("Assigned", ignoreCase = true) ||
            it.status.equals("Pending", ignoreCase = true)
    } + pendingOutboxCount
    val completedDutiesCount = duties.count { it.status.equals("Completed", ignoreCase = true) }
    val effectiveCompletedCount = max(completedTotalCount, completedDutiesCount)

    val primaryTownName = remember(scopedCities, scopedTerritories, duties) {
        when {
            scopedCities.isNotEmpty() -> scopedCities.first().name
            duties.any { it.cityName.isNotBlank() } -> duties.first { it.cityName.isNotBlank() }.cityName
            scopedTerritories.any { it.cityName.isNotBlank() } ->
                scopedTerritories.first { it.cityName.isNotBlank() }.cityName
            else -> "Assigned Town"
        }
    }

    val primaryDistributorLabel = remember(scopedDistributors, scopedTerritories, duties) {
        when {
            duties.any { it.distributorNames.isNotEmpty() } -> {
                duties.first { it.distributorNames.isNotEmpty() }.distributorNames.first()
            }
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
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("dashboard_screen_container"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 2x2 Pastel Gradient KPI Bento Grid (Matching Sample Image)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProKpiPastelCard(
                    countText = assignedDutiesCount.toString(),
                    label = "Assigned Duties",
                    icon = Icons.Default.AssignmentTurnedIn,
                    iconBgColor = Color(0xFF1A5CE5),
                    cardBrush = AgKpiBlueBrush,
                    borderColor = Color(0xFFC7DCFF),
                    chevronBgColor = Color(0xFFCFE1FF),
                    onClick = onOpenDuties,
                    modifier = Modifier.weight(1f)
                )
                ProKpiPastelCard(
                    countText = effectiveCompletedCount.toString(),
                    label = "Completed",
                    icon = Icons.Default.CheckCircle,
                    iconBgColor = Color(0xFF12A150),
                    cardBrush = AgKpiGreenBrush,
                    borderColor = Color(0xFFBDF0D4),
                    chevronBgColor = Color(0xFFC4F2D9),
                    onClick = onOpenCompleted,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProKpiPastelCard(
                    countText = pendingDutiesCount.toString(),
                    label = "Pending",
                    icon = Icons.Default.Schedule,
                    iconBgColor = Color(0xFFF07B12),
                    cardBrush = AgKpiAmberBrush,
                    borderColor = Color(0xFFFFD8B0),
                    chevronBgColor = Color(0xFFFFDFBA),
                    onClick = if (pendingOutboxCount > 0) onOpenCompleted else onOpenDuties,
                    modifier = Modifier.weight(1f)
                )
                ProKpiPastelCard(
                    countText = scopedShops.size.toString(),
                    label = "Retail Shops",
                    icon = Icons.Default.Storefront,
                    iconBgColor = Color(0xFF7C3AED),
                    cardBrush = AgKpiPurpleBrush,
                    borderColor = Color(0xFFDCC7FF),
                    chevronBgColor = Color(0xFFE2D0FF),
                    onClick = onOpenShops,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. "Today's Visit Schedule" Section Card (Matching Sample Image)
        var showAllScheduleItems by remember(duties.size) { mutableStateOf(false) }
        val activeOrUpcomingDuties = remember(duties) {
            duties.sortedByDescending { it.dutyDate }
        }
        val visibleScheduleDuties = if (showAllScheduleItems || activeOrUpcomingDuties.size <= 2) {
            activeOrUpcomingDuties
        } else {
            activeOrUpcomingDuties.take(2)
        }

        ProSectionCard(
            title = "Today's Visit Schedule",
            icon = Icons.Default.DateRange,
            actionLabel = "View All →",
            onActionClick = onOpenDuties
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (visibleScheduleDuties.isEmpty()) {
                    // Fallback live scope schedule item when no explicit duty record is assigned yet
                    val todayParts = remember { parseDateBadgeParts(pktToday()) }
                    ScheduleVisitRow(
                        dayOfWeek = todayParts.first,
                        dayNum = todayParts.second,
                        monthYear = todayParts.third,
                        title = "Retail & Distributor Audit",
                        townText = primaryTownName,
                        distributorText = primaryDistributorLabel,
                        timeText = "09:00 AM – 05:00 PM (Field Shift)",
                        statusLabel = "Active",
                        localities = scopedTerritories.map { "${it.name} (${it.code})" },
                        onActionClick = onOpenAudit
                    )
                } else {
                    visibleScheduleDuties.forEachIndexed { idx, duty ->
                        val dateParts = remember(duty.dutyDate, duty.scheduledDate) {
                            parseDateBadgeParts(duty.scheduledDate)
                        }
                        val dutyTown = duty.cityName.ifBlank {
                            duty.cityNames.firstOrNull() ?: primaryTownName
                        }
                        val dutyDist = duty.distributorNames.firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: primaryDistributorLabel
                        val dutyTitle = duty.title.ifBlank { "Retail & Distributor Audit" }
                        val isCompleted = duty.status.equals("Completed", ignoreCase = true)
                        ScheduleVisitRow(
                            dayOfWeek = dateParts.first,
                            dayNum = dateParts.second,
                            monthYear = dateParts.third,
                            title = dutyTitle,
                            townText = dutyTown,
                            distributorText = dutyDist,
                            timeText = "10:00 AM – 04:00 PM • ${duty.code.ifBlank { "Field Visit" }}",
                            statusLabel = if (isCompleted) "Completed" else "Active",
                            localities = duty.territoryNames,
                            onActionClick = { onStartDutyAudit(duty) }
                        )
                        if (idx < visibleScheduleDuties.lastIndex) {
                            HorizontalDivider(color = AgBorderHairline)
                        }
                    }

                    ShowMoreListToggle(
                        totalCount = activeOrUpcomingDuties.size,
                        collapsedLimit = 2,
                        expanded = showAllScheduleItems,
                        itemLabel = "Scheduled Visits",
                        onToggle = { showAllScheduleItems = !showAllScheduleItems }
                    )
                }

                // Route Plans quick bar inside Today's Visit Schedule
                Surface(
                    color = AgIceBoxBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AgSoftBlueBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenPlans)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = AgRoyalBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Daily Audit Route Plans & Map Stops",
                                color = AgPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Open Plans",
                                color = AgRoyalBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AgRoyalBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. "Audit Progress" Section Card with Donut Ring Chart (Matching Sample Image)
        var showAllTimeProgress by remember { mutableStateOf(false) }
        val totalProgressItems = max(1, effectiveCompletedCount + inProgressDutiesCount + pendingDutiesCount)
        val successRatePercent = remember(effectiveCompletedCount, inProgressDutiesCount, pendingDutiesCount) {
            val denom = effectiveCompletedCount + inProgressDutiesCount + pendingDutiesCount
            if (denom <= 0) {
                0
            } else {
                ((effectiveCompletedCount.toFloat() / denom.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            }
        }

        ProSectionCard(
            title = "Audit Progress",
            icon = Icons.Default.BarChart,
            actionLabel = if (showAllTimeProgress) "All Time ▾" else "This Month ▾",
            onActionClick = { showAllTimeProgress = !showAllTimeProgress }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Multi-segment Donut Chart
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(98.dp)) {
                        val strokeWidth = 13.dp.toPx()
                        // Track circle
                        drawArc(
                            color = Color(0xFFE2E8F0),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        val completedSweep = (effectiveCompletedCount.toFloat() / totalProgressItems) * 360f
                        val inProgressSweep = (inProgressDutiesCount.toFloat() / totalProgressItems) * 360f
                        val pendingSweep = (pendingDutiesCount.toFloat() / totalProgressItems) * 360f

                        var startAngle = -90f
                        if (completedSweep > 0f) {
                            drawArc(
                                color = Color(0xFF16A34A),
                                startAngle = startAngle,
                                sweepAngle = completedSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += completedSweep
                        }
                        if (inProgressSweep > 0f) {
                            drawArc(
                                color = Color(0xFF185ADB),
                                startAngle = startAngle,
                                sweepAngle = inProgressSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += inProgressSweep
                        }
                        if (pendingSweep > 0f && effectiveCompletedCount == 0 && inProgressDutiesCount == 0) {
                            drawArc(
                                color = Color(0xFFF59E0B),
                                startAngle = -90f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$successRatePercent%",
                            color = AgForeground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Success Rate",
                            color = AgMutedText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(82.dp)
                        .background(AgBorderHairline)
                )

                // Right 3-Row Legend & Metrics
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProgressLegendRow(
                        dotColor = Color(0xFF16A34A),
                        icon = Icons.Default.CheckCircle,
                        label = "Completed",
                        count = effectiveCompletedCount,
                        onClick = onOpenCompleted
                    )
                    ProgressLegendRow(
                        dotColor = Color(0xFF185ADB),
                        icon = Icons.Default.Schedule,
                        label = "In Progress",
                        count = inProgressDutiesCount,
                        onClick = onOpenDuties
                    )
                    ProgressLegendRow(
                        dotColor = Color(0xFFF07B12),
                        icon = Icons.Default.PauseCircle,
                        label = "Pending",
                        count = pendingDutiesCount,
                        onClick = onOpenDuties
                    )
                }
            }
        }

        // 4. "Assigned Areas" Section Card with Stylized Map Preview (Matching Sample Image)
        ProSectionCard(
            title = "Assigned Areas",
            icon = Icons.Default.LocationOn,
            actionLabel = "View Map →",
            onActionClick = onOpenPlaces,
            contentPadding = PaddingValues(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenPlaces)
                ) {
                    // Stylized Vector Map Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Map base land color
                        drawRect(color = Color(0xFFEEF2F6))

                        // Green park zones
                        drawRoundRect(
                            color = Color(0xFFD5F2DC),
                            topLeft = Offset(w * 0.04f, h * 0.10f),
                            size = Size(w * 0.24f, h * 0.42f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = Color(0xFFD9F3E0),
                            topLeft = Offset(w * 0.36f, h * 0.55f),
                            size = Size(w * 0.25f, h * 0.38f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        drawRoundRect(
                            color = Color(0xFFE3ECFA),
                            topLeft = Offset(w * 0.28f, h * 0.06f),
                            size = Size(w * 0.32f, h * 0.34f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        // Secondary white roads
                        val roadColor = Color.White
                        drawLine(
                            color = roadColor,
                            start = Offset(0f, h * 0.32f),
                            end = Offset(w, h * 0.22f),
                            strokeWidth = 7.dp.toPx()
                        )
                        drawLine(
                            color = roadColor,
                            start = Offset(w * 0.18f, 0f),
                            end = Offset(w * 0.34f, h),
                            strokeWidth = 6.dp.toPx()
                        )
                        drawLine(
                            color = roadColor,
                            start = Offset(w * 0.54f, 0f),
                            end = Offset(w * 0.44f, h),
                            strokeWidth = 6.dp.toPx()
                        )

                        // Primary arterial highway (soft amber/gold)
                        drawLine(
                            color = Color(0xFFFDE68A),
                            start = Offset(0f, h * 0.72f),
                            end = Offset(w * 0.85f, h * 0.14f),
                            strokeWidth = 8.dp.toPx()
                        )
                    }

                    // Pin + Floating Town Pill on Map (Left-Center)
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drop Pin Circle
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(6.dp, CircleShape)
                                .background(AgRoyalBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(999.dp),
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, Color(0xFFDCE6F5))
                        ) {
                            Text(
                                text = primaryTownName,
                                color = AgForeground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Right Frosted Localities Counter Card
                    Surface(
                        color = Color(0xFFEDF4FF).copy(alpha = 0.96f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBDDFB)),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .widthIn(min = 122.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = AgPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Localities",
                                    color = AgPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = scopedTerritories.size.toString(),
                                color = AgForeground,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 28.sp
                            )
                            Text(
                                text = "Areas Assigned",
                                color = AgMutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Expandable Locality Chip Strip below the map
                if (scopedTerritories.isNotEmpty()) {
                    ExpandableChipGroup(
                        items = scopedTerritories.map { "${it.name} (${it.code})" },
                        initialCount = 3,
                        chipBg = AgIceBoxBg,
                        chipTextColor = AgPrimary
                    )
                }
            }
        }

        // 5. Bottom 3 Vibrant Gradient Quick Action Buttons (Matching Sample Image)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProGradientActionTile(
                title = "Start Audit",
                subtitle = "Begin new audit",
                icon = Icons.Default.Description,
                brush = AgStartAuditGradient,
                onClick = onOpenAudit,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_start_audit_button")
            )
            ProGradientActionTile(
                title = "View Duties",
                subtitle = "Check assignments",
                icon = Icons.Default.LocationOn,
                brush = AgViewDutiesGradient,
                onClick = onOpenDuties,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_view_duties_button")
            )
            ProGradientActionTile(
                title = if (syncing) "Syncing…" else "Sync Now",
                subtitle = "Keep data updated",
                icon = Icons.Default.Sync,
                brush = AgSyncPurpleGradient,
                enabled = !syncing,
                onClick = onSyncNow,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_sync_now_button")
            )
        }
    }
}

@Composable
private fun ScheduleVisitRow(
    dayOfWeek: String,
    dayNum: String,
    monthYear: String,
    title: String,
    townText: String,
    distributorText: String,
    timeText: String,
    statusLabel: String,
    localities: List<String>,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActionClick),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Date Badge Box
        Surface(
            color = Color(0xFFEAF2FF),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0E2FF)),
            modifier = Modifier.width(66.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = dayOfWeek,
                    color = AgForeground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dayNum,
                    color = AgForeground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp
                )
                Text(
                    text = monthYear,
                    color = AgRoyalBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // Timeline Node & Vertical Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFFDBEAFE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AgRoyalBlue, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(Color(0xFFDBEAFE))
            )
        }

        // Visit Details Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = AgForeground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Active / Status Pill
                val isCompleted = statusLabel.equals("Completed", ignoreCase = true)
                Surface(
                    color = if (isCompleted) Color(0xFFD1FAE5) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = statusLabel,
                            color = Color(0xFF15803D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = AgPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = townText,
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = AgPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = distributorText,
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AgMutedText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = timeText,
                    color = AgMutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (localities.size > 1) {
                ExpandableChipGroup(
                    items = localities,
                    initialCount = 2
                )
            }
        }
    }
}

@Composable
private fun ProgressLegendRow(
    dotColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                color = Color(0xFF1E293B),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = count.toString(),
            color = AgForeground,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun parseDateBadgeParts(dateStr: String): Triple<String, String, String> {
    return try {
        val raw = dateStr.trim().take(10)
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed: Date = parser.parse(raw) ?: Date()
        val dow = SimpleDateFormat("EEE", Locale.US).format(parsed)
        val day = SimpleDateFormat("dd", Locale.US).format(parsed)
        val monthYear = SimpleDateFormat("MMM yyyy", Locale.US).format(parsed)
        Triple(dow, day, monthYear)
    } catch (_: Exception) {
        val now = Date()
        Triple(
            SimpleDateFormat("EEE", Locale.US).format(now),
            SimpleDateFormat("dd", Locale.US).format(now),
            SimpleDateFormat("MMM yyyy", Locale.US).format(now)
        )
    }
}

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import pk.auditgrid.audittest.api.AuditGridApiClient
import pk.auditgrid.audittest.model.*
import pk.auditgrid.audittest.ui.theme.*
import pk.auditgrid.audittest.util.*

private data class TargetOption(
    val id: String,
    val label: String,
    val sub: String,
    val cityId: String = "",
    val territoryId: String = ""
)

@Composable
fun CustomAuditFormScreen(
    form: AuditFormDefinition,
    config: FormConfigPayload?,
    activeAuditor: AppUser,
    cities: List<City>,
    territories: List<Territory>,
    distributors: List<Distributor>,
    subLocations: List<SubLocation>,
    shops: List<Shop>,
    presetTargetId: String?,
    planLink: PlanLink?,
    apiClient: AuditGridApiClient,
    onBack: () -> Unit,
    onSubmitCustomAudit: suspend (CustomAuditSubmission, Map<String, Any?>, PlanLink?) -> CustomAuditSubmission,
    onSubmitted: (CustomAuditSubmission) -> Unit,
    onToast: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val targetType = form.target.ifEmpty { "distributor" }
    val targetLabel = when (targetType) {
        "subLocation" -> "Sub-Locality"
        "shop" -> "Shop"
        else -> "Distributor"
    }

    val cityIds = remember(activeAuditor, cities, territories) {
        activeAuditor.linkedCityIds(cities, territories)
    }
    val terrs = remember(activeAuditor, territories) { activeAuditor.linkedTerritoryIds(territories) }
    val assignedCities = remember(cities, cityIds) {
        cities.filter { cityIds.contains(it.id) && it.status != "Inactive" }
    }
    val assignedTerritories = remember(territories, terrs) {
        territories.filter { terrs.contains(it.id) && it.status != "Inactive" }
    }
    val options = remember(targetType, distributors, subLocations, shops, terrs) {
        when (targetType) {
            "subLocation" -> subLocations
                .filter { terrs.contains(it.territoryId) && it.status != "Inactive" }
                .map { TargetOption(it.id, it.name, it.code, it.cityId, it.territoryId) }
            "shop" -> shops
                .filter { terrs.contains(it.territoryId) && it.status != "Inactive" }
                .map { TargetOption(it.id, it.name, it.code, it.cityId, it.territoryId) }
            else -> distributors
                .filter { terrs.contains(it.territoryId) && it.status != "Inactive" }
                .map { TargetOption(it.id, it.setupName, it.code, it.cityId, it.territoryId) }
        }
    }

    var selectedCityFilterId by remember { mutableStateOf("") }
    var selectedTerritoryFilterId by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf(presetTargetId ?: "") }
    var search by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var photos by remember { mutableStateOf<List<AuditCapturedImage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var doneSubmission by remember { mutableStateOf<CustomAuditSubmission?>(null) }
    val submitKey = remember { newSubmitKey() }

    val settings = config?.settings ?: FormSettings()
    val fields = remember(config) {
        (config?.fields ?: emptyList()).filter { it.visible && it.type != "photo" }
    }
    val hasPhotoField = remember(config) {
        (config?.fields ?: emptyList()).any { it.type == "photo" && it.visible }
    }
    val photosOn = settings.allowPhotoCapture || hasPhotoField
    val chosen = options.find { it.id == targetId }

    val warmStreamPos by WarmLocationManager.latestPosition.collectAsState()
    var preFetchedPos by remember { mutableStateOf<DevicePosition?>(WarmLocationManager.getFreshWarmPosition()) }
    var gpsFetching by remember { mutableStateOf(false) }
    var gpsProblem by remember { mutableStateOf<String?>(null) }

    suspend fun prefetchGpsInBackground() {
        gpsFetching = true
        gpsProblem = null
        try {
            WarmLocationManager.startWarming(context)
            val pos = getDevicePosition(context, timeoutMs = 6000L)
            preFetchedPos = pos
        } catch (e: Exception) {
            gpsProblem = if (e is GpsException) e.message else "Acquiring GPS signal in background…"
        } finally {
            gpsFetching = false
        }
    }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch { prefetchGpsInBackground() }
    }

    DisposableEffect(Unit) {
        if (hasLocationPermission(context)) {
            WarmLocationManager.startWarming(context)
        }
        onDispose {
            WarmLocationManager.stopWarming(context)
        }
    }

    LaunchedEffect(form.id, targetId) {
        if (!hasLocationPermission(context)) {
            locPermLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            prefetchGpsInBackground()
        }
    }

    val effectiveGpsPos = warmStreamPos ?: preFetchedPos

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (photos.size >= settings.maxPhotos) {
                onToast("Up to ${settings.maxPhotos} photos.")
            } else {
                val url = compressBitmapToDataUrl(bmp)
                photos = photos + AuditCapturedImage(
                    id = newSubmitKey(),
                    url = url,
                    caption = "",
                    capturedAt = currentTimePkt()
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else onToast("Camera permission is needed to take photos.")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (photos.size >= settings.maxPhotos) {
                onToast("Up to ${settings.maxPhotos} photos.")
            } else {
                scope.launch {
                    val url = compressUriToDataUrl(context, uri)
                    if (url != null) {
                        photos = photos + AuditCapturedImage(
                            id = newSubmitKey(),
                            url = url,
                            caption = "",
                            capturedAt = currentTimePkt()
                        )
                    } else {
                        onToast("Could not read that photo.")
                    }
                }
            }
        }
    }

    fun validate(): String {
        if (targetId.isBlank()) return "Choose the ${targetLabel.lowercase()} you audited."
        for (f in fields) {
            val v = answers[f.key]
            val empty = v == null || v.toString().isBlank()
            if (f.required && empty) return "${f.label} is required."
            if (f.type == "number" && !empty) {
                val n = v.toString().toDoubleOrNull() ?: return "${f.label} must be a number."
                if (f.min != null && n < f.min) return "${f.label} must be at least ${f.min}."
                if (f.max != null && n > f.max) return "${f.label} must be at most ${f.max}."
            }
        }
        if (settings.requirePhoto && photos.isEmpty()) return "At least one photo is required."
        return ""
    }

    val done = doneSubmission
    if (done != null) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgAccentGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Submitted",
                    tint = AgAccentGreen,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Audit submitted",
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = done.reportCode,
                    color = AgPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (done.locationMatched) "Location matched" else "Location flagged for manager review",
                    color = if (done.locationMatched) AgAccentGreenDark else AgWarning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (planLink != null) "Back to plan" else "Done",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    val shown = options.filter {
        (selectedCityFilterId.isBlank() || it.cityId == selectedCityFilterId) &&
            (selectedTerritoryFilterId.isBlank() || it.territoryId == selectedTerritoryFilterId) &&
            (it.label + " " + it.sub).contains(search, ignoreCase = true)
    }.take(40)

    val customChecks = buildList {
        add(targetId.isNotBlank())
        fields.forEach { f ->
            val v = answers[f.key]
            add(v != null && v.toString().isNotBlank())
        }
        if (photosOn && settings.requirePhoto) {
            add(photos.isNotEmpty())
        }
    }
    val customProgress = if (customChecks.isNotEmpty()) {
        customChecks.count { it }.toFloat() / customChecks.size.toFloat()
    } else 0f

    Surface(
        color = AuditSlateBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AuditBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AuditFormTopBar(
                title = form.title,
                subtitle = chosen?.let { "${it.label} (${it.sub}) · ${form.code}" } ?: "${form.code} · Select $targetLabel",
                progress = customProgress,
                gpsVerified = effectiveGpsPos != null,
                gpsLabelOverride = when {
                    gpsFetching -> "Locating..."
                    effectiveGpsPos != null -> "GPS Locked (±${effectiveGpsPos.accuracy.toInt()}m)"
                    else -> "Tap for GPS"
                },
                onBackClick = onBack,
                onRefreshGps = { scope.launch { prefetchGpsInBackground() } }
            )

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // SECTION 1: Target Identification
                AuditSectionCard(
                    title = "1. $targetLabel Identification",
                    icon = Icons.Default.QrCodeScanner,
                    badgeText = chosen?.sub ?: form.code
                ) {
                    if (assignedCities.isNotEmpty() || assignedTerritories.isNotEmpty()) {
                        Surface(
                            color = AgFormBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AuditBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ExpandableTextWithMore(
                                    text = "Assigned Towns (${assignedCities.size}): ${assignedCities.joinToString(", ") { it.name }}",
                                    maxLinesCollapsed = 1,
                                    thresholdChars = 48,
                                    textColor = AuditTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                ExpandableTextWithMore(
                                    text = "Assigned Localities (${assignedTerritories.size}): ${assignedTerritories.joinToString(", ") { "${it.name} (${it.code})" }}",
                                    maxLinesCollapsed = 1,
                                    thresholdChars = 48,
                                    textColor = AuditPrimaryBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (!presetTargetId.isNullOrBlank() && chosen != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgMutedBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "${chosen.label} (${chosen.sub})",
                                color = AuditTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        var showAllCustomTargets by remember(shown.size) { mutableStateOf(false) }
                        val visibleTargets = if (showAllCustomTargets || shown.size <= 5) {
                            shown
                        } else {
                            shown.take(5)
                        }
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Select or Search $targetLabel *") },
                            placeholder = { Text("Scan or type ${targetLabel.lowercase()} name or code…", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                FilledIconButton(
                                    onClick = {},
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = AuditPrimaryBlue.copy(alpha = 0.12f),
                                        contentColor = AuditPrimaryBlue
                                    ),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Search Target",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AuditBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.animateContentSize()) {
                                if (shown.isEmpty()) {
                                    Text(
                                        text = "Nothing in your assigned areas matches.",
                                        color = AuditTextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                } else {
                                    visibleTargets.forEachIndexed { idx, o ->
                                        val isSelected = o.id == targetId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isSelected) AgSelectedCardBg else AuditCardSurface)
                                                .clickable { targetId = o.id }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = o.label,
                                                color = AuditTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = o.sub,
                                                color = AuditTextSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (idx < visibleTargets.lastIndex) {
                                            HorizontalDivider(color = AuditBorderLight)
                                        }
                                    }
                                }
                            }
                        }
                        if (shown.size > 5) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ShowMoreListToggle(
                                totalCount = shown.size,
                                collapsedLimit = 5,
                                expanded = showAllCustomTargets,
                                itemLabel = targetLabel.ifBlank { "targets" },
                                onToggle = { showAllCustomTargets = !showAllCustomTargets }
                            )
                        }
                    }
                }

                // SECTION 2: Dynamic Audit Fields
                if (fields.isNotEmpty()) {
                    AuditSectionCard(
                        title = "2. Audit Checklist & Metrics",
                        icon = Icons.Default.Verified,
                        badgeText = "${fields.size} Fields"
                    ) {
                        fields.forEachIndexed { idx, f ->
                            CustomFieldInput(
                                field = f,
                                value = answers[f.key],
                                onChange = { v -> answers = answers + (f.key to v) }
                            )
                            if (idx < fields.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = AuditBorderLight
                                )
                            }
                        }
                    }
                }

                // SECTION 3: Photo Evidence
                if (photosOn) {
                    AuditSectionCard(
                        title = "${if (fields.isNotEmpty()) "3" else "2"}. Evidence & Photos",
                        icon = Icons.Default.CameraAlt,
                        badgeText = "${photos.size}/${settings.maxPhotos}"
                    ) {
                        AuditPhotoCaptureBlock(
                            photoCount = photos.size,
                            maxPhotos = settings.maxPhotos,
                            required = settings.requirePhoto,
                            subtitle = "Capture clear inspection evidence & site photos.",
                            onTakePhoto = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            onPickGallery = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        if (photos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                photos.forEach { p ->
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, AuditBorderLight, RoundedCornerShape(10.dp))
                                    ) {
                                        DataUrlImage(
                                            dataUrl = p.url,
                                            contentDescription = "Audit photo",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { photos = photos.filterNot { it.id == p.id } },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Surface(
                        color = AgDestructiveBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgDestructive.copy(alpha = 0.4f))
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
            }

            // Bottom Elevated Submit Bar
            Surface(
                color = AuditCardSurface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, AuditBorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AuditBorderLight)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, color = AuditTextPrimary)
                    }

                    Button(
                        onClick = {
                            val problem = validate()
                            error = problem
                            if (problem.isNotEmpty()) return@Button
                            busy = true
                            scope.launch {
                                try {
                                    val pos = effectiveGpsPos ?: WarmLocationManager.getFreshWarmPosition() ?: getDevicePosition(context)
                                    val cleanAnswers = mutableMapOf<String, Any?>()
                                    val stringAnswers = mutableMapOf<String, String>()
                                    for (f in fields) {
                                        val v = answers[f.key]
                                        if (v == null || v.toString().isBlank()) continue
                                        val cleanVal: Any = if (f.type == "number") {
                                            v.toString().toDoubleOrNull() ?: 0.0
                                        } else {
                                            v
                                        }
                                        cleanAnswers[f.key] = cleanVal
                                        stringAnswers[f.key] = cleanVal.toString()
                                    }
                                    val (tLat, tLng, tCityId, tTerId) = when (targetType) {
                                        "subLocation" -> {
                                            val s = subLocations.find { it.id == targetId }
                                            listOf(s?.lat ?: 0.0, s?.lng ?: 0.0, s?.cityId ?: "", s?.territoryId ?: "")
                                        }
                                        "shop" -> {
                                            val sh = shops.find { it.id == targetId }
                                            listOf(sh?.lat ?: 0.0, sh?.lng ?: 0.0, sh?.cityId ?: "", sh?.territoryId ?: "")
                                        }
                                        else -> {
                                            val d = distributors.find { it.id == targetId }
                                            listOf(d?.lat ?: 0.0, d?.lng ?: 0.0, d?.cityId ?: "", d?.territoryId ?: "")
                                        }
                                    }
                                    val targetLat = tLat as Double
                                    val targetLng = tLng as Double
                                    val cityIdStr = tCityId as String
                                    val terIdStr = tTerId as String
                                    val distM = if (targetLat != 0.0 || targetLng != 0.0) {
                                        calculateDistanceMeters(pos.lat, pos.lng, targetLat, targetLng)
                                    } else 0
                                    val matched = !settings.requireGps || (
                                        distM <= settings.allowedRadiusMeters &&
                                            pos.accuracy <= settings.maxAccuracyMeters
                                        )
                                    val cityObj = cities.find { it.id == cityIdStr }
                                    val terObj = territories.find { it.id == terIdStr }

                                    val localSubmission = CustomAuditSubmission(
                                        id = submitKey,
                                        reportCode = makeReportCode("AUD-CST"),
                                        formId = form.id,
                                        formCode = form.code,
                                        formTitle = form.title,
                                        targetType = targetType,
                                        targetId = targetId,
                                        targetCode = chosen?.sub ?: "",
                                        targetName = chosen?.label ?: targetId,
                                        cityName = cityObj?.name ?: "",
                                        territoryCode = terObj?.code ?: "",
                                        territoryName = terObj?.name ?: "",
                                        auditorId = activeAuditor.id,
                                        auditorName = activeAuditor.fullName,
                                        submittedDate = currentIsoDate(),
                                        submittedTime = currentTimePkt(),
                                        submittedAtISO = currentIsoTimestamp(),
                                        mobileLat = pos.lat,
                                        mobileLng = pos.lng,
                                        mobileAccuracyMeters = pos.accuracy.toInt(),
                                        distanceMeters = distM,
                                        allowedRadiusMeters = settings.allowedRadiusMeters,
                                        locationMatched = matched,
                                        answers = stringAnswers,
                                        capturedImages = photos,
                                        reviewStatus = "Submitted",
                                        planId = planLink?.planId,
                                        planItemId = planLink?.itemId
                                    )

                                    val saved = onSubmitCustomAudit(localSubmission, cleanAnswers, planLink)
                                    doneSubmission = saved
                                    onSubmitted(saved)
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not submit. Your entries are kept; try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuditPrimaryBlue),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("submit_custom_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (busy) "Submitting…" else "Submit Audit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldInput(
    field: FormField,
    value: Any?,
    onChange: (Any?) -> Unit
) {
    val labelText = if (field.required) "${field.label} *" else field.label
    when (field.type) {
        "yesno" -> {
            AuditYesNoRow(
                label = labelText,
                subtitle = field.helpText,
                value = value as? Boolean,
                onValueChange = { onChange(it) }
            )
        }
        "number" -> {
            AuditNumberStepperRow(
                label = labelText,
                subtitle = field.helpText ?: field.placeholder,
                value = value?.toString()?.toDoubleOrNull()?.toInt() ?: 0,
                onValueChange = { next ->
                    val clamped = when {
                        field.min != null && next < field.min.toInt() -> field.min.toInt()
                        field.max != null && next > field.max.toInt() -> field.max.toInt()
                        else -> next.coerceAtLeast(0)
                    }
                    onChange(clamped.toString())
                }
            )
        }
        "select" -> {
            if (field.options.size in 1..5) {
                AuditFilterChipsRow(
                    label = labelText,
                    subtitle = field.helpText,
                    options = field.options,
                    selectedOption = value?.toString() ?: "",
                    onOptionSelected = { onChange(it) }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = labelText,
                        color = AuditTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = value?.toString() ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select…") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            field.options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onChange(opt)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    field.helpText?.let {
                        Text(text = it, color = AuditTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        "textarea" -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = { onChange(it) },
                    label = { Text(labelText) },
                    placeholder = { Text(field.placeholder ?: "Enter remarks or notes...") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                field.helpText?.let {
                    Text(text = it, color = AuditTextSecondary, fontSize = 11.sp)
                }
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = { onChange(it) },
                    label = { Text(labelText) },
                    placeholder = { Text(field.placeholder ?: if (field.type == "date") "YYYY-MM-DD" else "") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                field.helpText?.let {
                    Text(text = it, color = AuditTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

package pk.auditgrid.audittest.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pk.auditgrid.audittest.util.MobileLocationStatus
import pk.auditgrid.audittest.util.dataUrlToBytes
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

// Executive Enterprise Field-Audit Mobile UI Palette (ui-ux-pro-max)
val AgNavyHeader = Color(0xFF0A2246)
val AgDutyBannerNavy = Color(0xFF1E293B)
val AgPrimary = Color(0xFF0E2F66)
val AgRoyalBlue = Color(0xFF185ADB)
val AgActionBlue = Color(0xFF1048A8)
val AgSecondaryBlue = Color(0xFF1D4ED8)
val AgShieldGreen = Color(0xFF10B981)
val AgShieldGreenDark = Color(0xFF059669)
val AgAccentGreen = Color(0xFF16A34A)
val AgAccentGreenDark = Color(0xFF047857)
val AgAccentGreenBg = Color(0xFFD1FAE5)
val AgGreenSuccess = AgAccentGreen
val AgBackground = Color(0xFFF4F7FB)
val AgFormBg = Color(0xFFF8FAFC)
val AgInputFill = AgFormBg
val AgSoftBlueBg = Color(0xFFE0ECFF)
val AgIceBoxBg = Color(0xFFF0F6FF)
val AgSoftBlueBorder = Color(0xFFD5E3F7)
val AgIceBoxBorder = AgSoftBlueBorder
val AgSelectedCardBg = Color(0xFFEFF6FF)
val AgCardWhite = Color(0xFFFFFFFF)
val AgForeground = Color(0xFF0F172A)
val AgDarkText = AgForeground
val AgMutedBg = Color(0xFFE6EEF8)
val AgMutedText = Color(0xFF475569)
val AgBorder = Color(0xFFE2E8F0)
val AgBorderHairline = Color(0xFFE2E8F0)
val AgLightBorder = Color(0xFFEBF1F8)
val AgDestructive = Color(0xFFDC2626)
val AgDestructiveDark = Color(0xFF991B1B)
val AgDestructiveBg = Color(0xFFFEE2E2)
val AgRedDanger = AgDestructive
val AgWarning = Color(0xFFB45309)
val AgWarningDark = Color(0xFF92400E)
val AgWarningBg = Color(0xFFFEF3C7)
val AgAmberPending = AgWarning
val AgPurple = Color(0xFF7C3AED)
val AgPurpleDark = Color(0xFF5B21B6)
val AgPurpleBg = Color(0xFFF3E8FF)

// Pro-Max Gradient Brushes matching the AuditGrid Dashboard Design
val AgHeroGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF061B3A),
        Color(0xFF0C2E63),
        Color(0xFF154596)
    ),
    start = Offset(0f, 0f),
    end = Offset(900f, 500f)
)

val AgSectionHeaderGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF0A2654),
        Color(0xFF15428C),
        Color(0xFF1B52A8)
    )
)

val AgStartAuditGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF286EF0),
        Color(0xFF1344AE)
    )
)

val AgViewDutiesGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF26C968),
        Color(0xFF0E9647)
    )
)

val AgSyncPurpleGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF9F42F5),
        Color(0xFF6A1FC2)
    )
)

val AgLastSyncPillGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF90BDF9),
        Color(0xFF72A5F2)
    )
)

val AgKpiBlueBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFEAF2FF), Color(0xFFDCE9FF))
)

val AgKpiGreenBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFE7FBF0), Color(0xFFD2F5E3))
)

val AgKpiAmberBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFFF4E4), Color(0xFFFFE6C8))
)

val AgKpiPurpleBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFF4ECFF), Color(0xFFE7D7FF))
)

private val AuditGridColorScheme = lightColorScheme(
    primary = AgPrimary,
    onPrimary = Color.White,
    primaryContainer = AgMutedBg,
    onPrimaryContainer = AgPrimary,
    secondary = AgRoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = AgSoftBlueBg,
    onSecondaryContainer = AgRoyalBlue,
    tertiary = AgAccentGreen,
    onTertiary = Color.White,
    background = AgBackground,
    onBackground = AgForeground,
    surface = AgCardWhite,
    onSurface = AgForeground,
    surfaceVariant = AgMutedBg,
    onSurfaceVariant = AgMutedText,
    outline = AgBorder,
    error = AgDestructive,
    onError = Color.White
)

@Composable
fun AuditGridTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuditGridColorScheme,
        content = content
    )
}

/**
 * Glossy 3D Emerald Green Shield with Mint Rim & White Checkmark matching the AuditGrid mockup.
 */
@Composable
fun AuditGridShieldLogo(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val outerShield = Path().apply {
                moveTo(w * 0.5f, h * 0.03f)
                lineTo(w * 0.93f, h * 0.20f)
                lineTo(w * 0.93f, h * 0.53f)
                cubicTo(
                    w * 0.93f, h * 0.78f,
                    w * 0.70f, h * 0.93f,
                    w * 0.5f, h * 0.98f
                )
                cubicTo(
                    w * 0.30f, h * 0.93f,
                    w * 0.07f, h * 0.78f,
                    w * 0.07f, h * 0.53f
                )
                lineTo(w * 0.07f, h * 0.20f)
                close()
            }
            drawPath(
                path = outerShield,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6EE7B7), Color(0xFF10B981))
                )
            )

            val innerShield = Path().apply {
                moveTo(w * 0.5f, h * 0.11f)
                lineTo(w * 0.85f, h * 0.25f)
                lineTo(w * 0.85f, h * 0.52f)
                cubicTo(
                    w * 0.85f, h * 0.74f,
                    w * 0.66f, h * 0.87f,
                    w * 0.5f, h * 0.91f
                )
                cubicTo(
                    w * 0.34f, h * 0.87f,
                    w * 0.15f, h * 0.74f,
                    w * 0.15f, h * 0.52f
                )
                lineTo(w * 0.15f, h * 0.25f)
                close()
            }
            drawPath(
                path = innerShield,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF047857))
                )
            )

            // White checkmark inside shield
            val checkPath = Path().apply {
                moveTo(w * 0.33f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.70f, h * 0.38f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = w * 0.105f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

fun formatPKR(amount: Double): String {
    val nf = NumberFormat.getIntegerInstance(Locale.US)
    return "Rs. ${nf.format(amount.roundToLong())}"
}

@Composable
fun GPSStatusChip(status: String) {
    val (color, icon, label) = when (status) {
        "GPS Verified" -> Triple(AgAccentGreen, Icons.Default.LocationOn, "GPS Verified")
        "Outside Allowed Radius" -> Triple(AgDestructive, Icons.Default.LocationOff, "Outside Allowed Radius")
        "Low GPS Accuracy" -> Triple(AgWarning, Icons.Default.SignalCellularAlt, "Low GPS Accuracy")
        "Location Unavailable" -> Triple(AgMutedText, Icons.Default.LocationOff, "Location Unavailable")
        "Awaiting Verification" -> Triple(AgSecondaryBlue, Icons.Default.Schedule, "Awaiting Verification")
        "Approved Exception" -> Triple(Color(0xFF7E22CE), Icons.Default.VerifiedUser, "Approved Exception")
        else -> Triple(AgMutedText, Icons.Default.LocationOn, status)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EntityStatusIndicator(status: String) {
    val isActive = status.equals("Active", ignoreCase = true)
    val color = if (isActive) AgAccentGreen else AgMutedText
    val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (isActive) "Active" else "Inactive",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProductGroupTag(groupName: String) {
    val lower = groupName.lowercase(Locale.US)
    val isGroupA = lower.contains("group a") || groupName == "GRP-A"
    val isGroupB = lower.contains("group b") || groupName == "GRP-B"

    val (bg, border, fg, label) = when {
        isGroupA -> Quad(Color(0x0D1E3A5F), AgPrimary.copy(alpha = 0.3f), AgPrimary, "Group A")
        isGroupB -> Quad(Color(0x0D134E4A), Color(0xFF0F766E).copy(alpha = 0.3f), Color(0xFF115E59), "Group B")
        else -> Quad(AgMutedBg.copy(alpha = 0.6f), AgBorder, AgForeground, groupName)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CodeBadge(code: String, bg: Color = AgSoftBlueBg, fg: Color = AgRoyalBlue) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = code,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DataUrlImage(
    dataUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bmp = remember(dataUrl) {
        try {
            val (_, bytes) = dataUrlToBytes(dataUrl)
            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(AgMutedBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Photo",
                fontSize = 10.sp,
                color = AgMutedText
            )
        }
    }
}

@Composable
fun MobileLocationOffBanner(
    status: MobileLocationStatus,
    onTurnOnLocation: () -> Unit,
    onGrantPermission: () -> Unit
) {
    if (status.isReady) return

    val isPermMissing = !status.hasPermission
    Surface(
        color = AgDestructiveBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, AgDestructive),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mobile_location_off_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AgDestructive, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = if (isPermMissing) "Location Permission is OFF" else "Mobile Location is OFF",
                    color = AgDestructiveDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Mobile location must be turned ON to open or start any audit form.",
                    color = AgDestructiveDark,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Button(
                onClick = if (isPermMissing) onGrantPermission else onTurnOnLocation,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AgDestructive),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("turn_on_location_banner_button")
            ) {
                Text(
                    text = if (isPermMissing) "Allow" else "Turn On",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MobileLocationRequiredDialog(
    status: MobileLocationStatus,
    onTurnOnLocation: () -> Unit,
    onGrantPermission: () -> Unit,
    onCheckAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPermMissing = !status.hasPermission
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AgCardWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, AgDestructive),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mobile_location_required_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AgDestructiveBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = AgDestructive,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Mobile Location Required",
                    color = AgForeground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isPermMissing) {
                        "Location permission is turned off for this app. Mobile location is mandatory to start an audit — no audit form can be opened while location is off."
                    } else {
                        "Your mobile location (GPS) is currently turned OFF. Mobile location is mandatory to start an audit — no audit form can be opened while mobile location is off."
                    },
                    color = AgMutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Button(
                    onClick = if (isPermMissing) onGrantPermission else onTurnOnLocation,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgActionBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("dialog_enable_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPermMissing) "Allow Location Permission" else "Turn On Mobile Location",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("dialog_close_location_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = AgForeground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onCheckAgain,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgAccentGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("dialog_retry_open_form_button")
                    ) {
                        Text(
                            text = "Try Again",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Progressive disclosure text block: truncates long strings cleanly and displays an inline
 * "More" / "Show Less" pill so dense metadata never overflows or clutters cards.
 */
@Composable
fun ExpandableTextWithMore(
    text: String,
    modifier: Modifier = Modifier,
    maxLinesCollapsed: Int = 1,
    thresholdChars: Int = 52,
    textColor: Color = AgMutedText,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = 17.sp
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val isLong = remember(text, thresholdChars) {
        text.length > thresholdChars ||
            text.count { it == '•' || it == ',' } >= 2 ||
            text.contains('\n')
    }

    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            maxLines = if (expanded || !isLong) Int.MAX_VALUE else maxLinesCollapsed,
            overflow = TextOverflow.Ellipsis
        )
        if (isLong) {
            Surface(
                color = AgSelectedCardBg,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AgRoyalBlue.copy(alpha = 0.25f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (expanded) "Show Less" else "More",
                        color = AgRoyalBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Show Less" else "More",
                        tint = AgRoyalBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Progressive disclosure chip group: shows the first [initialCount] items and collapses the rest
 * behind a "+N More" chip that expands cleanly on tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableChipGroup(
    items: List<String>,
    modifier: Modifier = Modifier,
    initialCount: Int = 2,
    emptyText: String = "None assigned",
    chipBg: Color = AgIceBoxBg,
    chipTextColor: Color = AgDarkText
) {
    val cleanItems = remember(items) { items.map { it.trim() }.filter { it.isNotBlank() } }
    var expanded by remember(cleanItems) { mutableStateOf(false) }

    if (cleanItems.isEmpty()) {
        Text(
            text = emptyText,
            color = AgMutedText,
            fontSize = 12.sp,
            modifier = modifier
        )
        return
    }

    val needsToggle = cleanItems.size > initialCount
    val visibleItems = if (expanded || !needsToggle) cleanItems else cleanItems.take(initialCount)

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visibleItems.forEach { item ->
            Surface(
                color = chipBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AgBorderHairline)
            ) {
                Text(
                    text = item,
                    color = chipTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        if (needsToggle) {
            Surface(
                color = AgSelectedCardBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AgRoyalBlue.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (expanded) "Show Less" else "+${cleanItems.size - initialCount} More",
                        color = AgRoyalBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AgRoyalBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full-width pagination/collapse bar for long lists of localities, shops, products, or audits.
 */
@Composable
fun ShowMoreListToggle(
    totalCount: Int,
    collapsedLimit: Int,
    expanded: Boolean,
    itemLabel: String = "items",
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalCount <= collapsedLimit) return

    val remaining = totalCount - collapsedLimit
    Surface(
        color = AgIceBoxBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AgSoftBlueBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AgRoyalBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (expanded) "Show Less $itemLabel" else "Show $remaining More $itemLabel",
                    color = AgRoyalBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = AgCardWhite,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AgBorderHairline)
            ) {
                Text(
                    text = if (expanded) "All $totalCount" else "$collapsedLimit of $totalCount",
                    color = AgMutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Compact per-card disclosure toggle for secondary metadata (CNIC, address, audit flags, remarks).
 */
@Composable
fun CardMoreDetailsToggle(
    expanded: Boolean,
    labelCollapsed: String = "More Details",
    labelExpanded: String = "Hide Details",
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (expanded) AgSelectedCardBg else AgFormBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (expanded) AgRoyalBlue.copy(alpha = 0.3f) else AgBorderHairline),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (expanded) labelExpanded else labelCollapsed,
                color = if (expanded) AgRoyalBlue else AgMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (expanded) AgRoyalBlue else AgMutedText,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Structured key-value tile for clean multi-column metadata alignment inside cards.
 */
@Composable
fun StructuredMetaGridItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AgDarkText
) {
    Column(
        modifier = modifier
            .background(AgFormBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(Locale.US),
            color = AgMutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Elevated Section Card with a deep Navy-to-Royal-Blue gradient top header bar,
 * matching the "Today's Visit Schedule", "Audit Progress", and "Assigned Areas" cards.
 */
@Composable
fun ProSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    Surface(
        color = AgCardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE6F5)),
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Navy-Royal Gradient Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgSectionHeaderGradient)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!actionLabel.isNullOrBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .then(
                                if (onActionClick != null) Modifier.clickable(onClick = onActionClick)
                                else Modifier
                            )
                    ) {
                        Text(
                            text = actionLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Card Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
            ) {
                content()
            }
        }
    }
}

/**
 * Pastel Gradient KPI Card with soft wave accent, rounded icon badge, bold count, label, and chevron pill.
 */
@Composable
fun ProKpiPastelCard(
    countText: String,
    label: String,
    icon: ImageVector,
    iconBgColor: Color,
    cardBrush: Brush,
    borderColor: Color,
    chevronBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(cardBrush)
                .padding(horizontal = 12.dp, vertical = 12.dp)
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
                            .size(44.dp)
                            .shadow(2.dp, CircleShape)
                            .background(iconBgColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = countText,
                            color = AgForeground,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 23.sp
                        )
                        Text(
                            text = label,
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(chevronBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open $label",
                        tint = iconBgColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Vibrant Gradient Quick Action Tile matching the bottom 3 buttons ("Start Audit", "View Duties", "Sync Now").
 */
@Composable
fun ProGradientActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Circular Stylized Auditor Portrait Avatar for the Top Hero Header.
 */
@Composable
fun ProAuditorAvatar(
    name: String,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.US)
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
            else -> "FA"
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE0ECFF), Color(0xFFB9D5FF))
                ),
                shape = CircleShape
            )
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Stylized portrait silhouette + initials badge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Shoulders arc
            drawArc(
                color = Color(0xFF1D4ED8).copy(alpha = 0.18f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.15f, h * 0.56f),
                size = androidx.compose.ui.geometry.Size(w * 0.70f, h * 0.55f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF0E2F66),
                modifier = Modifier.size(size * 0.44f)
            )
            Text(
                text = initials,
                color = Color(0xFF0E2F66),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 10.sp
            )
        }
    }
}

// ============================================================================
// PROFESSIONAL AUDIT FORM DESIGN SYSTEM (Slate / Royal Blue / Semantic Chips)
// ============================================================================

val AuditSlateBackground = Color(0xFFF1F5F9)
val AuditCardSurface = Color(0xFFFFFFFF)
val AuditPrimaryBlue = Color(0xFF2563EB)
val AuditCompliantGreen = Color(0xFF16A34A)
val AuditNonCompliantRed = Color(0xFFDC2626)
val AuditWarningAmber = Color(0xFFD97706)
val AuditTextPrimary = Color(0xFF0F172A)
val AuditTextSecondary = Color(0xFF64748B)
val AuditBorderLight = Color(0xFFE2E8F0)

@Composable
fun AuditFormTopBar(
    title: String,
    subtitle: String,
    progress: Float,
    gpsVerified: Boolean,
    onBackClick: () -> Unit,
    gpsLabelOverride: String? = null,
    onRefreshGps: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Surface(
        color = AuditCardSurface,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuditTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AuditTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AuditTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // GPS Status Badge
                Surface(
                    color = if (gpsVerified) AuditCompliantGreen.copy(alpha = 0.12f) else AuditWarningAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        if (gpsVerified) AuditCompliantGreen.copy(alpha = 0.3f) else AuditWarningAmber.copy(alpha = 0.3f)
                    ),
                    modifier = if (onRefreshGps != null) {
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onRefreshGps() }
                    } else Modifier
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = if (gpsVerified) AuditCompliantGreen else AuditWarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = gpsLabelOverride ?: if (gpsVerified) "GPS Locked" else "Locating...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (gpsVerified) AuditCompliantGreen else AuditWarningAmber
                        )
                    }
                }
                if (trailingAction != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    trailingAction()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Completion Progress Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { clampedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (clampedProgress >= 1f) AuditCompliantGreen else AuditPrimaryBlue,
                    trackColor = AuditBorderLight
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(clampedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (clampedProgress >= 1f) AuditCompliantGreen else AuditPrimaryBlue
                )
            }
        }
    }
}

@Composable
fun AuditSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AuditCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, AuditBorderLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(AuditPrimaryBlue.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = AuditPrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AuditTextPrimary
                    )
                }
                if (!badgeText.isNullOrBlank()) {
                    CodeBadge(badgeText, bg = AgSoftBlueBg, fg = AuditPrimaryBlue)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun AuditYesNoRow(
    label: String,
    subtitle: String? = null,
    value: Boolean?,
    onValueChange: (Boolean) -> Unit,
    yesText: String = "YES",
    noText: String = "NO"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuditTextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuditTextSecondary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedTogglePill(
                text = yesText,
                isSelected = value == true,
                activeColor = AuditCompliantGreen,
                onClick = { onValueChange(true) }
            )
            SegmentedTogglePill(
                text = noText,
                isSelected = value == false,
                activeColor = AuditNonCompliantRed,
                onClick = { onValueChange(false) }
            )
        }
    }
}

@Composable
fun SegmentedTogglePill(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Transparent,
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else AuditTextSecondary,
        label = "pillContent"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected) activeColor else AuditBorderLight)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun AuditNumberStepperRow(
    label: String,
    subtitle: String? = null,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuditTextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuditTextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(
                onClick = { if (value > 0) onValueChange(value - 1) },
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = AuditPrimaryBlue.copy(alpha = 0.10f),
                    contentColor = AuditPrimaryBlue
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
            }

            OutlinedTextField(
                value = value.toString(),
                onValueChange = { newText ->
                    val filtered = newText.filter { it.isDigit() }
                    onValueChange(filtered.toIntOrNull() ?: 0)
                },
                modifier = Modifier.width(72.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(10.dp)
            )

            FilledTonalIconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = AuditPrimaryBlue.copy(alpha = 0.10f),
                    contentColor = AuditPrimaryBlue
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditFilterChipsRow(
    label: String,
    subtitle: String? = null,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AuditTextPrimary
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AuditTextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { item ->
                val isSelected = selectedOption.equals(item, ignoreCase = true)
                val lower = item.lowercase(Locale.US)
                val semanticColor = when {
                    lower.contains("good") || lower.contains("excellent") || lower == "yes" || lower == "regular" || lower == "open" || lower.contains("proper") ->
                        AuditCompliantGreen
                    lower.contains("avg") || lower.contains("average") || lower.contains("fair") || lower.contains("near") || lower.contains("not regular") || lower.contains("irregular") ->
                        AuditWarningAmber
                    lower.contains("poor") || lower.contains("bad") || lower.contains("damage") || lower == "no" || lower == "never" || lower == "close" || lower.contains("none") ->
                        AuditNonCompliantRed
                    else -> AuditPrimaryBlue
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onOptionSelected(item) },
                    label = {
                        Text(
                            text = item,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = semanticColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = semanticColor.copy(alpha = 0.15f),
                        selectedLabelColor = semanticColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = AuditBorderLight,
                        selectedBorderColor = semanticColor.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun AuditPhotoCaptureBlock(
    photoCount: Int,
    maxPhotos: Int,
    required: Boolean,
    subtitle: String = "Capture clear front-facing shelf & batch code photos.",
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Add Photo Action Card (88.dp tactile tile)
        Surface(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = photoCount < maxPhotos, onClick = onTakePhoto),
            color = AuditPrimaryBlue.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, AuditPrimaryBlue.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Add Photo",
                    tint = AuditPrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add Photo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuditPrimaryBlue
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$photoCount / $maxPhotos Photo(s) Attached",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AuditTextPrimary
                )
                if (required) {
                    CodeBadge("Required", bg = AgWarningBg, fg = AuditWarningAmber)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AuditTextSecondary
            )
            OutlinedButton(
                onClick = onPickGallery,
                enabled = photoCount < maxPhotos,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AuditBorderLight),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Choose from Gallery",
                    tint = AuditPrimaryBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Upload from Gallery",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuditTextPrimary
                )
            }
        }
    }
}

@Composable
fun AuditStickyBottomBar(
    onSaveDraft: () -> Unit,
    onSubmit: () -> Unit,
    isSubmitEnabled: Boolean,
    saveDraftText: String = "Save Draft",
    submitText: String = "Submit Audit",
    saveDraftTestTag: String? = null,
    submitTestTag: String? = null
) {
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
                onClick = onSaveDraft,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .then(if (saveDraftTestTag != null) Modifier.testTag(saveDraftTestTag) else Modifier),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AuditBorderLight)
            ) {
                Text(saveDraftText, fontWeight = FontWeight.SemiBold, color = AuditTextPrimary, fontSize = 13.sp)
            }

            Button(
                onClick = onSubmit,
                enabled = isSubmitEnabled,
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .then(if (submitTestTag != null) Modifier.testTag(submitTestTag) else Modifier),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuditPrimaryBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(submitText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

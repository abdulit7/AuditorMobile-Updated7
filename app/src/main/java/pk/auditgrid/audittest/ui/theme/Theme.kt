package pk.auditgrid.audittest.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Emerald Green Shield with White Checkmark matching the AuditGrid top bar logo.
 */
@Composable
fun AuditGridShieldLogo(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.90f, h * 0.22f)
                lineTo(w * 0.90f, h * 0.52f)
                cubicTo(
                    w * 0.90f, h * 0.76f,
                    w * 0.68f, h * 0.91f,
                    w * 0.5f, h * 0.96f
                )
                cubicTo(
                    w * 0.32f, h * 0.91f,
                    w * 0.10f, h * 0.76f,
                    w * 0.10f, h * 0.52f
                )
                lineTo(w * 0.10f, h * 0.22f)
                close()
            }
            drawPath(
                path = shieldPath,
                color = AgShieldGreen
            )
            // White checkmark inside shield
            val checkPath = Path().apply {
                moveTo(w * 0.32f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.70f, h * 0.38f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = w * 0.10f,
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

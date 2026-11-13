package pk.auditgrid.audittest.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pk.auditgrid.audittest.model.AuditorNotification
import pk.auditgrid.audittest.ui.theme.*

@Composable
fun DutyNotificationsDialog(
    notifications: List<AuditorNotification>,
    onDismiss: () -> Unit,
    onMarkRead: (String) -> Unit,
    onOpenDuty: (String?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AgCardWhite,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .heightIn(max = 580.dp)
                .testTag("duty_notifications_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top header with close button
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
                            color = AgPrimary.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = AgPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Duty Notifications",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgForeground
                            )
                            val unreadCount = notifications.count { !it.isRead }
                            Text(
                                text = if (unreadCount > 0) "$unreadCount new assignment alert(s)" else "All up to date",
                                fontSize = 11.sp,
                                color = if (unreadCount > 0) Color(0xFFD97706) else AgMutedText,
                                fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AgMutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = AgBorder)

                // List of notifications
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = AgMutedText,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No notifications yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AgMutedText
                            )
                            Text(
                                text = "When duties are assigned on the web portal, you will be notified here.",
                                fontSize = 11.sp,
                                color = AgMutedText,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        notifications.forEach { notif ->
                            NotificationItemCard(
                                notification = notif,
                                onMarkRead = { onMarkRead(notif.id) },
                                onOpenDuty = {
                                    onMarkRead(notif.id)
                                    onOpenDuty(notif.dutyId)
                                }
                            )
                        }
                    }
                }

                // Footer close button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Done",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    notification: AuditorNotification,
    onMarkRead: () -> Unit,
    onOpenDuty: () -> Unit
) {
    Surface(
        color = if (!notification.isRead) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (!notification.isRead) Color(0xFF93C5FD) else AgBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!notification.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AgRoyalBlue)
                        )
                    }
                    Text(
                        text = notification.title,
                        fontSize = 13.sp,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        color = AgDarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    text = notification.createdAt.take(16).replace("T", " "),
                    fontSize = 10.sp,
                    color = AgMutedText
                )
            }

            ExpandableTextWithMore(
                text = notification.message,
                maxLinesCollapsed = 2,
                thresholdChars = 90,
                textColor = Color(0xFF334155),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!notification.dutyId.isNullOrBlank()) {
                    TextButton(
                        onClick = onOpenDuty,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "View Duty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgPrimary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AgPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (!notification.isRead) {
                    TextButton(
                        onClick = onMarkRead,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Mark read",
                            fontSize = 11.sp,
                            color = AgMutedText
                        )
                    }
                }
            }
        }
    }
}

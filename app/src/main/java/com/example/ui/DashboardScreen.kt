package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Enrollment
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

@Composable
fun DashboardScreen(
    displayName: String,
    userRole: String,
    filter: SchoolSearchFilter,
    nearbySchools: List<SchoolWithDistance>,
    savedSchoolsCount: Int,
    enrollmentsState: UiState<List<Enrollment>>,
    conversationCount: Int,
    isPlatformAdmin: Boolean = false,
    onNavigateTab: (MainTab) -> Unit,
    onRefreshOnline: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val enrollments = (enrollmentsState as? UiState.Success)?.data.orEmpty()
    val verifiedOnlineCount = nearbySchools.count { it.school.isVerifiedOnline() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Minimalist Greeting Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Welcome back, ${displayName.ifBlank { "Member" }}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${userRole.replaceFirstChar { it.uppercase() }} workspace • ${filter.centerLabel} (${filter.radiusKm} km radius)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onNavigateTab(MainTab.DISCOVER) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_explore_button")
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Explore Schools", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onRefreshOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_google_sync_button")
                        ) {
                            Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Sync Google", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 2x2 Minimal KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = "Verified Nearby",
                        value = "${nearbySchools.size}",
                        caption = "$verifiedOnlineCount grounded online",
                        icon = Icons.Filled.School,
                        onClick = { onNavigateTab(MainTab.DISCOVER) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_nearby")
                    )
                    DashboardStatCard(
                        title = "Saved Schools",
                        value = "$savedSchoolsCount",
                        caption = "Your shortlisted picks",
                        icon = Icons.Filled.Bookmark,
                        onClick = { onNavigateTab(MainTab.SAVED) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_saved")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = "Applications",
                        value = "${enrollments.size}",
                        caption = "Inquiries & admissions",
                        icon = Icons.Filled.FamilyRestroom,
                        onClick = { onNavigateTab(MainTab.PORTAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_applications")
                    )
                    DashboardStatCard(
                        title = "Conversations",
                        value = "$conversationCount",
                        caption = "Direct school threads",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = { onNavigateTab(MainTab.MESSAGES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_messages")
                    )
                }
            }
        }

        // Quick Tools & Destinations
        item {
            Text(
                text = "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardActionRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Skolquik AI Advisor",
                    subtitle = "Compare curricula, fees, and Google-verified options live",
                    onClick = { onNavigateTab(MainTab.AI_ADVISOR) },
                    testTag = "dashboard_quick_ai"
                )
                DashboardActionRow(
                    icon = Icons.Filled.CastForEducation,
                    title = "Progressive Tutors",
                    subtitle = "Find real Cambridge & WAEC tutoring providers via Google",
                    onClick = { onNavigateTab(MainTab.TUTORS) },
                    testTag = "dashboard_quick_tutors"
                )
                DashboardActionRow(
                    icon = Icons.Filled.Campaign,
                    title = "Sponsored Adverts",
                    subtitle = "Manage campaigns & view active school promotions",
                    onClick = { onNavigateTab(MainTab.ADVERTS) },
                    testTag = "dashboard_quick_adverts"
                )
                DashboardActionRow(
                    icon = Icons.Filled.Settings,
                    title = "Settings & Privacy",
                    subtitle = "Customize sunrise/sunset theme, highlight hue & location privacy",
                    onClick = { onNavigateTab(MainTab.SETTINGS) },
                    testTag = "dashboard_quick_settings"
                )
                if (isPlatformAdmin) {
                    DashboardActionRow(
                        icon = Icons.Filled.Security,
                        title = "Platform Admin",
                        subtitle = "View signed-up individuals & schools, app visits, and manage admins",
                        onClick = { onNavigateTab(MainTab.PLATFORM_ADMIN) },
                        testTag = "dashboard_quick_platform_admin"
                    )
                }
            }
        }

        // Recent Enrollment Inquiries Snapshot
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Applications & Inquiries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigateTab(MainTab.PORTAL) }) {
                    Text("Open Portal")
                }
            }
        }

        if (enrollments.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No applications submitted yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Open any verified school in Discover to submit an enrollment inquiry or track admissions status here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(enrollments.take(4), key = { it.id }) { enr ->
                val statusColor = when (enr.status) {
                    "APPROVED" -> StatusPresentGreen
                    "PENDING" -> StatusLateAmber
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = enr.schoolName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${enr.studentName} • ${enr.gradeApplying}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AttributeBadge(
                            text = enr.status,
                            containerColor = statusColor.copy(alpha = 0.14f),
                            contentColor = statusColor
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    caption: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

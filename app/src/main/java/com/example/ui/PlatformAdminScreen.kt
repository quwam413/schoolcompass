package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppVisitLog
import com.example.data.AppVisitStats
import com.example.data.CloudSqlAdminOverview
import com.example.data.CloudSqlConnectionStatus
import com.example.data.PlatformAdminRecord
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.UserProfile
import com.example.ui.theme.StatusPresentGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformAdminScreen(
    isPlatformAdmin: Boolean,
    isBootstrappedOwner: Boolean,
    currentUserEmail: String,
    currentUserId: String = "",
    allUsersState: UiState<List<UserProfile>>,
    schoolsState: UiState<List<School>>,
    platformAdminsState: UiState<List<PlatformAdminRecord>>,
    visitStatsState: UiState<AppVisitStats?>,
    visitLogsState: UiState<List<AppVisitLog>>,
    sqlAdminOverview: CloudSqlAdminOverview? = null,
    cloudSqlStatus: CloudSqlConnectionStatus = CloudSqlConnectionStatus(),
    isBusy: Boolean = false,
    onGrantAdmin: (UserProfile) -> Unit,
    onRevokeAdmin: (userId: String, email: String, displayName: String) -> Unit,
    onDeleteUser: (UserProfile, String) -> Unit = { _, _ -> },
    onVerifySchool: (School) -> Unit,
    onRefreshSqlOverview: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    if (!isPlatformAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("platform_admin_restricted"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Restricted access",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Admin Access Restricted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Only the platform owner (${SchoolConstants.PLATFORM_ADMIN_EMAIL}) and authorized admins can view this page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.testTag("restricted_back_button")
                    ) {
                        Text("Return to Discover")
                    }
                }
            }
        }
        return
    }

    val allUsers = (allUsersState as? UiState.Success)?.data.orEmpty()
    val allSchools = (schoolsState as? UiState.Success)?.data.orEmpty()
    val registeredSchools = remember(allSchools) { allSchools.filter { it.hasPlatformAdmin() } }
    val delegatedAdmins = (platformAdminsState as? UiState.Success)?.data.orEmpty()
    val delegatedAdminUids = remember(delegatedAdmins) {
        delegatedAdmins.map { it.userId.ifBlank { it.id } }.toSet()
    }
    val delegatedAdminEmails = remember(delegatedAdmins) {
        delegatedAdmins.map { it.email.trim().lowercase() }.toSet()
    }

    val visitStats = (visitStatsState as? UiState.Success)?.data
    val visitLogs = (visitLogsState as? UiState.Success)?.data.orEmpty()

    val totalVisits = maxOf(visitStats?.totalVisits ?: 0L, sqlAdminOverview?.totalVisits ?: 0L, visitLogs.size.toLong())
    val androidVisits = maxOf(visitStats?.androidVisits ?: 0L, sqlAdminOverview?.androidVisits ?: 0L)
    val webVisits = maxOf(visitStats?.webVisits ?: 0L, sqlAdminOverview?.webVisits ?: 0L)

    val individuals = remember(allUsers) { allUsers.filter { it.role != "admin" } }
    val schoolUsers = remember(allUsers) { allUsers.filter { it.role == "admin" } }

    var selectedSegment by remember { mutableIntStateOf(0) } // 0=Individuals, 1=Schools, 2=Admins, 3=Visits
    var searchQuery by remember { mutableStateOf("") }
    var pendingRevokeAdmin by remember { mutableStateOf<PlatformAdminRecord?>(null) }
    var pendingDeleteUser by remember { mutableStateOf<UserProfile?>(null) }
    var deleteReasonText by remember { mutableStateOf("Violated platform rules & regulations") }

    val q = searchQuery.trim().lowercase()
    val filteredIndividuals = remember(individuals, q) {
        if (q.isEmpty()) individuals
        else individuals.filter {
            it.displayName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                (it.phone?.lowercase()?.contains(q) == true) ||
                (it.homeAddress?.lowercase()?.contains(q) == true) ||
                (it.childSummary?.lowercase()?.contains(q) == true)
        }
    }
    val filteredSchoolUsers = remember(schoolUsers, q) {
        if (q.isEmpty()) schoolUsers
        else schoolUsers.filter {
            it.displayName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                (it.phone?.lowercase()?.contains(q) == true) ||
                (it.homeAddress?.lowercase()?.contains(q) == true) ||
                (it.childSummary?.lowercase()?.contains(q) == true)
        }
    }
    val filteredRegisteredSchools = remember(registeredSchools, q) {
        if (q.isEmpty()) registeredSchools
        else registeredSchools.filter {
            it.name.lowercase().contains(q) ||
                it.adminName.lowercase().contains(q) ||
                it.address.lowercase().contains(q)
        }
    }
    val filteredVisitLogs = remember(visitLogs, q) {
        if (q.isEmpty()) visitLogs
        else visitLogs.filter {
            it.displayName.lowercase().contains(q) ||
                it.userEmail.lowercase().contains(q) ||
                it.platform.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("platform_admin_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Platform Admin",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        AttributeBadge(
                            text = if (isBootstrappedOwner) "Owner" else "Admin",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Signed in as ${currentUserEmail.ifBlank { SchoolConstants.PLATFORM_ADMIN_EMAIL }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onRefreshSqlOverview,
                    modifier = Modifier.testTag("admin_refresh_analytics_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Refresh")
                }
            }
        }

        // App Visits Counter Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_visits_card")
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total App Visits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = formatNumber(totalVisits),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("admin_total_visits_value")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlatformVisitBadge(
                            icon = Icons.Filled.Android,
                            label = "Android App",
                            count = androidVisits,
                            modifier = Modifier.weight(1f)
                        )
                        PlatformVisitBadge(
                            icon = Icons.Filled.Language,
                            label = "Web App",
                            count = webVisits,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (totalVisits > 0L) {
                        val androidRatio = (androidVisits.toFloat() / totalVisits.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { androidRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }

                    if (cloudSqlStatus.isConnected) {
                        Text(
                            text = "Cross-platform sync active (${cloudSqlStatus.engine})",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPresentGreen
                        )
                    }
                }
            }
        }

        // Signed-Up Users KPIs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_users_kpi_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStatTile(
                    value = formatNumber(individuals.size.toLong()),
                    label = "Individuals",
                    icon = Icons.Filled.Person,
                    modifier = Modifier.weight(1f).testTag("kpi_individuals")
                )
                AdminStatTile(
                    value = formatNumber(schoolUsers.size.toLong()),
                    label = "School Users",
                    icon = Icons.Filled.School,
                    modifier = Modifier.weight(1f).testTag("kpi_school_users")
                )
                AdminStatTile(
                    value = formatNumber(registeredSchools.size.toLong()),
                    label = "Schools Listed",
                    icon = Icons.Filled.Verified,
                    modifier = Modifier.weight(1f).testTag("kpi_schools_listed")
                )
            }
        }

        // Search input
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search signed-up people, school accounts, or emails...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_directory_search")
            )
        }

        // Segment Tabs
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Individuals (${individuals.size})",
                    "Schools (${schoolUsers.size} / ${registeredSchools.size})",
                    "Admins (${delegatedAdmins.size + 1})",
                    "Visits (${visitLogs.size})"
                ).forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedSegment == idx,
                        onClick = { selectedSegment = idx },
                        label = { Text(label) },
                        modifier = Modifier.testTag("admin_segment_$idx")
                    )
                }
            }
        }

        if (allUsersState is UiState.Loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            when (selectedSegment) {
                0 -> {
                    if (filteredIndividuals.isEmpty()) {
                        item { AdminEmptyStateCard("No individual parent sign-ups match your filter.") }
                    } else {
                        items(filteredIndividuals, key = { "ind_${it.userId.ifBlank { it.id }}" }) { user ->
                            val uid = user.userId.ifBlank { user.id }
                            val isOwnerRow = user.email.equals(SchoolConstants.PLATFORM_ADMIN_EMAIL, ignoreCase = true)
                            val isDelegated = uid in delegatedAdminUids || user.email.lowercase() in delegatedAdminEmails
                            val ownedSchools = registeredSchools.filter { it.adminUid == uid }
                            SignedUpUserCard(
                                user = user,
                                ownedSchools = ownedSchools,
                                isOwnerAccount = isOwnerRow,
                                isDelegatedAdmin = isDelegated,
                                canManageAdmins = isBootstrappedOwner,
                                canDeleteUser = !isOwnerRow && uid != currentUserId,
                                isBusy = isBusy,
                                onMakeAdmin = { onGrantAdmin(user) },
                                onRevokeAdmin = {
                                    onRevokeAdmin(uid, user.email, user.displayName)
                                },
                                onDeleteUser = {
                                    deleteReasonText = "Violated platform rules & regulations"
                                    pendingDeleteUser = user
                                }
                            )
                        }
                    }
                }

                1 -> {
                    if (filteredSchoolUsers.isEmpty() && filteredRegisteredSchools.isEmpty()) {
                        item { AdminEmptyStateCard("No signed-up school accounts or registered schools match your filter.") }
                    } else {
                        if (filteredSchoolUsers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Signed-Up School Accounts (${filteredSchoolUsers.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(filteredSchoolUsers, key = { "sch_usr_${it.userId.ifBlank { it.id }}" }) { user ->
                                val uid = user.userId.ifBlank { user.id }
                                val isOwnerRow = user.email.equals(SchoolConstants.PLATFORM_ADMIN_EMAIL, ignoreCase = true)
                                val isDelegated = uid in delegatedAdminUids || user.email.lowercase() in delegatedAdminEmails
                                val ownedSchools = registeredSchools.filter { it.adminUid == uid }
                                SignedUpUserCard(
                                    user = user,
                                    ownedSchools = ownedSchools,
                                    isOwnerAccount = isOwnerRow,
                                    isDelegatedAdmin = isDelegated,
                                    canManageAdmins = isBootstrappedOwner,
                                    canDeleteUser = !isOwnerRow && uid != currentUserId,
                                    isBusy = isBusy,
                                    onMakeAdmin = { onGrantAdmin(user) },
                                    onRevokeAdmin = {
                                        onRevokeAdmin(uid, user.email, user.displayName)
                                    },
                                    onDeleteUser = {
                                        deleteReasonText = "Violated platform rules & regulations"
                                        pendingDeleteUser = user
                                    }
                                )
                            }
                        }
                        if (filteredRegisteredSchools.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Registered School Campuses (${filteredRegisteredSchools.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(filteredRegisteredSchools, key = { "sch_reg_${it.id}" }) { school ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_registered_school_${school.id}")
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(school.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${school.institutionType} • ${school.curriculum}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            AttributeBadge(
                                                text = school.verificationBadgeLabel(),
                                                containerColor = if (school.verificationStatus == "VERIFIED") StatusPresentGreen.copy(alpha = 0.14f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (school.verificationStatus == "VERIFIED") StatusPresentGreen
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Managed by ${school.adminName} (UID: ${school.adminUid})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${school.address} • GPS ${String.format(Locale.US, "%.4f, %.4f", school.latitude, school.longitude)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = buildString {
                                                append("Phone: ${school.contactPhone?.ifBlank { null } ?: "Not set"}")
                                                append(" • Fees: ${formatSchoolFeeSummary(school)}")
                                                if (school.capacity > 0L) {
                                                    append(" • Enrolled: ${school.enrolledCount}/${school.capacity}")
                                                }
                                                append(if (school.admissionsOpen) " • Admissions Open" else " • Admissions Closed")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(
                                            onClick = { onVerifySchool(school) },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Verify Online")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Primary Platform Owner", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(SchoolConstants.PLATFORM_ADMIN_EMAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("Full authority to manage users and grant/revoke admin access.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AttributeBadge(
                                    text = "OWNER",
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    if (delegatedAdmins.isEmpty()) {
                        item {
                            AdminEmptyStateCard(
                                if (isBootstrappedOwner) {
                                    "No delegated admins yet. Open Individuals or Schools tab and tap 'Make Admin' on any signed-up user."
                                } else {
                                    "No additional delegated admins listed."
                                }
                            )
                        }
                    } else {
                        items(delegatedAdmins, key = { "adm_${it.userId.ifBlank { it.id }}" }) { admin ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("delegated_admin_card_${admin.userId.ifBlank { admin.id }}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(admin.displayName.ifBlank { admin.email }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(admin.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = "Granted by ${admin.grantedByEmail} • ${formatTimestamp(admin.createdAt?.toDate()?.time)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isBootstrappedOwner) {
                                        OutlinedButton(
                                            onClick = { pendingRevokeAdmin = admin },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.testTag("revoke_admin_button_${admin.userId.ifBlank { admin.id }}")
                                        ) {
                                            Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Revoke")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (filteredVisitLogs.isEmpty()) {
                        item { AdminEmptyStateCard("No app visit logs recorded yet.") }
                    } else {
                        items(filteredVisitLogs, key = { "vis_${it.id}" }) { log ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.displayName.ifBlank { log.userEmail.ifBlank { "Member" } },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${log.userEmail} • ${if (log.role == "admin") "School Account" else "Individual"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatTimestamp(log.createdAt?.toDate()?.time),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    AttributeBadge(
                                        text = if (log.platform == "web") "WEB" else "ANDROID",
                                        containerColor = if (log.platform == "web") MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (log.platform == "web") MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRevokeAdmin?.let { adminToRevoke ->
        val targetUid = adminToRevoke.userId.ifBlank { adminToRevoke.id }
        AlertDialog(
            onDismissRequest = { pendingRevokeAdmin = null },
            title = { Text("Revoke Admin Access?") },
            text = {
                Text("${adminToRevoke.displayName.ifBlank { adminToRevoke.email }} will immediately lose access to the Platform Admin page.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRevokeAdmin(targetUid, adminToRevoke.email, adminToRevoke.displayName)
                        pendingRevokeAdmin = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revoke Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevokeAdmin = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingDeleteUser?.let { targetUser ->
        val targetLabel = targetUser.displayName.ifBlank { targetUser.email }
        AlertDialog(
            onDismissRequest = { pendingDeleteUser = null },
            modifier = Modifier.testTag("delete_user_confirm_dialog"),
            title = { Text("Delete Account for Rule Violation?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This permanently removes $targetLabel (${targetUser.email}) along with their schools, adverts, and tutor listings from the platform.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = deleteReasonText,
                        onValueChange = { deleteReasonText = it },
                        label = { Text("Violation reason") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_user_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(targetUser, deleteReasonText)
                        pendingDeleteUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_user_button")
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlatformVisitBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Column {
                Text(formatNumber(count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminStatTile(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignedUpUserCard(
    user: UserProfile,
    ownedSchools: List<School> = emptyList(),
    isOwnerAccount: Boolean,
    isDelegatedAdmin: Boolean,
    canManageAdmins: Boolean,
    canDeleteUser: Boolean = false,
    isBusy: Boolean,
    onMakeAdmin: () -> Unit,
    onRevokeAdmin: () -> Unit,
    onDeleteUser: () -> Unit = {}
) {
    val uid = user.userId.ifBlank { user.id }
    val roleLabel = if (user.role == "admin") "School Account" else "Individual"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_card_$uid")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName.ifBlank { user.email },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Account UID: $uid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttributeBadge(
                        text = roleLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                    if (isOwnerAccount || isDelegatedAdmin) {
                        AttributeBadge(
                            text = if (isOwnerAccount) "OWNER" else "ADMIN",
                            containerColor = StatusPresentGreen.copy(alpha = 0.15f),
                            contentColor = StatusPresentGreen
                        )
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SignupDetailLine(
                        icon = Icons.Filled.Email,
                        text = "Email: ${user.email.ifBlank { "Not provided" }}"
                    )
                    SignupDetailLine(
                        icon = Icons.Filled.Phone,
                        text = "Phone: ${user.phone?.ifBlank { null } ?: "Not provided"}"
                    )
                    SignupDetailLine(
                        icon = Icons.Filled.LocationOn,
                        text = "Address: ${user.homeAddress?.ifBlank { null } ?: "Not set"} • GPS ${String.format(Locale.US, "%.4f, %.4f", user.homeLat, user.homeLng)} • Radius ${user.searchRadiusKm} km"
                    )
                    SignupDetailLine(
                        icon = Icons.Filled.Schedule,
                        text = "Signed up: ${formatTimestamp(user.createdAt?.toDate()?.time)}"
                    )
                    if (!user.childSummary.isNullOrBlank()) {
                        SignupDetailLine(
                            icon = Icons.Filled.Person,
                            text = if (user.role == "admin") {
                                "Institution Notes: ${user.childSummary}"
                            } else {
                                "Child / Student Info: ${user.childSummary}"
                            }
                        )
                    }
                }
            }

            if (ownedSchools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Linked Schools (${ownedSchools.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ownedSchools.forEach { school ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${school.name} • ${school.institutionType} • ${school.curriculum} • ${school.verificationBadgeLabel()} • ${formatSchoolFeeSummary(school)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if ((canManageAdmins && !isOwnerAccount) || canDeleteUser) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canManageAdmins && !isOwnerAccount) {
                        if (isDelegatedAdmin) {
                            OutlinedButton(
                                onClick = onRevokeAdmin,
                                enabled = !isBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("user_revoke_admin_$uid")
                            ) {
                                Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Revoke Admin")
                            }
                        } else {
                            Button(
                                onClick = onMakeAdmin,
                                enabled = !isBusy,
                                modifier = Modifier.testTag("user_make_admin_$uid")
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Make Admin")
                            }
                        }
                    }
                    if (canDeleteUser) {
                        OutlinedButton(
                            onClick = onDeleteUser,
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("delete_user_button_$uid")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Delete User")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignupDetailLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdminEmptyStateCard(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun formatNumber(value: Long): String =
    NumberFormat.getIntegerInstance().format(value)

private fun formatSchoolFeeSummary(school: School): String {
    if (school.feeMin <= 0L && school.feeMax <= 0L) {
        return "${school.tuitionTier.ifBlank { "$$" }} (${school.feeCurrency})"
    }
    val fmt = NumberFormat.getIntegerInstance()
    val range = if (school.feeMax > school.feeMin && school.feeMin > 0L) {
        "${fmt.format(school.feeMin)}–${fmt.format(school.feeMax)}"
    } else {
        fmt.format(maxOf(school.feeMin, school.feeMax))
    }
    return "${school.feeCurrency} $range ${school.feePeriod}".trim()
}

private fun formatTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Just now"
    return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(java.util.Date(millis))
}

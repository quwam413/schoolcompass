package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Announcement
import com.example.data.AttendanceLog
import com.example.data.Enrollment
import com.example.data.NotificationHelper
import com.example.data.PaymentTransaction
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolWithDistance
import com.example.data.StudentRecord
import com.example.data.SubjectGrade
import com.example.ui.theme.FavoriteHeartColor
import com.example.ui.theme.StatusAbsentRed
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentPortalScreen(
    studentsState: UiState<List<StudentRecord>>,
    selectedStudentId: String?,
    attendanceState: UiState<List<AttendanceLog>>,
    gradesState: UiState<List<SubjectGrade>>,
    enrollmentsState: UiState<List<Enrollment>>,
    announcementsState: UiState<List<Announcement>>,
    myPaymentsState: UiState<List<PaymentTransaction>> = UiState.Success(emptyList()),
    favoriteSchools: List<SchoolWithDistance> = emptyList(),
    isBusy: Boolean = false,
    onSelectStudent: (String) -> Unit,
    onExploreSchools: () -> Unit,
    onToggleFavoriteSchool: (School) -> Unit = {},
    onPayEnrollmentFee: (Enrollment, Long, String, PaymentSubmissionDraft) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    var alertsEnabled by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        alertsEnabled = granted
    }

    // 0 = Progress & Attendance, 1 = Applications, 2 = Alerts & Events, 3 = Payments & Receipts
    var sectionIndex by remember { mutableIntStateOf(0) }
    var selectedAlertCategory by remember { mutableStateOf<String?>(null) }
    var payingEnrollment by remember { mutableStateOf<Enrollment?>(null) }
    var viewingReceipt by remember { mutableStateOf<PaymentTransaction?>(null) }

    val payments = (myPaymentsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_portal_academic),
                    contentDescription = "Secure parent academic portal header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Secure Parent Portal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Live attendance, grades, applications, receipts & alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }

        // Push Notification Permission Banner
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (alertsEnabled) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (alertsEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        tint = if (alertsEnabled) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (alertsEnabled) "Push Alerts Active" else "Enable Push Announcements",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (alertsEnabled) "Receiving real-time notifications for urgent notices & events."
                            else "Allow notifications for urgent school announcements and event dates.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!alertsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.testTag("enable_push_notifications_button")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }
        }

        // Sub-navigation chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = sectionIndex == 0,
                    onClick = { sectionIndex = 0 },
                    label = { Text("Student Progress") },
                    modifier = Modifier.testTag("portal_tab_progress")
                )
                FilterChip(
                    selected = sectionIndex == 1,
                    onClick = { sectionIndex = 1 },
                    label = { Text("Applications") },
                    modifier = Modifier.testTag("portal_tab_applications")
                )
                FilterChip(
                    selected = sectionIndex == 2,
                    onClick = { sectionIndex = 2 },
                    label = { Text("Announcements & Events") },
                    modifier = Modifier.testTag("portal_tab_announcements")
                )
                FilterChip(
                    selected = sectionIndex == 3,
                    onClick = { sectionIndex = 3 },
                    label = { Text("Payments & Receipts (${payments.size})") },
                    modifier = Modifier.testTag("portal_tab_payments")
                )
                FilterChip(
                    selected = sectionIndex == 4,
                    onClick = { sectionIndex = 4 },
                    label = { Text("Saved Schools (${favoriteSchools.size})") },
                    modifier = Modifier.testTag("portal_tab_favorites")
                )
            }
        }

        when (sectionIndex) {
            0 -> {
                when (studentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item {
                        ErrorBanner(studentsState.message)
                    }
                    is UiState.Success -> {
                        val students = studentsState.data
                        if (students.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.School,
                                    title = "No enrolled students linked yet",
                                    subtitle = "Apply to a school in Discover. Once the school administrator approves the enrollment, your child's live attendance and subject grades appear here.",
                                    actionLabel = "Discover Schools",
                                    onAction = onExploreSchools
                                )
                            }
                        } else {
                            val activeStudent = students.firstOrNull { it.id == selectedStudentId } ?: students.first()
                            if (students.size > 1) {
                                item {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        students.forEach { stu ->
                                            FilterChip(
                                                selected = stu.id == activeStudent.id,
                                                onClick = { onSelectStudent(stu.id) },
                                                label = { Text("${stu.studentName} • ${stu.gradeLevel}") }
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = activeStudent.studentName,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${activeStudent.schoolName} • ${activeStudent.gradeLevel}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            PortalMetricTile(
                                                label = "Attendance",
                                                value = "${activeStudent.attendanceRate}%",
                                                accent = StatusPresentGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                            PortalMetricTile(
                                                label = "Overall Avg",
                                                value = "${activeStudent.overallAverage}%",
                                                accent = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            PortalMetricTile(
                                                label = "GPA",
                                                value = String.format("%.2f", activeStudent.gpa),
                                                accent = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Present: ${activeStudent.presentDays}d", style = MaterialTheme.typography.labelMedium, color = StatusPresentGreen)
                                            Text("Tardy: ${activeStudent.tardyDays}d", style = MaterialTheme.typography.labelMedium, color = StatusLateAmber)
                                            Text("Absent: ${activeStudent.absentDays}d", style = MaterialTheme.typography.labelMedium, color = StatusAbsentRed)
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Academic Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val grades = (gradesState as? UiState.Success)?.data ?: emptyList()
                            if (grades.isEmpty()) {
                                item {
                                    Text(
                                        text = "No subject grades recorded yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(grades, key = { it.id }) { grade ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
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
                                                Text(grade.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Surface(
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "${grade.letterGrade} (${grade.score.toInt()}%)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            LinearProgressIndicator(
                                                progress = { (grade.score / 100.0).toFloat().coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().height(6.dp)
                                            )
                                            Text(
                                                text = "${grade.term}${if (!grade.teacherRemark.isNullOrBlank()) " • ${grade.teacherRemark}" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Recent Attendance Log",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val logs = (attendanceState as? UiState.Success)?.data ?: emptyList()
                            if (logs.isEmpty()) {
                                item {
                                    Text(
                                        text = "No daily attendance logs yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(logs, key = { it.id }) { log ->
                                    val statusColor = when (log.status) {
                                        "PRESENT", "EXCUSED" -> StatusPresentGreen
                                        "TARDY" -> StatusLateAmber
                                        else -> StatusAbsentRed
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(log.dateLabel, style = MaterialTheme.typography.titleSmall)
                                                if (!log.note.isNullOrBlank()) {
                                                    Text(log.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = statusColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = log.status,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = statusColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

            1 -> {
                when (enrollmentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(enrollmentsState.message) }
                    is UiState.Success -> {
                        val apps = enrollmentsState.data
                        if (apps.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.CheckCircle,
                                    title = "No enrollment applications yet",
                                    subtitle = "Select a school from Discover and submit an application to track admissions decisions in real time.",
                                    actionLabel = "Browse Schools",
                                    onAction = onExploreSchools
                                )
                            }
                        } else {
                            items(apps, key = { it.id }) { enr ->
                                val badgeColor = when (enr.status) {
                                    "APPROVED" -> StatusPresentGreen
                                    "WAITLISTED" -> StatusLateAmber
                                    "DECLINED" -> StatusAbsentRed
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                val payStatus = enr.paymentStatus ?: "UNPAID"
                                val payColor = paymentStatusColor(payStatus)
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
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
                                            Text(enr.schoolName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                AttributeBadge(
                                                    text = "Fee: $payStatus",
                                                    containerColor = payColor.copy(alpha = 0.14f),
                                                    contentColor = payColor
                                                )
                                                AttributeBadge(
                                                    text = enr.status,
                                                    containerColor = badgeColor.copy(alpha = 0.15f),
                                                    contentColor = badgeColor
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Student: ${enr.studentName} • Applying for ${enr.gradeApplying}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!enr.adminResponse.isNullOrBlank()) {
                                            Text(
                                                text = "Admin Note: ${enr.adminResponse}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (payStatus == "UNPAID" && enr.status != "DECLINED") {
                                            Button(
                                                onClick = { payingEnrollment = enr },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("pay_app_fee_${enr.id}")
                                            ) {
                                                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text("Pay Application Fee")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedAlertCategory == null,
                            onClick = { selectedAlertCategory = null },
                            label = { Text("All") }
                        )
                        listOf("Urgent", "Event", "Academic", "Admissions").forEach { cat ->
                            FilterChip(
                                selected = selectedAlertCategory == cat,
                                onClick = { selectedAlertCategory = if (selectedAlertCategory == cat) null else cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
                when (announcementsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(announcementsState.message) }
                    is UiState.Success -> {
                        val filtered = announcementsState.data.filter {
                            selectedAlertCategory == null || it.category == selectedAlertCategory
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Campaign,
                                    title = "No announcements yet",
                                    subtitle = "School administrators broadcast urgent alerts, open house events, and term schedules here."
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { ann ->
                                val catColor = when (ann.category) {
                                    "Urgent" -> StatusAbsentRed
                                    "Event" -> MaterialTheme.colorScheme.secondary
                                    "Academic" -> MaterialTheme.colorScheme.primary
                                    else -> StatusPresentGreen
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
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
                                            Text(ann.schoolName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = catColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = ann.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = catColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(ann.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!ann.eventDate.isNullOrBlank()) {
                                            AssistChip(
                                                onClick = {},
                                                leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                label = { Text(ann.eventDate) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                when (myPaymentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(myPaymentsState.message) }
                    is UiState.Success -> {
                        if (payments.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.ReceiptLong,
                                    title = "No payment receipts yet",
                                    subtitle = "School application fees, tutor sessions, and paid advert receipts appear here immediately after checkout."
                                )
                            }
                        } else {
                            items(payments, key = { "my_pay_${it.id}" }) { pay ->
                                PaymentTransactionCard(
                                    payment = pay,
                                    isIncoming = false,
                                    showVerifyButton = false,
                                    onViewReceipt = { viewingReceipt = pay }
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                if (favoriteSchools.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Favorite,
                            title = "No favorite schools saved yet",
                            subtitle = "Tap the heart icon on any school card or inside SchoolDetailView to save institutions to your Firestore profile.",
                            actionLabel = "Discover Schools",
                            onAction = onExploreSchools
                        )
                    }
                } else {
                    items(favoriteSchools, key = { "fav_${it.school.id}" }) { item ->
                        val school = item.school
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = school.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${school.institutionType} • ${school.curriculum} • ${String.format("%.1f", item.distanceKm)} km",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = school.formattedFeeRange(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusPresentGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleFavoriteSchool(school) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Remove ${school.name} from favorites",
                                        tint = FavoriteHeartColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    payingEnrollment?.let { enr ->
        PaymentCheckoutDialog(
            title = "Pay Application Fee",
            subtitle = "${enr.schoolName} • ${enr.studentName}",
            recipientName = enr.schoolName,
            amount = SchoolConstants.DEFAULT_APPLICATION_FEE_NGN,
            currency = "NGN",
            isBusy = isBusy,
            allowPayLater = false,
            onDismiss = { payingEnrollment = null },
            onConfirmPayment = { draft ->
                onPayEnrollmentFee(enr, SchoolConstants.DEFAULT_APPLICATION_FEE_NGN, "NGN", draft)
                payingEnrollment = null
            }
        )
    }

    viewingReceipt?.let { pay ->
        PaymentReceiptDialog(
            payment = pay,
            onDismiss = { viewingReceipt = null }
        )
    }
}

@Composable
private fun PortalMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

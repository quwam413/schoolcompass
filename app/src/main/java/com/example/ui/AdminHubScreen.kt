package com.example.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Advert
import com.example.data.AdvertInquiry
import com.example.data.AttendanceLog
import com.example.data.Enrollment
import com.example.data.PaymentTransaction
import com.example.data.ProgressiveTutor
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolVerificationResult
import com.example.data.SchoolWithDistance
import com.example.data.StudentRecord
import com.example.data.SubjectGrade
import com.example.data.TutorSession
import com.example.data.UserProfile
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminHubScreen(
    currentUserId: String,
    adminName: String,
    defaultLat: Double,
    defaultLng: Double,
    isPlatformAdmin: Boolean = false,
    initialOpenOversight: Boolean = false,
    schoolsState: UiState<List<School>>,
    unclaimedExternalSchools: List<SchoolWithDistance> = emptyList(),
    claimingExternalSchool: School? = null,
    adminEnrollmentsState: UiState<List<Enrollment>>,
    adminStudentsState: UiState<List<StudentRecord>>,
    selectedAdminStudentId: String?,
    adminAttendanceState: UiState<List<AttendanceLog>>,
    adminGradesState: UiState<List<SubjectGrade>>,
    receivedPaymentsState: UiState<List<PaymentTransaction>> = UiState.Success(emptyList()),
    adminAllUsersState: UiState<List<UserProfile>> = UiState.Success(emptyList()),
    adminAllEnrollmentsState: UiState<List<Enrollment>> = UiState.Success(emptyList()),
    adminAllTutorSessionsState: UiState<List<TutorSession>> = UiState.Success(emptyList()),
    adminAllInquiriesState: UiState<List<AdvertInquiry>> = UiState.Success(emptyList()),
    adminAllPaymentsState: UiState<List<PaymentTransaction>> = UiState.Success(emptyList()),
    advertsState: UiState<List<Advert>> = UiState.Success(emptyList()),
    tutorsUiState: UiState<List<ProgressiveTutor>> = UiState.Success(emptyList()),
    isBusy: Boolean,
    onSaveSchool: (School, Boolean) -> Unit,
    onClearClaimingSchool: () -> Unit = {},
    onVerifyDraftSchool: (School, (SchoolVerificationResult) -> Unit) -> Unit = { _, _ -> },
    onVerifyRegisteredSchool: (School) -> Unit = {},
    onUpdateEnrollmentStatus: (Enrollment, String, String?) -> Unit,
    onSelectAdminStudent: (String) -> Unit,
    onLogAttendance: (StudentRecord, String, String, String?) -> Unit,
    onAddGrade: (StudentRecord, String, Double, String, String?) -> Unit,
    onPublishAnnouncement: (School, String, String, String, String?) -> Unit,
    onConfirmPaymentPaid: (PaymentTransaction) -> Unit = {}
) {
    // 0 = Schools, 1 = Enrollments, 2 = Students, 3 = Alerts, 4 = Payments, 5 = Platform Oversight
    var adminTab by remember { mutableIntStateOf(if (initialOpenOversight && isPlatformAdmin) 5 else 0) }
    var oversightCategory by remember { mutableIntStateOf(0) }
    var showSchoolEditor by remember { mutableStateOf(false) }
    var editingSchool by remember { mutableStateOf<School?>(null) }
    var viewingReceipt by remember { mutableStateOf<PaymentTransaction?>(null) }

    LaunchedEffect(initialOpenOversight, isPlatformAdmin) {
        if (initialOpenOversight && isPlatformAdmin) {
            adminTab = 5
        }
    }

    LaunchedEffect(claimingExternalSchool) {
        if (claimingExternalSchool != null) {
            adminTab = 0
            editingSchool = claimingExternalSchool
            showSchoolEditor = true
        }
    }

    var attendanceStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var gradeStudent by remember { mutableStateOf<StudentRecord?>(null) }

    val allSchools = (schoolsState as? UiState.Success)?.data ?: emptyList()
    val mySchools = remember(allSchools, currentUserId) {
        allSchools.filter { it.adminUid == currentUserId }
    }
    val receivedPayments = (receivedPaymentsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "School Admin Hub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Update profiles, fees, enrollment & student records live",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingSchool = null
                        showSchoolEditor = true
                    },
                    modifier = Modifier.testTag("admin_register_school_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Register")
                }
            }
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isPlatformAdmin) {
                    StatusPresentGreen.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPlatformAdmin) {
                        "Super Admin Oversight Active (${SchoolConstants.PLATFORM_ADMIN_EMAIL})"
                    } else {
                        "School Admin Mode • Global Platform Oversight requires Super Admin (${SchoolConstants.PLATFORM_ADMIN_EMAIL})"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPlatformAdmin) StatusPresentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = adminTab == 0,
                    onClick = { adminTab = 0 },
                    label = { Text("My Schools (${mySchools.size})") },
                    modifier = Modifier.testTag("admin_tab_schools")
                )
                FilterChip(
                    selected = adminTab == 1,
                    onClick = { adminTab = 1 },
                    label = { Text("Enrollments") },
                    modifier = Modifier.testTag("admin_tab_enrollments")
                )
                FilterChip(
                    selected = adminTab == 2,
                    onClick = { adminTab = 2 },
                    label = { Text("Students") },
                    modifier = Modifier.testTag("admin_tab_students")
                )
                FilterChip(
                    selected = adminTab == 3,
                    onClick = { adminTab = 3 },
                    label = { Text("Alerts") },
                    modifier = Modifier.testTag("admin_tab_alerts")
                )
                FilterChip(
                    selected = adminTab == 4,
                    onClick = { adminTab = 4 },
                    label = { Text("Payments (${receivedPayments.size})") },
                    modifier = Modifier.testTag("admin_tab_payments")
                )
                if (isPlatformAdmin) {
                    FilterChip(
                        selected = adminTab == 5,
                        onClick = { adminTab = 5 },
                        label = { Text("Oversight (All)") },
                        modifier = Modifier.testTag("admin_tab_oversight")
                    )
                }
            }
        }

        when (adminTab) {
            0 -> {
                if (unclaimedExternalSchools.isNotEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Verified Online Schools Nearby (${unclaimedExternalSchools.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "These schools are already visible and searchable online. Tap one below if you are the school's administrator and want to claim & enrich its profile:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    unclaimedExternalSchools.take(8).forEach { ext ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                editingSchool = ext.school
                                                showSchoolEditor = true
                                            },
                                            label = { Text("${ext.school.name} (${String.format("%.1f", ext.distanceKm)}km)") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (mySchools.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.School,
                            title = "No schools registered under your account",
                            subtitle = "Pick a nearby geolocated school above to enrich it, or register your school with Nigerian, British, or Dual curriculum, tuition fee ranges, and sports focus.",
                            actionLabel = "Register School Now",
                            onAction = {
                                editingSchool = null
                                showSchoolEditor = true
                            }
                        )
                    }
                } else {
                    items(mySchools, key = { it.id }) { school ->
                        val ratio = (school.enrolledCount.toFloat() / school.capacity.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(school.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${school.curriculum} • ${school.formattedFeeRange()}", style = MaterialTheme.typography.bodySmall, color = StatusPresentGreen)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editingSchool = school
                                            showSchoolEditor = true
                                        },
                                        modifier = Modifier.testTag("edit_school_${school.id}")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Edit")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AttributeBadge(
                                        text = school.verificationBadgeLabel(),
                                        containerColor = if (school.isVerifiedOnline()) {
                                            StatusPresentGreen.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (school.isVerifiedOnline()) {
                                            StatusPresentGreen
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    TextButton(
                                        onClick = { onVerifyRegisteredSchool(school) },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Verify on Internet")
                                    }
                                }
                                if (!school.verificationSource.isNullOrBlank()) {
                                    Text(
                                        text = school.verificationSource,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Enrollment: ${school.enrolledCount} / ${school.capacity} seats (${if (school.admissionsOpen) "Open" else "Closed"})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                )
                                if (school.sportsFocus.isNotEmpty()) {
                                    Text(
                                        text = "Sports Focus: ${school.sportsFocus.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                when (adminEnrollmentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(adminEnrollmentsState.message) }
                    is UiState.Success -> {
                        val list = adminEnrollmentsState.data
                        if (list.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.HowToReg,
                                    title = "No enrollment requests yet",
                                    subtitle = "When parents apply to your registered schools, their applications appear here for instant approval or waitlisting."
                                )
                            }
                        } else {
                            items(list, key = { it.id }) { enr ->
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
                                            Text("${enr.studentName} (${enr.gradeApplying})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                val payStatus = enr.paymentStatus ?: "UNPAID"
                                                AttributeBadge(
                                                    text = "Fee: $payStatus",
                                                    containerColor = if (payStatus == "PAID") StatusPresentGreen.copy(alpha = 0.14f)
                                                    else StatusLateAmber.copy(alpha = 0.14f),
                                                    contentColor = if (payStatus == "PAID") StatusPresentGreen else StatusLateAmber
                                                )
                                                Text(enr.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Text("${enr.schoolName} • Parent: ${enr.parentName}", style = MaterialTheme.typography.bodySmall)
                                        if (!enr.notes.isNullOrBlank()) {
                                            Text("Notes: ${enr.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (enr.status == "PENDING" || enr.status == "WAITLISTED") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                Button(
                                                    onClick = { onUpdateEnrollmentStatus(enr, "APPROVED", "Welcome to ${enr.schoolName}!") },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.weight(1f).testTag("approve_enr_${enr.id}")
                                                ) {
                                                    Text("Approve")
                                                }
                                                OutlinedButton(
                                                    onClick = { onUpdateEnrollmentStatus(enr, "WAITLISTED", "Added to priority waitlist.") },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Waitlist")
                                                }
                                                OutlinedButton(
                                                    onClick = { onUpdateEnrollmentStatus(enr, "DECLINED", "Class capacity reached.") },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Decline")
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

            2 -> {
                when (adminStudentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(adminStudentsState.message) }
                    is UiState.Success -> {
                        val students = adminStudentsState.data
                        if (students.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.School,
                                    title = "No enrolled students yet",
                                    subtitle = "Approve an enrollment application in the Enrollments tab to manage that student's daily attendance and academic grades."
                                )
                            }
                        } else {
                            items(students, key = { it.id }) { stu ->
                                Surface(
                                    onClick = { onSelectAdminStudent(stu.id) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (stu.id == selectedAdminStudentId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("${stu.studentName} • ${stu.gradeLevel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(stu.schoolName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = "Attendance: ${stu.attendanceRate}% • Avg: ${stu.overallAverage}% • GPA: ${String.format("%.2f", stu.gpa)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { attendanceStudent = stu },
                                                modifier = Modifier.weight(1f).testTag("log_attendance_${stu.id}")
                                            ) {
                                                Text("Log Attendance")
                                            }
                                            Button(
                                                onClick = { gradeStudent = stu },
                                                modifier = Modifier.weight(1f).testTag("add_grade_${stu.id}")
                                            ) {
                                                Text("Add Grade")
                                            }
                                        }
                                    }
                                }
                            }
                            val logs = (adminAttendanceState as? UiState.Success)?.data ?: emptyList()
                            val grades = (adminGradesState as? UiState.Success)?.data ?: emptyList()
                            if (logs.isNotEmpty() || grades.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Latest Recorded Entries: ${logs.size} attendance, ${grades.size} grades",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                if (mySchools.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Campaign,
                            title = "Register a school first",
                            subtitle = "Once you register your school, you can broadcast push announcements and event updates to parents.",
                            actionLabel = "Register School",
                            onAction = {
                                editingSchool = null
                                showSchoolEditor = true
                            }
                        )
                    }
                } else {
                    item {
                        AnnouncementComposerCard(
                            schools = mySchools,
                            isBusy = isBusy,
                            onPublish = onPublishAnnouncement
                        )
                    }
                }
            }

            4 -> {
                when (receivedPaymentsState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(receivedPaymentsState.message) }
                    is UiState.Success -> {
                        if (receivedPayments.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Payments,
                                    title = "No incoming payments yet",
                                    subtitle = "Application fees, tutor session fees, and settlements payable to you appear here with verification controls."
                                )
                            }
                        } else {
                            items(receivedPayments, key = { "rec_pay_${it.id}" }) { pay ->
                                PaymentTransactionCard(
                                    payment = pay,
                                    isIncoming = true,
                                    showVerifyButton = true,
                                    onVerifyPaid = { onConfirmPaymentPaid(pay) },
                                    onViewReceipt = { viewingReceipt = pay }
                                )
                            }
                        }
                    }
                }
            }

            5 -> if (isPlatformAdmin) {
                val allUsers = (adminAllUsersState as? UiState.Success)?.data ?: emptyList()
                val allTutors = (tutorsUiState as? UiState.Success)?.data ?: emptyList()
                val allEnrollments = (adminAllEnrollmentsState as? UiState.Success)?.data ?: emptyList()
                val allSessions = (adminAllTutorSessionsState as? UiState.Success)?.data ?: emptyList()
                val allAdverts = (advertsState as? UiState.Success)?.data ?: emptyList()
                val allInquiries = (adminAllInquiriesState as? UiState.Success)?.data ?: emptyList()
                val allPayments = (adminAllPaymentsState as? UiState.Success)?.data ?: emptyList()

                platformOversightItems(
                    selectedCategory = oversightCategory,
                    onSelectCategory = { oversightCategory = it },
                    users = allUsers,
                    schools = allSchools,
                    tutors = allTutors,
                    enrollments = allEnrollments,
                    tutorSessions = allSessions,
                    adverts = allAdverts,
                    inquiries = allInquiries,
                    payments = allPayments,
                    isBusy = isBusy,
                    onVerifySchool = onVerifyRegisteredSchool,
                    onConfirmPaymentPaid = onConfirmPaymentPaid,
                    onViewReceipt = { viewingReceipt = it }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showSchoolEditor) {
        SchoolEditorSheet(
            existingSchool = editingSchool,
            nearbyUnclaimedSchools = unclaimedExternalSchools,
            defaultAdminName = adminName,
            defaultLat = defaultLat,
            defaultLng = defaultLng,
            isBusy = isBusy,
            onVerifyDraft = onVerifyDraftSchool,
            onDismiss = {
                showSchoolEditor = false
                onClearClaimingSchool()
            },
            onSave = { school, isNew ->
                onSaveSchool(school, isNew)
                showSchoolEditor = false
            }
        )
    }

    attendanceStudent?.let { stu ->
        LogAttendanceDialog(
            student = stu,
            onDismiss = { attendanceStudent = null },
            onConfirm = { dateLabel, status, note ->
                onLogAttendance(stu, dateLabel, status, note)
                attendanceStudent = null
            }
        )
    }

    gradeStudent?.let { stu ->
        AddGradeDialog(
            student = stu,
            onDismiss = { gradeStudent = null },
            onConfirm = { subject, score, term, remark ->
                onAddGrade(stu, subject, score, term, remark)
                gradeStudent = null
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnouncementComposerCard(
    schools: List<School>,
    isBusy: Boolean,
    onPublish: (School, String, String, String, String?) -> Unit
) {
    var selectedSchoolId by remember(schools) { mutableStateOf(schools.first().id) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Event") }
    var eventDate by remember { mutableStateOf("") }

    val selectedSchool = schools.firstOrNull { it.id == selectedSchoolId } ?: schools.first()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Broadcast Announcement / Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (schools.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    schools.forEach { sch ->
                        FilterChip(
                            selected = sch.id == selectedSchool.id,
                            onClick = { selectedSchoolId = sch.id },
                            label = { Text(sch.name) }
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchoolConstants.ANNOUNCEMENT_CATEGORIES.forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Headline *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("announcement_title_input")
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Announcement Details *") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("announcement_body_input")
            )
            OutlinedTextField(
                value = eventDate,
                onValueChange = { eventDate = it },
                label = { Text("Event Date / Time (optional)") },
                placeholder = { Text("e.g. Dec 4 • 9:00 AM") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onPublish(selectedSchool, title, body, category, eventDate.ifBlank { null })
                    title = ""
                    body = ""
                    eventDate = ""
                },
                enabled = !isBusy && title.trim().length >= 2 && body.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("publish_announcement_button")
            ) {
                Text("Publish & Notify Parents")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogAttendanceDialog(
    student: StudentRecord,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var dateLabel by remember { mutableStateOf("Today") }
    var status by remember { mutableStateOf("PRESENT") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Attendance • ${student.studentName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = { dateLabel = it },
                    label = { Text("Date Label *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PRESENT", "TARDY", "EXCUSED", "ABSENT").forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(dateLabel, status, note.ifBlank { null }) },
                enabled = dateLabel.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddGradeDialog(
    student: StudentRecord,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String?) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var scoreText by remember { mutableStateOf("88") }
    var term by remember { mutableStateOf("Term 1 2026") }
    var remark by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Grade • ${student.studentName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject *") },
                    placeholder = { Text("e.g., Mathematics, English, Physics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it },
                    label = { Text("Score (0–100) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Term *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("Teacher Remark (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val scoreVal = scoreText.toDoubleOrNull()
            Button(
                onClick = { if (scoreVal != null) onConfirm(subject, scoreVal, term, remark.ifBlank { null }) },
                enabled = subject.isNotBlank() && term.isNotBlank() && scoreVal != null && scoreVal in 0.0..100.0
            ) { Text("Save Grade") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

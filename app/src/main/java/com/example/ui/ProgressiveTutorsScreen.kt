package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ProgressiveTutor
import com.example.data.SchoolConstants
import com.example.data.TutorSearchFilter
import com.example.data.TutorSession
import com.example.data.TutorWithDistance
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusAbsentRed
import com.example.ui.theme.StatusLateAmber
import com.example.ui.theme.StatusPresentGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressiveTutorsScreen(
    currentUserId: String,
    defaultTeacherName: String,
    tutorsUiState: UiState<List<ProgressiveTutor>>,
    filteredTutors: List<TutorWithDistance>,
    filter: TutorSearchFilter,
    clientSessionsState: UiState<List<TutorSession>>,
    teacherSessionsState: UiState<List<TutorSession>>,
    tutorVisitorCounts: Map<String, Int> = emptyMap(),
    isBusy: Boolean,
    onlineTutorsLoading: Boolean = false,
    onlineTutorsError: String? = null,
    onUpdateFilter: ((TutorSearchFilter) -> TutorSearchFilter) -> Unit,
    onResetFilters: () -> Unit,
    onSaveTutorProfile: (ProgressiveTutor, Boolean) -> Unit,
    onInspectTutorPage: (ProgressiveTutor) -> Unit = {},
    onBookSession: (ProgressiveTutor, String, String, String, String, String?, Int, PaymentSubmissionDraft?) -> Unit,
    onPayForSession: (TutorSession, Long, String, PaymentSubmissionDraft) -> Unit = { _, _, _, _ -> },
    onUpdateSessionStatus: (TutorSession, String, String?) -> Unit,
    onDiscoverOnlineTutors: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val fusedClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    var sectionIndex by remember { mutableIntStateOf(0) }
    var showTutorEditor by remember { mutableStateOf(false) }
    var editingTutor by remember { mutableStateOf<ProgressiveTutor?>(null) }
    var bookingTutor by remember { mutableStateOf<ProgressiveTutor?>(null) }
    var confirmingSession by remember { mutableStateOf<TutorSession?>(null) }
    var payingSession by remember { mutableStateOf<TutorSession?>(null) }
    var showAllSubjects by remember { mutableStateOf(false) }

    val gpsScope = androidx.compose.runtime.rememberCoroutineScope()

    @SuppressLint("MissingPermission")
    fun centerClientOnGps() {
        val cts = CancellationTokenSource()
        fun applyTutorGps(lat: Double, lng: Double) {
            gpsScope.launch {
                val placeLabel = com.example.data.GoogleMapsPlatformService.resolveHumanReadableLocation(
                    context = context,
                    latitude = lat,
                    longitude = lng
                )
                onUpdateFilter {
                    it.copy(
                        clientLat = lat,
                        clientLng = lng,
                        clientLabel = placeLabel
                    )
                }
            }
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    applyTutorGps(loc.latitude, loc.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            applyTutorGps(last.latitude, last.longitude)
                        }
                    }
                }
            }
    }

    val gpsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            centerClientOnGps()
        }
    }

    val allTutors = (tutorsUiState as? UiState.Success)?.data ?: emptyList()
    val ownedTutors = remember(allTutors, currentUserId) {
        if (currentUserId.isBlank()) emptyList() else allTutors.filter { it.teacherUid == currentUserId }
    }
    val totalTutorVisitors = remember(ownedTutors, tutorVisitorCounts) {
        ownedTutors.sumOf { tutorVisitorCounts[it.id] ?: 0 }
    }
    val clientSessions = (clientSessionsState as? UiState.Success)?.data ?: emptyList()
    val teacherSessions = (teacherSessionsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Branded Header Banner
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
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
                            Text(
                                text = "THE PROGRESSIVE TUTORS",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Certified online teachers for Cambridge & Nigerian subjects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.CastForEducation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                editingTutor = allTutors.firstOrNull { it.teacherUid == currentUserId }
                                showTutorEditor = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("offer_tutoring_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Offer Tutoring", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onDiscoverOnlineTutors,
                            enabled = !onlineTutorsLoading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("discover_online_tutors_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (onlineTutorsLoading) "Searching…" else "Find via Google", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (ownedTutors.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutor_owner_visits_summary")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Your tutor page visitors",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Your Tutor Page Visitors",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatPageVisitorsLabel(totalTutorVisitors),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Section Switcher: Browse Tutors vs Online Sessions
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "Browse Tutors (${filteredTutors.size})",
                    "Online Sessions (${clientSessions.size + teacherSessions.size})"
                ).forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = sectionIndex == idx,
                        onClick = { sectionIndex = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                        modifier = Modifier.testTag("tutors_section_$idx")
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (sectionIndex == 0) {
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search tutor, Cambridge or Nigerian subject…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tutors_search_input")
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filter Tutors", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onResetFilters) { Text("Reset") }
                        }

                        Text("Tutor Sex", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(null to "All", "Female" to "Female", "Male" to "Male").forEach { (sexOpt, label) ->
                                FilterChip(
                                    selected = filter.sexFilter == sexOpt,
                                    onClick = { onUpdateFilter { it.copy(sexFilter = sexOpt) } },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("tutor_sex_$label")
                                )
                            }
                        }

                        Text("Curriculum Focus", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Cambridge", "Nigerian", "Cambridge & Nigerian").forEach { track ->
                                FilterChip(
                                    selected = filter.curriculumFilter == track,
                                    onClick = { onUpdateFilter { it.copy(curriculumFilter = track) } },
                                    label = { Text(track) },
                                    modifier = Modifier.testTag("tutor_curriculum_$track")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Subjects (Cambridge & Nigerian)", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { showAllSubjects = !showAllSubjects }) {
                                Text(if (showAllSubjects) "Show Compact" else "Show All Subjects")
                            }
                        }

                        val showCambridge = filter.curriculumFilter != "Nigerian"
                        val showNigerian = filter.curriculumFilter != "Cambridge"

                        if (showCambridge) {
                            Text(
                                "Cambridge Subjects",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val camList = if (showAllSubjects) SchoolConstants.CAMBRIDGE_SUBJECTS else SchoolConstants.CAMBRIDGE_SUBJECTS.take(4)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                camList.forEach { subj ->
                                    val active = subj in filter.selectedSubjects
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            onUpdateFilter { cur ->
                                                val next = if (active) cur.selectedSubjects - subj else cur.selectedSubjects + subj
                                                cur.copy(selectedSubjects = next)
                                            }
                                        },
                                        label = { Text(subj.removePrefix("Cambridge ")) }
                                    )
                                }
                            }
                        }

                        if (showNigerian) {
                            Text(
                                "Nigerian Subjects",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            val nigList = if (showAllSubjects) SchoolConstants.NIGERIAN_SUBJECTS else SchoolConstants.NIGERIAN_SUBJECTS.take(4)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                nigList.forEach { subj ->
                                    val active = subj in filter.selectedSubjects
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            onUpdateFilter { cur ->
                                                val next = if (active) cur.selectedSubjects - subj else cur.selectedSubjects + subj
                                                cur.copy(selectedSubjects = next)
                                            }
                                        },
                                        label = { Text(subj) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Distance from Client Location", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "From ${filter.clientLabel} • " +
                                        if (filter.limitByDistance) "Within ${filter.maxDistanceKm} km" else "Sorted nearest first (Any km)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = filter.limitByDistance,
                                onCheckedChange = { enabled ->
                                    onUpdateFilter { it.copy(limitByDistance = enabled) }
                                },
                                modifier = Modifier.testTag("tutor_distance_limit_switch")
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                                            centerClientOnGps()
                                        } else {
                                            gpsPermLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("tutor_gps_button")
                                ) {
                                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("My GPS")
                                }
                            }
                            items(SchoolConstants.PRESET_LOCATIONS) { preset ->
                                FilterChip(
                                    selected = filter.clientLabel == preset.label,
                                    onClick = {
                                        onUpdateFilter {
                                            it.copy(
                                                clientLat = preset.lat,
                                                clientLng = preset.lng,
                                                clientLabel = preset.label
                                            )
                                        }
                                    },
                                    label = { Text(preset.label) }
                                )
                            }
                        }

                        if (filter.limitByDistance) {
                            Slider(
                                value = filter.maxDistanceKm.toFloat(),
                                onValueChange = { km ->
                                    onUpdateFilter { it.copy(maxDistanceKm = km.roundToInt().coerceIn(2, 100)) }
                                },
                                valueRange = 2f..100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tutor_distance_slider")
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(5, 15, 30, 50, 100)) { km ->
                                    FilterChip(
                                        selected = filter.maxDistanceKm == km,
                                        onClick = { onUpdateFilter { it.copy(maxDistanceKm = km) } },
                                        label = { Text("≤ ${km}km") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (tutorsUiState) {
                UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> item { ErrorBanner(tutorsUiState.message) }
                is UiState.Success -> {
                    if (onlineTutorsError != null) {
                        item {
                            ErrorBanner("Online tutor search notice: $onlineTutorsError")
                        }
                    }
                    if (tutorsUiState.data.isEmpty() && filteredTutors.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.CastForEducation,
                                title = "Discover real tutors near ${filter.clientLabel}",
                                subtitle = "Search Google Search & Maps for verified Cambridge & Nigerian tutoring centres or publish your own tutoring profile.",
                                actionLabel = if (onlineTutorsLoading) "Searching Google…" else "Find Real Tutors via Google",
                                onAction = { if (!onlineTutorsLoading) onDiscoverOnlineTutors() }
                            )
                        }
                    } else if (filteredTutors.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.LocationOn,
                                title = "No tutors match your filters",
                                subtitle = "Adjust sex, subject, curriculum track, or widen your distance filter.",
                                actionLabel = "Reset Tutor Filters",
                                onAction = onResetFilters
                            )
                        }
                    } else {
                        items(filteredTutors, key = { it.tutor.id }) { item ->
                            val isOwner = item.tutor.teacherUid == currentUserId && currentUserId.isNotBlank()
                            TutorCard(
                                item = item,
                                isOwner = isOwner,
                                visitorCount = tutorVisitorCounts[item.tutor.id] ?: 0,
                                onBookSession = {
                                    onInspectTutorPage(item.tutor)
                                    bookingTutor = item.tutor
                                },
                                onOpenExternalUrl = { url ->
                                    onInspectTutorPage(item.tutor)
                                    runCatching { uriHandler.openUri(url) }
                                },
                                onEditProfile = {
                                    editingTutor = item.tutor
                                    showTutorEditor = true
                                }
                            )
                        }
                    }
                }
            }
        } else {
            if (teacherSessions.isNotEmpty()) {
                item {
                    Text(
                        "Incoming Student Session Requests (Teacher)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(teacherSessions, key = { "teach_${it.id}" }) { session ->
                    TutorSessionCard(
                        session = session,
                        isTeacherView = true,
                        onConfirm = { confirmingSession = session },
                        onComplete = { onUpdateSessionStatus(session, "COMPLETED", session.meetingLink) },
                        onCancel = { onUpdateSessionStatus(session, "CANCELLED", null) },
                        onPayNow = {},
                        onOpenLink = { url ->
                            runCatching { uriHandler.openUri(url) }
                        }
                    )
                }
            }

            item {
                Text(
                    "My Booked Online Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (clientSessions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.VideoCall,
                        title = "No online tutoring sessions booked yet",
                        subtitle = "Browse THE PROGRESSIVE TUTORS to request a live Cambridge or Nigerian subject session.",
                        actionLabel = "Browse Tutors",
                        onAction = { sectionIndex = 0 }
                    )
                }
            } else {
                items(clientSessions, key = { "client_${it.id}" }) { session ->
                    TutorSessionCard(
                        session = session,
                        isTeacherView = false,
                        onConfirm = {},
                        onComplete = {},
                        onCancel = { onUpdateSessionStatus(session, "CANCELLED", null) },
                        onPayNow = { payingSession = session },
                        onOpenLink = { url ->
                            runCatching { uriHandler.openUri(url) }
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showTutorEditor) {
        TutorEditorDialog(
            existingTutor = editingTutor,
            defaultName = defaultTeacherName,
            defaultLat = filter.clientLat,
            defaultLng = filter.clientLng,
            defaultLocationLabel = filter.clientLabel,
            isBusy = isBusy,
            onDismiss = { showTutorEditor = false },
            onSave = { tutor, isNew ->
                onSaveTutorProfile(tutor, isNew)
                showTutorEditor = false
            }
        )
    }

    bookingTutor?.let { tutor ->
        BookTutorSessionDialog(
            tutor = tutor,
            isBusy = isBusy,
            onDismiss = { bookingTutor = null },
            onBook = { studentName, track, subject, schedule, notes, hours, draft ->
                onBookSession(tutor, studentName, track, subject, schedule, notes, hours, draft)
                bookingTutor = null
                sectionIndex = 1
            }
        )
    }

    confirmingSession?.let { session ->
        ConfirmTutorSessionDialog(
            session = session,
            isBusy = isBusy,
            onDismiss = { confirmingSession = null },
            onConfirm = { link ->
                onUpdateSessionStatus(session, "CONFIRMED", link)
                confirmingSession = null
            }
        )
    }

    payingSession?.let { session ->
        val matchedTutor = allTutors.firstOrNull { it.id == session.tutorId }
        val fee = matchedTutor?.hourlyRate ?: 7_000L
        val curr = matchedTutor?.currency ?: "NGN"
        PaymentCheckoutDialog(
            title = "Pay Tutoring Session",
            subtitle = "${session.subject} • ${session.studentName}",
            recipientName = session.tutorName,
            amount = fee,
            currency = curr,
            isBusy = isBusy,
            allowPayLater = false,
            onDismiss = { payingSession = null },
            onConfirmPayment = { draft ->
                onPayForSession(session, fee, curr, draft)
                payingSession = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorCard(
    item: TutorWithDistance,
    isOwner: Boolean,
    visitorCount: Int = 0,
    onBookSession: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onEditProfile: () -> Unit
) {
    val t = item.tutor
    val isOnlineVerifiedOnly = t.isVerifiedOnlineOnly()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutor_card_${t.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!t.bannerImageUrl.isNullOrBlank()) {
                BannerImage(
                    imageUrl = t.bannerImageUrl,
                    fallbackResId = R.drawable.img_portal_academic,
                    contentDescription = "${t.fullName} display banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("tutor_banner_${t.id}")
                )
            }
            if (isOwner) {
                PageVisitorsBadge(
                    visitorCount = visitorCount,
                    testTag = "tutor_visits_badge_${t.id}"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(t.headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "${t.locationLabel} • ${String.format("%.1f", item.distanceKm)} km from you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = t.formattedHourlyRate(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusPresentGreen
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                        Text("${t.rating}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isOnlineVerifiedOnly) {
                    AttributeBadge(
                        text = "Verified Online",
                        containerColor = StatusPresentGreen.copy(alpha = 0.14f),
                        contentColor = StatusPresentGreen
                    )
                } else {
                    AttributeBadge(
                        text = t.sex,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                AttributeBadge(
                    text = t.meetingPlatform,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!isOnlineVerifiedOnly) {
                    AttributeBadge(
                        text = "${t.yearsExperience} yrs exp",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (t.bio.isNotBlank()) {
                Text(t.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!t.verificationSource.isNullOrBlank()) {
                Text(
                    text = "Source: ${t.verificationSource}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!t.contactPhone.isNullOrBlank()) {
                Text(
                    text = "Phone: ${t.contactPhone}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusPresentGreen
                )
            }

            if (t.cambridgeSubjects.isNotEmpty()) {
                Text("Cambridge:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.cambridgeSubjects.forEach { subj ->
                        AttributeBadge(
                            text = subj,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (t.nigerianSubjects.isNotEmpty()) {
                Text("Nigerian:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.nigerianSubjects.forEach { subj ->
                        AttributeBadge(
                            text = subj,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.availability, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOnlineVerifiedOnly) {
                    Button(
                        onClick = onBookSession,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("book_tutor_${t.id}")
                    ) {
                        Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Book Online Session")
                    }
                } else {
                    val primaryUrl = t.websiteUrl ?: t.googleMapsUrl
                    if (!primaryUrl.isNullOrBlank()) {
                        Button(
                            onClick = { onOpenExternalUrl(primaryUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_tutor_source_${t.id}")
                        ) {
                            Text(if (t.websiteUrl != null) "Visit Website" else "Open Google Maps")
                        }
                    }
                    if (!t.googleMapsUrl.isNullOrBlank() && t.websiteUrl != null) {
                        OutlinedButton(
                            onClick = { onOpenExternalUrl(t.googleMapsUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Google Maps")
                        }
                    }
                }
                if (isOwner) {
                    OutlinedButton(onClick = onEditProfile) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorSessionCard(
    session: TutorSession,
    isTeacherView: Boolean,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onPayNow: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val statusColor = when (session.status) {
        "CONFIRMED" -> StatusPresentGreen
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        "CANCELLED" -> StatusAbsentRed
        else -> StatusLateAmber
    }
    val payStatus = session.paymentStatus ?: "UNPAID"
    val payColor = paymentStatusColor(payStatus)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTeacherView) "${session.studentName} (${session.clientName})" else session.tutorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttributeBadge(
                        text = payStatus,
                        containerColor = payColor.copy(alpha = 0.14f),
                        contentColor = payColor
                    )
                    AttributeBadge(
                        text = session.status,
                        containerColor = statusColor.copy(alpha = 0.16f),
                        contentColor = statusColor
                    )
                }
            }
            Text("${session.subject} • ${session.curriculumTrack}", style = MaterialTheme.typography.bodyMedium)
            Text("Schedule: ${session.preferredSchedule}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.notes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
            session.meetingLink?.let { link ->
                OutlinedButton(
                    onClick = { onOpenLink(link) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Join Class: $link", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (isTeacherView) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.status == "REQUESTED") {
                        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirm + Link") }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Decline") }
                    } else if (session.status == "CONFIRMED") {
                        Button(onClick = onComplete, modifier = Modifier.weight(1f)) { Text("Mark Completed") }
                    }
                }
            } else {
                if (payStatus == "UNPAID" && session.status != "CANCELLED") {
                    Button(
                        onClick = onPayNow,
                        modifier = Modifier.fillMaxWidth().testTag("pay_session_${session.id}")
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Pay Session Fee")
                    }
                }
                if (session.status in listOf("REQUESTED", "CONFIRMED")) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Session")
                    }
                }
            }
        }
    }
}

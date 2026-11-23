package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CastForEducation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthManager
import com.example.auth.AuthScreen
import com.example.data.NetworkMonitor
import com.example.data.NotificationHelper
import com.example.data.PaymentRepository
import com.example.data.SchoolRepository
import com.example.ui.AdminHubScreen
import com.example.ui.AiAdvisorScreen
import com.example.ui.DiscoverScreen
import com.example.ui.MainTab
import com.example.ui.MessagesScreen
import com.example.ui.PaidAdvertsScreen
import com.example.ui.ParentPortalScreen
import com.example.ui.PaymentReceiptDialog
import com.example.ui.ProgressiveTutorsScreen
import com.example.ui.RegistrationPortalScreen
import com.example.ui.SchoolViewModel
import com.example.ui.UiState
import com.example.ui.theme.SchoolCompassTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before any UI or ViewModel creation.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            SchoolCompassTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var preferAdminPortalAfterAuth by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null) {
                preferAdminPortalAfterAuth = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { preferAdmin ->
                preferAdminPortalAfterAuth = preferAdmin
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            AuthenticatedShell(
                currentUserId = user.uid,
                userEmail = user.email ?: "",
                userDisplayName = user.displayName ?: "",
                initialPreferAdminPortal = preferAdminPortalAfterAuth
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    initialPreferAdminPortal: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val networkMonitor = remember(context) { NetworkMonitor(context) }
    DisposableEffect(networkMonitor) {
        onDispose { networkMonitor.unregister() }
    }
    val isNetworkOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
    val connectionLabel by networkMonitor.connectionLabel.collectAsStateWithLifecycle()

    val viewModel: SchoolViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SchoolViewModel(
                    repository = SchoolRepository(db),
                    paymentRepository = PaymentRepository(db),
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    LaunchedEffect(initialPreferAdminPortal) {
        if (initialPreferAdminPortal) {
            viewModel.selectTab(MainTab.ADMIN)
        }
    }

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val schoolsState by viewModel.schoolsState.collectAsStateWithLifecycle()
    val filteredSchools by viewModel.filteredSchools.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val selectedConversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val parentStudentsState by viewModel.parentStudentsState.collectAsStateWithLifecycle()
    val selectedPortalStudentId by viewModel.selectedPortalStudentId.collectAsStateWithLifecycle()
    val activePortalAttendanceState by viewModel.activePortalAttendanceState.collectAsStateWithLifecycle()
    val activePortalGradesState by viewModel.activePortalGradesState.collectAsStateWithLifecycle()
    val parentEnrollmentsState by viewModel.parentEnrollmentsState.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcementsState.collectAsStateWithLifecycle()
    val adminEnrollmentsState by viewModel.adminEnrollmentsState.collectAsStateWithLifecycle()
    val adminStudentsState by viewModel.adminStudentsState.collectAsStateWithLifecycle()
    val selectedAdminStudentId by viewModel.selectedAdminStudentId.collectAsStateWithLifecycle()
    val activeAdminAttendanceState by viewModel.activeAdminAttendanceState.collectAsStateWithLifecycle()
    val activeAdminGradesState by viewModel.activeAdminGradesState.collectAsStateWithLifecycle()
    val advertsState by viewModel.advertsState.collectAsStateWithLifecycle()
    val activeAdvertsWithDistance by viewModel.activeAdvertsWithDistance.collectAsStateWithLifecycle()
    val tutorsState by viewModel.tutorsState.collectAsStateWithLifecycle()
    val tutorFilter by viewModel.tutorFilter.collectAsStateWithLifecycle()
    val filteredTutors by viewModel.filteredTutors.collectAsStateWithLifecycle()
    val clientTutorSessionsState by viewModel.clientTutorSessionsState.collectAsStateWithLifecycle()
    val teacherTutorSessionsState by viewModel.teacherTutorSessionsState.collectAsStateWithLifecycle()
    val unclaimedExternalSchools by viewModel.unclaimedExternalSchools.collectAsStateWithLifecycle()
    val claimingExternalSchool by viewModel.claimingExternalSchool.collectAsStateWithLifecycle()
    val externalDirectoryLoading by viewModel.externalDirectoryLoading.collectAsStateWithLifecycle()
    val externalDirectoryError by viewModel.externalDirectoryError.collectAsStateWithLifecycle()
    val isGeocodingLocation by viewModel.isGeocodingLocation.collectAsStateWithLifecycle()
    val selectedSchoolDetailState by viewModel.selectedSchoolDetailState.collectAsStateWithLifecycle()
    val selectedSchoolAnnouncementsState by viewModel.selectedSchoolAnnouncementsState.collectAsStateWithLifecycle()
    val myPaymentsState by viewModel.myPaymentsState.collectAsStateWithLifecycle()
    val receivedPaymentsState by viewModel.receivedPaymentsState.collectAsStateWithLifecycle()
    val receivedInquiriesState by viewModel.receivedInquiriesState.collectAsStateWithLifecycle()
    val adminAllUsersState by viewModel.adminAllUsersState.collectAsStateWithLifecycle()
    val adminAllEnrollmentsState by viewModel.adminAllEnrollmentsState.collectAsStateWithLifecycle()
    val adminAllTutorSessionsState by viewModel.adminAllTutorSessionsState.collectAsStateWithLifecycle()
    val adminAllInquiriesState by viewModel.adminAllInquiriesState.collectAsStateWithLifecycle()
    val adminAllPaymentsState by viewModel.adminAllPaymentsState.collectAsStateWithLifecycle()
    val latestPaymentReceipt by viewModel.latestPaymentReceipt.collectAsStateWithLifecycle()
    val favoriteSchoolIds by viewModel.favoriteSchoolIds.collectAsStateWithLifecycle()
    val favoriteSchools by viewModel.favoriteSchools.collectAsStateWithLifecycle()
    val aiModelOption by viewModel.aiModelOption.collectAsStateWithLifecycle()
    val aiChatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val aiChatError by viewModel.aiChatError.collectAsStateWithLifecycle()
    val isAiGenerating by viewModel.isAiGenerating.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showProfileEditor by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.newAnnouncementAlerts.collect { announcement ->
            NotificationHelper.postAnnouncementNotification(context, announcement)
        }
    }

    when (val prof = profileState) {
        UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        is UiState.Success -> {
            val profile = prof.data
            if (profile == null || showProfileEditor) {
                RegistrationPortalScreen(
                    existingProfile = profile,
                    defaultName = userDisplayName,
                    defaultEmail = userEmail,
                    isBusy = isBusy,
                    onSaveProfile = { role, name, phone, addr, lat, lng, radius, child ->
                        viewModel.saveUserProfile(
                            existing = profile,
                            role = role,
                            displayName = name,
                            phone = phone,
                            homeAddress = addr,
                            homeLat = lat,
                            homeLng = lng,
                            searchRadiusKm = radius,
                            childSummary = child,
                            onSuccess = {
                                showProfileEditor = false
                                if (initialPreferAdminPortal || (profile == null && role == "admin")) {
                                    viewModel.selectTab(MainTab.ADMIN)
                                }
                            }
                        )
                    },
                    onCancel = if (profile != null) { { showProfileEditor = false } } else null
                )
                return
            }
        }
        is UiState.Error -> {
            // Fall through to main shell with snackbar if transient profile error occurs
        }
    }

    val currentProfile = (profileState as? UiState.Success)?.data
    val navItems = listOf(
        Triple(MainTab.DISCOVER, stringResource(R.string.tab_discover), Pair(Icons.Filled.Explore, Icons.Outlined.Explore)),
        Triple(MainTab.TUTORS, stringResource(R.string.tab_progressive_tutors), Pair(Icons.Filled.CastForEducation, Icons.Outlined.CastForEducation)),
        Triple(MainTab.MESSAGES, stringResource(R.string.tab_messages), Pair(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)),
        Triple(MainTab.PORTAL, stringResource(R.string.tab_portal), Pair(Icons.Filled.FamilyRestroom, Icons.Outlined.FamilyRestroom)),
        Triple(MainTab.ADMIN, stringResource(R.string.tab_admin), Pair(Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings))
    )
    val railItems = navItems + listOf(
        Triple(
            MainTab.ADVERTS,
            stringResource(R.string.tab_adverts),
            Pair(Icons.Filled.Campaign, Icons.Outlined.Campaign)
        ),
        Triple(
            MainTab.AI_ADVISOR,
            stringResource(R.string.tab_ai_advisor),
            Pair(Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_launcher_foreground),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        if (viewModel.isPlatformAdmin) {
                            IconButton(
                                onClick = { viewModel.selectTab(MainTab.ADMIN) },
                                modifier = Modifier.testTag("open_oversight_shortcut")
                            ) {
                                Icon(
                                    Icons.Filled.AdminPanelSettings,
                                    contentDescription = "Platform Super Admin Oversight",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.selectTab(MainTab.AI_ADVISOR) },
                            modifier = Modifier.testTag("open_ai_advisor_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Open Skolquik AI Advisor",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.selectTab(MainTab.ADVERTS) },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .testTag("open_paid_adverts_button")
                        ) {
                            Icon(
                                Icons.Filled.Campaign,
                                contentDescription = "Open Paid Adverts Page",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("Adverts", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = { showProfileEditor = true },
                            modifier = Modifier.testTag("profile_settings_button")
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = "Registration & Profile")
                        }
                        IconButton(
                            onClick = {
                                AuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        navItems.forEach { (tab, label, icons) ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = if (tab == MainTab.TUTORS) {
                                            MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, lineHeight = 10.sp)
                                        } else {
                                            MaterialTheme.typography.labelSmall
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        railItems.forEach { (tab, label, icons) ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        MainTab.DISCOVER -> DiscoverScreen(
                            schoolsUiState = schoolsState,
                            filteredSchools = filteredSchools,
                            filter = searchFilter,
                            isBusy = isBusy,
                            isNetworkOnline = isNetworkOnline,
                            connectionLabel = connectionLabel,
                            externalDirectoryLoading = externalDirectoryLoading,
                            externalDirectoryError = externalDirectoryError,
                            isGeocodingLocation = isGeocodingLocation,
                            onRefreshExternalSchools = viewModel::refreshExternalSchools,
                            onGeocodeSearch = viewModel::geocodeAndMoveSearchCenter,
                            onUpdateFilter = viewModel::updateFilter,
                            onResetFilters = viewModel::resetFilters,
                            onMessageAdmin = viewModel::openChatWithSchool,
                            onSubmitEnrollment = { school, studentName, gradeApplying, notes, draft ->
                                viewModel.submitEnrollment(school, studentName, gradeApplying, notes, draft)
                            },
                            onClaimSchool = viewModel::startClaimingExternalSchool,
                            onSeedSampleSchools = viewModel::seedSampleSchoolsAroundCenter,
                            onRegisterSchoolClick = { viewModel.selectTab(MainTab.ADMIN) },
                            activeAdverts = activeAdvertsWithDistance.filter { it.advert.status == "ACTIVE" },
                            onOpenPaidAdverts = { viewModel.selectTab(MainTab.ADVERTS) },
                            favoriteSchoolIds = favoriteSchoolIds,
                            onToggleFavoriteSchool = viewModel::toggleFavoriteSchool,
                            onOpenAiAdvisor = { viewModel.selectTab(MainTab.AI_ADVISOR) },
                            selectedSchoolDetailState = selectedSchoolDetailState,
                            selectedSchoolAnnouncementsState = selectedSchoolAnnouncementsState,
                            onSelectSchoolForDetail = viewModel::selectSchoolForDetail
                        )
                        MainTab.TUTORS -> ProgressiveTutorsScreen(
                            currentUserId = currentUserId,
                            defaultTeacherName = currentProfile?.displayName ?: userDisplayName.ifBlank { "Tutor" },
                            tutorsUiState = tutorsState,
                            filteredTutors = filteredTutors,
                            filter = tutorFilter,
                            clientSessionsState = clientTutorSessionsState,
                            teacherSessionsState = teacherTutorSessionsState,
                            isBusy = isBusy,
                            onUpdateFilter = viewModel::updateTutorFilter,
                            onResetFilters = viewModel::resetTutorFilters,
                            onSaveTutorProfile = { tutor, isNew ->
                                viewModel.createOrUpdateTutor(tutor, isNew)
                            },
                            onBookSession = { tutor, studentName, track, subject, schedule, notes, hours, draft ->
                                viewModel.bookTutorSession(tutor, studentName, track, subject, schedule, notes, hours, draft)
                            },
                            onPayForSession = viewModel::payForTutorSession,
                            onUpdateSessionStatus = viewModel::updateTutorSessionStatus,
                            onSeedSampleTutors = viewModel::seedSampleTutors
                        )
                        MainTab.MESSAGES -> MessagesScreen(
                            currentUserId = currentUserId,
                            conversationsState = conversationsState,
                            selectedConversation = selectedConversation,
                            messagesState = activeMessagesState,
                            onSelectConversation = viewModel::selectConversation,
                            onSendMessage = viewModel::sendMessage,
                            onBrowseSchoolsClick = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
                        MainTab.PORTAL -> ParentPortalScreen(
                            studentsState = parentStudentsState,
                            selectedStudentId = selectedPortalStudentId,
                            attendanceState = activePortalAttendanceState,
                            gradesState = activePortalGradesState,
                            enrollmentsState = parentEnrollmentsState,
                            announcementsState = announcementsState,
                            myPaymentsState = myPaymentsState,
                            favoriteSchools = favoriteSchools,
                            isBusy = isBusy,
                            onSelectStudent = viewModel::selectPortalStudent,
                            onExploreSchools = { viewModel.selectTab(MainTab.DISCOVER) },
                            onToggleFavoriteSchool = viewModel::toggleFavoriteSchool,
                            onPayEnrollmentFee = viewModel::payForEnrollment
                        )
                        MainTab.ADMIN -> AdminHubScreen(
                            currentUserId = currentUserId,
                            adminName = currentProfile?.displayName ?: userDisplayName.ifBlank { "School Admin" },
                            defaultLat = currentProfile?.homeLat ?: searchFilter.centerLat,
                            defaultLng = currentProfile?.homeLng ?: searchFilter.centerLng,
                            isPlatformAdmin = viewModel.isPlatformAdmin,
                            initialOpenOversight = initialPreferAdminPortal,
                            schoolsState = schoolsState,
                            unclaimedExternalSchools = unclaimedExternalSchools,
                            claimingExternalSchool = claimingExternalSchool,
                            adminEnrollmentsState = adminEnrollmentsState,
                            adminStudentsState = adminStudentsState,
                            selectedAdminStudentId = selectedAdminStudentId,
                            adminAttendanceState = activeAdminAttendanceState,
                            adminGradesState = activeAdminGradesState,
                            receivedPaymentsState = receivedPaymentsState,
                            adminAllUsersState = adminAllUsersState,
                            adminAllEnrollmentsState = adminAllEnrollmentsState,
                            adminAllTutorSessionsState = adminAllTutorSessionsState,
                            adminAllInquiriesState = adminAllInquiriesState,
                            adminAllPaymentsState = adminAllPaymentsState,
                            advertsState = advertsState,
                            tutorsUiState = tutorsState,
                            isBusy = isBusy,
                            onSaveSchool = { school, isNew ->
                                viewModel.createOrUpdateSchool(school, isNew)
                            },
                            onClearClaimingSchool = viewModel::clearClaimingExternalSchool,
                            onVerifyDraftSchool = viewModel::verifyDraftSchoolOnInternet,
                            onVerifyRegisteredSchool = viewModel::verifyRegisteredSchoolOnInternet,
                            onUpdateEnrollmentStatus = viewModel::updateEnrollmentStatus,
                            onSelectAdminStudent = viewModel::selectAdminStudent,
                            onLogAttendance = viewModel::logStudentAttendance,
                            onAddGrade = viewModel::addStudentGrade,
                            onPublishAnnouncement = { school, title, body, category, eventDate ->
                                viewModel.publishAnnouncement(school, title, body, category, eventDate)
                            },
                            onConfirmPaymentPaid = { payment ->
                                viewModel.confirmPaymentPaid(payment)
                            }
                        )
                        MainTab.ADVERTS -> PaidAdvertsScreen(
                            currentUserId = currentUserId,
                            defaultAdvertiserName = currentProfile?.displayName ?: userDisplayName.ifBlank { "Partner" },
                            defaultEmail = userEmail,
                            defaultLat = currentProfile?.homeLat ?: searchFilter.centerLat,
                            defaultLng = currentProfile?.homeLng ?: searchFilter.centerLng,
                            advertsUiState = advertsState,
                            sortedAdverts = activeAdvertsWithDistance,
                            receivedInquiriesState = receivedInquiriesState,
                            isBusy = isBusy,
                            onSaveAdvert = { advert, isNew, draft ->
                                viewModel.createOrUpdateAdvert(advert, isNew, draft)
                            },
                            onPayExistingAdvert = viewModel::payForExistingAdvert,
                            onUpdateAdvertStatus = viewModel::updateAdvertStatus,
                            onDeleteAdvert = viewModel::deleteAdvert,
                            onSeedSampleAdverts = viewModel::seedSampleAdverts,
                            onSubmitInquiry = viewModel::submitAdvertInquiry,
                            onUpdateInquiryStatus = viewModel::updateAdvertInquiryStatus,
                            onNavigateToSchools = { viewModel.selectTab(MainTab.DISCOVER) },
                            onNavigateToTutors = { viewModel.selectTab(MainTab.TUTORS) },
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
                        MainTab.AI_ADVISOR -> AiAdvisorScreen(
                            messages = aiChatMessages,
                            selectedModel = aiModelOption,
                            isGenerating = isAiGenerating,
                            errorMessage = aiChatError,
                            searchFilter = searchFilter,
                            favoriteCount = favoriteSchoolIds.size,
                            onSelectModel = viewModel::setAiModelOption,
                            onSendMessage = viewModel::sendAiAdvisorMessage,
                            onClearChat = viewModel::clearAiAdvisorChat,
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
                    }
                }
            }
        }
    }

    latestPaymentReceipt?.let { receipt ->
        PaymentReceiptDialog(
            payment = receipt,
            onDismiss = viewModel::clearLatestPaymentReceipt
        )
    }
}

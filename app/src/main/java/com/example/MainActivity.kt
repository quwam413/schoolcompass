package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.DashboardScreen
import com.example.ui.DiscoverScreen
import com.example.ui.HelpSupportScreen
import com.example.ui.MainTab
import com.example.ui.MessagesScreen
import com.example.ui.PaidAdvertsScreen
import com.example.ui.ParentPortalScreen
import com.example.ui.PaymentReceiptDialog
import com.example.ui.ProgressiveTutorsScreen
import com.example.ui.RegistrationPortalScreen
import com.example.ui.SavedSchoolsScreen
import com.example.ui.SchoolViewModel
import com.example.ui.SettingsPrivacyScreen
import com.example.ui.SkolquikMinimalShell
import com.example.ui.UiState
import com.example.ui.theme.SchoolCompassTheme
import com.example.ui.theme.ThemePreferencesStore
import com.example.ui.theme.ThemeSettings
import com.example.ui.theme.resolveIsDarkTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before any UI or ViewModel creation.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { ThemePreferencesStore(context.applicationContext) }
            val themeSettings by themeStore.settingsFlow.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            var solarLatLng by remember { mutableStateOf(6.4474 to 3.4737) }
            val resolvedDark = resolveIsDarkTheme(
                appearanceMode = themeSettings.appearanceMode,
                latitude = solarLatLng.first,
                longitude = solarLatLng.second
            )
            SchoolCompassTheme(
                darkTheme = resolvedDark,
                highlightHue = themeSettings.highlightHue,
                dynamicColor = false
            ) {
                AppContent(
                    themeStore = themeStore,
                    themeSettings = themeSettings,
                    onSolarLocationChanged = { lat, lng -> solarLatLng = lat to lng }
                )
            }
        }
    }
}

@Composable
fun AppContent(
    themeStore: ThemePreferencesStore,
    themeSettings: ThemeSettings,
    onSolarLocationChanged: (Double, Double) -> Unit
) {
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
                initialPreferAdminPortal = preferAdminPortalAfterAuth,
                themeStore = themeStore,
                themeSettings = themeSettings,
                onSolarLocationChanged = onSolarLocationChanged
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    initialPreferAdminPortal: Boolean,
    themeStore: ThemePreferencesStore,
    themeSettings: ThemeSettings,
    onSolarLocationChanged: (Double, Double) -> Unit
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
    val onlineGroundingLoading by viewModel.onlineGroundingLoading.collectAsStateWithLifecycle()
    val onlineGroundingError by viewModel.onlineGroundingError.collectAsStateWithLifecycle()
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
    val isHomeRefreshing by viewModel.isHomeRefreshing.collectAsStateWithLifecycle()
    val onlineTutorsLoading by viewModel.onlineTutorsLoading.collectAsStateWithLifecycle()
    val onlineTutorsError by viewModel.onlineTutorsError.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(searchFilter.centerLat, searchFilter.centerLng) {
        onSolarLocationChanged(searchFilter.centerLat, searchFilter.centerLng)
    }

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

    SkolquikMinimalShell(
        selectedTab = selectedTab,
        displayName = currentProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
        userEmail = userEmail,
        userRole = currentProfile?.role ?: "parent",
        isPlatformAdmin = viewModel.isPlatformAdmin,
        favoriteCount = favoriteSchoolIds.size,
        snackbarHostState = snackbarHostState,
        onSelectTab = viewModel::selectTab,
        onOpenProfile = { showProfileEditor = true },
        onSignOut = {
            AuthManager.signOut(
                credentialManager = credentialManager,
                scope = scope
            )
        }
    ) {
        when (selectedTab) {
                        MainTab.DISCOVER -> DiscoverScreen(
                            schoolsUiState = schoolsState,
                            filteredSchools = filteredSchools,
                            filter = searchFilter,
                            isBusy = isBusy,
                            isRefreshingHome = isHomeRefreshing,
                            isNetworkOnline = isNetworkOnline,
                            connectionLabel = connectionLabel,
                            externalDirectoryLoading = externalDirectoryLoading,
                            externalDirectoryError = externalDirectoryError,
                            onlineGroundingLoading = onlineGroundingLoading,
                            onlineGroundingError = onlineGroundingError,
                            isGeocodingLocation = isGeocodingLocation,
                            onPullToRefreshHome = viewModel::refreshHomeFromPullDown,
                            onRefreshExternalSchools = viewModel::refreshExternalSchools,
                            onSearchOnline = { viewModel.searchVerifiedSchoolsOnline() },
                            onGeocodeSearch = viewModel::geocodeAndMoveSearchCenter,
                            onUpdateFilter = viewModel::updateFilter,
                            onResetFilters = viewModel::resetFilters,
                            onMessageAdmin = viewModel::openChatWithSchool,
                            onSubmitEnrollment = { school, studentName, gradeApplying, notes, draft ->
                                viewModel.submitEnrollment(school, studentName, gradeApplying, notes, draft)
                            },
                            onClaimSchool = viewModel::startClaimingExternalSchool,
                            onSubmitExtraDetails = { school, feeMin, feeMax, curr, period, open, window, boarding, photos, notes, courses, grades, site, phone ->
                                viewModel.submitOptionalSchoolDetails(
                                    school = school,
                                    feeMin = feeMin,
                                    feeMax = feeMax,
                                    feeCurrency = curr,
                                    feePeriod = period,
                                    admissionsOpen = open,
                                    admissionWindow = window,
                                    boardingOption = boarding,
                                    photoUrls = photos,
                                    extraNotes = notes,
                                    coursesOffered = courses,
                                    gradeLevels = grades,
                                    websiteUrl = site,
                                    contactPhone = phone
                                )
                            },
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
                        MainTab.DASHBOARD -> DashboardScreen(
                            displayName = currentProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                            userRole = currentProfile?.role ?: "parent",
                            filter = searchFilter,
                            nearbySchools = filteredSchools,
                            savedSchoolsCount = favoriteSchoolIds.size,
                            enrollmentsState = parentEnrollmentsState,
                            conversationCount = (conversationsState as? UiState.Success)?.data?.size ?: 0,
                            onNavigateTab = viewModel::selectTab,
                            onRefreshOnline = {
                                viewModel.refreshHomeFromPullDown()
                                viewModel.selectTab(MainTab.DISCOVER)
                            },
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
                        MainTab.SAVED -> SavedSchoolsScreen(
                            favoriteSchools = favoriteSchools,
                            favoriteSchoolIds = favoriteSchoolIds,
                            selectedSchoolDetailState = selectedSchoolDetailState,
                            selectedSchoolAnnouncementsState = selectedSchoolAnnouncementsState,
                            onSelectSchoolForDetail = viewModel::selectSchoolForDetail,
                            onToggleFavoriteSchool = viewModel::toggleFavoriteSchool,
                            onMessageAdmin = viewModel::openChatWithSchool,
                            onSubmitEnrollment = { school, studentName, gradeApplying, notes, draft ->
                                viewModel.submitEnrollment(school, studentName, gradeApplying, notes, draft)
                            },
                            onClaimSchool = viewModel::startClaimingExternalSchool,
                            onSubmitExtraDetails = { school, feeMin, feeMax, curr, period, open, window, boarding, photos, notes, courses, grades, site, phone ->
                                viewModel.submitOptionalSchoolDetails(
                                    school = school,
                                    feeMin = feeMin,
                                    feeMax = feeMax,
                                    feeCurrency = curr,
                                    feePeriod = period,
                                    admissionsOpen = open,
                                    admissionWindow = window,
                                    boardingOption = boarding,
                                    photoUrls = photos,
                                    extraNotes = notes,
                                    coursesOffered = courses,
                                    gradeLevels = grades,
                                    websiteUrl = site,
                                    contactPhone = phone
                                )
                            },
                            onBrowseDiscover = { viewModel.selectTab(MainTab.DISCOVER) },
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
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
                            onlineTutorsLoading = onlineTutorsLoading,
                            onlineTutorsError = onlineTutorsError,
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
                            onDiscoverOnlineTutors = { viewModel.discoverRealTutorsOnline() }
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
                        MainTab.SETTINGS -> SettingsPrivacyScreen(
                            themeSettings = themeSettings,
                            latitude = searchFilter.centerLat,
                            longitude = searchFilter.centerLng,
                            locationLabel = searchFilter.centerLabel,
                            displayName = currentProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                            userEmail = userEmail,
                            onSelectAppearanceMode = { mode ->
                                scope.launch { themeStore.setAppearanceMode(mode) }
                            },
                            onSelectHighlightHue = { hue ->
                                scope.launch { themeStore.setHighlightHue(hue) }
                            },
                            onToggleAutoOnlineDiscovery = { enabled ->
                                scope.launch { themeStore.setAutoOnlineDiscovery(enabled) }
                            },
                            onTogglePreciseLocation = { enabled ->
                                scope.launch { themeStore.setPreciseLocation(enabled) }
                            },
                            onOpenProfile = { showProfileEditor = true },
                            onSignOut = {
                                AuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
                        MainTab.HELP -> HelpSupportScreen(
                            onOpenAiAdvisor = { viewModel.selectTab(MainTab.AI_ADVISOR) },
                            onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                            onBack = { viewModel.selectTab(MainTab.DISCOVER) }
                        )
        }
    }

    latestPaymentReceipt?.let { receipt ->
        PaymentReceiptDialog(
            payment = receipt,
            onDismiss = viewModel::clearLatestPaymentReceipt
        )
    }
}

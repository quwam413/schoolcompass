package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Advert
import com.example.data.AdvertInquiry
import com.example.data.AdvertWithDistance
import com.example.data.AiAdvisorMessage
import com.example.data.AiAdvisorModelOption
import com.example.data.AiSchoolAdvisorService
import com.example.data.Announcement
import com.example.data.AttendanceLog
import com.example.data.ChatMessage
import com.example.data.Conversation
import com.example.data.Enrollment
import com.example.data.ExternalSchoolService
import com.example.data.FaithFilter
import com.example.data.GoogleMapsPlatformService
import com.example.data.GroundingSourceLink
import com.example.data.OnlineSchoolGroundingService
import com.example.data.PaymentRepository
import com.example.data.PaymentTransaction
import com.example.data.ProgressiveTutor
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolRepository
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolVerificationResult
import com.example.data.SchoolWithDistance
import com.example.data.SortOption
import com.example.data.SpecialFilter
import com.example.data.StudentRecord
import com.example.data.SubjectGrade
import com.example.data.TutorSearchFilter
import com.example.data.TutorSession
import com.example.data.TutorWithDistance
import com.example.data.UserProfile
import com.example.data.calculateDistanceKm
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MainTab {
    DISCOVER,
    DASHBOARD,
    SAVED,
    TUTORS,
    MESSAGES,
    PORTAL,
    ADMIN,
    ADVERTS,
    AI_ADVISOR,
    SETTINGS,
    HELP
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SchoolViewModel(
    private val repository: SchoolRepository,
    private val paymentRepository: PaymentRepository,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String,
    private val aiAdvisorService: AiSchoolAdvisorService = AiSchoolAdvisorService(),
    private val onlineGroundingService: OnlineSchoolGroundingService = OnlineSchoolGroundingService()
) : ViewModel() {

    private companion object {
        const val TAG = "SchoolVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val isPlatformAdmin: Boolean =
        userEmail.trim().equals(SchoolConstants.PLATFORM_ADMIN_EMAIL, ignoreCase = true)

    private fun <T> Flow<T>.toUiStateFlow(): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing stream", rootError)
                emit(UiState.Error(rootError.message ?: "Unable to sync data"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiStateFlow()

    val schoolsState: StateFlow<UiState<List<School>>> =
        repository.observeSchools().toUiStateFlow()

    val parentEnrollmentsState: StateFlow<UiState<List<Enrollment>>> =
        repository.observeParentEnrollments(currentUserId).toUiStateFlow()

    val adminEnrollmentsState: StateFlow<UiState<List<Enrollment>>> =
        repository.observeAdminEnrollments(currentUserId).toUiStateFlow()

    val parentStudentsState: StateFlow<UiState<List<StudentRecord>>> =
        repository.observeParentStudents(currentUserId).toUiStateFlow()

    val adminStudentsState: StateFlow<UiState<List<StudentRecord>>> =
        repository.observeAdminStudents(currentUserId).toUiStateFlow()

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId).toUiStateFlow()

    val announcementsState: StateFlow<UiState<List<Announcement>>> =
        repository.observeAnnouncements().toUiStateFlow()

    val advertsState: StateFlow<UiState<List<Advert>>> =
        repository.observeAdverts().toUiStateFlow()

    val tutorsState: StateFlow<UiState<List<ProgressiveTutor>>> =
        repository.observeTutors().toUiStateFlow()

    val clientTutorSessionsState: StateFlow<UiState<List<TutorSession>>> =
        repository.observeClientTutorSessions(currentUserId).toUiStateFlow()

    val teacherTutorSessionsState: StateFlow<UiState<List<TutorSession>>> =
        repository.observeTeacherTutorSessions(currentUserId).toUiStateFlow()

    val myPaymentsState: StateFlow<UiState<List<PaymentTransaction>>> =
        paymentRepository.observePaymentsByPayer(currentUserId).toUiStateFlow()

    val receivedPaymentsState: StateFlow<UiState<List<PaymentTransaction>>> =
        paymentRepository.observePaymentsByRecipient(currentUserId).toUiStateFlow()

    val sentInquiriesState: StateFlow<UiState<List<AdvertInquiry>>> =
        paymentRepository.observeInquiriesBySender(currentUserId).toUiStateFlow()

    val receivedInquiriesState: StateFlow<UiState<List<AdvertInquiry>>> =
        paymentRepository.observeInquiriesByOwner(currentUserId).toUiStateFlow()

    // Strictly gated so regular users never subscribe to global admin queries
    val adminAllUsersState: StateFlow<UiState<List<UserProfile>>> =
        if (isPlatformAdmin) paymentRepository.observeAllUsersForAdmin().toUiStateFlow()
        else MutableStateFlow<UiState<List<UserProfile>>>(UiState.Success(emptyList())).asStateFlow()

    val adminAllEnrollmentsState: StateFlow<UiState<List<Enrollment>>> =
        if (isPlatformAdmin) paymentRepository.observeAllEnrollmentsForAdmin().toUiStateFlow()
        else MutableStateFlow<UiState<List<Enrollment>>>(UiState.Success(emptyList())).asStateFlow()

    val adminAllTutorSessionsState: StateFlow<UiState<List<TutorSession>>> =
        if (isPlatformAdmin) paymentRepository.observeAllTutorSessionsForAdmin().toUiStateFlow()
        else MutableStateFlow<UiState<List<TutorSession>>>(UiState.Success(emptyList())).asStateFlow()

    val adminAllInquiriesState: StateFlow<UiState<List<AdvertInquiry>>> =
        if (isPlatformAdmin) paymentRepository.observeAllInquiriesForAdmin().toUiStateFlow()
        else MutableStateFlow<UiState<List<AdvertInquiry>>>(UiState.Success(emptyList())).asStateFlow()

    val adminAllPaymentsState: StateFlow<UiState<List<PaymentTransaction>>> =
        if (isPlatformAdmin) paymentRepository.observeAllPaymentsForAdmin().toUiStateFlow()
        else MutableStateFlow<UiState<List<PaymentTransaction>>>(UiState.Success(emptyList())).asStateFlow()

    private val _searchFilter = MutableStateFlow(SchoolSearchFilter())
    val searchFilter: StateFlow<SchoolSearchFilter> = _searchFilter.asStateFlow()

    private val _tutorFilter = MutableStateFlow(TutorSearchFilter())
    val tutorFilter: StateFlow<TutorSearchFilter> = _tutorFilter.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.DISCOVER)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedConversation = MutableStateFlow<Conversation?>(null)
    val selectedConversation: StateFlow<Conversation?> = _selectedConversation.asStateFlow()

    private val _selectedPortalStudentId = MutableStateFlow<String?>(null)
    val selectedPortalStudentId: StateFlow<String?> = _selectedPortalStudentId.asStateFlow()

    private val _selectedAdminStudentId = MutableStateFlow<String?>(null)
    val selectedAdminStudentId: StateFlow<String?> = _selectedAdminStudentId.asStateFlow()

    private val _externalSchools = MutableStateFlow<List<School>>(emptyList())
    val externalSchools: StateFlow<List<School>> = _externalSchools.asStateFlow()

    private val _externalDirectoryLoading = MutableStateFlow(false)
    val externalDirectoryLoading: StateFlow<Boolean> = _externalDirectoryLoading.asStateFlow()

    private val _externalDirectoryError = MutableStateFlow<String?>(null)
    val externalDirectoryError: StateFlow<String?> = _externalDirectoryError.asStateFlow()

    private val _onlineGroundingLoading = MutableStateFlow(false)
    val onlineGroundingLoading: StateFlow<Boolean> = _onlineGroundingLoading.asStateFlow()

    private val _onlineGroundingError = MutableStateFlow<String?>(null)
    val onlineGroundingError: StateFlow<String?> = _onlineGroundingError.asStateFlow()

    private val _isGeocodingLocation = MutableStateFlow(false)
    val isGeocodingLocation: StateFlow<Boolean> = _isGeocodingLocation.asStateFlow()

    private val _claimingExternalSchool = MutableStateFlow<School?>(null)
    val claimingExternalSchool: StateFlow<School?> = _claimingExternalSchool.asStateFlow()

    private val _selectedDetailSchoolId = MutableStateFlow<String?>(null)

    private val _latestPaymentReceipt = MutableStateFlow<PaymentTransaction?>(null)
    val latestPaymentReceipt: StateFlow<PaymentTransaction?> = _latestPaymentReceipt.asStateFlow()

    private val _isHomeRefreshing = MutableStateFlow(false)
    val isHomeRefreshing: StateFlow<Boolean> = _isHomeRefreshing.asStateFlow()

    private val _discoveredOnlineTutors = MutableStateFlow<List<ProgressiveTutor>>(emptyList())

    private val _onlineTutorsLoading = MutableStateFlow(false)
    val onlineTutorsLoading: StateFlow<Boolean> = _onlineTutorsLoading.asStateFlow()

    private val _onlineTutorsError = MutableStateFlow<String?>(null)
    val onlineTutorsError: StateFlow<String?> = _onlineTutorsError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _aiModelOption = MutableStateFlow(AiAdvisorModelOption.BALANCED)
    val aiModelOption: StateFlow<AiAdvisorModelOption> = _aiModelOption.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<AiAdvisorMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<AiAdvisorMessage>> = _aiChatMessages.asStateFlow()

    private val _aiChatError = MutableStateFlow<String?>(null)
    val aiChatError: StateFlow<String?> = _aiChatError.asStateFlow()

    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages = _uiMessages.asSharedFlow()

    private val _newAnnouncementAlerts = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val newAnnouncementAlerts = _newAnnouncementAlerts.asSharedFlow()

    private var initialAnnouncementsSeen = false
    private val seenAnnouncementIds = mutableSetOf<String>()

    private data class GeoFetchKey(val latBucket: Long, val lngBucket: Long, val radiusKm: Int, val lat: Double, val lng: Double)

    init {
        viewModelScope.launch {
            _searchFilter
                .map { f ->
                    GeoFetchKey(
                        latBucket = (f.centerLat * 200).toLong(),
                        lngBucket = (f.centerLng * 200).toLong(),
                        radiusKm = f.radiusKm,
                        lat = f.centerLat,
                        lng = f.centerLng
                    )
                }
                .distinctUntilChanged { a, b ->
                    a.latBucket == b.latBucket && a.lngBucket == b.lngBucket && a.radiusKm == b.radiusKm
                }
                .debounce(550L)
                .collect { key ->
                    fetchExternalSchoolsInternal(key.lat, key.lng, key.radiusKm)
                }
        }
        viewModelScope.launch {
            _searchFilter
                .map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(650L)
                .collect { q ->
                    if (q.length >= 3) {
                        searchVerifiedSchoolsOnlineInternal(q)
                    }
                }
        }
        viewModelScope.launch {
            announcementsState.collect { state ->
                if (state is UiState.Success) {
                    val items = state.data
                    if (!initialAnnouncementsSeen) {
                        items.forEach { seenAnnouncementIds.add(it.id) }
                        initialAnnouncementsSeen = true
                    } else {
                        items.filter { it.id.isNotBlank() && seenAnnouncementIds.add(it.id) }
                            .forEach { _newAnnouncementAlerts.tryEmit(it) }
                    }
                }
            }
        }
        viewModelScope.launch {
            userProfileState.collect { state ->
                if (state is UiState.Success) {
                    val profile = state.data
                    if (profile != null) {
                        _searchFilter.update { current ->
                            if (current.centerLabel == "Lekki Phase 1, Lagos" &&
                                (profile.homeAddress != null || profile.searchRadiusKm.toInt() != current.radiusKm)
                            ) {
                                current.copy(
                                    centerLat = profile.homeLat,
                                    centerLng = profile.homeLng,
                                    centerLabel = profile.homeAddress ?: current.centerLabel,
                                    radiusKm = profile.searchRadiusKm.toInt()
                                )
                            } else current
                        }
                        _tutorFilter.update { current ->
                            if (current.clientLabel == "Lekki Phase 1, Lagos" && profile.homeAddress != null) {
                                current.copy(
                                    clientLat = profile.homeLat,
                                    clientLng = profile.homeLng,
                                    clientLabel = profile.homeAddress
                                )
                            } else current
                        }
                    }
                }
            }
        }
    }

    val unclaimedExternalSchools: StateFlow<List<SchoolWithDistance>> =
        combine(schoolsState, _externalSchools, _searchFilter) { schoolsUi, external, filter ->
            val registered = (schoolsUi as? UiState.Success)?.data ?: emptyList()
            val merged = ExternalSchoolService.mergeRegisteredAndExternalSchools(registered, external)
            merged.filter { !it.hasPlatformAdmin() }
                .map { s ->
                    SchoolWithDistance(
                        school = s,
                        distanceKm = calculateDistanceKm(filter.centerLat, filter.centerLng, s.latitude, s.longitude)
                    )
                }
                .sortedBy { it.distanceKm }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val favoriteSchoolIds: StateFlow<Set<String>> =
        userProfileState.map { state ->
            (state as? UiState.Success)?.data?.favoriteSchoolIds?.toSet().orEmpty()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val favoriteSchools: StateFlow<List<SchoolWithDistance>> =
        combine(schoolsState, _externalSchools, _searchFilter, favoriteSchoolIds) { schoolsUi, external, filter, favIds ->
            if (favIds.isEmpty()) return@combine emptyList()
            val registered = (schoolsUi as? UiState.Success)?.data ?: emptyList()
            ExternalSchoolService.mergeRegisteredAndExternalSchools(registered, external)
                .filter { it.id in favIds }
                .map { school ->
                    SchoolWithDistance(
                        school = school,
                        distanceKm = calculateDistanceKm(
                            filter.centerLat, filter.centerLng,
                            school.latitude, school.longitude
                        )
                    )
                }
                .sortedBy { it.distanceKm }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val filteredSchools: StateFlow<List<SchoolWithDistance>> =
        combine(schoolsState, _externalSchools, _searchFilter, favoriteSchoolIds) { schoolsUi, external, filter, favIds ->
            val registered = (schoolsUi as? UiState.Success)?.data ?: emptyList()
            val schools = ExternalSchoolService.mergeRegisteredAndExternalSchools(registered, external)
            schools.map { school ->
                val dist = calculateDistanceKm(
                    filter.centerLat, filter.centerLng,
                    school.latitude, school.longitude
                )
                SchoolWithDistance(school, dist)
            }.filter { item ->
                val s = item.school
                val matchesFavorite = !filter.onlyFavorites || (s.id in favIds)
                val matchesRadius = filter.onlyFavorites || !filter.limitByRadius || item.distanceKm <= filter.radiusKm
                val matchesQuery = filter.query.isBlank() ||
                    s.name.contains(filter.query, ignoreCase = true) ||
                    s.address.contains(filter.query, ignoreCase = true) ||
                    s.description.contains(filter.query, ignoreCase = true) ||
                    s.institutionType.contains(filter.query, ignoreCase = true) ||
                    s.curriculum.contains(filter.query, ignoreCase = true) ||
                    (s.boardingOption?.contains(filter.query, ignoreCase = true) == true) ||
                    (s.verificationSource?.contains(filter.query, ignoreCase = true) == true) ||
                    s.gradeLevels.any { it.contains(filter.query, ignoreCase = true) } ||
                    s.coursesOffered.any { it.contains(filter.query, ignoreCase = true) } ||
                    s.activities.any { it.contains(filter.query, ignoreCase = true) } ||
                    s.sportsFocus.any { it.contains(filter.query, ignoreCase = true) }
                val matchesInstitutionType = filter.institutionType == null || s.institutionType == filter.institutionType
                val matchesCurriculum = filter.curriculum == null || s.curriculum == filter.curriculum
                val matchesFaith = when (filter.faithFilter) {
                    FaithFilter.ALL -> true
                    FaithFilter.FAITH_BASED -> s.faithBased
                    FaithFilter.SECULAR -> !s.faithBased
                }
                val matchesSpecial = when (filter.specialSchoolFilter) {
                    SpecialFilter.ALL -> true
                    SpecialFilter.SPECIAL_ONLY -> s.specialSchool
                    SpecialFilter.GENERAL_ONLY -> !s.specialSchool
                }
                val matchesMix = filter.studentMix == null || s.studentMix == filter.studentMix
                val matchesProgrammes = filter.selectedProgrammes.isEmpty() ||
                    filter.selectedProgrammes.any { prog -> s.gradeLevels.any { it.equals(prog, ignoreCase = true) } }
                val matchesCourses = filter.selectedCourses.isEmpty() ||
                    filter.selectedCourses.any { req -> s.coursesOffered.any { it.contains(req, ignoreCase = true) } }
                val matchesActivities = filter.selectedActivities.isEmpty() ||
                    filter.selectedActivities.all { req -> s.activities.contains(req) }
                val matchesSports = filter.selectedSports.isEmpty() ||
                    filter.selectedSports.all { sport -> s.sportsFocus.contains(sport) }
                val matchesRank = s.rankingScore >= filter.minRankingScore
                val matchesFee = filter.maxFeeBudget == null ||
                    (!s.hasPlatformAdmin() && s.feeMax == 0L && filter.maxFeeBudget >= 0L) ||
                    s.feeMin <= filter.maxFeeBudget

                matchesFavorite && matchesRadius && matchesQuery && matchesInstitutionType && matchesCurriculum && matchesFaith &&
                    matchesSpecial && matchesMix && matchesProgrammes && matchesCourses &&
                    matchesActivities && matchesSports && matchesRank && matchesFee
            }.let { list ->
                when (filter.sortBy) {
                    SortOption.NEAREST -> list.sortedWith(compareBy<SchoolWithDistance> { it.distanceKm }.thenByDescending { it.school.hasPlatformAdmin() })
                    SortOption.HIGHEST_RANKED -> list.sortedWith(compareByDescending<SchoolWithDistance> { it.school.hasPlatformAdmin() }.thenBy { it.school.districtRank })
                    SortOption.TOP_RATED -> list.sortedWith(compareByDescending<SchoolWithDistance> { it.school.hasPlatformAdmin() }.thenByDescending { it.school.rating })
                    SortOption.LOWEST_FEE -> list.sortedWith(compareByDescending<SchoolWithDistance> { it.school.hasPlatformAdmin() }.thenBy { it.school.feeMin })
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val allTutorsCombined: StateFlow<List<ProgressiveTutor>> =
        combine(tutorsState, _discoveredOnlineTutors) { tutorsUi, onlineTutors ->
            val registered = (tutorsUi as? UiState.Success)?.data.orEmpty()
            val existingNames = registered.map { it.fullName.trim().lowercase() }.toSet()
            registered + onlineTutors.filter { it.fullName.trim().lowercase() !in existingNames }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val filteredTutors: StateFlow<List<TutorWithDistance>> =
        combine(allTutorsCombined, _tutorFilter) { tutors, filter ->
            tutors.filter { it.onlineActive }.map { tutor ->
                val dist = calculateDistanceKm(
                    filter.clientLat, filter.clientLng,
                    tutor.latitude, tutor.longitude
                )
                TutorWithDistance(tutor, dist)
            }.filter { item ->
                val t = item.tutor
                val matchesDistance = !filter.limitByDistance || item.distanceKm <= filter.maxDistanceKm
                val matchesSex = filter.sexFilter == null || t.sex == filter.sexFilter
                val hasCambridge = t.cambridgeSubjects.isNotEmpty() || "Cambridge" in t.curriculumTracks
                val hasNigerian = t.nigerianSubjects.isNotEmpty() || "Nigerian" in t.curriculumTracks
                val matchesCurriculum = when (filter.curriculumFilter) {
                    "Cambridge" -> hasCambridge
                    "Nigerian" -> hasNigerian
                    "Cambridge & Nigerian" -> hasCambridge && hasNigerian
                    else -> true
                }
                val matchesSubjects = filter.selectedSubjects.isEmpty() ||
                    filter.selectedSubjects.all { req -> req in t.allSubjects }
                val matchesQuery = filter.query.isBlank() ||
                    t.fullName.contains(filter.query, ignoreCase = true) ||
                    t.headline.contains(filter.query, ignoreCase = true) ||
                    t.qualifications.contains(filter.query, ignoreCase = true) ||
                    t.allSubjects.any { it.contains(filter.query, ignoreCase = true) }

                matchesDistance && matchesSex && matchesCurriculum && matchesSubjects && matchesQuery
            }.sortedBy { it.distanceKm }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val activeAdvertsWithDistance: StateFlow<List<AdvertWithDistance>> =
        combine(advertsState, _searchFilter) { advertsUi, filter ->
            val list = (advertsUi as? UiState.Success)?.data ?: emptyList()
            list.map { ad ->
                val dist = calculateDistanceKm(filter.centerLat, filter.centerLng, ad.latitude, ad.longitude)
                AdvertWithDistance(ad, dist)
            }.sortedWith(
                compareBy<AdvertWithDistance> {
                    when (it.advert.planTier) {
                        "District Hero" -> 0
                        "Featured Spotlight" -> 1
                        else -> 2
                    }
                }.thenBy { it.distanceKm }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _selectedConversation.flatMapLatest { conv ->
            if (conv == null) flowOf(emptyList())
            else repository.observeMessages(conv.id)
        }.toUiStateFlow()

    val activePortalAttendanceState: StateFlow<UiState<List<AttendanceLog>>> =
        combine(_selectedPortalStudentId, parentStudentsState) { selectedId, studentsUi ->
            val list = (studentsUi as? UiState.Success)?.data ?: emptyList()
            selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { studentId ->
            if (studentId == null) flowOf(emptyList())
            else repository.observeStudentAttendance(studentId, isAdmin = false)
        }.toUiStateFlow()

    val activePortalGradesState: StateFlow<UiState<List<SubjectGrade>>> =
        combine(_selectedPortalStudentId, parentStudentsState) { selectedId, studentsUi ->
            val list = (studentsUi as? UiState.Success)?.data ?: emptyList()
            selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { studentId ->
            if (studentId == null) flowOf(emptyList())
            else repository.observeStudentGrades(studentId, isAdmin = false)
        }.toUiStateFlow()

    val activeAdminAttendanceState: StateFlow<UiState<List<AttendanceLog>>> =
        combine(_selectedAdminStudentId, adminStudentsState) { selectedId, studentsUi ->
            val list = (studentsUi as? UiState.Success)?.data ?: emptyList()
            selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { studentId ->
            if (studentId == null) flowOf(emptyList())
            else repository.observeStudentAttendance(studentId, isAdmin = true)
        }.toUiStateFlow()

    val activeAdminGradesState: StateFlow<UiState<List<SubjectGrade>>> =
        combine(_selectedAdminStudentId, adminStudentsState) { selectedId, studentsUi ->
            val list = (studentsUi as? UiState.Success)?.data ?: emptyList()
            selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { studentId ->
            if (studentId == null) flowOf(emptyList())
            else repository.observeStudentGrades(studentId, isAdmin = true)
        }.toUiStateFlow()

    val selectedSchoolDetailState: StateFlow<UiState<School?>> =
        _selectedDetailSchoolId.flatMapLatest { schoolId ->
            if (schoolId == null) flowOf(null)
            else repository.observeSchoolById(schoolId)
        }.toUiStateFlow()

    val selectedSchoolAnnouncementsState: StateFlow<UiState<List<Announcement>>> =
        _selectedDetailSchoolId.flatMapLatest { schoolId ->
            if (schoolId == null) flowOf(emptyList())
            else repository.observeSchoolAnnouncements(schoolId)
        }.toUiStateFlow()

    private fun currentDisplayName(): String =
        (userProfileState.value as? UiState.Success)?.data?.displayName?.ifBlank { null }
            ?: userDisplayName.ifBlank { "Parent" }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun clearLatestPaymentReceipt() {
        _latestPaymentReceipt.value = null
    }

    fun updateFilter(transform: (SchoolSearchFilter) -> SchoolSearchFilter) {
        _searchFilter.update(transform)
    }

    fun resetFilters() {
        _searchFilter.update {
            SchoolSearchFilter(
                centerLat = it.centerLat,
                centerLng = it.centerLng,
                centerLabel = it.centerLabel,
                radiusKm = it.radiusKm
            )
        }
    }

    fun updateTutorFilter(transform: (TutorSearchFilter) -> TutorSearchFilter) {
        _tutorFilter.update(transform)
    }

    fun resetTutorFilters() {
        _tutorFilter.update {
            TutorSearchFilter(
                clientLat = it.clientLat,
                clientLng = it.clientLng,
                clientLabel = it.clientLabel
            )
        }
    }

    fun selectConversation(conversation: Conversation?) {
        _selectedConversation.value = conversation
    }

    fun selectPortalStudent(studentId: String) {
        _selectedPortalStudentId.value = studentId
    }

    fun selectAdminStudent(studentId: String) {
        _selectedAdminStudentId.value = studentId
    }

    fun selectSchoolForDetail(school: School?) {
        if (school == null) {
            _selectedDetailSchoolId.value = null
            return
        }
        val trimmedId = school.id.trim()
        val validId = trimmedId.takeIf {
            it.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))
        } ?: school.externalPlaceId?.takeIf { it.isNotBlank() }?.let {
            ExternalSchoolService.claimSchoolDocId(it)
        }
        _selectedDetailSchoolId.value = validId
    }

    fun saveUserProfile(
        existing: UserProfile?,
        role: String,
        displayName: String,
        phone: String?,
        homeAddress: String?,
        homeLat: Double,
        homeLng: Double,
        searchRadiusKm: Int,
        childSummary: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveUserProfile(
                existing = existing,
                role = role,
                displayName = displayName,
                email = userEmail,
                phone = phone,
                homeAddress = homeAddress,
                homeLat = homeLat,
                homeLng = homeLng,
                searchRadiusKm = searchRadiusKm,
                childSummary = childSummary
            )
            _isBusy.value = false
            res.onSuccess {
                _searchFilter.update {
                    it.copy(
                        centerLat = homeLat,
                        centerLng = homeLng,
                        centerLabel = homeAddress?.ifBlank { null } ?: it.centerLabel,
                        radiusKm = searchRadiusKm
                    )
                }
                _uiMessages.tryEmit("Registration profile saved")
                onSuccess()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    private suspend fun fetchExternalSchoolsInternal(lat: Double, lng: Double, radiusKm: Int) {
        _externalDirectoryLoading.value = true
        _externalDirectoryError.value = null
        _onlineGroundingLoading.value = true
        _onlineGroundingError.value = null
        val filter = _searchFilter.value
        val (osmRes, googleRes, groundedRes) = coroutineScope {
            val osmDeferred = async { ExternalSchoolService.fetchNearbyExternalSchools(lat, lng, radiusKm) }
            val googleDeferred = async { GoogleMapsPlatformService.fetchNearbyGooglePlacesSchools(lat, lng, radiusKm) }
            val groundedDeferred = async {
                onlineGroundingService.searchVerifiedOnlineSchools(
                    query = filter.query,
                    centerLat = lat,
                    centerLng = lng,
                    centerLabel = filter.centerLabel,
                    radiusKm = radiusKm
                )
            }
            Triple(osmDeferred.await(), googleDeferred.await(), groundedDeferred.await())
        }
        _externalDirectoryLoading.value = false
        _onlineGroundingLoading.value = false

        val googleSchools = googleRes.getOrNull().orEmpty()
        val groundedSchools = groundedRes.getOrNull().orEmpty()
        val osmSchools = osmRes.getOrNull().orEmpty()

        val mergedExternal = ExternalSchoolService.mergeRegisteredAndExternalSchools(
            registered = ExternalSchoolService.mergeRegisteredAndExternalSchools(
                registered = googleSchools,
                external = groundedSchools
            ),
            external = osmSchools
        )
        if (mergedExternal.isNotEmpty() || osmRes.isSuccess || googleRes.isSuccess || groundedRes.isSuccess) {
            _externalSchools.value = mergedExternal
            val toPersist = (groundedSchools + googleSchools).distinctBy { it.id }
            if (toPersist.isNotEmpty()) {
                repository.upsertDiscoveredOnlineSchools(toPersist).onFailure { err ->
                    Log.w(TAG, "Cloud upsert for discovered online schools deferred: ${err.localizedMessage}")
                }
            }
        } else {
            val err = groundedRes.exceptionOrNull() ?: googleRes.exceptionOrNull() ?: osmRes.exceptionOrNull()
            Log.w(TAG, "External school geolocation lookup failed", err)
            _externalDirectoryError.value = err?.localizedMessage ?: "Live map directory lookup unavailable"
            _onlineGroundingError.value = groundedRes.exceptionOrNull()?.localizedMessage
        }
    }

    private suspend fun searchVerifiedSchoolsOnlineInternal(query: String) {
        val filter = _searchFilter.value
        _onlineGroundingLoading.value = true
        _onlineGroundingError.value = null
        val res = onlineGroundingService.searchVerifiedOnlineSchools(
            query = query,
            centerLat = filter.centerLat,
            centerLng = filter.centerLng,
            centerLabel = filter.centerLabel,
            radiusKm = filter.radiusKm
        )
        _onlineGroundingLoading.value = false
        res.onSuccess { discovered ->
            if (discovered.isNotEmpty()) {
                _externalSchools.update { current ->
                    ExternalSchoolService.mergeRegisteredAndExternalSchools(
                        registered = discovered,
                        external = current
                    )
                }
                repository.upsertDiscoveredOnlineSchools(discovered).onFailure { err ->
                    Log.w(TAG, "Cloud upsert for grounded query deferred: ${err.localizedMessage}")
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Online school search grounding failed", err)
            _onlineGroundingError.value = err.localizedMessage ?: "Online grounded search failed"
        }
    }

    fun searchVerifiedSchoolsOnline(queryOverride: String? = null) {
        val targetQuery = (queryOverride ?: _searchFilter.value.query).trim()
        if (queryOverride != null && queryOverride != _searchFilter.value.query) {
            _searchFilter.update { it.copy(query = queryOverride) }
        }
        viewModelScope.launch {
            searchVerifiedSchoolsOnlineInternal(targetQuery)
        }
    }

    fun geocodeAndMoveSearchCenter(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiMessages.tryEmit("Enter a city, district, or address to locate.")
            return
        }
        viewModelScope.launch {
            _isGeocodingLocation.value = true
            val restrictNigeria = _searchFilter.value.lockToNigeria
            val res = GoogleMapsPlatformService.geocodePlace(trimmed, restrictToNigeria = restrictNigeria)
            _isGeocodingLocation.value = false
            res.onSuccess { place ->
                _searchFilter.update {
                    it.copy(
                        centerLat = place.latitude,
                        centerLng = place.longitude,
                        centerLabel = place.formattedAddress
                    )
                }
                _uiMessages.tryEmit("Map centered on ${place.formattedAddress} (${place.source})")
            }.onFailure { err ->
                _uiMessages.tryEmit(err.localizedMessage ?: "Could not find \"$trimmed\" on the map")
            }
        }
    }

    fun refreshExternalSchools() {
        val f = _searchFilter.value
        viewModelScope.launch {
            fetchExternalSchoolsInternal(f.centerLat, f.centerLng, f.radiusKm)
        }
    }

    fun refreshHomeFromPullDown() {
        val f = _searchFilter.value
        if (_isHomeRefreshing.value) return
        viewModelScope.launch {
            _isHomeRefreshing.value = true
            try {
                fetchExternalSchoolsInternal(f.centerLat, f.centerLng, f.radiusKm)
                if (f.query.isNotBlank()) {
                    searchVerifiedSchoolsOnlineInternal(f.query)
                }
            } finally {
                _isHomeRefreshing.value = false
            }
        }
    }

    fun submitOptionalSchoolDetails(
        school: School,
        feeMin: Long,
        feeMax: Long,
        feeCurrency: String,
        feePeriod: String,
        admissionsOpen: Boolean,
        admissionWindow: String?,
        boardingOption: String?,
        photoUrls: List<String>,
        extraNotes: String?,
        coursesOffered: List<String>,
        gradeLevels: List<String>,
        websiteUrl: String?,
        contactPhone: String?,
        onSuccess: () -> Unit = {}
    ) {
        val contributor = currentDisplayName()
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitOptionalSchoolDetails(
                school = school,
                contributorName = contributor,
                feeMin = feeMin,
                feeMax = feeMax,
                feeCurrency = feeCurrency,
                feePeriod = feePeriod,
                admissionsOpen = admissionsOpen,
                admissionWindow = admissionWindow,
                boardingOption = boardingOption,
                photoUrls = photoUrls,
                extraNotes = extraNotes,
                coursesOffered = coursesOffered,
                gradeLevels = gradeLevels,
                websiteUrl = websiteUrl,
                contactPhone = contactPhone
            )
            _isBusy.value = false
            res.onSuccess { persistedId ->
                _selectedDetailSchoolId.value = persistedId
                _uiMessages.tryEmit("Extra details saved for ${school.name}")
                onSuccess()
            }.onFailure { err ->
                _uiMessages.tryEmit(err.localizedMessage ?: "Could not save extra school details")
            }
        }
    }

    fun startClaimingExternalSchool(school: School) {
        _claimingExternalSchool.value = school
        _selectedTab.value = MainTab.ADMIN
    }

    fun clearClaimingExternalSchool() {
        _claimingExternalSchool.value = null
    }

    fun verifyDraftSchoolOnInternet(school: School, onResult: (SchoolVerificationResult) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ExternalSchoolService.verifySchoolOnInternet(school)
            _isBusy.value = false
            onResult(result)
            _uiMessages.tryEmit(result.source)
        }
    }

    fun verifyRegisteredSchoolOnInternet(school: School) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ExternalSchoolService.verifySchoolOnInternet(school)
            val saveRes = repository.updateSchoolVerification(school.id, result)
            _isBusy.value = false
            saveRes.onSuccess {
                _uiMessages.tryEmit("${school.name}: ${result.source}")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Verification update failed")
            }
        }
    }

    fun createOrUpdateSchool(school: School, isNew: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val verification = ExternalSchoolService.verifySchoolOnInternet(school)
            val resolvedStatus = when {
                verification.status == "VERIFIED" -> "VERIFIED"
                school.verificationStatus == "VERIFIED_ONLINE" -> "VERIFIED_ONLINE"
                verification.status == "INTERNET_MATCHED" -> "INTERNET_MATCHED"
                else -> school.verificationStatus.ifBlank { verification.status }
            }
            val enrichedSchool = school.copy(
                externalPlaceId = school.externalPlaceId ?: verification.matchedExternalId,
                websiteUrl = school.websiteUrl?.takeIf { it.isNotBlank() } ?: verification.discoveredWebsite,
                verificationStatus = resolvedStatus,
                verificationSource = school.verificationSource?.takeIf { it.isNotBlank() && resolvedStatus == "VERIFIED_ONLINE" }
                    ?: verification.source
            )
            val res = repository.createOrUpdateSchool(enrichedSchool, isNew)
            _isBusy.value = false
            res.onSuccess {
                _claimingExternalSchool.value = null
                val actionLabel = if (!school.externalPlaceId.isNullOrBlank() && (isNew || !school.hasPlatformAdmin())) {
                    "School profile claimed & enriched ($resolvedStatus)"
                } else if (isNew) {
                    "School registered ($resolvedStatus)"
                } else {
                    "School profile updated ($resolvedStatus)"
                }
                _uiMessages.tryEmit(actionLabel)
                onSuccess()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save school")
            }
        }
    }

    fun submitEnrollment(
        school: School,
        studentName: String,
        gradeApplying: String,
        notes: String?,
        paymentDraft: PaymentSubmissionDraft? = null,
        onSuccess: () -> Unit = {}
    ) {
        val parentName = currentDisplayName()
        val effectiveDraft = if (school.hasPlatformAdmin()) paymentDraft else null
        val feeAmount = if (school.feeMin > 0L) {
            (school.feeMin / 20L).coerceIn(5_000L, 50_000L)
        } else {
            SchoolConstants.DEFAULT_APPLICATION_FEE_NGN
        }
        viewModelScope.launch {
            _isBusy.value = true
            val enrRes = repository.submitEnrollment(
                school = school,
                parentName = parentName,
                studentName = studentName,
                gradeApplying = gradeApplying,
                notes = notes,
                paymentStatus = if (effectiveDraft == null) "UNPAID" else null
            )
            enrRes.onSuccess { enrollmentId ->
                if (effectiveDraft != null) {
                    val payRes = paymentRepository.createPaymentTransaction(
                        payerName = parentName,
                        payerEmail = userEmail,
                        recipientUid = school.adminUid,
                        recipientName = school.name,
                        paymentType = "SCHOOL_APPLICATION_FEE",
                        referenceId = enrollmentId,
                        referenceTitle = "Application Fee • $studentName ($gradeApplying)",
                        amount = feeAmount,
                        currency = school.feeCurrency,
                        paymentMethod = effectiveDraft.paymentMethod,
                        gatewayProvider = effectiveDraft.gatewayProvider,
                        notes = effectiveDraft.notes
                    )
                    _isBusy.value = false
                    payRes.onSuccess { tx ->
                        _latestPaymentReceipt.value = tx
                        _uiMessages.tryEmit("Application & payment (${tx.receiptNumber}) submitted")
                        onSuccess()
                    }.onFailure {
                        _uiMessages.tryEmit("Application saved, but payment failed: ${it.localizedMessage ?: "error"}")
                    }
                } else {
                    _isBusy.value = false
                    val actionMsg = if (school.hasPlatformAdmin()) {
                        "Enrollment application sent to ${school.name}"
                    } else {
                        "Enrollment inquiry saved for ${school.name}"
                    }
                    _uiMessages.tryEmit(actionMsg)
                    onSuccess()
                }
            }.onFailure {
                _isBusy.value = false
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to submit enrollment")
            }
        }
    }

    fun payForEnrollment(
        enrollment: Enrollment,
        amount: Long,
        currency: String,
        draft: PaymentSubmissionDraft
    ) {
        val parentName = currentDisplayName()
        viewModelScope.launch {
            _isBusy.value = true
            val payRes = paymentRepository.createPaymentTransaction(
                payerName = parentName,
                payerEmail = userEmail,
                recipientUid = enrollment.adminUid,
                recipientName = enrollment.schoolName,
                paymentType = "SCHOOL_APPLICATION_FEE",
                referenceId = enrollment.id,
                referenceTitle = "Application Fee • ${enrollment.studentName} (${enrollment.gradeApplying})",
                amount = amount,
                currency = currency,
                paymentMethod = draft.paymentMethod,
                gatewayProvider = draft.gatewayProvider,
                notes = draft.notes
            )
            _isBusy.value = false
            payRes.onSuccess { tx ->
                _latestPaymentReceipt.value = tx
                _uiMessages.tryEmit("Payment recorded (${tx.receiptNumber})")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Payment failed")
            }
        }
    }

    fun updateEnrollmentStatus(enrollment: Enrollment, status: String, response: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateEnrollmentStatus(enrollment, status, response)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Application marked $status")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to update application")
            }
        }
    }

    fun logStudentAttendance(student: StudentRecord, dateLabel: String, status: String, note: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.logStudentAttendance(student, dateLabel, status, note)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Attendance recorded for ${student.studentName}")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to record attendance")
            }
        }
    }

    fun addStudentGrade(student: StudentRecord, subject: String, score: Double, term: String, remark: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addStudentGrade(student, subject, score, term, remark)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Grade updated for ${student.studentName}")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save grade")
            }
        }
    }

    fun openChatWithSchool(school: School) {
        val myName = currentDisplayName()
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.getOrCreateConversation(school, myName)
            _isBusy.value = false
            res.onSuccess { conv ->
                _selectedConversation.value = conv
                _selectedTab.value = MainTab.MESSAGES
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Unable to open conversation")
            }
        }
    }

    fun sendMessage(conversation: Conversation, text: String) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val myName = currentDisplayName()
        val role = if (conversation.adminUid == currentUserId && conversation.parentUid != currentUserId) {
            "admin"
        } else {
            profile?.role ?: "parent"
        }
        viewModelScope.launch {
            val res = repository.sendMessage(conversation, myName, role, text)
            res.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Message failed to send")
            }
        }
    }

    fun publishAnnouncement(
        school: School,
        title: String,
        body: String,
        category: String,
        eventDate: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.publishAnnouncement(school, title, body, category, eventDate)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Announcement broadcast live")
                onSuccess()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to broadcast announcement")
            }
        }
    }

    fun createOrUpdateAdvert(
        advert: Advert,
        isNew: Boolean,
        paymentDraft: PaymentSubmissionDraft? = null,
        onSuccess: () -> Unit = {}
    ) {
        val payerName = currentDisplayName()
        val matchedPlan = SchoolConstants.ADVERT_PLANS.firstOrNull { it.tier == advert.planTier }
            ?: SchoolConstants.ADVERT_PLANS[1]
        val adToSave = if (isNew && paymentDraft == null) {
            advert.copy(paymentStatus = "UNPAID")
        } else advert

        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createOrUpdateAdvert(adToSave, isNew)
            res.onSuccess { advertId ->
                if (isNew && paymentDraft != null) {
                    val payRes = paymentRepository.createPaymentTransaction(
                        payerName = payerName,
                        payerEmail = userEmail,
                        recipientUid = currentUserId,
                        recipientName = "Skolquik Paid Adverts",
                        paymentType = "ADVERT_CAMPAIGN",
                        referenceId = advertId,
                        referenceTitle = "${matchedPlan.tier} • ${advert.title}",
                        amount = matchedPlan.amountNgn,
                        currency = "NGN",
                        paymentMethod = paymentDraft.paymentMethod,
                        gatewayProvider = paymentDraft.gatewayProvider,
                        notes = paymentDraft.notes
                    )
                    _isBusy.value = false
                    payRes.onSuccess { tx ->
                        _latestPaymentReceipt.value = tx
                        _uiMessages.tryEmit("Paid advert published & payment (${tx.receiptNumber}) recorded")
                        onSuccess()
                    }.onFailure {
                        _uiMessages.tryEmit("Advert saved, payment error: ${it.localizedMessage ?: "failed"}")
                    }
                } else {
                    _isBusy.value = false
                    _uiMessages.tryEmit(if (isNew) "Paid advert published live" else "Advert updated")
                    onSuccess()
                }
            }.onFailure {
                _isBusy.value = false
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to save advert")
            }
        }
    }

    fun payForExistingAdvert(advert: Advert, draft: PaymentSubmissionDraft) {
        val payerName = currentDisplayName()
        val matchedPlan = SchoolConstants.ADVERT_PLANS.firstOrNull { it.tier == advert.planTier }
            ?: SchoolConstants.ADVERT_PLANS[1]
        viewModelScope.launch {
            _isBusy.value = true
            val payRes = paymentRepository.createPaymentTransaction(
                payerName = payerName,
                payerEmail = userEmail,
                recipientUid = currentUserId,
                recipientName = "Skolquik Paid Adverts",
                paymentType = "ADVERT_CAMPAIGN",
                referenceId = advert.id,
                referenceTitle = "${matchedPlan.tier} • ${advert.title}",
                amount = matchedPlan.amountNgn,
                currency = "NGN",
                paymentMethod = draft.paymentMethod,
                gatewayProvider = draft.gatewayProvider,
                notes = draft.notes
            )
            _isBusy.value = false
            payRes.onSuccess { tx ->
                _latestPaymentReceipt.value = tx
                _uiMessages.tryEmit("Advert campaign paid (${tx.receiptNumber})")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Advert payment failed")
            }
        }
    }

    fun updateAdvertStatus(advertId: String, newStatus: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateAdvertStatus(advertId, newStatus)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Advert status updated to $newStatus")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to update advert status")
            }
        }
    }

    fun deleteAdvert(advertId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteAdvert(advertId)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Advert removed")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to remove advert")
            }
        }
    }

    fun submitAdvertInquiry(
        advert: Advert,
        senderName: String,
        senderEmail: String,
        phone: String?,
        message: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = paymentRepository.submitAdvertInquiry(
                advert = advert,
                senderName = senderName,
                senderEmail = senderEmail,
                phone = phone,
                message = message
            )
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Inquiry sent to ${advert.advertiserName}")
                onSuccess()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to send inquiry")
            }
        }
    }

    fun updateAdvertInquiryStatus(inquiryId: String, status: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = paymentRepository.updateAdvertInquiryStatus(inquiryId, status)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Inquiry marked $status")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to update inquiry")
            }
        }
    }

    fun createOrUpdateTutor(tutor: ProgressiveTutor, isNew: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createOrUpdateTutor(tutor, isNew)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit(
                    if (isNew) "Online tutoring profile published to THE PROGRESSIVE TUTORS"
                    else "Tutoring profile updated"
                )
                onSuccess()
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to publish tutor profile")
            }
        }
    }

    fun bookTutorSession(
        tutor: ProgressiveTutor,
        studentName: String,
        curriculumTrack: String,
        subject: String,
        preferredSchedule: String,
        notes: String?,
        hours: Int = 1,
        paymentDraft: PaymentSubmissionDraft? = null,
        onSuccess: () -> Unit = {}
    ) {
        val clientName = currentDisplayName()
        val safeHours = hours.coerceIn(1, 10)
        val totalFee = (tutor.hourlyRate * safeHours).coerceAtLeast(1L)
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.bookTutorSession(
                tutor = tutor,
                clientName = clientName,
                studentName = studentName,
                curriculumTrack = curriculumTrack,
                subject = subject,
                preferredSchedule = preferredSchedule,
                notes = notes,
                paymentStatus = if (paymentDraft == null) "UNPAID" else null
            )
            res.onSuccess { sessionId ->
                if (paymentDraft != null) {
                    val payRes = paymentRepository.createPaymentTransaction(
                        payerName = clientName,
                        payerEmail = userEmail,
                        recipientUid = tutor.teacherUid,
                        recipientName = tutor.fullName,
                        paymentType = "TUTOR_SESSION",
                        referenceId = sessionId,
                        referenceTitle = "$subject (${safeHours}h) • $studentName",
                        amount = totalFee,
                        currency = tutor.currency,
                        paymentMethod = paymentDraft.paymentMethod,
                        gatewayProvider = paymentDraft.gatewayProvider,
                        notes = paymentDraft.notes
                    )
                    _isBusy.value = false
                    payRes.onSuccess { tx ->
                        _latestPaymentReceipt.value = tx
                        _uiMessages.tryEmit("Tutoring session booked & payment (${tx.receiptNumber}) confirmed")
                        onSuccess()
                    }.onFailure {
                        _uiMessages.tryEmit("Session booked, payment error: ${it.localizedMessage ?: "failed"}")
                    }
                } else {
                    _isBusy.value = false
                    _uiMessages.tryEmit("Online tutoring session requested with ${tutor.fullName}")
                    onSuccess()
                }
            }.onFailure {
                _isBusy.value = false
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to request session")
            }
        }
    }

    fun payForTutorSession(
        session: TutorSession,
        amount: Long,
        currency: String,
        draft: PaymentSubmissionDraft
    ) {
        val clientName = currentDisplayName()
        viewModelScope.launch {
            _isBusy.value = true
            val res = paymentRepository.createPaymentTransaction(
                payerName = clientName,
                payerEmail = userEmail,
                recipientUid = session.teacherUid,
                recipientName = session.tutorName,
                paymentType = "TUTOR_SESSION",
                referenceId = session.id,
                referenceTitle = "${session.subject} • ${session.studentName}",
                amount = amount,
                currency = currency,
                paymentMethod = draft.paymentMethod,
                gatewayProvider = draft.gatewayProvider,
                notes = draft.notes
            )
            _isBusy.value = false
            res.onSuccess { tx ->
                _latestPaymentReceipt.value = tx
                _uiMessages.tryEmit("Session fee paid (${tx.receiptNumber})")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Session payment failed")
            }
        }
    }

    fun confirmPaymentPaid(payment: PaymentTransaction, note: String? = "Verified paid") {
        viewModelScope.launch {
            _isBusy.value = true
            val res = paymentRepository.updatePaymentStatus(payment, "PAID", note)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Payment ${payment.receiptNumber} verified as PAID")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to verify payment")
            }
        }
    }

    fun updateTutorSessionStatus(session: TutorSession, newStatus: String, meetingLink: String? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateTutorSessionStatus(session, newStatus, meetingLink)
            _isBusy.value = false
            res.onSuccess {
                _uiMessages.tryEmit("Session marked $newStatus")
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Failed to update session")
            }
        }
    }

    fun discoverRealTutorsOnline(queryOverride: String? = null) {
        val tf = _tutorFilter.value
        val query = (queryOverride ?: tf.query).trim()
        if (_onlineTutorsLoading.value) return
        viewModelScope.launch {
            _onlineTutorsLoading.value = true
            _onlineTutorsError.value = null
            val res = onlineGroundingService.searchVerifiedOnlineTutors(
                query = query,
                centerLat = tf.clientLat,
                centerLng = tf.clientLng,
                centerLabel = tf.clientLabel
            )
            _onlineTutorsLoading.value = false
            res.onSuccess { discovered ->
                if (discovered.isNotEmpty()) {
                    _discoveredOnlineTutors.update { existing ->
                        (discovered + existing).distinctBy { it.id }
                    }
                    _uiMessages.tryEmit("Found ${discovered.size} verified tutoring providers via Google")
                } else {
                    _uiMessages.tryEmit("No additional tutoring providers found online for ${tf.clientLabel}")
                }
            }.onFailure { err ->
                _onlineTutorsError.value = err.localizedMessage ?: "Online tutor lookup failed"
            }
        }
    }

    fun toggleFavoriteSchool(school: School) {
        val currentFavs = favoriteSchoolIds.value
        val currentlyFavorite = school.id in currentFavs
        if (!currentlyFavorite && currentFavs.size >= 100) {
            _uiMessages.tryEmit("You can save up to 100 favorite schools.")
            return
        }
        viewModelScope.launch {
            val res = repository.toggleFavoriteSchool(school, currentlyFavorite)
            res.onSuccess { nowSaved ->
                _uiMessages.tryEmit(
                    if (nowSaved) "Saved ${school.name} to favorites"
                    else "Removed ${school.name} from favorites"
                )
            }.onFailure {
                _uiMessages.tryEmit(it.localizedMessage ?: "Could not update favorite status")
            }
        }
    }

    fun setAiModelOption(option: AiAdvisorModelOption) {
        _aiModelOption.value = option
    }

    fun clearAiAdvisorChat() {
        _aiChatMessages.value = emptyList()
        _aiChatError.value = null
    }

    fun sendAiAdvisorMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty() || _isAiGenerating.value) return

        val previousHistory = _aiChatMessages.value
        val userMsg = AiAdvisorMessage(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            isUser = true,
            text = trimmed
        )
        val assistantMsgId = "ai_${UUID.randomUUID().toString().take(8)}"
        val selectedOption = _aiModelOption.value
        val placeholderAiMsg = AiAdvisorMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            modelLabel = selectedOption.label,
            isStreaming = true
        )

        _aiChatError.value = null
        _isAiGenerating.value = true
        _aiChatMessages.value = previousHistory + userMsg + placeholderAiMsg

        viewModelScope.launch {
            val accumulated = StringBuilder()
            val accumulatedLinks = linkedMapOf<String, GroundingSourceLink>()
            try {
                val registered = (schoolsState.value as? UiState.Success)?.data.orEmpty()
                val knownSchools = ExternalSchoolService.mergeRegisteredAndExternalSchools(
                    registered = registered,
                    external = _externalSchools.value
                )
                val grounding = aiAdvisorService.buildGoogleMapsGroundingContext(
                    userQuery = trimmed,
                    currentFilter = _searchFilter.value,
                    knownSchools = knownSchools,
                    favoriteSchoolIds = favoriteSchoolIds.value,
                    tutorCount = (tutorsState.value as? UiState.Success)?.data?.size ?: 0,
                    activeAdvertCount = activeAdvertsWithDistance.value.count { it.advert.status == "ACTIVE" }
                )

                val groundedPrompt = "${grounding.promptBlock}\n\nUser Question: $trimmed"

                withTimeout(60_000L) {
                    aiAdvisorService.streamGroundedChatTurn(
                        modelOption = selectedOption,
                        previousMessages = previousHistory,
                        groundedUserPrompt = groundedPrompt,
                        centerLat = grounding.centerLat,
                        centerLng = grounding.centerLng
                    ).collect { chunk ->
                        chunk.groundingLinks.forEach { link ->
                            accumulatedLinks.putIfAbsent(link.uri, link)
                        }
                        if (chunk.text.isNotEmpty()) {
                            accumulated.append(chunk.text)
                        }
                        if (chunk.text.isNotEmpty() || chunk.groundingLinks.isNotEmpty()) {
                            val partial = accumulated.toString()
                            val linksSnapshot = accumulatedLinks.values.toList()
                            _aiChatMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(
                                            text = partial,
                                            groundedArea = grounding.areaLabel,
                                            groundedSchoolCount = grounding.matchedSchoolCount,
                                            isStreaming = true,
                                            groundingLinks = linksSnapshot
                                        )
                                    } else msg
                                }
                            }
                        }
                    }
                }

                if (accumulated.isBlank()) {
                    throw IllegalStateException("Gemini returned an empty response.")
                }

                val finalLinks = accumulatedLinks.values.toList()
                _aiChatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(
                                text = accumulated.toString(),
                                groundedArea = grounding.areaLabel,
                                groundedSchoolCount = grounding.matchedSchoolCount,
                                isStreaming = false,
                                groundingLinks = finalLinks
                            )
                        } else msg
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Firebase AI Advisor chat timed out", e)
                _aiChatMessages.update { it.filterNot { m -> m.id == assistantMsgId } }
                _aiChatError.value = "Skolquik AI timed out after 60 seconds. Check your connection and try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Advisor chat failed", e)
                _aiChatMessages.update { it.filterNot { m -> m.id == assistantMsgId } }
                _aiChatError.value = e.localizedMessage ?: "Skolquik AI request failed."
            } finally {
                _isAiGenerating.value = false
            }
        }
    }
}


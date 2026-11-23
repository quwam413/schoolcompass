package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import com.example.ui.theme.FavoriteHeartColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Advert
import com.example.data.AdvertWithDistance
import com.example.data.Announcement
import com.example.data.FaithFilter
import com.example.data.GoogleMapsPlatformService
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import com.example.data.SpecialFilter
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusPresentGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun DiscoverScreen(
    schoolsUiState: UiState<List<School>>,
    filteredSchools: List<SchoolWithDistance>,
    filter: SchoolSearchFilter,
    isBusy: Boolean,
    isNetworkOnline: Boolean = true,
    connectionLabel: String = "Internet Connected",
    externalDirectoryLoading: Boolean = false,
    externalDirectoryError: String? = null,
    isGeocodingLocation: Boolean = false,
    onRefreshExternalSchools: () -> Unit = {},
    onGeocodeSearch: (String) -> Unit = {},
    onUpdateFilter: ((SchoolSearchFilter) -> SchoolSearchFilter) -> Unit,
    onResetFilters: () -> Unit,
    onMessageAdmin: (School) -> Unit,
    onSubmitEnrollment: (School, String, String, String?, PaymentSubmissionDraft?) -> Unit,
    onClaimSchool: (School) -> Unit = {},
    onSeedSampleSchools: () -> Unit,
    onRegisterSchoolClick: () -> Unit,
    activeAdverts: List<AdvertWithDistance> = emptyList(),
    onOpenPaidAdverts: () -> Unit = {},
    favoriteSchoolIds: Set<String> = emptySet(),
    onToggleFavoriteSchool: (School) -> Unit = {},
    onOpenAiAdvisor: () -> Unit = {},
    selectedSchoolDetailState: UiState<School?> = UiState.Success(null),
    selectedSchoolAnnouncementsState: UiState<List<Announcement>> = UiState.Success(emptyList()),
    onSelectSchoolForDetail: (School?) -> Unit = {}
) {
    val context = LocalContext.current
    val fusedClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    var previousOnline by remember { mutableStateOf(isNetworkOnline) }
    LaunchedEffect(isNetworkOnline) {
        if (!previousOnline && isNetworkOnline) {
            onRefreshExternalSchools()
        }
        previousOnline = isNetworkOnline
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedSchoolItem by remember { mutableStateOf<SchoolWithDistance?>(null) }
    var inspectedAdvert by remember { mutableStateOf<Advert?>(null) }

    LaunchedEffect(selectedSchoolItem?.school?.id) {
        onSelectSchoolForDetail(selectedSchoolItem?.school)
    }

    fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    var hasLocationPermission by remember { mutableStateOf(checkLocationPermission()) }
    var isLocatingUser by remember { mutableStateOf(false) }
    var locationDeniedHint by remember { mutableStateOf(false) }

    val centerLatLng = LatLng(filter.centerLat, filter.centerLng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerLatLng, zoomForRadiusKm(filter.radiusKm))
    }

    LaunchedEffect(filter.centerLat, filter.centerLng, filter.radiusKm) {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(
                LatLng(filter.centerLat, filter.centerLng),
                zoomForRadiusKm(filter.radiusKm)
            ),
            durationMs = 350
        )
    }

    @SuppressLint("MissingPermission")
    fun centerOnGps() {
        isLocatingUser = true
        locationDeniedHint = false
        val cts = CancellationTokenSource()
        fun applyGpsCoordinates(lat: Double, lng: Double) {
            onUpdateFilter {
                it.copy(
                    centerLat = lat,
                    centerLng = lng,
                    centerLabel = "Current GPS",
                    userGpsLat = lat,
                    userGpsLng = lng,
                    limitByRadius = true,
                    lockToNigeria = if (GoogleMapsPlatformService.isInsideNigeriaBounds(lat, lng)) it.lockToNigeria else false
                )
            }
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { currentLoc ->
                if (currentLoc != null) {
                    isLocatingUser = false
                    applyGpsCoordinates(currentLoc.latitude, currentLoc.longitude)
                } else {
                    fusedClient.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            isLocatingUser = false
                            if (lastLoc != null) {
                                applyGpsCoordinates(lastLoc.latitude, lastLoc.longitude)
                            }
                        }
                        .addOnFailureListener { isLocatingUser = false }
                }
            }
            .addOnFailureListener {
                fusedClient.lastLocation
                    .addOnSuccessListener { lastLoc ->
                        isLocatingUser = false
                        if (lastLoc != null) {
                            applyGpsCoordinates(lastLoc.latitude, lastLoc.longitude)
                        }
                    }
                    .addOnFailureListener { isLocatingUser = false }
            }
    }

    val gpsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            centerOnGps()
        } else {
            locationDeniedHint = true
        }
    }

    fun requestOrCenterOnGps() {
        if (checkLocationPermission()) {
            hasLocationPermission = true
            centerOnGps()
        } else {
            gpsPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            centerOnGps()
        }
    }

    val activeFilterCount = listOfNotNull(
        filter.institutionType,
        filter.curriculum,
        filter.studentMix,
        filter.maxFeeBudget,
        if (filter.onlyFavorites) filter.onlyFavorites else null,
        if (filter.faithFilter != FaithFilter.ALL) filter.faithFilter else null,
        if (filter.specialSchoolFilter != SpecialFilter.ALL) filter.specialSchoolFilter else null,
        if (filter.minRankingScore > 0.0) filter.minRankingScore else null
    ).size + filter.selectedProgrammes.size + filter.selectedCourses.size + filter.selectedActivities.size + filter.selectedSports.size

    @Composable
    fun SearchAndQuickFilterHeader() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search school, uni, course, curriculum…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_search_input")
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("open_filters_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge { Text("$activeFilterCount") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = "Open school filters",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filter.onlyFavorites,
                        onClick = { onUpdateFilter { it.copy(onlyFavorites = !it.onlyFavorites) } },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = if (filter.onlyFavorites) FavoriteHeartColor else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Favorites (${favoriteSchoolIds.size})") },
                        modifier = Modifier.testTag("favorites_filter_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenAiAdvisor,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Ask Skolquik AI") },
                        modifier = Modifier.testTag("discover_ai_advisor_chip")
                    )
                }
                items(SchoolConstants.INSTITUTION_TYPES) { instType ->
                    FilterChip(
                        selected = filter.institutionType == instType,
                        onClick = {
                            onUpdateFilter { it.copy(institutionType = if (it.institutionType == instType) null else instType) }
                        },
                        label = { Text(instType) },
                        modifier = Modifier.testTag("institution_type_chip_$instType")
                    )
                }
                items(listOf("Nigerian", "British", "Nigerian & British")) { curr ->
                    FilterChip(
                        selected = filter.curriculum == curr,
                        onClick = {
                            onUpdateFilter { it.copy(curriculum = if (it.curriculum == curr) null else curr) }
                        },
                        label = { Text(curr) },
                        modifier = Modifier.testTag("curriculum_chip_$curr")
                    )
                }
                item {
                    FilterChip(
                        selected = filter.faithFilter == FaithFilter.FAITH_BASED,
                        onClick = {
                            onUpdateFilter {
                                it.copy(
                                    faithFilter = if (it.faithFilter == FaithFilter.FAITH_BASED) {
                                        FaithFilter.ALL
                                    } else {
                                        FaithFilter.FAITH_BASED
                                    }
                                )
                            }
                        },
                        label = { Text("Faith-Based") }
                    )
                }
                item {
                    FilterChip(
                        selected = filter.specialSchoolFilter == SpecialFilter.SPECIAL_ONLY,
                        onClick = {
                            onUpdateFilter {
                                it.copy(
                                    specialSchoolFilter = if (it.specialSchoolFilter == SpecialFilter.SPECIAL_ONLY) {
                                        SpecialFilter.ALL
                                    } else {
                                        SpecialFilter.SPECIAL_ONLY
                                    }
                                )
                            }
                        },
                        label = { Text("Special Ed") }
                    )
                }
            }
        }
    }

    @Composable
    fun ResultsHeaderRow() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredSchools.size} schools within ${filter.radiusKm} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sorted: ${filter.sortBy.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (externalDirectoryLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Discovering existing geolocated schools nearby…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (externalDirectoryError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Map directory sync paused ($externalDirectoryError)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefreshExternalSchools) {
                        Text("Retry")
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdvertSpotlightGrid4x2(
                    activeAdverts = activeAdverts,
                    onAdvertClick = { ad ->
                        if (ad != null) inspectedAdvert = ad else onOpenPaidAdverts()
                    },
                    onOpenPaidAdverts = onOpenPaidAdverts
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiscoverMapSection(
                            filter = filter,
                            filteredSchools = filteredSchools,
                            hasLocationPermission = hasLocationPermission,
                            isLocatingUser = isLocatingUser,
                            locationDeniedHint = locationDeniedHint,
                            isNetworkOnline = isNetworkOnline,
                            connectionLabel = connectionLabel,
                            isGeocodingLocation = isGeocodingLocation,
                            cameraPositionState = cameraPositionState,
                            onRequestGpsCenter = ::requestOrCenterOnGps,
                            onGeocodeSearch = onGeocodeSearch,
                            onUpdateFilter = onUpdateFilter,
                            onSelectSchool = { selectedSchoolItem = it }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { SearchAndQuickFilterHeader() }
                        item { ResultsHeaderRow() }
                        when (schoolsUiState) {
                            UiState.Loading -> item {
                                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            is UiState.Error -> item { ErrorBanner(schoolsUiState.message) }
                            is UiState.Success -> {
                                if (filteredSchools.isEmpty() && schoolsUiState.data.isEmpty() && !externalDirectoryLoading) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Filled.School,
                                            title = "No schools found around ${filter.centerLabel}",
                                            subtitle = "Register a new school in the Admin Hub, retry map discovery, or load sample Nigerian & British schools around ${filter.centerLabel}.",
                                            actionLabel = if (isBusy) "Loading…" else "Load Sample Schools Around Me",
                                            onAction = { if (!isBusy) onSeedSampleSchools() }
                                        )
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = onRegisterSchoolClick,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Or Register Your Own School")
                                        }
                                    }
                                } else if (filteredSchools.isEmpty() && !externalDirectoryLoading) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Filled.LocationOn,
                                            title = "No schools match within ${filter.radiusKm} km",
                                            subtitle = "Drag the radius slider wider or reset your curriculum, fee, or sports filters.",
                                            actionLabel = "Reset Filters & Expand",
                                            onAction = {
                                                onResetFilters()
                                                onUpdateFilter { it.copy(radiusKm = 50) }
                                            }
                                        )
                                    }
                                } else {
                                    items(filteredSchools, key = { it.school.id }) { item ->
                                        SchoolResultCard(
                                            item = item,
                                            isFavorite = item.school.id in favoriteSchoolIds,
                                            onToggleFavorite = onToggleFavoriteSchool,
                                            onClick = { selectedSchoolItem = item }
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 4x2 Advert Tile Grid pinned at the very top of the Home Screen
                item {
                    AdvertSpotlightGrid4x2(
                        activeAdverts = activeAdverts,
                        onAdvertClick = { ad ->
                            if (ad != null) inspectedAdvert = ad else onOpenPaidAdverts()
                        },
                        onOpenPaidAdverts = onOpenPaidAdverts
                    )
                }

                item { SearchAndQuickFilterHeader() }

                item {
                    DiscoverMapSection(
                        filter = filter,
                        filteredSchools = filteredSchools,
                        hasLocationPermission = hasLocationPermission,
                        isLocatingUser = isLocatingUser,
                        locationDeniedHint = locationDeniedHint,
                        isNetworkOnline = isNetworkOnline,
                        connectionLabel = connectionLabel,
                        isGeocodingLocation = isGeocodingLocation,
                        cameraPositionState = cameraPositionState,
                        onRequestGpsCenter = ::requestOrCenterOnGps,
                        onGeocodeSearch = onGeocodeSearch,
                        onUpdateFilter = onUpdateFilter,
                        onSelectSchool = { selectedSchoolItem = it }
                    )
                }

                item { ResultsHeaderRow() }

                when (schoolsUiState) {
                    UiState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item { ErrorBanner(schoolsUiState.message) }
                    is UiState.Success -> {
                        if (filteredSchools.isEmpty() && schoolsUiState.data.isEmpty() && !externalDirectoryLoading) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.School,
                                    title = "No schools found around ${filter.centerLabel}",
                                    subtitle = "Register a new school in the Admin Hub, retry map discovery, or load sample Nigerian & British schools around ${filter.centerLabel}.",
                                    actionLabel = if (isBusy) "Loading…" else "Load Sample Schools Around Me",
                                    onAction = { if (!isBusy) onSeedSampleSchools() }
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = onRegisterSchoolClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Or Register Your Own School")
                                }
                            }
                        } else if (filteredSchools.isEmpty() && !externalDirectoryLoading) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.LocationOn,
                                    title = "No schools match within ${filter.radiusKm} km",
                                    subtitle = "Drag the radius slider wider or reset curriculum, fee, or sports filters.",
                                    actionLabel = "Reset Filters & Expand",
                                    onAction = {
                                        onResetFilters()
                                        onUpdateFilter { it.copy(radiusKm = 50) }
                                    }
                                )
                            }
                        } else {
                            items(filteredSchools, key = { it.school.id }) { item ->
                                SchoolResultCard(
                                    item = item,
                                    isFavorite = item.school.id in favoriteSchoolIds,
                                    onToggleFavorite = onToggleFavoriteSchool,
                                    onClick = { selectedSchoolItem = item }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showFilterSheet) {
        SchoolFilterBottomSheet(
            filter = filter,
            onUpdateFilter = onUpdateFilter,
            onReset = onResetFilters,
            onDismiss = { showFilterSheet = false }
        )
    }

    selectedSchoolItem?.let { item ->
        val detailSchool = (selectedSchoolDetailState as? UiState.Success)?.data ?: item.school
        val isSelectedFav = (item.school.id in favoriteSchoolIds) || (detailSchool.id in favoriteSchoolIds)
        SchoolDetailBottomSheet(
            item = item,
            firestoreDetailState = selectedSchoolDetailState,
            announcementsState = selectedSchoolAnnouncementsState,
            isFavorite = isSelectedFav,
            onToggleFavorite = onToggleFavoriteSchool,
            onDismiss = { selectedSchoolItem = null },
            onMessageAdmin = { school ->
                selectedSchoolItem = null
                onMessageAdmin(school)
            },
            onSubmitEnrollment = onSubmitEnrollment,
            onClaimSchool = { school ->
                selectedSchoolItem = null
                onClaimSchool(school)
            }
        )
    }

    inspectedAdvert?.let { ad ->
        AlertDialog(
            onDismissRequest = { inspectedAdvert = null },
            title = { Text(ad.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${ad.advertiserName} • ${ad.planTier}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(ad.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Area: ${ad.targetLocation} • ${ad.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!ad.contactInfo.isNullOrBlank()) {
                        Text(
                            text = "Contact: ${ad.contactInfo}",
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusPresentGreen
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    inspectedAdvert = null
                    onOpenPaidAdverts()
                }) {
                    Text(ad.ctaLabel.ifBlank { "Open Adverts" })
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedAdvert = null }) { Text("Close") }
            }
        )
    }
}

private data class HomeAdvertTileSlot(
    val badge: String,
    val title: String,
    val subtitle: String,
    val advert: Advert?,
    val isLiveSponsored: Boolean
)

@Composable
private fun AdvertSpotlightGrid4x2(
    activeAdverts: List<AdvertWithDistance>,
    onAdvertClick: (Advert?) -> Unit,
    onOpenPaidAdverts: () -> Unit
) {
    val fallbackPrompts = remember {
        listOf(
            HomeAdvertTileSlot("AD SLOT", "Promote School", "From ₦15k", null, false),
            HomeAdvertTileSlot("TUTORS", "Cambridge & WAEC", "Boost Classes", null, false),
            HomeAdvertTileSlot("HERO AD", "Admissions Open", "District Hero", null, false),
            HomeAdvertTileSlot("CAMP", "Sports & STEM", "Holiday Ads", null, false),
            HomeAdvertTileSlot("UNI / POLY", "Tertiary Spotlight", "Course Promo", null, false),
            HomeAdvertTileSlot("PREP", "JAMB / IGCSE", "Exam Bootcamps", null, false),
            HomeAdvertTileSlot("SPOTLIGHT", "Special Needs", "Parent Reach", null, false),
            HomeAdvertTileSlot("POST AD", "Launch Campaign", "4×2 Banner", null, false)
        )
    }

    val slots = remember(activeAdverts) {
        (0 until 8).map { idx ->
            val live = activeAdverts.getOrNull(idx)?.advert
            if (live != null) {
                HomeAdvertTileSlot(
                    badge = if (live.planTier == "District Hero") "HERO" else "AD",
                    title = live.title,
                    subtitle = live.advertiserName,
                    advert = live,
                    isLiveSponsored = true
                )
            } else {
                fallbackPrompts[idx]
            }
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_advert_grid_4x2")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sponsored Adverts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Post / All Adverts →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenPaidAdverts() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("discover_paid_adverts_banner")
                )
            }

            // 4 columns x 2 rows = 8 tiles
            for (rowIdx in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (colIdx in 0 until 4) {
                        val tileIdx = rowIdx * 4 + colIdx
                        val slot = slots[tileIdx]
                        val bgColor = when {
                            slot.isLiveSponsored && slot.badge == "HERO" ->
                                MaterialTheme.colorScheme.secondaryContainer
                            slot.isLiveSponsored ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            else ->
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val fgColor = when {
                            slot.isLiveSponsored && slot.badge == "HERO" ->
                                MaterialTheme.colorScheme.onSecondaryContainer
                            slot.isLiveSponsored ->
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else ->
                                MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            onClick = { onAdvertClick(slot.advert) },
                            shape = MaterialTheme.shapes.small,
                            color = bgColor,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (slot.isLiveSponsored) {
                                    StarGold.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .testTag("home_advert_tile_$tileIdx")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (slot.isLiveSponsored) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = StarGold,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Text(
                                        text = slot.badge,
                                        fontSize = 8.sp,
                                        lineHeight = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (slot.isLiveSponsored) fgColor else MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = slot.title,
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fgColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = slot.subtitle,
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp,
                                    color = fgColor.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

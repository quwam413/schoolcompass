package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.Advert
import com.example.data.AdvertWithDistance
import com.example.data.Announcement
import com.example.data.FaithFilter
import com.example.data.GoogleMapsPlatformService
import com.example.data.School
import com.example.data.SchoolSearchFilter
import kotlinx.coroutines.launch
import com.example.data.SchoolWithDistance
import com.example.data.SpecialFilter
import com.example.ui.theme.FavoriteHeartColor
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusPresentGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    schoolsUiState: UiState<List<School>>,
    filteredSchools: List<SchoolWithDistance>,
    filter: SchoolSearchFilter,
    isBusy: Boolean,
    isRefreshingHome: Boolean = false,
    isNetworkOnline: Boolean = true,
    connectionLabel: String = "Internet Connected",
    externalDirectoryLoading: Boolean = false,
    externalDirectoryError: String? = null,
    onlineGroundingLoading: Boolean = false,
    onlineGroundingError: String? = null,
    isGeocodingLocation: Boolean = false,
    onPullToRefreshHome: () -> Unit = {},
    onRefreshExternalSchools: () -> Unit = {},
    onSearchOnline: () -> Unit = {},
    onGeocodeSearch: (String) -> Unit = {},
    onUpdateFilter: ((SchoolSearchFilter) -> SchoolSearchFilter) -> Unit,
    onResetFilters: () -> Unit,
    onMessageAdmin: (School) -> Unit,
    onSubmitEnrollment: (School, String, String, String?, PaymentSubmissionDraft?) -> Unit,
    onClaimSchool: (School) -> Unit = {},
    onSubmitExtraDetails: (
        School, Long, Long, String, String, Boolean, String?, String?,
        List<String>, String?, List<String>, List<String>, String?, String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onRegisterSchoolClick: () -> Unit,
    activeAdverts: List<AdvertWithDistance> = emptyList(),
    onOpenPaidAdverts: () -> Unit = {},
    favoriteSchoolIds: Set<String> = emptySet(),
    onToggleFavoriteSchool: (School) -> Unit = {},
    onOpenAiAdvisor: () -> Unit = {},
    selectedSchoolDetailState: UiState<School?> = UiState.Success(null),
    selectedSchoolAnnouncementsState: UiState<List<Announcement>> = UiState.Success(emptyList()),
    currentUserId: String = "",
    schoolVisitorCounts: Map<String, Int> = emptyMap(),
    onSelectSchoolForDetail: (School?) -> Unit = {},
    searchFocusTick: Int = 0
) {
    val context = LocalContext.current
    val fusedClient = remember(context) {
        runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull()
    }

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
    var enrichingSchool by remember { mutableStateOf<School?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val phoneListState = rememberLazyListState()
    val wideListState = rememberLazyListState()

    LaunchedEffect(searchFocusTick) {
        if (searchFocusTick > 0) {
            runCatching { phoneListState.animateScrollToItem(1) }
            runCatching { wideListState.animateScrollToItem(0) }
            delay(80L)
            runCatching { searchFocusRequester.requestFocus() }
            runCatching { keyboardController?.show() }
        }
    }

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

    val hasValidGoogleMapsKey = remember { GoogleMapsPlatformService.hasValidGoogleMapsKey() }
    var mapsSdkReady by remember { mutableStateOf(false) }
    LaunchedEffect(hasValidGoogleMapsKey) {
        if (!hasValidGoogleMapsKey) return@LaunchedEffect
        withFrameNanos { }
        val status = runCatching {
            MapsInitializer.initialize(context.applicationContext)
        }.getOrDefault(-1)
        mapsSdkReady = (status == 0)
    }

    LaunchedEffect(mapsSdkReady, filter.centerLat, filter.centerLng, filter.radiusKm) {
        val target = LatLng(filter.centerLat, filter.centerLng)
        val zoom = zoomForRadiusKm(filter.radiusKm)
        if (!mapsSdkReady) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(target, zoom)
            return@LaunchedEffect
        }
        try {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, zoom),
                durationMs = 350
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(target, zoom)
        }
    }

    val gpsScope = androidx.compose.runtime.rememberCoroutineScope()

    @SuppressLint("MissingPermission")
    fun centerOnGps() {
        isLocatingUser = true
        locationDeniedHint = false
        val cts = CancellationTokenSource()
        fun applyGpsCoordinates(lat: Double, lng: Double) {
            gpsScope.launch {
                val placeLabel = GoogleMapsPlatformService.resolveHumanReadableLocation(context, lat, lng)
                onUpdateFilter {
                    it.copy(
                        centerLat = lat,
                        centerLng = lng,
                        centerLabel = placeLabel,
                        userGpsLat = lat,
                        userGpsLng = lng,
                        limitByRadius = true,
                        lockToNigeria = if (GoogleMapsPlatformService.isInsideNigeriaBounds(lat, lng)) it.lockToNigeria else false
                    )
                }
            }
        }
        val client = fusedClient
        if (client == null) {
            isLocatingUser = false
            return
        }
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { currentLoc ->
                    if (currentLoc != null) {
                        isLocatingUser = false
                        applyGpsCoordinates(currentLoc.latitude, currentLoc.longitude)
                    } else {
                        try {
                            client.lastLocation
                                .addOnSuccessListener { lastLoc ->
                                    isLocatingUser = false
                                    if (lastLoc != null) {
                                        applyGpsCoordinates(lastLoc.latitude, lastLoc.longitude)
                                    }
                                }
                                .addOnFailureListener { isLocatingUser = false }
                        } catch (_: Exception) {
                            isLocatingUser = false
                        }
                    }
                }
                .addOnFailureListener {
                    try {
                        client.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                isLocatingUser = false
                                if (lastLoc != null) {
                                    applyGpsCoordinates(lastLoc.latitude, lastLoc.longitude)
                                }
                            }
                            .addOnFailureListener { isLocatingUser = false }
                    } catch (_: Exception) {
                        isLocatingUser = false
                    }
                }
        } catch (_: Exception) {
            isLocatingUser = false
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
            runCatching {
                gpsPermLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }.onFailure {
                locationDeniedHint = true
            }
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
            OutlinedTextField(
                value = filter.query,
                onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(
                            onClick = onSearchOnline,
                            enabled = !onlineGroundingLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("discover_search_online_button")
                        ) {
                            if (onlineGroundingLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.TravelExplore,
                                    contentDescription = "Search verified schools online",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_filters_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeFilterCount > 0) {
                                        Badge { Text("$activeFilterCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Open school filters",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                placeholder = { Text("Search schools, curricula, cities…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchOnline() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .testTag("discover_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Primary & Secondary", "University")) { instType ->
                    FilterChip(
                        selected = filter.institutionType == instType,
                        onClick = {
                            onUpdateFilter { it.copy(institutionType = if (it.institutionType == instType) null else instType) }
                        },
                        label = { Text(instType) },
                        modifier = Modifier.testTag("institution_type_chip_$instType")
                    )
                }
                item {
                    FilterChip(
                        selected = filter.curriculum == "Nigerian & British",
                        onClick = {
                            onUpdateFilter {
                                it.copy(curriculum = if (it.curriculum == "Nigerian & British") null else "Nigerian & British")
                            }
                        },
                        label = { Text("Dual Curriculum") },
                        modifier = Modifier.testTag("curriculum_chip_Nigerian & British")
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = { showFilterSheet = true },
                        label = { Text(if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "More Filters") }
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
            if (onlineGroundingLoading || externalDirectoryLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (onlineGroundingLoading) {
                            "Searching Google & Maps for verified schools…"
                        } else {
                            "Discovering verified schools nearby…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (onlineGroundingError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Online school search notice: $onlineGroundingError",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSearchOnline) {
                        Text("Retry")
                    }
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

    PullToRefreshBox(
        isRefreshing = isRefreshingHome,
        onRefresh = onPullToRefreshHome,
        modifier = Modifier
            .fillMaxSize()
            .testTag("discover_pull_to_refresh")
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdvertSpotlightCarousel4x2(
                    activeAdverts = activeAdverts,
                    realSchools = filteredSchools,
                    onAdvertClick = { ad -> inspectedAdvert = ad },
                    onSchoolSlideClick = { schoolItem -> selectedSchoolItem = schoolItem },
                    onDiscoverOnline = onSearchOnline,
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
                            .weight(0.50f)
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
                        state = wideListState,
                        modifier = Modifier
                            .weight(0.50f)
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
                                if (filteredSchools.isEmpty() && schoolsUiState.data.isEmpty() && !externalDirectoryLoading && !onlineGroundingLoading) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Filled.School,
                                            title = "No schools cached around ${filter.centerLabel}",
                                            subtitle = "Pull down to reload or query Google Search & Maps for verified schools near ${filter.centerLabel}.",
                                            actionLabel = if (onlineGroundingLoading) "Searching Google…" else "Discover Schools via Google",
                                            onAction = onSearchOnline
                                        )
                                    }
                                    item {
                                        OutlinedButton(
                                            onClick = onRegisterSchoolClick,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Register or Claim a School")
                                        }
                                    }
                                } else if (filteredSchools.isEmpty() && !externalDirectoryLoading) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Filled.LocationOn,
                                            title = "No schools match within ${filter.radiusKm} km",
                                            subtitle = "Widen your radius or reset your filters.",
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
                                            onSubmitExtraDetails = { enrichingSchool = it },
                                            onClaimSchool = onClaimSchool,
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
                state = phoneListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Single-Advert 4x2 Moving Carousel (up to 10 live/verified adverts, slides right-to-left)
                item {
                    AdvertSpotlightCarousel4x2(
                        activeAdverts = activeAdverts,
                        realSchools = filteredSchools,
                        onAdvertClick = { ad -> inspectedAdvert = ad },
                        onSchoolSlideClick = { schoolItem -> selectedSchoolItem = schoolItem },
                        onDiscoverOnline = onSearchOnline,
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
                        if (filteredSchools.isEmpty() && schoolsUiState.data.isEmpty() && !externalDirectoryLoading && !onlineGroundingLoading) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.School,
                                    title = "No schools cached around ${filter.centerLabel}",
                                    subtitle = "Pull down to reload or query Google Search & Maps for verified schools near ${filter.centerLabel}.",
                                    actionLabel = if (onlineGroundingLoading) "Searching Google…" else "Discover Schools via Google",
                                    onAction = onSearchOnline
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = onRegisterSchoolClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Register or Claim a School")
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
                                    onSubmitExtraDetails = { enrichingSchool = it },
                                    onClaimSchool = onClaimSchool,
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
        val isOwned = currentUserId.isNotBlank() && detailSchool.adminUid == currentUserId
        val visitorCount = schoolVisitorCounts[detailSchool.id] ?: 0
        SchoolDetailBottomSheet(
            item = item,
            firestoreDetailState = selectedSchoolDetailState,
            announcementsState = selectedSchoolAnnouncementsState,
            isFavorite = isSelectedFav,
            isOwnedByCurrentUser = isOwned,
            pageVisitorCount = visitorCount,
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
            },
            onSubmitExtraDetails = { school ->
                enrichingSchool = school
            }
        )
    }

    enrichingSchool?.let { targetSchool ->
        SubmitSchoolDetailsDialog(
            school = targetSchool,
            onDismiss = { enrichingSchool = null },
            onSubmit = { feeMin, feeMax, curr, period, open, window, boarding, photos, notes, courses, grades, site, phone ->
                onSubmitExtraDetails(
                    targetSchool, feeMin, feeMax, curr, period, open,
                    window, boarding, photos, notes, courses, grades, site, phone
                )
                enrichingSchool = null
            }
        )
    }

    inspectedAdvert?.let { ad ->
        AlertDialog(
            onDismissRequest = { inspectedAdvert = null },
            title = { Text(ad.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!ad.bannerImageUrl.isNullOrBlank()) {
                        BannerImage(
                            imageUrl = ad.bannerImageUrl,
                            fallbackResId = com.example.R.drawable.img_campus_hero,
                            contentDescription = ad.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
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

internal data class HomeAdvertSlide(
    val badge: String,
    val title: String,
    val subtitle: String,
    val ctaText: String,
    val bannerImageUrl: String? = null,
    val advert: Advert?,
    val schoolItem: SchoolWithDistance? = null,
    val isLiveSponsored: Boolean,
    val accentPair: Pair<Color, Color>
)

internal fun buildTenAdvertSlides(
    activeAdverts: List<AdvertWithDistance>,
    realSchools: List<SchoolWithDistance> = emptyList()
): List<HomeAdvertSlide> {
    val palettes = listOf(
        Color(0xFF0B3B75) to Color(0xFF1565C0),
        Color(0xFF153243) to Color(0xFF284B63),
        Color(0xFF1E3A5F) to Color(0xFF2D6A8E),
        Color(0xFF1F4E46) to Color(0xFF2E7D6B),
        Color(0xFF2B2D42) to Color(0xFF4A507A),
        Color(0xFF1A434E) to Color(0xFF2B7A78),
        Color(0xFF2C3E50) to Color(0xFF4CA1AF),
        Color(0xFF223843) to Color(0xFF3E6985),
        Color(0xFF1B3B36) to Color(0xFF316D62),
        Color(0xFF1F2D4A) to Color(0xFF3B5998)
    )

    val combined = buildList {
        activeAdverts.forEachIndexed { idx, advDist ->
            if (size >= 10) return@forEachIndexed
            val live = advDist.advert
            add(
                HomeAdvertSlide(
                    badge = if (live.planTier == "District Hero") "HERO AD" else "SPONSORED",
                    title = live.title,
                    subtitle = "${live.advertiserName} • ${live.targetLocation.ifBlank { live.category }}",
                    ctaText = live.ctaLabel.ifBlank { "View Offer" },
                    bannerImageUrl = live.bannerImageUrl,
                    advert = live,
                    schoolItem = null,
                    isLiveSponsored = true,
                    accentPair = palettes[idx % palettes.size]
                )
            )
        }
        realSchools.forEach { item ->
            if (size >= 10) return@forEach
            val s = item.school
            val idx = size
            add(
                HomeAdvertSlide(
                    badge = s.verificationBadgeLabel().uppercase(),
                    title = s.name,
                    subtitle = buildString {
                        append(s.curriculum)
                        if (s.address.isNotBlank()) append(" • ${s.address}")
                    },
                    ctaText = "View School",
                    bannerImageUrl = s.bannerImageUrl,
                    advert = null,
                    schoolItem = item,
                    isLiveSponsored = false,
                    accentPair = palettes[idx % palettes.size]
                )
            )
        }
    }

    if (combined.isNotEmpty()) {
        return combined.take(10)
    }

    return listOf(
        HomeAdvertSlide(
            badge = "GOOGLE LIVE SEARCH",
            title = "Discovering Real Schools Nearby",
            subtitle = "Pull down or tap to search Google Search & Maps for verified schools around you.",
            ctaText = "Search Google",
            advert = null,
            schoolItem = null,
            isLiveSponsored = false,
            accentPair = palettes[0]
        )
    )
}

@Composable
private fun AdvertSpotlightCarousel4x2(
    activeAdverts: List<AdvertWithDistance>,
    realSchools: List<SchoolWithDistance>,
    onAdvertClick: (Advert) -> Unit,
    onSchoolSlideClick: (SchoolWithDistance) -> Unit,
    onDiscoverOnline: () -> Unit,
    onOpenPaidAdverts: () -> Unit
) {
    val slides = remember(activeAdverts, realSchools) {
        buildTenAdvertSlides(activeAdverts, realSchools)
    }
    val virtualPageCount = if (slides.size <= 1) 1 else slides.size * 1000
    val startPage = if (slides.size <= 1) 0 else slides.size * 500
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { virtualPageCount })
    val activeIndex = if (slides.isEmpty()) 0 else pagerState.currentPage % slides.size

    // Automatic right-to-left sliding across live adverts & real schools; pauses while user drags
    LaunchedEffect(pagerState, slides.size) {
        if (slides.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4000L)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(durationMillis = 550)
                )
            }
        }
    }

    // Single 4x2-proportioned moving advert card
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .testTag("home_advert_grid_4x2")
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .testTag("advert_carousel_pager")
        ) { virtualPage ->
            val slideIndex = virtualPage % slides.size
            val slide = slides[slideIndex]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(slide.accentPair.first, slide.accentPair.second)
                        )
                    )
                    .clickable {
                        when {
                            slide.advert != null -> onAdvertClick(slide.advert)
                            slide.schoolItem != null -> onSchoolSlideClick(slide.schoolItem)
                            else -> onDiscoverOnline()
                        }
                    }
                    .testTag("advert_slide_$slideIndex")
            ) {
                if (!slide.bannerImageUrl.isNullOrBlank()) {
                    BannerImage(
                        imageUrl = slide.bannerImageUrl,
                        fallbackResId = com.example.R.drawable.img_campus_hero,
                        contentDescription = slide.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.42f),
                                        Color.Black.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Badge + Index & Post Ad Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (slide.isLiveSponsored) Icons.Filled.Star else Icons.Filled.Campaign,
                                    contentDescription = null,
                                    tint = if (slide.isLiveSponsored) StarGold else Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${slide.badge} • ${slideIndex + 1}/${slides.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "Post Ad →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenPaidAdverts() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("discover_paid_adverts_banner")
                        )
                    }

                    // Middle: Single Clean Advert Headline & Subcopy
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = slide.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bottom Row: 10-Dot Progress Strip + Clean CTA Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(slides.size) { dotIdx ->
                                val isSelected = dotIdx == activeIndex
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                    animationSpec = tween(250),
                                    label = "advert_dot_width"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.22f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = slide.ctaText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GoogleMapsPlatformService
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import com.example.ui.theme.FavoriteHeartColor
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusAbsentRed
import com.example.ui.theme.StatusPresentGreen
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun DiscoverMapSection(
    filter: SchoolSearchFilter,
    filteredSchools: List<SchoolWithDistance>,
    hasLocationPermission: Boolean,
    isLocatingUser: Boolean,
    locationDeniedHint: Boolean,
    isNetworkOnline: Boolean = true,
    connectionLabel: String = "Internet Connected",
    isGeocodingLocation: Boolean = false,
    cameraPositionState: CameraPositionState,
    onRequestGpsCenter: () -> Unit,
    onGeocodeSearch: (String) -> Unit = {},
    onUpdateFilter: ((SchoolSearchFilter) -> SchoolSearchFilter) -> Unit,
    onSelectSchool: (SchoolWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapExpanded by remember { mutableStateOf(false) }
    var showMapControls by remember { mutableStateOf(false) }
    val hasValidGoogleMapsKey = remember { GoogleMapsPlatformService.hasValidGoogleMapsKey() }
    var useLiveStreetMap by remember(hasValidGoogleMapsKey) { mutableStateOf(!hasValidGoogleMapsKey) }
    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var trafficEnabled by remember { mutableStateOf(false) }
    var addressQuery by remember { mutableStateOf("") }
    var fitNigeriaOverviewTick by remember { mutableIntStateOf(0) }
    val centerLatLng = LatLng(filter.centerLat, filter.centerLng)
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { MapsInitializer.initialize(context.applicationContext) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Minimalist Map Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isNetworkOnline) StatusPresentGreen else StatusAbsentRed)
                )
                Column {
                    Text(
                        text = filter.centerLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isNetworkOnline) "Live Map • ${if (useLiveStreetMap) "OpenStreetMap" else "Google Maps"}" else "Offline • Cached directory",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.centerLabel == "Current GPS",
                    onClick = onRequestGpsCenter,
                    leadingIcon = {
                        Icon(
                            imageVector = if (isLocatingUser) Icons.Filled.LocationSearching else Icons.Filled.MyLocation,
                            contentDescription = "Center on my GPS location",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text(if (isLocatingUser) "Locating…" else "My GPS") },
                    modifier = Modifier.testTag("gps_location_button")
                )
                FilterChip(
                    selected = showMapControls,
                    onClick = { showMapControls = !showMapControls },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Toggle area & map layer options",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Area") },
                    modifier = Modifier.testTag("toggle_map_controls_button")
                )
            }
        }

        // Collapsible Area, Map Engine & Place Search Controls (kept tucked away until needed)
        AnimatedVisibility(visible = showMapControls) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(SchoolConstants.PRESET_LOCATIONS) { preset ->
                            FilterChip(
                                selected = filter.centerLabel == preset.label,
                                onClick = {
                                    onUpdateFilter {
                                        it.copy(
                                            centerLat = preset.lat,
                                            centerLng = preset.lng,
                                            centerLabel = preset.label,
                                            lockToNigeria = GoogleMapsPlatformService.isInsideNigeriaBounds(preset.lat, preset.lng)
                                        )
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = { addressQuery = it },
                            leadingIcon = { Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            placeholder = {
                                Text(
                                    if (filter.lockToNigeria) "Jump to area in Nigeria (e.g. Ikorodu)…"
                                    else "Jump map to area, city or address…"
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (addressQuery.trim().length >= 2) {
                                        onGeocodeSearch(addressQuery.trim())
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("map_geocode_input")
                        )
                        Button(
                            onClick = { onGeocodeSearch(addressQuery.trim()) },
                            enabled = !isGeocodingLocation && addressQuery.trim().length >= 2,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("map_geocode_button")
                        ) {
                            if (isGeocodingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Go")
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = useLiveStreetMap,
                                onClick = { useLiveStreetMap = true },
                                leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("OpenStreetMap") },
                                modifier = Modifier.testTag("map_engine_osm_chip")
                            )
                        }
                        item {
                            FilterChip(
                                selected = !useLiveStreetMap,
                                onClick = { useLiveStreetMap = false },
                                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Google Maps") },
                                modifier = Modifier.testTag("map_engine_google_chip")
                            )
                        }
                        if (useLiveStreetMap) {
                            item {
                                FilterChip(
                                    selected = filter.showNigeriaStates,
                                    onClick = { onUpdateFilter { it.copy(showNigeriaStates = !it.showNigeriaStates) } },
                                    leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    label = { Text("States") },
                                    modifier = Modifier.testTag("map_nigeria_states_chip")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filter.lockToNigeria,
                                    onClick = { onUpdateFilter { it.copy(lockToNigeria = !it.lockToNigeria) } },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (filter.lockToNigeria) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = { Text(if (filter.lockToNigeria) "Nigeria" else "Global") },
                                    modifier = Modifier.testTag("map_nigeria_lock_chip")
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = {
                                        onUpdateFilter { it.copy(lockToNigeria = true) }
                                        fitNigeriaOverviewTick++
                                    },
                                    modifier = Modifier.testTag("map_fit_nigeria_button")
                                ) {
                                    Text("Fit Nigeria")
                                }
                            }
                        } else {
                            items(
                                listOf(
                                    MapType.NORMAL to "Normal",
                                    MapType.HYBRID to "Hybrid",
                                    MapType.SATELLITE to "Satellite",
                                    MapType.TERRAIN to "Terrain"
                                )
                            ) { (mapType, label) ->
                                FilterChip(
                                    selected = selectedMapType == mapType,
                                    onClick = { selectedMapType = mapType },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("gmap_type_${label.lowercase()}")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = trafficEnabled,
                                    onClick = { trafficEnabled = !trafficEnabled },
                                    leadingIcon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    label = { Text("Traffic") },
                                    modifier = Modifier.testTag("gmap_traffic_chip")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (locationDeniedHint) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Location permission is off. Select an area or tap anywhere on the map to set your search center.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (!useLiveStreetMap && !hasValidGoogleMapsKey) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Google Maps SDK requires a MAPS_API_KEY in AI Studio Secrets. Switch to OpenStreetMap inside Area options for instant tiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Integrated Minimalist Map Viewport
        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .height(if (mapExpanded) 360.dp else 232.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            if (useLiveStreetMap) {
                InternetStreetMapView(
                    filter = filter,
                    filteredSchools = filteredSchools,
                    isNetworkOnline = isNetworkOnline,
                    fitNigeriaOverviewTick = fitNigeriaOverviewTick,
                    onPinLocation = { lat, lng ->
                        onUpdateFilter {
                            it.copy(
                                centerLat = lat,
                                centerLng = lng,
                                centerLabel = "Pinned (${String.format("%.3f", lat)}, ${String.format("%.3f", lng)})",
                                lockToNigeria = if (GoogleMapsPlatformService.isInsideNigeriaBounds(lat, lng)) it.lockToNigeria else false
                            )
                        }
                    },
                    onSelectSchool = onSelectSchool,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("google_map_view"),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapType = selectedMapType,
                        isMyLocationEnabled = hasLocationPermission,
                        isTrafficEnabled = trafficEnabled,
                        isBuildingEnabled = true,
                        isIndoorEnabled = true
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                        compassEnabled = true,
                        mapToolbarEnabled = true
                    ),
                    onMapClick = { tapped ->
                        onUpdateFilter {
                            it.copy(
                                centerLat = tapped.latitude,
                                centerLng = tapped.longitude,
                                centerLabel = "Pinned (${String.format("%.3f", tapped.latitude)}, ${String.format("%.3f", tapped.longitude)})"
                            )
                        }
                    }
                ) {
                    Marker(
                        state = MarkerState(position = centerLatLng),
                        title = "Search Center",
                        snippet = if (filter.limitByRadius) "${filter.centerLabel} • ${filter.radiusKm} km radius" else filter.centerLabel,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                    if (filter.limitByRadius) {
                        Circle(
                            center = centerLatLng,
                            radius = filter.radiusKm * 1000.0,
                            fillColor = primaryColor.copy(alpha = 0.14f),
                            strokeColor = primaryColor.copy(alpha = 0.85f),
                            strokeWidth = 3.5f
                        )
                    }
                    filteredSchools.forEach { item ->
                        val s = item.school
                        val isRegistered = s.hasPlatformAdmin()
                        val markerHue = when {
                            !isRegistered -> BitmapDescriptorFactory.HUE_YELLOW
                            s.institutionType == "University" -> BitmapDescriptorFactory.HUE_CYAN
                            s.institutionType == "Polytechnic" || s.institutionType == "College of Education" || s.institutionType == "Technical / Vocational" -> BitmapDescriptorFactory.HUE_MAGENTA
                            s.specialSchool -> BitmapDescriptorFactory.HUE_VIOLET
                            s.curriculum == "Nigerian & British" -> BitmapDescriptorFactory.HUE_GREEN
                            s.curriculum == "British" -> BitmapDescriptorFactory.HUE_BLUE
                            s.curriculum == "Nigerian" -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_ROSE
                        }
                        val snippetText = if (isRegistered) {
                            "${s.institutionType} • ${s.curriculum} • ${s.formattedFeeRange()} • ${String.format("%.1f", item.distanceKm)} km"
                        } else {
                            "${s.institutionType} (${s.verificationBadgeLabel()}) • ${String.format("%.1f", item.distanceKm)} km"
                        }
                        Marker(
                            state = MarkerState(position = LatLng(s.latitude, s.longitude)),
                            title = s.name,
                            snippet = snippetText,
                            icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                            onInfoWindowClick = { onSelectSchool(item) }
                        )
                    }
                }
            }

            // Live Radius & Match Count Overlay Badge
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (filter.limitByRadius) {
                            "${filteredSchools.size} within ${filter.radiusKm} km • Tap map"
                        } else {
                            "${filteredSchools.size} markers (All) • Tap map"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Map Action Buttons (Recenter GPS & Expand/Collapse Map)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onRequestGpsCenter,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_recenter_gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Center map on user location"
                    )
                }
                FilledTonalIconButton(
                    onClick = { mapExpanded = !mapExpanded },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (mapExpanded) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
                        contentDescription = if (mapExpanded) "Compact map view" else "Expand map view"
                    )
                }
            }
        }
        }

        // Dedicated Minimalist Kilometer Search Radius Card
        RadiusFilterCard(
            filter = filter,
            matchCount = filteredSchools.size,
            isLocatingUser = isLocatingUser,
            onRequestGpsCenter = onRequestGpsCenter,
            onUpdateFilter = onUpdateFilter
        )
    }
}

@Composable
fun RadiusFilterCard(
    filter: SchoolSearchFilter,
    matchCount: Int,
    isLocatingUser: Boolean = false,
    onRequestGpsCenter: () -> Unit = {},
    onUpdateFilter: ((SchoolSearchFilter) -> SchoolSearchFilter) -> Unit
) {
    val isCenteredOnGps = filter.centerLabel == "Current GPS"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_radius_filter_component")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCenteredOnGps) Icons.Filled.MyLocation else Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = if (isCenteredOnGps) StatusPresentGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (filter.limitByRadius) "Radius • ${filter.radiusKm} km" else "All Distances",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (filter.limitByRadius) {
                            "$matchCount visible markers within ${filter.radiusKm} km of ${filter.centerLabel}"
                        } else {
                            "$matchCount markers shown across all distances from ${filter.centerLabel}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = onRequestGpsCenter,
                        modifier = Modifier.testTag("radius_use_my_location_button")
                    ) {
                        Text(
                            text = if (isLocatingUser) "Locating…" else if (isCenteredOnGps) "My GPS" else "Use GPS",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Switch(
                        checked = filter.limitByRadius,
                        onCheckedChange = { enabled ->
                            onUpdateFilter { it.copy(limitByRadius = enabled) }
                        },
                        modifier = Modifier.testTag("radius_filter_toggle")
                    )
                }
            }

            Slider(
                value = filter.radiusKm.toFloat().coerceIn(2f, 100f),
                enabled = filter.limitByRadius,
                onValueChange = { km ->
                    onUpdateFilter { it.copy(radiusKm = km.roundToInt().coerceIn(2, 100), limitByRadius = true) }
                },
                valueRange = 2f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_radius_slider")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(2, 5, 10, 15, 25, 50, 100)) { km ->
                    FilterChip(
                        selected = filter.limitByRadius && filter.radiusKm == km,
                        onClick = { onUpdateFilter { it.copy(radiusKm = km, limitByRadius = true) } },
                        label = { Text("${km} km") },
                        modifier = Modifier.testTag("radius_chip_${km}km")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolResultCard(
    item: SchoolWithDistance,
    isFavorite: Boolean = false,
    onToggleFavorite: (School) -> Unit = {},
    onSubmitExtraDetails: (School) -> Unit = {},
    onClaimSchool: (School) -> Unit = {},
    onClick: () -> Unit
) {
    val s = item.school
    val uriHandler = LocalUriHandler.current
    val citations = remember(s.sourceCitations, s.googleMapsUrl, s.websiteUrl) { s.parsedCitations() }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("school_card_${s.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Header: Title, Location & Favorite Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${s.address} • ${String.format("%.1f", item.distanceKm)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onToggleFavorite(s) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("card_favorite_button_${s.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove ${s.name} from favorites" else "Save ${s.name} to favorites",
                        tint = if (isFavorite) FavoriteHeartColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Concise Verification & Key Attributes Strip (max 3–4 clean pills)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (s.isVerifiedOnline()) StatusPresentGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (s.isVerifiedOnline()) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = StatusPresentGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (s.hasPlatformAdmin()) {
                                "${s.verificationBadgeLabel()} • #${s.districtRank}"
                            } else {
                                s.verificationBadgeLabel()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (s.isVerifiedOnline()) StatusPresentGreen else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                AttributeBadge(
                    text = s.institutionType,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                AttributeBadge(
                    text = s.curriculum,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!s.boardingOption.isNullOrBlank() && s.boardingOption != "Not Specified") {
                    AttributeBadge(
                        text = s.boardingOption,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Fee + Admission Status + Verified Source Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.formattedFeeRange(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (s.feeMin > 0L || s.hasPlatformAdmin()) StatusPresentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (s.hasPlatformAdmin()) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                        Text("${s.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = s.admissionWindow?.takeIf { it.isNotBlank() }
                            ?: if (s.admissionsOpen) "Admissions Open" else "Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (s.admissionsOpen) StatusPresentGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Minimal Program / Source Summary
            val summaryText = when {
                s.gradeLevels.isNotEmpty() -> s.gradeLevels.joinToString(" • ")
                s.coursesOffered.isNotEmpty() -> s.coursesOffered.take(3).joinToString(" • ")
                s.isVerifiedOnline() -> s.primarySourceSummary()
                else -> ""
            }
            if (summaryText.isNotBlank()) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (citations.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(citations.take(2)) { link ->
                        FilterChip(
                            selected = false,
                            onClick = { runCatching { uriHandler.openUri(link.uri) } },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (link.sourceType == "Google Maps") Icons.Filled.Map else Icons.Filled.Public,
                                    contentDescription = "Open ${link.sourceType} link",
                                    modifier = Modifier.size(13.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${link.sourceType}: ${link.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Lightweight Optional Actions Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onSubmitExtraDetails(s) },
                    modifier = Modifier.testTag("card_submit_details_${s.id}")
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Submit More Details", style = MaterialTheme.typography.labelMedium)
                }
                if (!s.hasPlatformAdmin()) {
                    TextButton(
                        onClick = { onClaimSchool(s) },
                        modifier = Modifier.testTag("card_claim_school_${s.id}")
                    ) {
                        Text("Claim Profile", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

fun zoomForRadiusKm(radiusKm: Int): Float {
    val clamped = radiusKm.coerceIn(2, 100).toDouble()
    return (14.0f - (ln(clamped) / ln(2.0)).toFloat() * 0.95f).coerceIn(6.5f, 13.5f)
}

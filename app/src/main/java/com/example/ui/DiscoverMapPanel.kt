package com.example.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.GoogleMapsPlatformService
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolSearchFilter
import com.example.data.SchoolWithDistance
import com.example.ui.theme.FavoriteHeartColor
import com.example.ui.theme.StarGold
import com.example.ui.theme.StatusAbsentRed
import com.example.ui.theme.StatusPresentGreen
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
        // GPS + Preset Location Picker Strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                OutlinedButton(
                    onClick = onRequestGpsCenter,
                    modifier = Modifier.testTag("gps_location_button")
                ) {
                    Icon(
                        imageVector = if (isLocatingUser) Icons.Filled.LocationSearching else Icons.Filled.MyLocation,
                        contentDescription = "Center on my GPS location",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (isLocatingUser) "Locating…" else "My GPS")
                }
            }
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

        // Map Engine Switcher & Live Internet Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = useLiveStreetMap,
                    onClick = { useLiveStreetMap = true },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text("OpenStreetMap") },
                    modifier = Modifier.testTag("map_engine_osm_chip")
                )
                FilterChip(
                    selected = !useLiveStreetMap,
                    onClick = { useLiveStreetMap = false },
                    leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text("Google Maps") },
                    modifier = Modifier.testTag("map_engine_google_chip")
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isNetworkOnline) StatusPresentGreen.copy(alpha = 0.14f) else StatusAbsentRed.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isNetworkOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = connectionLabel,
                        tint = if (isNetworkOnline) StatusPresentGreen else StatusAbsentRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isNetworkOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNetworkOnline) StatusPresentGreen else StatusAbsentRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Nigeria-Scoped Nominatim / Google Maps Place Search Input
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
                        if (filter.lockToNigeria) "Search a place in Nigeria (e.g. Ikorodu, Abuja)…"
                        else "Jump map to area, city or address…"
                    )
                },
                singleLine = true,
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
                    Text("Search")
                }
            }
        }

        // OpenStreetMap Nigeria Bounds & State Boundaries Strip vs Google Maps Layers
        if (useLiveStreetMap) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = filter.showNigeriaStates,
                        onClick = { onUpdateFilter { it.copy(showNigeriaStates = !it.showNigeriaStates) } },
                        leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("Nigeria States") },
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
                        label = { Text(if (filter.lockToNigeria) "Nigeria Bounds" else "Global Bounds") },
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
                        Text("All Nigeria")
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(14.dp)) },
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

        if (locationDeniedHint) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Location permission is off. Select an area above or tap anywhere on the map to set your search center.",
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
                    text = "Google Maps SDK requires a MAPS_API_KEY in AI Studio Secrets. Switch to OpenStreetMap for instant interactive Leaflet map tiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Integrated Map Viewport (Live Internet Street Map or Google Maps SDK)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .height(if (mapExpanded) 380.dp else 270.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
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
                            "${s.institutionType} (Map Listing) • ${String.format("%.1f", item.distanceKm)} km • Tap to Claim"
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

        // Dedicated Current-Location Kilometer Search Radius Component
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
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_radius_filter_component")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Location & Search Radius",
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (filter.limitByRadius) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (filter.limitByRadius) "${filter.radiusKm} km" else "All km",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (filter.limitByRadius) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Current Location Sync + Filter Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRequestGpsCenter,
                    modifier = Modifier.testTag("radius_use_my_location_button")
                ) {
                    Icon(
                        imageVector = if (isLocatingUser) Icons.Filled.LocationSearching else Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = when {
                            isLocatingUser -> "Getting Current Location…"
                            isCenteredOnGps -> "Centered on My Location"
                            else -> "Filter From My Location"
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Filter markers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("2 km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("50 km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("100 km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
    onClick: () -> Unit
) {
    val s = item.school
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("school_card_${s.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${s.address} • ${String.format("%.1f", item.distanceKm)} km away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (s.hasPlatformAdmin()) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "#${s.districtRank} • ${s.rankingScore.toInt()}pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                                Text("${s.rating}", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Map Listing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
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
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.hasPlatformAdmin()) {
                    AttributeBadge(
                        text = s.verificationBadgeLabel(),
                        containerColor = if (s.verificationStatus == "VERIFIED") {
                            StatusPresentGreen.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (s.verificationStatus == "VERIFIED") {
                            StatusPresentGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                } else {
                    AttributeBadge(
                        text = "Not Registered Yet • Tap to Claim",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                AttributeBadge(
                    text = s.institutionType,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                AttributeBadge(
                    text = s.curriculum,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                AttributeBadge(
                    text = if (s.faithBased) "Faith (${s.faithTradition})" else "Secular",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (s.specialSchool) {
                    AttributeBadge(
                        text = "Special Ed School",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                AttributeBadge(
                    text = s.studentMix,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Filled.AttachMoney,
                        contentDescription = null,
                        tint = if (s.hasPlatformAdmin()) StatusPresentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = s.formattedFeeRange(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (s.hasPlatformAdmin()) StatusPresentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = when {
                        !s.hasPlatformAdmin() -> "Unclaimed Geo Pin"
                        s.admissionsOpen -> "Admissions Open"
                        else -> "Closed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !s.hasPlatformAdmin() -> MaterialTheme.colorScheme.tertiary
                        s.admissionsOpen -> StatusPresentGreen
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            if (s.gradeLevels.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Programmes: ${s.gradeLevels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (s.coursesOffered.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                    val previewCourses = s.coursesOffered.take(4).joinToString(", ")
                    val suffix = if (s.coursesOffered.size > 4) " +${s.coursesOffered.size - 4} more" else ""
                    Text(
                        text = "Courses: $previewCourses$suffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (s.sportsFocus.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Sports: ${s.sportsFocus.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun zoomForRadiusKm(radiusKm: Int): Float {
    val clamped = radiusKm.coerceIn(2, 100).toDouble()
    return (14.0f - (ln(clamped) / ln(2.0)).toFloat() * 0.95f).coerceIn(6.5f, 13.5f)
}

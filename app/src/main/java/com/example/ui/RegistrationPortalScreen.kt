package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.SchoolConstants
import com.example.data.UserProfile
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistrationPortalScreen(
    existingProfile: UserProfile?,
    defaultName: String,
    defaultEmail: String,
    isBusy: Boolean,
    onSaveProfile: (
        role: String,
        displayName: String,
        phone: String?,
        homeAddress: String?,
        homeLat: Double,
        homeLng: Double,
        searchRadiusKm: Int,
        childSummary: String?
    ) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val fusedClient = remember(context) {
        runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull()
    }

    var selectedRole by remember(existingProfile) {
        mutableStateOf(existingProfile?.role ?: "parent")
    }
    var displayName by remember(existingProfile) {
        mutableStateOf(existingProfile?.displayName ?: defaultName.ifBlank { "" })
    }
    var phone by remember(existingProfile) {
        mutableStateOf(existingProfile?.phone ?: "")
    }
    var homeAddress by remember(existingProfile) {
        mutableStateOf(existingProfile?.homeAddress ?: SchoolConstants.PRESET_LOCATIONS.first().label)
    }
    var homeLat by remember(existingProfile) {
        mutableDoubleStateOf(existingProfile?.homeLat ?: SchoolConstants.PRESET_LOCATIONS.first().lat)
    }
    var homeLng by remember(existingProfile) {
        mutableDoubleStateOf(existingProfile?.homeLng ?: SchoolConstants.PRESET_LOCATIONS.first().lng)
    }
    var searchRadiusKm by remember(existingProfile) {
        mutableIntStateOf(existingProfile?.searchRadiusKm?.toInt() ?: 15)
    }
    var childSummary by remember(existingProfile) {
        mutableStateOf(existingProfile?.childSummary ?: "")
    }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isLocatingGps by remember { mutableStateOf(false) }
    var hasPromptedLocation by remember { mutableStateOf(false) }

    @SuppressLint("MissingPermission")
    fun fetchGps(applyAddressLabel: Boolean = true) {
        isLocatingGps = true
        val cts = CancellationTokenSource()
        val client = fusedClient
        if (client == null) {
            isLocatingGps = false
            locationMessage = "GPS service unavailable; pick a neighborhood below"
            return
        }
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { currentLoc ->
                    if (currentLoc != null) {
                        isLocatingGps = false
                        homeLat = currentLoc.latitude
                        homeLng = currentLoc.longitude
                        if (applyAddressLabel) {
                            homeAddress = "Current GPS (${String.format("%.3f", currentLoc.latitude)}, ${String.format("%.3f", currentLoc.longitude)})"
                        }
                        locationMessage = "Device GPS captured (${String.format("%.4f", currentLoc.latitude)}, ${String.format("%.4f", currentLoc.longitude)})"
                    } else {
                        try {
                            client.lastLocation
                                .addOnSuccessListener { lastLoc ->
                                    isLocatingGps = false
                                    if (lastLoc != null) {
                                        homeLat = lastLoc.latitude
                                        homeLng = lastLoc.longitude
                                        if (applyAddressLabel) {
                                            homeAddress = "Current GPS (${String.format("%.3f", lastLoc.latitude)}, ${String.format("%.3f", lastLoc.longitude)})"
                                        }
                                        locationMessage = "Device GPS captured (${String.format("%.4f", lastLoc.latitude)}, ${String.format("%.4f", lastLoc.longitude)})"
                                    } else {
                                        locationMessage = "GPS coordinates unavailable; pick a neighborhood below"
                                    }
                                }
                                .addOnFailureListener {
                                    isLocatingGps = false
                                    locationMessage = "Unable to read GPS; pick a neighborhood below"
                                }
                        } catch (_: Exception) {
                            isLocatingGps = false
                            locationMessage = "Unable to read GPS; pick a neighborhood below"
                        }
                    }
                }
                .addOnFailureListener {
                    try {
                        client.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                isLocatingGps = false
                                if (lastLoc != null) {
                                    homeLat = lastLoc.latitude
                                    homeLng = lastLoc.longitude
                                    if (applyAddressLabel) {
                                        homeAddress = "Current GPS (${String.format("%.3f", lastLoc.latitude)}, ${String.format("%.3f", lastLoc.longitude)})"
                                    }
                                    locationMessage = "Device GPS captured (${String.format("%.4f", lastLoc.latitude)}, ${String.format("%.4f", lastLoc.longitude)})"
                                } else {
                                    locationMessage = "Unable to read GPS; pick a neighborhood below"
                                }
                            }
                            .addOnFailureListener {
                                isLocatingGps = false
                                locationMessage = "Unable to read GPS; pick a neighborhood below"
                            }
                    } catch (_: Exception) {
                        isLocatingGps = false
                        locationMessage = "Unable to read GPS; pick a neighborhood below"
                    }
                }
        } catch (_: Exception) {
            isLocatingGps = false
            locationMessage = "Unable to read GPS; pick a neighborhood below"
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchGps(applyAddressLabel = true)
        else locationMessage = "Location access declined; choose a neighborhood preset below"
    }

    // Automatically prompt location access when registering or updating profile online
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchGps(applyAddressLabel = existingProfile == null)
        } else if (!hasPromptedLocation) {
            hasPromptedLocation = true
            runCatching {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existingProfile == null) "Complete Your Registration" else "Registration & Search Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Register as a Parent searching for schools or a School Administrator managing campus profiles and admissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Role Selector Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleOptionCard(
                    title = "Parent / Guardian",
                    subtitle = "Search schools, message admins, track student portal",
                    selected = selectedRole == "parent",
                    icon = Icons.Filled.FamilyRestroom,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("role_parent_card"),
                    onClick = { selectedRole = "parent" }
                )
                RoleOptionCard(
                    title = "School Admin",
                    subtitle = "Register school, edit fees & profile, manage enrollment",
                    selected = selectedRole == "admin",
                    icon = Icons.Filled.AdminPanelSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("role_admin_card"),
                    onClick = { selectedRole = "admin" }
                )
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Full Name *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("registration_name_input")
            )

            OutlinedTextField(
                value = defaultEmail,
                onValueChange = {},
                enabled = false,
                label = { Text("Google Account Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Contact Phone (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedRole == "parent") {
                OutlinedTextField(
                    value = childSummary,
                    onValueChange = { childSummary = it },
                    label = { Text("Student Grade / Interests (optional)") },
                    placeholder = { Text("e.g., Grade 5 • Robotics, Swimming") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Location Perimeter Defaults
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Primary Location & Perimeter",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = homeAddress,
                        onValueChange = { homeAddress = it },
                        label = { Text("Neighborhood / Search Area") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SchoolConstants.PRESET_LOCATIONS.forEach { preset ->
                            FilterChip(
                                selected = homeAddress == preset.label,
                                onClick = {
                                    homeAddress = preset.label
                                    homeLat = preset.lat
                                    homeLng = preset.lng
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                                fetchGps(applyAddressLabel = true)
                            } else {
                                runCatching {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }.onFailure {
                                    locationMessage = "Location prompt unavailable; choose a neighborhood preset below"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("registration_gps_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (isLocatingGps) "Capturing Device GPS…" else "Use My Current GPS Location")
                    }
                    Text(
                        text = "Coordinates: ${String.format("%.4f", homeLat)}, ${String.format("%.4f", homeLng)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (locationMessage != null) {
                        Text(
                            text = locationMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = "Default Search Radius: $searchRadiusKm km",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = searchRadiusKm.toFloat(),
                        onValueChange = { searchRadiusKm = it.roundToInt().coerceIn(2, 50) },
                        valueRange = 2f..50f
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick = {
                        onSaveProfile(
                            selectedRole,
                            displayName.ifBlank { defaultName.ifBlank { "Skolquik User" } },
                            phone.ifBlank { null },
                            homeAddress.ifBlank { null },
                            homeLat,
                            homeLng,
                            searchRadiusKm,
                            childSummary.ifBlank { null }
                        )
                    },
                    enabled = !isBusy && displayName.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_registration_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (existingProfile == null) "Complete Registration" else "Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
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
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

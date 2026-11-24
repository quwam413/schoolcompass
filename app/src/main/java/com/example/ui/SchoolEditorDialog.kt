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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.School
import com.example.data.SchoolConstants
import com.example.data.SchoolVerificationResult
import com.example.data.SchoolWithDistance
import com.example.ui.theme.StatusPresentGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchoolEditorSheet(
    existingSchool: School?,
    nearbyUnclaimedSchools: List<SchoolWithDistance> = emptyList(),
    defaultAdminName: String,
    defaultLat: Double,
    defaultLng: Double,
    isBusy: Boolean,
    onVerifyDraft: (School, (SchoolVerificationResult) -> Unit) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onSave: (School, Boolean) -> Unit
) {
    val context = LocalContext.current
    val fusedClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    val isClaimingUnregistered = existingSchool != null && !existingSchool.hasPlatformAdmin()
    val isCreatingOnPlatform = existingSchool == null || isClaimingUnregistered

    var externalPlaceId by remember(existingSchool) { mutableStateOf(existingSchool?.externalPlaceId) }
    var websiteUrl by remember(existingSchool) { mutableStateOf(existingSchool?.websiteUrl ?: "") }
    var verificationStatus by remember(existingSchool) { mutableStateOf(existingSchool?.verificationStatus ?: "UNVERIFIED") }
    var verificationSource by remember(existingSchool) { mutableStateOf(existingSchool?.verificationSource) }

    var name by remember(existingSchool) { mutableStateOf(existingSchool?.name ?: "") }
    var description by remember(existingSchool) {
        mutableStateOf(
            if (isClaimingUnregistered) {
                ""
            } else {
                existingSchool?.description ?: ""
            }
        )
    }
    var address by remember(existingSchool) { mutableStateOf(existingSchool?.address ?: "") }
    var latitude by remember(existingSchool) { mutableDoubleStateOf(existingSchool?.latitude ?: defaultLat) }
    var longitude by remember(existingSchool) { mutableDoubleStateOf(existingSchool?.longitude ?: defaultLng) }
    var curriculum by remember(existingSchool) { mutableStateOf(existingSchool?.curriculum ?: "Nigerian & British") }
    var institutionType by remember(existingSchool) { mutableStateOf(existingSchool?.institutionType ?: "Primary & Secondary") }
    var faithBased by remember(existingSchool) { mutableStateOf(existingSchool?.faithBased ?: false) }
    var faithTradition by remember(existingSchool) { mutableStateOf(existingSchool?.faithTradition ?: "Secular") }
    var specialSchool by remember(existingSchool) { mutableStateOf(existingSchool?.specialSchool ?: false) }
    var specialNeedsSupport by remember(existingSchool) { mutableStateOf(existingSchool?.specialNeedsSupport ?: "General Education") }
    var studentMix by remember(existingSchool) { mutableStateOf(existingSchool?.studentMix ?: "Mixed") }
    var feeMinText by remember(existingSchool) {
        val initialMin = if (isClaimingUnregistered || existingSchool == null) 350_000L else existingSchool.feeMin
        mutableStateOf(initialMin.toString())
    }
    var feeMaxText by remember(existingSchool) {
        val initialMax = if (isClaimingUnregistered || existingSchool == null) 750_000L else existingSchool.feeMax
        mutableStateOf(initialMax.toString())
    }
    var feeCurrency by remember(existingSchool) { mutableStateOf(existingSchool?.feeCurrency ?: "NGN") }
    var feePeriod by remember(existingSchool) { mutableStateOf(existingSchool?.feePeriod ?: "Per Term") }
    var capacityText by remember(existingSchool) { mutableStateOf((existingSchool?.capacity ?: 400L).toString()) }
    var enrolledText by remember(existingSchool) { mutableStateOf((existingSchool?.enrolledCount ?: 0L).toString()) }
    var rankingScoreText by remember(existingSchool) { mutableStateOf((existingSchool?.rankingScore ?: 88.0).toString()) }
    var districtRankText by remember(existingSchool) { mutableStateOf((existingSchool?.districtRank ?: 5L).toString()) }
    var contactPhone by remember(existingSchool) { mutableStateOf(existingSchool?.contactPhone ?: "") }
    var admissionsOpen by remember(existingSchool) { mutableStateOf(existingSchool?.admissionsOpen ?: true) }
    var selectedGrades by remember(existingSchool) {
        mutableStateOf(
            existingSchool?.gradeLevels?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf("Primary", "Junior Secondary")
        )
    }
    var selectedPresetCourses by remember(existingSchool) {
        mutableStateOf(
            existingSchool?.coursesOffered
                ?.filter { it in SchoolConstants.COURSES_OFFERED_OPTIONS }
                ?.toSet()
                ?: emptySet()
        )
    }
    var customCoursesText by remember(existingSchool) {
        mutableStateOf(
            existingSchool?.coursesOffered
                ?.filter { it !in SchoolConstants.COURSES_OFFERED_OPTIONS }
                ?.joinToString(", ")
                ?: ""
        )
    }
    var selectedActivities by remember(existingSchool) {
        mutableStateOf(
            existingSchool?.activities?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf("Robotics", "Debate & MUN")
        )
    }
    var selectedSports by remember(existingSchool) {
        mutableStateOf(
            existingSchool?.sportsFocus?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf("Football (Soccer)", "Athletics & Track")
        )
    }

    var gpsStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLocatingCampus by remember { mutableStateOf(false) }
    var hasPromptedLocation by remember { mutableStateOf(false) }

    fun applyClaimedSchool(claimed: School) {
        externalPlaceId = claimed.externalPlaceId
        name = claimed.name
        address = claimed.address
        latitude = claimed.latitude
        longitude = claimed.longitude
        contactPhone = claimed.contactPhone ?: contactPhone
        websiteUrl = claimed.websiteUrl ?: websiteUrl
        verificationStatus = claimed.verificationStatus
        verificationSource = claimed.verificationSource
        curriculum = claimed.curriculum
        institutionType = claimed.institutionType
        faithBased = claimed.faithBased
        faithTradition = claimed.faithTradition
        specialSchool = claimed.specialSchool
        specialNeedsSupport = claimed.specialNeedsSupport
        studentMix = claimed.studentMix
        if (claimed.gradeLevels.isNotEmpty()) selectedGrades = claimed.gradeLevels.toSet()
        if (claimed.coursesOffered.isNotEmpty()) {
            selectedPresetCourses = claimed.coursesOffered.filter { it in SchoolConstants.COURSES_OFFERED_OPTIONS }.toSet()
            customCoursesText = claimed.coursesOffered.filter { it !in SchoolConstants.COURSES_OFFERED_OPTIONS }.joinToString(", ")
        }
        gpsStatusMessage = "Preserving map geolocation (${String.format("%.4f", claimed.latitude)}, ${String.format("%.4f", claimed.longitude)})"
    }

    @SuppressLint("MissingPermission")
    fun captureDeviceGps(updateCoordsImmediately: Boolean = true) {
        isLocatingCampus = true
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { currentLoc ->
                if (currentLoc != null) {
                    isLocatingCampus = false
                    if (updateCoordsImmediately) {
                        latitude = currentLoc.latitude
                        longitude = currentLoc.longitude
                    }
                    gpsStatusMessage = "Device GPS ready (${String.format("%.4f", currentLoc.latitude)}, ${String.format("%.4f", currentLoc.longitude)})"
                } else {
                    fusedClient.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            isLocatingCampus = false
                            if (lastLoc != null && updateCoordsImmediately) {
                                latitude = lastLoc.latitude
                                longitude = lastLoc.longitude
                                gpsStatusMessage = "Device GPS ready (${String.format("%.4f", lastLoc.latitude)}, ${String.format("%.4f", lastLoc.longitude)})"
                            } else {
                                gpsStatusMessage = "GPS signal unavailable on device; using map pin coordinates"
                            }
                        }
                        .addOnFailureListener {
                            isLocatingCampus = false
                            gpsStatusMessage = "Unable to read GPS; verify campus coordinates below"
                        }
                }
            }
            .addOnFailureListener {
                isLocatingCampus = false
                gpsStatusMessage = "Unable to read GPS; verify campus coordinates below"
            }
    }

    val campusLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // Preserve claimed school's real geolocation unless creating a fresh entry with no externalPlaceId
            captureDeviceGps(updateCoordsImmediately = externalPlaceId == null && existingSchool == null)
        } else {
            gpsStatusMessage = "Location access declined; using selected coordinates"
        }
    }

    // Automatically prompt device location access when registering or updating a school online
    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            captureDeviceGps(updateCoordsImmediately = externalPlaceId == null && existingSchool == null)
        } else if (!hasPromptedLocation) {
            hasPromptedLocation = true
            campusLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    fun buildDraftSchool(): School {
        val minFee = feeMinText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val maxFee = (feeMaxText.toLongOrNull() ?: minFee).coerceAtLeast(minFee)
        val tier = when {
            maxFee == 0L -> "Public / Free"
            maxFee <= 450_000L -> "$"
            maxFee <= 1_000_000L -> "$$"
            else -> "$$$"
        }
        val customCourses = customCoursesText.split(',')
            .map { it.trim() }
            .filter { it.length >= 2 }
        val combinedCourses = (selectedPresetCourses.toList() + customCourses).distinct().take(25)
        return (existingSchool ?: School()).copy(
            adminName = existingSchool?.adminName?.takeIf { it.isNotBlank() } ?: defaultAdminName,
            name = name.trim(),
            description = description.trim(),
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            curriculum = curriculum,
            institutionType = institutionType,
            faithBased = faithBased,
            faithTradition = if (faithBased) faithTradition.trim().ifBlank { "Christian" } else "Secular",
            specialSchool = specialSchool,
            specialNeedsSupport = specialNeedsSupport.trim().ifBlank { "General Education" },
            studentMix = studentMix,
            gradeLevels = selectedGrades.toList().take(10),
            coursesOffered = combinedCourses,
            activities = selectedActivities.toList(),
            sportsFocus = selectedSports.toList(),
            feeMin = minFee,
            feeMax = maxFee,
            feeCurrency = feeCurrency,
            feePeriod = feePeriod,
            tuitionTier = tier,
            capacity = capacityText.toLongOrNull()?.coerceIn(1L, 10000L) ?: 400L,
            enrolledCount = enrolledText.toLongOrNull()?.coerceIn(0L, 10000L) ?: 0L,
            districtRank = districtRankText.toLongOrNull()?.coerceIn(1L, 9999L) ?: 10L,
            rankingScore = rankingScoreText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 85.0,
            admissionsOpen = admissionsOpen,
            contactPhone = contactPhone.trim().ifBlank { null },
            externalPlaceId = externalPlaceId,
            websiteUrl = websiteUrl.trim().ifBlank { null },
            verificationStatus = verificationStatus,
            verificationSource = verificationSource
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when {
                    externalPlaceId != null && isCreatingOnPlatform -> "Claim & Enrich Existing School"
                    isCreatingOnPlatform -> "Register School Profile"
                    else -> "Edit School Profile (Live)"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Pick from nearby already-geolocated schools instead of creating a duplicate
            if (isCreatingOnPlatform && existingSchool == null && nearbyUnclaimedSchools.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Add Details to an Existing Map School",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select an already geolocated school below so registration enriches its existing listing instead of creating a duplicate:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(nearbyUnclaimedSchools.take(12), key = { it.school.id }) { item ->
                                val picked = externalPlaceId != null && externalPlaceId == item.school.externalPlaceId
                                FilterChip(
                                    selected = picked,
                                    onClick = { applyClaimedSchool(item.school) },
                                    label = { Text("${item.school.name} (${String.format("%.1f", item.distanceKm)}km)") }
                                )
                            }
                        }
                    }
                }
            }

            if (externalPlaceId != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Linked to existing map geolocation ID: $externalPlaceId",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("School Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("school_editor_name_input")
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Campus Address *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("school_editor_address_input")
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Overview & Programs") },
                placeholder = { Text("Add academic ethos, facilities, and admissions summary…") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Admissions Phone (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { websiteUrl = it },
                label = { Text("Official School Website (for Internet Verification)") },
                placeholder = { Text("https://yourschool.edu.ng") },
                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("school_editor_website_input")
            )

            // Internet Verification Check Panel
            val isVerifiedStatus = verificationStatus in listOf("VERIFIED", "VERIFIED_ONLINE", "INTERNET_MATCHED")
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isVerifiedStatus) {
                    StatusPresentGreen.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = if (isVerifiedStatus) StatusPresentGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (verificationStatus) {
                                    "VERIFIED" -> "Verified (Admin & Online)"
                                    "VERIFIED_ONLINE", "INTERNET_MATCHED" -> "Verified Online"
                                    else -> "Unverified"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onVerifyDraft(buildDraftSchool()) { result ->
                                    verificationStatus = result.status
                                    verificationSource = result.source
                                    if (websiteUrl.isBlank() && !result.discoveredWebsite.isNullOrBlank()) {
                                        websiteUrl = result.discoveredWebsite
                                    }
                                    if (externalPlaceId == null && !result.matchedExternalId.isNullOrBlank()) {
                                        externalPlaceId = result.matchedExternalId
                                    }
                                }
                            },
                            enabled = !isBusy && name.trim().length >= 2,
                            modifier = Modifier.testTag("school_editor_verify_button")
                        ) {
                            Text("Verify on Internet")
                        }
                    }
                    if (!verificationSource.isNullOrBlank()) {
                        Text(
                            text = verificationSource!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Cross-checks OpenStreetMap public directories and live website records automatically on save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Campus Geolocation & Device Location Access
            Text("Campus Geolocation (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        captureDeviceGps(updateCoordsImmediately = true)
                    } else {
                        campusLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("school_editor_gps_button")
            ) {
                Icon(
                    imageVector = if (isLocatingCampus) Icons.Filled.LocationSearching else Icons.Filled.MyLocation,
                    contentDescription = "Use device GPS location for school",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(if (isLocatingCampus) "Locating Campus GPS…" else "Capture Campus GPS from Device")
            }
            if (gpsStatusMessage != null) {
                Text(
                    text = gpsStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.PRESET_LOCATIONS.forEach { preset ->
                    FilterChip(
                        selected = kotlin.math.abs(latitude - preset.lat) < 0.001 && kotlin.math.abs(longitude - preset.lng) < 0.001,
                        onClick = {
                            latitude = preset.lat
                            longitude = preset.lng
                        },
                        label = { Text(preset.label) }
                    )
                }
            }

            Text("Institution Type", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.INSTITUTION_TYPES.forEach { instType ->
                    FilterChip(
                        selected = institutionType == instType,
                        onClick = {
                            institutionType = instType
                            if (existingSchool == null) {
                                when (instType) {
                                    "University" -> selectedGrades = setOf("Undergraduate", "Postgraduate")
                                    "Polytechnic" -> selectedGrades = setOf("ND / HND Programme")
                                    "College of Education" -> selectedGrades = setOf("NCE Programme", "Undergraduate")
                                    "Technical / Vocational" -> selectedGrades = setOf("ND / HND Programme", "Senior Secondary")
                                    else -> selectedGrades = setOf("Primary", "Junior Secondary")
                                }
                            }
                        },
                        label = { Text(instType) }
                    )
                }
            }

            Text("Curriculum", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.CURRICULA.forEach { curr ->
                    FilterChip(
                        selected = curriculum == curr,
                        onClick = { curriculum = curr },
                        label = { Text(curr) }
                    )
                }
            }

            Text("School Fees Range", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = feeMinText,
                    onValueChange = { feeMinText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Min Fee") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("school_editor_fee_min")
                )
                OutlinedTextField(
                    value = feeMaxText,
                    onValueChange = { feeMaxText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Max Fee") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("school_editor_fee_max")
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchoolConstants.FEE_CURRENCIES.forEach { cur ->
                    FilterChip(selected = feeCurrency == cur, onClick = { feeCurrency = cur }, label = { Text(cur) })
                }
                SchoolConstants.FEE_PERIODS.forEach { per ->
                    FilterChip(selected = feePeriod == per, onClick = { feePeriod = per }, label = { Text(per) })
                }
            }

            // Faith, Special Needs, Mixed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Faith-Based School", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = faithBased,
                    onCheckedChange = {
                        faithBased = it
                        if (!it) faithTradition = "Secular" else if (faithTradition == "Secular") faithTradition = "Christian"
                    }
                )
            }
            if (faithBased) {
                OutlinedTextField(
                    value = faithTradition,
                    onValueChange = { faithTradition = it },
                    label = { Text("Faith Tradition (e.g. Christian, Islamic, Interfaith)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dedicated Special Needs School", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = specialSchool,
                    onCheckedChange = {
                        specialSchool = it
                        if (it && specialNeedsSupport == "General Education") {
                            specialNeedsSupport = "Special Education & Therapy"
                        } else if (!it) {
                            specialNeedsSupport = "General Education"
                        }
                    }
                )
            }
            OutlinedTextField(
                value = specialNeedsSupport,
                onValueChange = { specialNeedsSupport = it },
                label = { Text("Special Education / Support Scope") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Student Mix", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SchoolConstants.STUDENT_MIX_OPTIONS.forEach { mix ->
                    FilterChip(selected = studentMix == mix, onClick = { studentMix = mix }, label = { Text(mix) })
                }
            }

            Text("Sports Focus", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.SPORTS_FOCUS_OPTIONS.forEach { sport ->
                    val active = sport in selectedSports
                    FilterChip(
                        selected = active,
                        onClick = { selectedSports = if (active) selectedSports - sport else selectedSports + sport },
                        label = { Text(sport) }
                    )
                }
            }

            Text("Extracurricular Activities", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.EXTRACURRICULAR_ACTIVITIES.forEach { act ->
                    val active = act in selectedActivities
                    FilterChip(
                        selected = active,
                        onClick = { selectedActivities = if (active) selectedActivities - act else selectedActivities + act },
                        label = { Text(act) }
                    )
                }
            }

            Text("Programmes & Grade Levels Run", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.ALL_PROGRAMMES.forEach { lvl ->
                    val active = lvl in selectedGrades
                    FilterChip(
                        selected = active,
                        onClick = { selectedGrades = if (active) selectedGrades - lvl else selectedGrades + lvl },
                        label = { Text(lvl) }
                    )
                }
            }

            Text("Courses Offered", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolConstants.COURSES_OFFERED_OPTIONS.forEach { course ->
                    val active = course in selectedPresetCourses
                    FilterChip(
                        selected = active,
                        onClick = {
                            selectedPresetCourses = if (active) selectedPresetCourses - course else selectedPresetCourses + course
                        },
                        label = { Text(course) }
                    )
                }
            }
            OutlinedTextField(
                value = customCoursesText,
                onValueChange = { customCoursesText = it },
                label = { Text("Additional Courses / Departments (comma-separated)") },
                placeholder = { Text("e.g. Architecture, Pharmacy, Fine Arts") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("school_editor_custom_courses_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it.filter { c -> c.isDigit() } },
                    label = { Text("Capacity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = enrolledText,
                    onValueChange = { enrolledText = it.filter { c -> c.isDigit() } },
                    label = { Text("Enrolled") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = districtRankText,
                    onValueChange = { districtRankText = it.filter { c -> c.isDigit() } },
                    label = { Text("District Rank #") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rankingScoreText,
                    onValueChange = { rankingScoreText = it },
                    label = { Text("Ranking Score (0–100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Admissions Currently Open", style = MaterialTheme.typography.titleSmall)
                Switch(checked = admissionsOpen, onCheckedChange = { admissionsOpen = it })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(buildDraftSchool(), isCreatingOnPlatform) },
                    enabled = !isBusy && name.trim().length >= 2 && address.trim().length >= 2,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("school_editor_save_button")
                ) {
                    Text(
                        when {
                            externalPlaceId != null && isCreatingOnPlatform -> "Claim & Save"
                            isCreatingOnPlatform -> "Register School"
                            else -> "Save Live"
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

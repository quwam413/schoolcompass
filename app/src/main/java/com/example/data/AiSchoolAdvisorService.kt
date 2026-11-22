package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

data class MapsGroundingContext(
    val areaLabel: String,
    val centerLat: Double,
    val centerLng: Double,
    val matchedSchoolCount: Int,
    val promptBlock: String
)

class AiSchoolAdvisorService {

    private companion object {
        const val TAG = "AiSchoolAdvisor"

        val SYSTEM_INSTRUCTION = """
            You are Compass AI, the official multi-turn education & geolocation advisor inside SchoolCompass.
            Your expertise covers Primary & Secondary schools (Nigerian, British, Nigerian & British Dual, IB, American, Montessori, STEM) as well as Universities, Polytechnics, Colleges of Education, and Technical/Vocational institutes, plus Progressive Tutors (Cambridge & Nigerian subjects).
            
            GROUNDING RULES (CRITICAL):
            1. Always ground location, distance, school availability, courses offered, tuition fee ranges, sports focus, and verification details in the [GOOGLE MAPS & SCHOOL DIRECTORY GROUNDING CONTEXT] supplied with the user's message.
            2. Quote exact distances in km from the active map pin, fees (including "Fees not added yet" for unclaimed map listings), curricula, programmes, and sports focus when recommending schools.
            3. Clearly distinguish between "Registered & Verified" schools (managed on SchoolCompass with full fees/courses/enrollment) and "Map Directory Listing (Unclaimed)" schools discovered via Google Maps Places / OpenStreetMap.
            4. If the user asks about a school or location not present in the grounded list, clearly say so and suggest widening the radius slider or using Pin Area on the Discover map rather than inventing school facts or fake addresses.
            5. Keep responses structured, warm, concise, and easy to scan with bullet points.
        """.trimIndent()

        val LOCATION_HINT_REGEX = Regex(
            pattern = """\b(?:near|around|in|at|to|from)\s+([A-Za-z][A-Za-z0-9 .'-]{2,35})""",
            option = RegexOption.IGNORE_CASE
        )
    }

    // Lazy initialization per firebase-ai-logic-android requirements (never evaluated eagerly at construction)
    private val balancedModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.BALANCED.modelId)
    }

    private val liteModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.LITE.modelId)
    }

    private val deepModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.DEEP.modelId)
    }

    private fun buildGenerativeModel(modelName: String): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.45f
                topP = 0.9f
            },
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )
    }

    private fun modelForOption(option: AiAdvisorModelOption): GenerativeModel = when (option) {
        AiAdvisorModelOption.BALANCED -> balancedModel
        AiAdvisorModelOption.LITE -> liteModel
        AiAdvisorModelOption.DEEP -> deepModel
    }

    suspend fun buildGoogleMapsGroundingContext(
        userQuery: String,
        currentFilter: SchoolSearchFilter,
        knownSchools: List<School>,
        favoriteSchoolIds: Set<String>,
        tutorCount: Int,
        activeAdvertCount: Int
    ): MapsGroundingContext {
        var resolvedLat = currentFilter.centerLat
        var resolvedLng = currentFilter.centerLng
        var resolvedArea = currentFilter.centerLabel
        var usedLiveGeocode = false

        val locationCandidate = extractLocationCandidate(userQuery)
        if (locationCandidate != null &&
            !currentFilter.centerLabel.contains(locationCandidate, ignoreCase = true)
        ) {
            val geocoded = GoogleMapsPlatformService.geocodePlace(locationCandidate).getOrNull()
            if (geocoded != null) {
                resolvedLat = geocoded.latitude
                resolvedLng = geocoded.longitude
                resolvedArea = "${geocoded.formattedAddress} (${geocoded.source})"
                usedLiveGeocode = true
            }
        }

        val googlePlacesSchools = if (usedLiveGeocode || GoogleMapsPlatformService.hasValidGoogleMapsKey()) {
            GoogleMapsPlatformService.fetchNearbyGooglePlacesSchools(
                centerLat = resolvedLat,
                centerLng = resolvedLng,
                radiusKm = currentFilter.radiusKm.coerceAtLeast(15)
            ).getOrNull().orEmpty()
        } else {
            emptyList()
        }

        val combinedSchools = ExternalSchoolService.mergeRegisteredAndExternalSchools(
            registered = knownSchools,
            external = googlePlacesSchools
        )

        val scored = combinedSchools.map { school ->
            val dist = calculateDistanceKm(resolvedLat, resolvedLng, school.latitude, school.longitude)
            SchoolWithDistance(school, dist)
        }.sortedWith(
            compareByDescending<SchoolWithDistance> { it.school.id in favoriteSchoolIds }
                .thenBy { it.distanceKm > currentFilter.radiusKm }
                .thenByDescending { it.school.hasPlatformAdmin() }
                .thenBy { it.distanceKm }
        )

        val selected = scored.take(14)

        val sb = StringBuilder()
        sb.appendLine("[GOOGLE MAPS & SCHOOL DIRECTORY GROUNDING CONTEXT]")
        sb.appendLine(
            String.format(
                Locale.US,
                "Active Map Pin: %s (%.4f, %.4f) | Radius: %d km | Saved Favorites: %d | Online Tutors: %d | Active Adverts: %d",
                resolvedArea,
                resolvedLat,
                resolvedLng,
                currentFilter.radiusKm,
                favoriteSchoolIds.size,
                tutorCount,
                activeAdvertCount
            )
        )
        if (selected.isEmpty()) {
            sb.appendLine("No schools are currently loaded around these coordinates.")
        } else {
            sb.appendLine("Grounded Schools (${selected.size} shown of ${combinedSchools.size} total):")
            selected.forEachIndexed { idx, item ->
                val s = item.school
                val favTag = if (s.id in favoriteSchoolIds) " [SAVED FAVORITE]" else ""
                val regTag = if (s.hasPlatformAdmin()) "Registered (${s.verificationBadgeLabel()})" else "Map Directory Listing (Unclaimed)"
                val progText = s.gradeLevels.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not specified"
                val courseText = s.coursesOffered.takeIf { it.isNotEmpty() }?.take(6)?.joinToString(", ") ?: "General"
                val sportsText = s.sportsFocus.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not listed"
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%d. %s%s | %s | Type: %s | Curriculum: %s | Dist: %.1f km | Coords: (%.4f, %.4f) | Address: %s | Fees: %s | Rating: %.1f/5 | Programmes: %s | Courses: %s | Sports: %s | Faith: %s | Admissions: %s",
                        idx + 1,
                        s.name,
                        favTag,
                        regTag,
                        s.institutionType,
                        s.curriculum,
                        item.distanceKm,
                        s.latitude,
                        s.longitude,
                        s.address,
                        s.formattedFeeRange(),
                        s.rating,
                        progText,
                        courseText,
                        sportsText,
                        if (s.faithBased) s.faithTradition else "Secular",
                        if (s.admissionsOpen) "Open" else "Closed"
                    )
                )
            }
        }
        sb.appendLine("[END GROUNDING CONTEXT]")

        return MapsGroundingContext(
            areaLabel = resolvedArea,
            centerLat = resolvedLat,
            centerLng = resolvedLng,
            matchedSchoolCount = selected.size,
            promptBlock = sb.toString()
        )
    }

    fun streamChatTurn(
        modelOption: AiAdvisorModelOption,
        previousMessages: List<AiAdvisorMessage>,
        groundedUserPrompt: String
    ): Flow<String> {
        val model = modelForOption(modelOption)
        val history: List<Content> = previousMessages
            .filter { it.text.isNotBlank() && !it.isStreaming }
            .takeLast(12)
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

        val chat = model.startChat(history = history)
        Log.d(TAG, "Starting Gemini turn on ${modelOption.modelId} with ${history.size} history turns")
        return chat.sendMessageStream(groundedUserPrompt).map { chunk ->
            chunk.text.orEmpty()
        }
    }

    private fun extractLocationCandidate(query: String): String? {
        val stopWords = setOf(
            "the", "my", "this", "school", "schools", "university", "universities",
            "nigerian", "british", "cambridge", "area", "map", "radius", "favorites",
            "saved", "computer", "science", "engineering", "medicine", "sports", "fees"
        )
        val matches = LOCATION_HINT_REGEX.findAll(query).toList()
        for (match in matches.asReversed()) {
            val raw = match.groupValues.getOrNull(1)?.trim()?.trimEnd('.', '?', '!', ',') ?: continue
            val firstWord = raw.substringBefore(' ').lowercase(Locale.US)
            if (firstWord !in stopWords && raw.length >= 3) {
                return raw
            }
        }
        return null
    }
}

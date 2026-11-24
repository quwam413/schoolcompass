package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.LatLng as AiLatLng
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.retrievalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Locale

data class MapsGroundingContext(
    val areaLabel: String,
    val centerLat: Double,
    val centerLng: Double,
    val matchedSchoolCount: Int,
    val promptBlock: String
)

data class AiStreamChunk(
    val text: String,
    val groundingLinks: List<GroundingSourceLink> = emptyList()
)

class AiSchoolAdvisorService {

    private companion object {
        const val TAG = "AiSchoolAdvisor"

        val SYSTEM_INSTRUCTION = """
            You are Skolquik AI, the official multi-turn education & geolocation advisor inside Skolquik (Search Smart, Enroll Fast.).
            Your expertise covers Primary & Secondary schools (Nigerian, British, Nigerian & British Dual, IB, American, Montessori, STEM) as well as Universities, Polytechnics, Colleges of Education, and Technical/Vocational institutes, plus Progressive Tutors (Cambridge & Nigerian subjects).
            
            GROUNDING RULES (CRITICAL):
            1. Always ground location, distance, school availability, courses offered, tuition fee ranges, sports focus, and verification details using BOTH Google Search + Google Maps grounding AND the [GOOGLE MAPS & SCHOOL DIRECTORY GROUNDING CONTEXT] supplied with the user's message.
            2. Treat already-verified online schools ("Verified Online" from Google Search & Google Maps or "Verified (Admin & Online)") as immediately browsable and actionable—never tell users a school must register on Skolquik first before it can appear or be compared.
            3. Quote exact distances in km from the active map pin, fees (including "Fees not added yet" when tuition has not yet been contributed), curricula, programmes, admission windows, boarding options, and verified online source references when recommending schools.
            4. Note that optional school claiming or community "Submit More Details" enriches tuition brackets, admission windows, boarding options, and photos without ever gating discovery.
            5. Keep responses structured, warm, concise, and easy to scan with bullet points.
        """.trimIndent()

        val LOCATION_HINT_REGEX = Regex(
            pattern = """\b(?:near|around|in|at|to|from)\s+([A-Za-z][A-Za-z0-9 .'-]{2,35})""",
            option = RegexOption.IGNORE_CASE
        )
    }

    // Lazy initialization per firebase-ai-logic-android requirements (never evaluated eagerly at construction)
    private val balancedModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.BALANCED.modelId, enableGroundingTools = true)
    }

    private val liteModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.LITE.modelId, enableGroundingTools = true)
    }

    private val deepModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.DEEP.modelId, enableGroundingTools = true)
    }

    private val plainFallbackModel: GenerativeModel by lazy {
        buildGenerativeModel(AiAdvisorModelOption.BALANCED.modelId, enableGroundingTools = false)
    }

    private fun buildGenerativeModel(
        modelName: String,
        enableGroundingTools: Boolean,
        centerLat: Double = 6.4474,
        centerLng: Double = 3.4737
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.45f
                topP = 0.9f
            },
            tools = if (enableGroundingTools) listOf(Tool.googleSearch(), Tool.googleMaps()) else null,
            toolConfig = if (enableGroundingTools) {
                ToolConfig(
                    functionCallingConfig = null,
                    retrievalConfig = retrievalConfig {
                        latLng = AiLatLng(centerLat, centerLng)
                        languageCode = "en"
                    }
                )
            } else null,
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )
    }

    private fun modelForOption(
        option: AiAdvisorModelOption,
        centerLat: Double? = null,
        centerLng: Double? = null
    ): GenerativeModel {
        if (centerLat != null && centerLng != null) {
            return buildGenerativeModel(
                modelName = option.modelId,
                enableGroundingTools = true,
                centerLat = centerLat,
                centerLng = centerLng
            )
        }
        return when (option) {
            AiAdvisorModelOption.BALANCED -> balancedModel
            AiAdvisorModelOption.LITE -> liteModel
            AiAdvisorModelOption.DEEP -> deepModel
        }
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
                .thenByDescending { it.school.isVerifiedOnline() }
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
            sb.appendLine("No cached schools are currently loaded around these coordinates; use Google Search & Google Maps grounding to answer with verified online schools.")
        } else {
            sb.appendLine("Grounded Schools (${selected.size} shown of ${combinedSchools.size} total):")
            selected.forEachIndexed { idx, item ->
                val s = item.school
                val favTag = if (s.id in favoriteSchoolIds) " [SAVED FAVORITE]" else ""
                val statusTag = if (s.hasPlatformAdmin()) {
                    "Verified (Admin & Online)"
                } else {
                    "${s.verificationBadgeLabel()} (${s.primarySourceSummary()})"
                }
                val progText = s.gradeLevels.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not specified"
                val courseText = s.coursesOffered.takeIf { it.isNotEmpty() }?.take(6)?.joinToString(", ") ?: "General"
                val sportsText = s.sportsFocus.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Not listed"
                val extraParts = buildList {
                    s.boardingOption?.takeIf { it.isNotBlank() }?.let { add("Boarding: $it") }
                    s.admissionWindow?.takeIf { it.isNotBlank() }?.let { add("Window: $it") }
                    s.websiteUrl?.takeIf { it.isNotBlank() }?.let { add("Web: $it") }
                    s.googleMapsPlaceRef?.takeIf { it.isNotBlank() }?.let { add("MapsRef: $it") }
                }.joinToString(" | ")
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%d. %s%s | %s | Type: %s | Curriculum: %s | Dist: %.1f km | Coords: (%.4f, %.4f) | Address: %s | Fees: %s | Rating: %.1f/5 | Programmes: %s | Courses: %s | Sports: %s | Faith: %s | Admissions: %s%s",
                        idx + 1,
                        s.name,
                        favTag,
                        statusTag,
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
                        if (s.admissionsOpen) "Open" else "Closed",
                        if (extraParts.isNotBlank()) " | $extraParts" else ""
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

    fun streamGroundedChatTurn(
        modelOption: AiAdvisorModelOption,
        previousMessages: List<AiAdvisorMessage>,
        groundedUserPrompt: String,
        centerLat: Double? = null,
        centerLng: Double? = null
    ): Flow<AiStreamChunk> = flow {
        val history: List<Content> = previousMessages
            .filter { it.text.isNotBlank() && !it.isStreaming }
            .takeLast(12)
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

        val groundedModel = modelForOption(modelOption, centerLat, centerLng)
        val groundedChat = groundedModel.startChat(history = history)
        Log.d(TAG, "Starting grounded Gemini turn on ${modelOption.modelId} with ${history.size} history turns")

        val stream = groundedChat.sendMessageStream(groundedUserPrompt)
            .map { resp ->
                AiStreamChunk(
                    text = resp.text.orEmpty(),
                    groundingLinks = OnlineSchoolGroundingService.extractGroundingSourceLinks(resp)
                )
            }
            .catch { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Grounded stream failed (${err.message}); falling back to search-only/plain stream")
                val fallbackChat = plainFallbackModel.startChat(history = history)
                emitAll(
                    fallbackChat.sendMessageStream(groundedUserPrompt).map { resp ->
                        AiStreamChunk(
                            text = resp.text.orEmpty(),
                            groundingLinks = OnlineSchoolGroundingService.extractGroundingSourceLinks(resp)
                        )
                    }
                )
            }
        emitAll(stream)
    }

    fun streamChatTurn(
        modelOption: AiAdvisorModelOption,
        previousMessages: List<AiAdvisorMessage>,
        groundedUserPrompt: String
    ): Flow<String> = streamGroundedChatTurn(
        modelOption = modelOption,
        previousMessages = previousMessages,
        groundedUserPrompt = groundedUserPrompt
    ).map { it.text }

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

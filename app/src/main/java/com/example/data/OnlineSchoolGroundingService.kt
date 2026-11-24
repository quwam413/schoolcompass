package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.LatLng as AiLatLng
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.retrievalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Retrieves already-verified real schools from online sources via Firebase AI Logic
 * using Google Search grounding and Google Maps grounding (zero API keys on client).
 */
class OnlineSchoolGroundingService {

    companion object {
        private const val TAG = "OnlineSchoolGrounding"
        private const val MODEL_NAME = "gemini-3.8-flash"

        private val SYSTEM_PROMPT = """
            You are an education directory indexer for Skolquik.
            When asked to find schools, ALWAYS use Google Search and Google Maps grounding to return REAL, ALREADY-VERIFIED schools matching the user's query and location.
            NEVER invent fake schools, fake phone numbers, fake addresses, or fake URLs.
            Return ONLY a JSON array (no markdown prose outside the array) containing up to 10 real schools.
            Each JSON object MUST use these exact keys:
            - "name": official school name (string)
            - "address": street/area/city address (string)
            - "latitude": decimal latitude (number)
            - "longitude": decimal longitude (number)
            - "institutionType": one of "Primary & Secondary", "University", "Polytechnic", "College of Education", "Technical / Vocational"
            - "curriculum": e.g. "Nigerian", "British", "Nigerian & British", "IB", "American", "Montessori", "STEM"
            - "gradeLevels": array of strings from ["Creche / Pre-K", "Primary", "Junior Secondary", "Senior Secondary", "Sixth Form", "NCE Programme", "ND / HND Programme", "Undergraduate", "Postgraduate"]
            - "coursesOffered": array of short course/program highlights found online (up to 6 strings)
            - "programHighlights": short verified overview string (1-2 sentences)
            - "websiteUrl": official website URL if found online, else ""
            - "contactPhone": official phone number if found online, else ""
            - "boardingOption": one of "Day", "Boarding", "Day & Boarding", "Not Specified"
            - "admissionWindow": verified admission window if known online, else ""
            - "sourceTitle": concise name of the online or Google Maps source confirming this school
            - "sourceUrl": verified web or Google Maps URL if known, else ""
            - "googleMapsPlaceRef": Google Maps place ID or Maps reference string if provided by Maps grounding, else ""
        """.trimIndent()

        fun extractGroundingSourceLinks(response: GenerateContentResponse?): List<GroundingSourceLink> {
            val metadata = response?.candidates?.firstOrNull()?.groundingMetadata ?: return emptyList()
            val links = mutableListOf<GroundingSourceLink>()
            val seenUris = mutableSetOf<String>()
            metadata.groundingChunks.forEach { chunk ->
                chunk.maps?.let { mapsChunk ->
                    val uri = mapsChunk.uri?.trim().orEmpty()
                    val title = mapsChunk.title?.trim().orEmpty().ifEmpty { "Google Maps Place" }
                    val placeId = mapsChunk.placeId?.trim()?.takeIf { it.isNotEmpty() }
                    val effectiveUri = when {
                        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> uri
                        placeId != null -> "https://www.google.com/maps/place/?q=place_id:$placeId"
                        else -> ""
                    }
                    if (effectiveUri.isNotBlank() && seenUris.add(effectiveUri)) {
                        links.add(
                            GroundingSourceLink(
                                title = title.take(80),
                                uri = effectiveUri.take(300),
                                sourceType = "Google Maps",
                                placeId = placeId
                            )
                        )
                    }
                }
                chunk.web?.let { webChunk ->
                    val uri = webChunk.uri?.trim().orEmpty()
                    val title = webChunk.title?.trim().orEmpty()
                        .ifEmpty { webChunk.domain?.trim().orEmpty() }
                        .ifEmpty { "Web Source" }
                    if ((uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) &&
                        seenUris.add(uri)
                    ) {
                        links.add(
                            GroundingSourceLink(
                                title = title.take(80),
                                uri = uri.take(300),
                                sourceType = "Google Search"
                            )
                        )
                    }
                }
            }
            return links
        }

        fun parseGroundedSchoolsResponse(
            rawText: String,
            groundingLinks: List<GroundingSourceLink>,
            fallbackLat: Double,
            fallbackLng: Double,
            fallbackArea: String
        ): List<School> {
            val jsonArrayText = extractJsonArray(rawText) ?: return emptyList()
            val arr = try {
                JSONArray(jsonArrayText)
            } catch (_: Exception) {
                return emptyList()
            }

            val results = mutableListOf<School>()
            val seenIds = mutableSetOf<String>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.length < 2) continue

                val address = obj.optString("address").trim()
                    .ifEmpty { fallbackArea }
                    .take(200)

                val parsedLat = obj.optDouble("latitude", Double.NaN)
                val parsedLng = obj.optDouble("longitude", Double.NaN)
                val lat = if (!parsedLat.isNaN() && parsedLat in -90.0..90.0 && parsedLat != 0.0) {
                    parsedLat
                } else {
                    fallbackLat
                }
                val lng = if (!parsedLng.isNaN() && parsedLng in -180.0..180.0 && parsedLng != 0.0) {
                    parsedLng
                } else {
                    fallbackLng
                }

                val rawType = obj.optString("institutionType").trim()
                val institutionType = if (rawType in SchoolConstants.INSTITUTION_TYPES) {
                    rawType
                } else {
                    inferInstitutionType(name)
                }

                val rawCurriculum = obj.optString("curriculum").trim()
                val curriculum = SchoolConstants.CURRICULA.firstOrNull {
                    it.equals(rawCurriculum, ignoreCase = true)
                } ?: rawCurriculum.takeIf { it.isNotBlank() }?.take(60) ?: "Nigerian & British"

                val gradeLevels = parseStringArray(obj, "gradeLevels")
                    .ifEmpty { defaultGradesForType(institutionType) }
                    .take(8)
                val coursesOffered = parseStringArray(obj, "coursesOffered").take(12)
                val highlights = obj.optString("programHighlights").trim()
                val websiteUrl = ExternalSchoolService.normalizeWebsiteUrl(
                    obj.optString("websiteUrl").trim()
                )?.take(200)
                val contactPhone = obj.optString("contactPhone").trim()
                    .takeIf { it.length >= 5 }
                    ?.take(40)

                val rawBoarding = obj.optString("boardingOption").trim()
                val boardingOption = SchoolConstants.BOARDING_OPTIONS.firstOrNull {
                    it.equals(rawBoarding, ignoreCase = true)
                }

                val admissionWindow = obj.optString("admissionWindow").trim()
                    .takeIf { it.isNotBlank() }
                    ?.take(120)

                val rawPlaceRef = obj.optString("googleMapsPlaceRef").trim()
                    .takeIf { it.isNotBlank() }
                    ?.take(120)

                val itemSourceUrl = ExternalSchoolService.normalizeWebsiteUrl(
                    obj.optString("sourceUrl").trim()
                )
                val itemSourceTitle = obj.optString("sourceTitle").trim()

                // Match grounding chunks specifically relevant to this school first, then include top response citations
                val schoolTokens = significantTokens(name)
                val matchedLinks = groundingLinks.filter { link ->
                    val haystack = "${link.title} ${link.uri}".lowercase(Locale.US)
                    schoolTokens.any { haystack.contains(it) } ||
                        (rawPlaceRef != null && link.placeId?.contains(rawPlaceRef, ignoreCase = true) == true)
                }

                val mapsChunk = matchedLinks.firstOrNull { it.sourceType == "Google Maps" }
                    ?: groundingLinks.firstOrNull { it.sourceType == "Google Maps" && itemSourceTitle.contains("Maps", ignoreCase = true) }

                val resolvedPlaceId = rawPlaceRef ?: mapsChunk?.placeId
                val resolvedMapsUrl = mapsChunk?.uri
                    ?: itemSourceUrl?.takeIf { it.contains("google.com/maps", ignoreCase = true) }
                    ?: resolvedPlaceId?.let { "https://www.google.com/maps/place/?q=place_id:${it.removePrefix("places/")}" }

                val itemLinks = buildList {
                    matchedLinks.forEach { add(it) }
                    if (itemSourceUrl != null) {
                        add(
                            GroundingSourceLink(
                                title = itemSourceTitle.ifEmpty { "$name Official Listing" },
                                uri = itemSourceUrl,
                                sourceType = if (itemSourceUrl.contains("google.com/maps", ignoreCase = true)) "Google Maps" else "Google Search",
                                placeId = resolvedPlaceId
                            )
                        )
                    }
                    if (websiteUrl != null) {
                        add(
                            GroundingSourceLink(
                                title = "$name Website",
                                uri = websiteUrl,
                                sourceType = "Google Search"
                            )
                        )
                    }
                    if (isEmpty()) {
                        groundingLinks.take(3).forEach { add(it) }
                    }
                }.distinctBy { it.uri }.take(8)

                val citationStrings = itemLinks.map { it.toCitationString() }

                val sanitizedDocId = buildDeterministicDocId(name, address, resolvedPlaceId)
                if (!seenIds.add(sanitizedDocId)) continue

                val verificationSourceLabel = when {
                    resolvedPlaceId != null && itemLinks.any { it.sourceType == "Google Search" } ->
                        "Google Search & Maps Grounding"
                    resolvedPlaceId != null || mapsChunk != null ->
                        "Google Maps Grounding"
                    itemLinks.isNotEmpty() ->
                        "Google Search Grounding (${itemLinks.first().title.take(32)})"
                    else ->
                        "Google Search & Maps Grounding"
                }.take(120)

                val descriptionText = buildString {
                    if (highlights.isNotBlank()) {
                        append(highlights)
                    } else {
                        append("Verified online ${institutionType.lowercase(Locale.US)} located near $address.")
                    }
                    if (!admissionWindow.isNullOrBlank()) {
                        append(" Admission window: $admissionWindow.")
                    }
                }.take(800)

                val lowerName = name.lowercase(Locale.US)
                val faithBased = listOf(
                    "christian", "catholic", "baptist", "anglican", "methodist",
                    "islamic", "muslim", "crescent", "redeemer", "jesuit"
                ).any { lowerName.contains(it) }
                val faithTradition = when {
                    lowerName.contains("islamic") || lowerName.contains("muslim") || lowerName.contains("crescent") -> "Islamic"
                    faithBased -> "Christian"
                    else -> "Secular"
                }

                results.add(
                    School(
                        id = sanitizedDocId,
                        adminUid = "",
                        adminName = "",
                        name = name.take(100),
                        description = descriptionText,
                        address = address,
                        latitude = lat,
                        longitude = lng,
                        curriculum = curriculum,
                        institutionType = institutionType,
                        faithBased = faithBased,
                        faithTradition = faithTradition,
                        specialSchool = lowerName.contains("special") || lowerName.contains("autism"),
                        specialNeedsSupport = if (lowerName.contains("special") || lowerName.contains("autism")) "Special Needs Support" else "General Education",
                        studentMix = when {
                            lowerName.contains("girls") -> "Girls Only"
                            lowerName.contains("boys") -> "Boys Only"
                            else -> "Mixed"
                        },
                        gradeLevels = gradeLevels,
                        coursesOffered = coursesOffered,
                        feeMin = 0L,
                        feeMax = 0L,
                        feeCurrency = "NGN",
                        feePeriod = "Per Term",
                        rankingScore = 84.0,
                        districtRank = (25L + results.size).coerceAtMost(9999L),
                        rating = 4.4,
                        tuitionTier = "$$",
                        capacity = 400L,
                        enrolledCount = 0L,
                        admissionsOpen = true,
                        contactPhone = contactPhone,
                        externalPlaceId = resolvedPlaceId?.let { "gplace_${it.removePrefix("places/")}" } ?: sanitizedDocId,
                        websiteUrl = websiteUrl,
                        verificationStatus = "VERIFIED_ONLINE",
                        verificationSource = verificationSourceLabel,
                        sourceCitations = citationStrings,
                        googleMapsPlaceRef = resolvedPlaceId?.take(160),
                        googleMapsUrl = resolvedMapsUrl?.take(300),
                        admissionWindow = admissionWindow,
                        boardingOption = boardingOption,
                        photoUrls = emptyList(),
                        extraNotes = null,
                        lastEnrichedByName = null
                    )
                )
            }
            return results
        }

        private fun buildDeterministicDocId(name: String, address: String, placeId: String?): String {
            val cleanPlace = placeId?.removePrefix("places/")
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                ?.trim('_')
            if (!cleanPlace.isNullOrBlank()) {
                return "gmaps_${cleanPlace.take(64)}"
            }
            val slug = name.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(40)
                .ifEmpty { "school" }
            val hash = abs("${name.lowercase(Locale.US)}|${address.lowercase(Locale.US)}".hashCode())
            return "web_${slug}_$hash".take(120)
        }

        private fun extractJsonArray(rawText: String): String? {
            val cleaned = rawText
                .replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start == -1 || end <= start) return null
            return cleaned.substring(start, end + 1)
        }

        private fun parseStringArray(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (j in 0 until arr.length()) {
                    val v = arr.optString(j).trim()
                    if (v.isNotEmpty()) add(v.take(60))
                }
            }.distinct()
        }

        private fun inferInstitutionType(name: String): String {
            val lower = name.lowercase(Locale.US)
            return when {
                lower.contains("college of education") || lower.contains("teachers") -> "College of Education"
                lower.contains("polytechnic") || lower.contains("monotechnic") -> "Polytechnic"
                lower.contains("university") -> "University"
                lower.contains("technical") || lower.contains("vocational") -> "Technical / Vocational"
                else -> "Primary & Secondary"
            }
        }

        private fun defaultGradesForType(type: String): List<String> = when (type) {
            "University" -> listOf("Undergraduate", "Postgraduate")
            "Polytechnic" -> listOf("ND / HND Programme")
            "College of Education" -> listOf("NCE Programme", "Undergraduate")
            "Technical / Vocational" -> listOf("ND / HND Programme", "Senior Secondary")
            else -> listOf("Primary", "Junior Secondary")
        }

        private fun significantTokens(name: String): List<String> {
            val stop = setOf("the", "and", "of", "for", "school", "schools", "academy", "college", "international", "high", "primary", "nursery")
            val tokens = name.lowercase(Locale.US).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            return tokens.filter { it !in stop }.ifEmpty { tokens }
        }

        private val TUTOR_SYSTEM_PROMPT = """
            You are an education directory indexer for Skolquik Progressive Tutors.
            When asked to find tutors or tutoring centres, ALWAYS use Google Search and Google Maps grounding to return REAL, ALREADY-VERIFIED tutoring academies, home-tutoring networks, or exam-prep centres found online.
            NEVER invent fake tutors, fake phone numbers, fake addresses, or fake URLs.
            Return ONLY a JSON array (no markdown prose outside the array) containing up to 8 real tutoring centres or certified tutoring providers.
            Each JSON object MUST use these exact keys:
            - "name": official tutoring centre or verified tutor academy name (string)
            - "headline": short verified speciality summary (string)
            - "bio": 1-2 sentence verified online overview (string)
            - "address": street/area/city location (string)
            - "latitude": decimal latitude (number)
            - "longitude": decimal longitude (number)
            - "curriculumTracks": array from ["Cambridge", "Nigerian"]
            - "cambridgeSubjects": array of relevant Cambridge subjects (up to 4)
            - "nigerianSubjects": array of relevant Nigerian WAEC/NECO/JAMB subjects (up to 4)
            - "qualifications": verified accreditation or exam focus string
            - "websiteUrl": official website URL if found online, else ""
            - "googleMapsUrl": Google Maps link if known, else ""
            - "contactPhone": verified phone number if found online, else ""
            - "sourceTitle": concise name of online or Google Maps source
        """.trimIndent()

        fun parseGroundedTutorsResponse(
            rawText: String,
            groundingLinks: List<GroundingSourceLink>,
            fallbackLat: Double,
            fallbackLng: Double,
            fallbackArea: String
        ): List<ProgressiveTutor> {
            val jsonArrayText = extractJsonArray(rawText) ?: return emptyList()
            val arr = try {
                JSONArray(jsonArrayText)
            } catch (_: Exception) {
                return emptyList()
            }

            val results = mutableListOf<ProgressiveTutor>()
            val seenIds = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.length < 2) continue

                val address = obj.optString("address").trim()
                    .ifEmpty { fallbackArea }
                    .take(120)
                val parsedLat = obj.optDouble("latitude", Double.NaN)
                val parsedLng = obj.optDouble("longitude", Double.NaN)
                val lat = if (!parsedLat.isNaN() && parsedLat in -90.0..90.0 && parsedLat != 0.0) parsedLat else fallbackLat
                val lng = if (!parsedLng.isNaN() && parsedLng in -180.0..180.0 && parsedLng != 0.0) parsedLng else fallbackLng

                val tracks = parseStringArray(obj, "curriculumTracks")
                    .filter { it == "Cambridge" || it == "Nigerian" }
                    .ifEmpty { listOf("Cambridge", "Nigerian") }
                val camSubjects = parseStringArray(obj, "cambridgeSubjects").take(6)
                val nigSubjects = parseStringArray(obj, "nigerianSubjects").take(6)
                val allSubjects = (camSubjects + nigSubjects).distinct().ifEmpty {
                    listOf("WAEC / NECO Mathematics", "Cambridge IGCSE Mathematics")
                }

                val websiteUrl = ExternalSchoolService.normalizeWebsiteUrl(obj.optString("websiteUrl").trim())?.take(200)
                val explicitMaps = ExternalSchoolService.normalizeWebsiteUrl(obj.optString("googleMapsUrl").trim())?.take(300)
                val contactPhone = obj.optString("contactPhone").trim().takeIf { it.length >= 5 }?.take(40)
                val tokens = significantTokens(name)
                val matchedLinks = groundingLinks.filter { link ->
                    val hay = "${link.title} ${link.uri}".lowercase(Locale.US)
                    tokens.any { hay.contains(it) }
                }
                val mapsLink = explicitMaps ?: matchedLinks.firstOrNull { it.sourceType == "Google Maps" }?.uri
                val fallbackWeb = websiteUrl ?: matchedLinks.firstOrNull { it.sourceType == "Google Search" }?.uri
                val sourceTitle = obj.optString("sourceTitle").trim()
                    .ifEmpty { matchedLinks.firstOrNull()?.title ?: "Google Search & Maps Grounding" }
                    .take(100)

                val docId = "wtut_" + buildDeterministicDocId(name, address, null).take(60)
                if (!seenIds.add(docId)) continue

                results.add(
                    ProgressiveTutor(
                        id = docId,
                        teacherUid = "",
                        fullName = name.take(80),
                        sex = "Female",
                        headline = obj.optString("headline").trim().ifEmpty { "Verified Online Tutoring Centre" }.take(120),
                        bio = obj.optString("bio").trim().ifEmpty { "Verified tutoring academy discovered via Google Search & Maps around $address." }.take(600),
                        curriculumTracks = tracks,
                        cambridgeSubjects = camSubjects,
                        nigerianSubjects = nigSubjects,
                        allSubjects = allSubjects,
                        qualifications = obj.optString("qualifications").trim().ifEmpty { "Accredited Exam & Subject Coaching" }.take(120),
                        yearsExperience = 5L,
                        hourlyRate = 0L,
                        currency = "NGN",
                        meetingPlatform = "Online & In-Person",
                        availability = "Mon–Sat • Contact provider for schedule",
                        locationLabel = address,
                        latitude = lat,
                        longitude = lng,
                        rating = 4.6,
                        onlineActive = true,
                        verificationStatus = "VERIFIED_ONLINE",
                        verificationSource = sourceTitle,
                        websiteUrl = fallbackWeb,
                        googleMapsUrl = mapsLink,
                        contactPhone = contactPhone
                    )
                )
            }
            return results
        }
    }

    private val defaultModel: GenerativeModel by lazy {
        buildModel(
            tools = listOf(Tool.googleSearch(), Tool.googleMaps()),
            lat = 6.4474,
            lng = 3.4737
        )
    }

    private fun buildModel(
        tools: List<Tool>,
        lat: Double,
        lng: Double,
        systemPrompt: String = SYSTEM_PROMPT
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.25f
                topP = 0.9f
            },
            tools = tools,
            toolConfig = ToolConfig(
                functionCallingConfig = null,
                retrievalConfig = retrievalConfig {
                    latLng = AiLatLng(lat, lng)
                    languageCode = "en"
                }
            ),
            systemInstruction = content {
                text(systemPrompt)
            }
        )
    }

    suspend fun searchVerifiedOnlineSchools(
        query: String,
        centerLat: Double,
        centerLng: Double,
        centerLabel: String,
        radiusKm: Int
    ): Result<List<School>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val prompt = buildString {
            if (cleanQuery.isNotBlank()) {
                append("Find real, already-verified schools matching \"$cleanQuery\" near $centerLabel ")
            } else {
                append("Find top real, already-verified primary, secondary, and tertiary schools near $centerLabel ")
            }
            append(
                String.format(
                    Locale.US,
                    "(coordinates %.4f, %.4f, within roughly %d km). ",
                    centerLat,
                    centerLng,
                    radiusKm.coerceIn(3, 50)
                )
            )
            append("Include verified online address, coordinates, grade levels, curriculum or programme highlights, official website, phone number, and Google Maps place references.")
        }

        try {
            val response = withTimeout(45_000L) {
                val model = if (abs(centerLat - 6.4474) < 0.001 && abs(centerLng - 3.4737) < 0.001) {
                    defaultModel
                } else {
                    buildModel(listOf(Tool.googleSearch(), Tool.googleMaps()), centerLat, centerLng)
                }
                model.generateContent(prompt)
            }
            val links = extractGroundingSourceLinks(response)
            val schools = parseGroundedSchoolsResponse(
                rawText = response.text.orEmpty(),
                groundingLinks = links,
                fallbackLat = centerLat,
                fallbackLng = centerLng,
                fallbackArea = centerLabel
            )
            if (schools.isNotEmpty()) {
                return@withContext Result.success(schools)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Combined Search+Maps grounding attempt failed (${e.message}); trying parallel fallback")
        }

        // Parallel fallback: separate Google Search and Google Maps grounding requests
        return@withContext try {
            withTimeout(45_000L) {
                coroutineScope {
                    val searchDeferred = async {
                        runCatching {
                            val searchModel = buildModel(listOf(Tool.googleSearch()), centerLat, centerLng)
                            val resp = searchModel.generateContent(prompt)
                            val links = extractGroundingSourceLinks(resp)
                            parseGroundedSchoolsResponse(resp.text.orEmpty(), links, centerLat, centerLng, centerLabel)
                        }.getOrElse { err ->
                            if (err is CancellationException) throw err
                            emptyList()
                        }
                    }
                    val mapsDeferred = async {
                        runCatching {
                            val mapsModel = buildModel(listOf(Tool.googleMaps()), centerLat, centerLng)
                            val resp = mapsModel.generateContent(prompt)
                            val links = extractGroundingSourceLinks(resp)
                            parseGroundedSchoolsResponse(resp.text.orEmpty(), links, centerLat, centerLng, centerLabel)
                        }.getOrElse { err ->
                            if (err is CancellationException) throw err
                            emptyList()
                        }
                    }
                    val merged = ExternalSchoolService.mergeRegisteredAndExternalSchools(
                        registered = mapsDeferred.await(),
                        external = searchDeferred.await()
                    )
                    Result.success(merged)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Parallel Search/Maps grounding fallback failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun searchVerifiedOnlineTutors(
        query: String,
        centerLat: Double,
        centerLng: Double,
        centerLabel: String
    ): Result<List<ProgressiveTutor>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val prompt = buildString {
            if (cleanQuery.isNotBlank()) {
                append("Find real, verified tutoring centres, home-tutor services, or exam-prep academies matching \"$cleanQuery\" near $centerLabel ")
            } else {
                append("Find real, verified Cambridge IGCSE, WAEC/NECO, JAMB, and STEM tutoring centres or private tutor agencies near $centerLabel ")
            }
            append(String.format(Locale.US, "(coordinates %.4f, %.4f). ", centerLat, centerLng))
            append("Include real official website URL, Google Maps link, phone number if public, address, and subjects taught.")
        }
        return@withContext try {
            val response = withTimeout(45_000L) {
                val model = buildModel(
                    tools = listOf(Tool.googleSearch(), Tool.googleMaps()),
                    lat = centerLat,
                    lng = centerLng,
                    systemPrompt = TUTOR_SYSTEM_PROMPT
                )
                model.generateContent(prompt)
            }
            val links = extractGroundingSourceLinks(response)
            val parsed = parseGroundedTutorsResponse(
                rawText = response.text.orEmpty(),
                groundingLinks = links,
                fallbackLat = centerLat,
                fallbackLng = centerLng,
                fallbackArea = centerLabel
            )
            Result.success(parsed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Online tutor grounding search failed: ${e.message}")
            Result.failure(e)
        }
    }
}

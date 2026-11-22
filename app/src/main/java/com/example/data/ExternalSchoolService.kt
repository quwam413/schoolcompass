package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SchoolVerificationResult(
    val status: String,
    val source: String,
    val discoveredWebsite: String? = null,
    val matchedExternalId: String? = null
)

object ExternalSchoolService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    suspend fun fetchNearbyExternalSchools(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Int
    ): Result<List<School>> = withContext(Dispatchers.IO) {
        val radiusMeters = (radiusKm.coerceIn(2, 50) * 1000)
        val query = """
            [out:json][timeout:12];
            (
              node["amenity"~"^(school|kindergarten|college|university)$"](around:$radiusMeters,$centerLat,$centerLng);
              way["amenity"~"^(school|kindergarten|college|university)$"](around:$radiusMeters,$centerLat,$centerLng);
              relation["amenity"~"^(school|kindergarten|college|university)$"](around:$radiusMeters,$centerLat,$centerLng);
            );
            out center tags 60;
        """.trimIndent()

        var lastError: Exception? = null
        for (endpoint in overpassEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "SchoolCompassAndroid/1.0 (https://schoolcompass.app)")
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        return@withContext Result.success(parseOverpassSchoolsJson(bodyStr))
                    }
                    lastError = IllegalStateException("Directory lookup returned HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Unable to reach public map school directory"))
    }

    fun parseOverpassSchoolsJson(jsonBody: String): List<School> {
        val root = JSONObject(jsonBody)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val parsed = mutableListOf<School>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            val rawName = tags.optString("name").trim()
                .ifEmpty { tags.optString("official_name").trim() }
                .ifEmpty { tags.optString("short_name").trim() }
            if (rawName.length < 2) continue

            val type = el.optString("type").trim().ifEmpty { "node" }
            val osmNumId = el.optLong("id", 0L)
            if (osmNumId == 0L) continue
            val externalId = "${type}_${osmNumId}"
            if (!seenIds.add(externalId)) continue

            val centerObj = el.optJSONObject("center")
            val lat = if (el.has("lat")) el.optDouble("lat", Double.NaN) else centerObj?.optDouble("lat", Double.NaN) ?: Double.NaN
            val lng = if (el.has("lon")) el.optDouble("lon", Double.NaN) else centerObj?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) continue

            val fullAddr = tags.optString("addr:full").trim()
            val streetParts = listOf(
                tags.optString("addr:housenumber").trim(),
                tags.optString("addr:street").trim(),
                tags.optString("addr:suburb").trim().ifEmpty { tags.optString("addr:neighbourhood").trim() },
                tags.optString("addr:city").trim().ifEmpty { tags.optString("addr:state").trim() }
            ).filter { it.isNotBlank() }
            val address = fullAddr.ifEmpty {
                if (streetParts.isNotEmpty()) streetParts.joinToString(", ")
                else "Map Coordinates (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)})"
            }.take(200)

            val rawWebsite = tags.optString("website").trim()
                .ifEmpty { tags.optString("contact:website").trim() }
                .ifEmpty { tags.optString("url").trim() }
            val websiteUrl = normalizeWebsiteUrl(rawWebsite)?.take(200)

            val phone = tags.optString("phone").trim()
                .ifEmpty { tags.optString("contact:phone").trim() }
                .ifEmpty { tags.optString("mobile").trim() }
                .takeIf { it.isNotEmpty() }
                ?.take(40)

            val religion = tags.optString("religion").trim()
            val denomination = tags.optString("denomination").trim()
            val faithBased = religion.isNotEmpty() || denomination.isNotEmpty()
            val faithTradition = when {
                denomination.isNotEmpty() -> denomination.replaceFirstChar { it.uppercase() }.take(60)
                religion.isNotEmpty() -> religion.replaceFirstChar { it.uppercase() }.take(60)
                else -> "Secular"
            }

            val lowerName = rawName.lowercase(Locale.US)
            val specialTag = tags.optString("school:type").trim() + " " + tags.optString("special_needs").trim()
            val isSpecial = specialTag.contains("special", ignoreCase = true) ||
                lowerName.contains("special") || lowerName.contains("autism")

            val inferredCurriculum = when {
                lowerName.contains("british") || lowerName.contains("cambridge") || lowerName.contains("igcse") -> "British"
                lowerName.contains("montessori") -> "Montessori"
                lowerName.contains("american") -> "American"
                lowerName.contains("international") || (lowerName.contains("nigerian") && lowerName.contains("british")) -> "Nigerian & British"
                else -> "Nigerian"
            }

            val amenity = tags.optString("amenity").trim().lowercase(Locale.US)
            val institutionType = when {
                lowerName.contains("college of education") || lowerName.contains("teacher training") || lowerName.contains("teachers' college") -> "College of Education"
                lowerName.contains("polytechnic") || lowerName.contains("monotechnic") || lowerName.contains("institute of technology") -> "Polytechnic"
                amenity == "university" || lowerName.contains("university") -> "University"
                lowerName.contains("technical") || lowerName.contains("vocational") -> "Technical / Vocational"
                else -> "Primary & Secondary"
            }

            val gradeHint = tags.optString("isced:level").trim()
            val gradeLevels = buildList {
                when (institutionType) {
                    "University" -> {
                        add("Undergraduate")
                        add("Postgraduate")
                    }
                    "Polytechnic" -> {
                        add("ND / HND Programme")
                    }
                    "College of Education" -> {
                        add("NCE Programme")
                        add("Undergraduate")
                    }
                    "Technical / Vocational" -> {
                        add("ND / HND Programme")
                        add("Senior Secondary")
                    }
                    else -> {
                        if (amenity == "kindergarten" || gradeHint.contains("0")) add("Creche / Pre-K")
                        if (gradeHint.contains("1") || lowerName.contains("primary") || lowerName.contains("nursery")) add("Primary")
                        if (gradeHint.contains("2") || lowerName.contains("secondary") || lowerName.contains("college") || lowerName.contains("grammar")) add("Junior Secondary")
                        if (gradeHint.contains("3") || lowerName.contains("college") || lowerName.contains("high") || lowerName.contains("grammar")) add("Senior Secondary")
                        if (isEmpty()) add("Primary")
                    }
                }
            }.distinct()

            val rawCoursesTags = listOf(
                tags.optString("courses").trim(),
                tags.optString("faculty").trim(),
                tags.optString("department").trim(),
                tags.optString("subject").trim()
            ).filter { it.isNotEmpty() }
                .flatMap { it.split(';', ',') }
                .map { it.trim().replaceFirstChar { c -> c.uppercase() } }
                .filter { it.length >= 2 }

            val inferredCourses = when {
                rawCoursesTags.isNotEmpty() -> rawCoursesTags.distinct().take(15)
                institutionType == "University" -> listOf("Computer Science", "Business Administration", "Sciences & Biotechnology", "Arts & Humanities")
                institutionType == "Polytechnic" -> listOf("Computer Science", "Electrical Electronics Eng.", "Civil Engineering", "Business Administration")
                institutionType == "College of Education" -> listOf("Education & Pedagogy", "Early Childhood Education", "Sciences & Biotechnology")
                institutionType == "Technical / Vocational" -> listOf("Electrical Electronics Eng.", "Agricultural Technology", "Computer Science")
                else -> emptyList()
            }

            val operatorTag = tags.optString("operator").trim()
            val descParts = buildList {
                add("Publicly mapped ${institutionType.lowercase(Locale.US)} discovered via OpenStreetMap.")
                if (operatorTag.isNotEmpty()) add("Operator: $operatorTag.")
                add("Not yet registered on SchoolCompass — administrators can claim this listing to add courses, programmes, fee ranges, and sports.")
            }

            parsed.add(
                School(
                    id = "osm_$externalId",
                    adminUid = "",
                    adminName = "",
                    name = rawName.take(100),
                    description = descParts.joinToString(" ").take(800),
                    address = address,
                    latitude = lat,
                    longitude = lng,
                    curriculum = inferredCurriculum,
                    institutionType = institutionType,
                    faithBased = faithBased,
                    faithTradition = faithTradition,
                    specialSchool = isSpecial,
                    specialNeedsSupport = if (isSpecial) "Special Needs Support" else "General Education",
                    studentMix = when {
                        lowerName.contains("girls") -> "Girls Only"
                        lowerName.contains("boys") -> "Boys Only"
                        else -> "Mixed"
                    },
                    gradeLevels = gradeLevels,
                    coursesOffered = inferredCourses,
                    activities = emptyList(),
                    sportsFocus = emptyList(),
                    feeMin = 0L,
                    feeMax = 0L,
                    feeCurrency = "NGN",
                    feePeriod = "Per Term",
                    rankingScore = 80.0,
                    districtRank = 50L + parsed.size,
                    rating = 4.2,
                    tuitionTier = "$$",
                    capacity = 300L,
                    enrolledCount = 0L,
                    admissionsOpen = true,
                    contactPhone = phone,
                    externalPlaceId = externalId,
                    websiteUrl = websiteUrl,
                    verificationStatus = "INTERNET_MATCHED",
                    verificationSource = "OpenStreetMap Directory (${type}/$osmNumId)"
                )
            )
        }
        return parsed
    }

    suspend fun verifySchoolOnInternet(school: School): SchoolVerificationResult = withContext(Dispatchers.IO) {
        var directoryMatched = false
        var matchedExtId = school.externalPlaceId
        var discoveredWebsite = normalizeWebsiteUrl(school.websiteUrl)

        // 1. Cross-check OpenStreetMap / Nominatim public internet registry
        try {
            val nominatimUrl = if (!school.externalPlaceId.isNullOrBlank()) {
                val osmToken = toNominatimOsmToken(school.externalPlaceId)
                if (osmToken != null) {
                    "https://nominatim.openstreetmap.org/lookup?format=jsonv2&osm_ids=$osmToken&extratags=1"
                } else null
            } else null

            val lookupUrl = nominatimUrl ?: buildString {
                append("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&extratags=1&q=")
                append(URLEncoder.encode(school.name.trim(), "UTF-8"))
            }

            val req = Request.Builder()
                .url(lookupUrl)
                .header("User-Agent", "SchoolCompassAndroid/1.0 (https://schoolcompass.app)")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val itemLat = obj.optString("lat").toDoubleOrNull() ?: continue
                            val itemLng = obj.optString("lon").toDoubleOrNull() ?: continue
                            val distKm = calculateDistanceKm(school.latitude, school.longitude, itemLat, itemLng)
                            val displayName = obj.optString("display_name")
                            val nameMatch = namesSimilar(school.name, displayName)
                            if (!school.externalPlaceId.isNullOrBlank() || (distKm <= 15.0 && nameMatch) || distKm <= 2.5) {
                                directoryMatched = true
                                val osmType = obj.optString("osm_type").trim()
                                val osmId = obj.optLong("osm_id", 0L)
                                if (matchedExtId.isNullOrBlank() && osmType.isNotBlank() && osmId > 0L) {
                                    matchedExtId = "${osmType}_${osmId}"
                                }
                                val extratags = obj.optJSONObject("extratags")
                                if (discoveredWebsite.isNullOrBlank() && extratags != null) {
                                    val candidate = extratags.optString("website").ifBlank { extratags.optString("contact:website") }
                                    discoveredWebsite = normalizeWebsiteUrl(candidate)
                                }
                                break
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Network lookup failed gracefully; continue with website check
        }

        // 2. Live HTTP check of school official website if present
        var websiteVerified = false
        var websiteHttpCode: Int? = null
        var websiteHost: String? = null
        val targetUrl = discoveredWebsite
        if (!targetUrl.isNullOrBlank()) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "SchoolCompassAndroid/1.0")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    websiteHttpCode = resp.code
                    websiteHost = resp.request.url.host
                    if (resp.code in 200..399) {
                        val snippet = resp.body?.source()?.let { src ->
                            src.request(16_384)
                            src.buffer.clone().readUtf8()
                        }.orEmpty().lowercase(Locale.US)
                        val nameTokens = significantTokens(school.name)
                        val hasTokenMatch = nameTokens.any { snippet.contains(it) } ||
                            (websiteHost?.lowercase(Locale.US)?.let { host -> nameTokens.any { host.contains(it) } } == true)
                        val hasEduKeyword = listOf(
                            "school", "academy", "college", "university", "polytechnic",
                            "education", "faculty", "department", "students", "admissions"
                        ).any {
                            snippet.contains(it)
                        }
                        if (hasTokenMatch || hasEduKeyword) {
                            websiteVerified = true
                        }
                    }
                }
            } catch (_: Exception) {
                // Website unreachable
            }
        }

        return@withContext when {
            directoryMatched && websiteVerified -> SchoolVerificationResult(
                status = "VERIFIED",
                source = "OSM Directory + Website (${websiteHost ?: "site"} HTTP ${websiteHttpCode ?: 200})",
                discoveredWebsite = discoveredWebsite,
                matchedExternalId = matchedExtId
            )
            websiteVerified -> SchoolVerificationResult(
                status = "VERIFIED",
                source = "Official Website Verified (${websiteHost ?: "web"} HTTP ${websiteHttpCode ?: 200})",
                discoveredWebsite = discoveredWebsite,
                matchedExternalId = matchedExtId
            )
            directoryMatched -> SchoolVerificationResult(
                status = "INTERNET_MATCHED",
                source = "Matched OpenStreetMap Directory (${matchedExtId ?: "OSM"})",
                discoveredWebsite = discoveredWebsite,
                matchedExternalId = matchedExtId
            )
            websiteHttpCode != null && websiteHttpCode in 200..399 -> SchoolVerificationResult(
                status = "INTERNET_MATCHED",
                source = "Website Reachable (${websiteHost ?: "web"} HTTP $websiteHttpCode)",
                discoveredWebsite = discoveredWebsite,
                matchedExternalId = matchedExtId
            )
            else -> SchoolVerificationResult(
                status = "UNVERIFIED",
                source = "No matching OpenStreetMap listing or reachable website verified yet",
                discoveredWebsite = discoveredWebsite,
                matchedExternalId = matchedExtId
            )
        }
    }

    fun claimSchoolDocId(externalPlaceId: String): String {
        val sanitized = externalPlaceId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(110)
        return "ext_$sanitized"
    }

    fun mergeRegisteredAndExternalSchools(
        registered: List<School>,
        external: List<School>
    ): List<School> {
        val claimedIds = registered.mapNotNull { it.externalPlaceId?.takeIf(String::isNotBlank) }.toSet()
        val unclaimedExternal = external.filter { ext ->
            val extId = ext.externalPlaceId
            val claimedById = extId != null && extId in claimedIds
            val claimedByProximityAndName = registered.any { reg ->
                val dist = calculateDistanceKm(reg.latitude, reg.longitude, ext.latitude, ext.longitude)
                dist <= 0.45 && namesSimilar(reg.name, ext.name)
            }
            !claimedById && !claimedByProximityAndName
        }
        return registered + unclaimedExternal
    }

    fun normalizeWebsiteUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains('.') -> "https://$trimmed"
            else -> null
        }
    }

    private fun toNominatimOsmToken(externalPlaceId: String): String? {
        val parts = externalPlaceId.split('_')
        if (parts.size != 2) return null
        val prefix = when (parts[0].lowercase(Locale.US)) {
            "node" -> "N"
            "way" -> "W"
            "relation" -> "R"
            else -> return null
        }
        return if (parts[1].all { it.isDigit() }) "$prefix${parts[1]}" else null
    }

    private fun significantTokens(name: String): List<String> {
        val stopWords = setOf("the", "and", "of", "for", "school", "schools", "academy", "college", "international", "high", "primary", "nursery")
        val all = name.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
        val nonStop = all.filter { it !in stopWords }
        return nonStop.ifEmpty { all }
    }

    private fun namesSimilar(a: String, b: String): Boolean {
        val tokensA = significantTokens(a).toSet()
        val lowerB = b.lowercase(Locale.US)
        if (tokensA.isEmpty()) return false
        return tokensA.any { lowerB.contains(it) }
    }
}

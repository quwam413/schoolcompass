package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeocodedPlace(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val source: String
)

object GoogleMapsPlatformService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun hasValidGoogleMapsKey(): Boolean {
        val key = BuildConfig.MAPS_API_KEY.trim()
        return key.isNotEmpty() && key != "MY_MAPS_API_KEY"
    }

    fun isInsideNigeriaBounds(lat: Double, lng: Double): Boolean =
        lat in 4.0..14.0 && lng in 2.5..14.9

    suspend fun geocodePlace(
        query: String,
        restrictToNigeria: Boolean = false
    ): Result<GeocodedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Enter a city, area, or address to search."))
        }
        val effectiveQuery = if (restrictToNigeria && !trimmed.contains("nigeria", ignoreCase = true)) {
            "$trimmed, Nigeria"
        } else {
            trimmed
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val encodedGoogle = URLEncoder.encode(effectiveQuery, "UTF-8")

        if (hasValidGoogleMapsKey()) {
            try {
                val componentSuffix = if (restrictToNigeria) "&components=country:NG" else ""
                val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedGoogle$componentSuffix&key=${BuildConfig.MAPS_API_KEY.trim()}"
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && body.isNotBlank()) {
                        parseGoogleGeocodingJson(body)?.let { place ->
                            return@withContext Result.success(place)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to public Nominatim geocoder
            }
        }

        return@withContext try {
            val countryParam = if (restrictToNigeria) "&countrycodes=ng" else ""
            val nominatimUrl = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=3$countryParam&q=$encoded"
            val req = Request.Builder()
                .url(nominatimUrl)
                .header("User-Agent", "SchoolCompassAndroid/1.0 (https://schoolcompass.app)")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    Result.failure(IllegalStateException("Location search returned HTTP ${resp.code}"))
                } else {
                    parseNominatimGeocodingJson(body)
                        ?.let { Result.success(it) }
                        ?: Result.failure(
                            IllegalStateException(
                                if (restrictToNigeria) "No place found in Nigeria for \"$trimmed\""
                                else "No matching location found for \"$trimmed\""
                            )
                        )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchNearbyGooglePlacesSchools(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Int
    ): Result<List<School>> = withContext(Dispatchers.IO) {
        if (!hasValidGoogleMapsKey()) {
            return@withContext Result.success(emptyList())
        }
        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        val radiusMeters = (radiusKm.coerceIn(2, 50) * 1000)
        val combined = mutableListOf<School>()
        val seenIds = mutableSetOf<String>()
        var lastError: Exception? = null

        for (placeType in listOf("school", "university")) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$centerLat,$centerLng&radius=$radiusMeters&type=$placeType&key=$apiKey"
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && body.isNotBlank()) {
                        parseGooglePlacesSchoolsJson(body, placeType).forEach { s ->
                            val extKey = s.externalPlaceId ?: s.id
                            if (seenIds.add(extKey)) {
                                combined.add(s)
                            }
                        }
                    } else {
                        lastError = IllegalStateException("Google Places returned HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (combined.isNotEmpty() || lastError == null) {
            Result.success(combined)
        } else {
            Result.failure(lastError!!)
        }
    }

    fun parseGoogleGeocodingJson(jsonBody: String): GeocodedPlace? {
        val root = JSONObject(jsonBody)
        if (root.optString("status") != "OK") return null
        val results = root.optJSONArray("results") ?: return null
        val first = results.optJSONObject(0) ?: return null
        val loc = first.optJSONObject("geometry")?.optJSONObject("location") ?: return null
        val lat = loc.optDouble("lat", Double.NaN)
        val lng = loc.optDouble("lng", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        val formatted = first.optString("formatted_address").trim()
            .ifEmpty { String.format(Locale.US, "%.4f, %.4f", lat, lng) }
        return GeocodedPlace(
            formattedAddress = formatted.take(120),
            latitude = lat,
            longitude = lng,
            source = "Google Maps Geocoding"
        )
    }

    fun parseNominatimGeocodingJson(jsonBody: String): GeocodedPlace? {
        if (!jsonBody.trimStart().startsWith("[")) return null
        val arr = JSONArray(jsonBody)
        val first = arr.optJSONObject(0) ?: return null
        val lat = first.optString("lat").toDoubleOrNull() ?: return null
        val lng = first.optString("lon").toDoubleOrNull() ?: return null
        val display = first.optString("display_name").trim()
            .ifEmpty { String.format(Locale.US, "%.4f, %.4f", lat, lng) }
        val compactLabel = display.split(",").take(2).joinToString(",").trim().ifEmpty { display }
        return GeocodedPlace(
            formattedAddress = compactLabel.take(120),
            latitude = lat,
            longitude = lng,
            source = "OpenStreetMap Geocoder"
        )
    }

    fun parseGooglePlacesSchoolsJson(jsonBody: String, queryPlaceType: String = "school"): List<School> {
        val root = JSONObject(jsonBody)
        val status = root.optString("status")
        if (status != "OK" && status != "ZERO_RESULTS") return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        val parsed = mutableListOf<School>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val placeId = item.optString("place_id").trim()
            val name = item.optString("name").trim()
            if (placeId.isEmpty() || name.length < 2) continue
            val extId = "gplace_$placeId"
            if (!seenIds.add(extId)) continue

            val loc = item.optJSONObject("geometry")?.optJSONObject("location") ?: continue
            val lat = loc.optDouble("lat", Double.NaN)
            val lng = loc.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue

            val vicinity = item.optString("vicinity").trim()
                .ifEmpty { item.optString("formatted_address").trim() }
                .ifEmpty { "Coordinates (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)})" }
                .take(200)
            val rating = item.optDouble("rating", 4.3).coerceIn(1.0, 5.0)
            val lowerName = name.lowercase(Locale.US)

            val typesArr = item.optJSONArray("types")
            val types = buildList {
                if (typesArr != null) {
                    for (t in 0 until typesArr.length()) {
                        add(typesArr.optString(t).lowercase(Locale.US))
                    }
                }
            }

            val institutionType = when {
                lowerName.contains("college of education") || lowerName.contains("teachers' college") -> "College of Education"
                lowerName.contains("polytechnic") || lowerName.contains("monotechnic") || lowerName.contains("institute of technology") -> "Polytechnic"
                queryPlaceType == "university" || "university" in types || lowerName.contains("university") -> "University"
                lowerName.contains("technical") || lowerName.contains("vocational") -> "Technical / Vocational"
                else -> "Primary & Secondary"
            }

            val curriculum = when {
                lowerName.contains("british") || lowerName.contains("cambridge") || lowerName.contains("igcse") -> "British"
                lowerName.contains("montessori") -> "Montessori"
                lowerName.contains("american") -> "American"
                lowerName.contains("international") -> "Nigerian & British"
                else -> "Nigerian"
            }

            val isSpecial = lowerName.contains("special") || lowerName.contains("autism")
            val faithBased = listOf("christian", "catholic", "baptist", "anglican", "methodist", "islamic", "muslim", "st.", "saint", "crescent").any {
                lowerName.contains(it)
            }
            val faithTradition = when {
                lowerName.contains("islamic") || lowerName.contains("muslim") || lowerName.contains("crescent") -> "Islamic"
                faithBased -> "Christian"
                else -> "Secular"
            }

            val gradeLevels = when (institutionType) {
                "University" -> listOf("Undergraduate", "Postgraduate")
                "Polytechnic" -> listOf("ND / HND Programme")
                "College of Education" -> listOf("NCE Programme", "Undergraduate")
                "Technical / Vocational" -> listOf("ND / HND Programme", "Senior Secondary")
                else -> listOf("Primary", "Junior Secondary")
            }

            val coursesOffered = when (institutionType) {
                "University" -> listOf("Computer Science", "Business Administration", "Sciences & Biotechnology", "Arts & Humanities")
                "Polytechnic" -> listOf("Computer Science", "Electrical Electronics Eng.", "Civil Engineering")
                "College of Education" -> listOf("Education & Pedagogy", "Early Childhood Education")
                else -> emptyList()
            }

            parsed.add(
                School(
                    id = "gmaps_${placeId.take(64)}",
                    adminUid = "",
                    adminName = "",
                    name = name.take(100),
                    description = "Geolocated ${institutionType.lowercase(Locale.US)} discovered via Google Maps Places. Administrators can claim this listing to add full curriculum, courses, fees, and sports.",
                    address = vicinity,
                    latitude = lat,
                    longitude = lng,
                    curriculum = curriculum,
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
                    coursesOffered = coursesOffered,
                    rankingScore = (rating * 18.0).coerceIn(70.0, 96.0),
                    districtRank = 40L + parsed.size,
                    rating = rating,
                    tuitionTier = "$$",
                    capacity = 350L,
                    enrolledCount = 0L,
                    admissionsOpen = true,
                    externalPlaceId = extId,
                    verificationStatus = "INTERNET_MATCHED",
                    verificationSource = "Google Maps Places API"
                )
            )
        }
        return parsed
    }

    fun openGoogleMapsDirections(context: Context, lat: Double, lng: Double, label: String? = null) {
        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedApp = runCatching {
            context.startActivity(mapIntent)
            true
        }.getOrDefault(false)

        if (!launchedApp) {
            val encodedLabel = URLEncoder.encode(label?.ifBlank { "$lat,$lng" } ?: "$lat,$lng", "UTF-8")
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&query=$encodedLabel")
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    fun openGoogleStreetView(context: Context, lat: Double, lng: Double) {
        val svUri = Uri.parse("google.streetview:cbll=$lat,$lng")
        val mapIntent = Intent(Intent.ACTION_VIEW, svUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedApp = runCatching {
            context.startActivity(mapIntent)
            true
        }.getOrDefault(false)

        if (!launchedApp) {
            val webUri = Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lng")
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }
}

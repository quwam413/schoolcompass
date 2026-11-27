package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class CloudSqlConnectionStatus(
    val isConnected: Boolean = false,
    val engine: String = "PostgreSQL",
    val message: String = "Ready to link with Web Cloud SQL API",
    val syncedSchoolsCount: Int = 0,
    val lastSyncedAtMillis: Long? = null
)

class CloudSqlSyncClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val authProvider: () -> FirebaseAuth? = { runCatching { Firebase.auth }.getOrNull() }
) {
    companion object {
        private const val TAG = "CloudSqlSyncClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        fun normalizeSafeBaseUrl(rawUrl: String): String? {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            return runCatching {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase() ?: return@runCatching null
                val host = uri.host?.lowercase() ?: return@runCatching null
                val isLoopbackDev = host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1"
                if (scheme == "https" || (scheme == "http" && isLoopbackDev)) {
                    trimmed
                } else {
                    null
                }
            }.getOrNull()
        }

        fun parseHealthResponse(body: String, statusCode: Int): CloudSqlConnectionStatus {
            if (statusCode !in 200..299) {
                val hint = if (statusCode == 404) {
                    "Web API /api/sql/health returned 404 — add Cloud SQL routes to your Web project"
                } else {
                    "Cloud SQL API returned HTTP $statusCode"
                }
                return CloudSqlConnectionStatus(
                    isConnected = false,
                    message = hint
                )
            }
            return runCatching {
                val json = JSONObject(body)
                val ok = json.optBoolean("ok", true)
                val engine = json.optString("engine", "PostgreSQL").ifBlank { "PostgreSQL" }
                val count = json.optInt("schoolsCount", 0)
                val serverMessage = json.optString("message", "").ifBlank {
                    "Connected to shared $engine database"
                }
                CloudSqlConnectionStatus(
                    isConnected = ok,
                    engine = engine,
                    message = serverMessage,
                    syncedSchoolsCount = count,
                    lastSyncedAtMillis = System.currentTimeMillis()
                )
            }.getOrElse {
                val lower = body.lowercase()
                val diagnostic = if ("accounts.google.com" in lower || "servicelogin" in lower) {
                    "Shared Web URL is behind a Google login wall — set Shared access to Public (or deploy your Web server to Cloud Run) so Android can reach /api/sql/*"
                } else {
                    "Web server returned the SPA page (index.html) instead of JSON — register /api/sql/health before app.get('*') on your Web server and re-share the Web app"
                }
                CloudSqlConnectionStatus(
                    isConnected = false,
                    message = diagnostic
                )
            }
        }

        fun schoolToJson(school: School): JSONObject {
            return JSONObject().apply {
                put("id", school.id)
                put("adminUid", school.adminUid)
                put("adminName", school.adminName)
                put("name", school.name)
                put("description", school.description)
                put("address", school.address)
                put("latitude", school.latitude)
                put("longitude", school.longitude)
                put("curriculum", school.curriculum)
                put("institutionType", school.institutionType)
                put("faithBased", school.faithBased)
                put("faithTradition", school.faithTradition)
                put("specialSchool", school.specialSchool)
                put("specialNeedsSupport", school.specialNeedsSupport)
                put("studentMix", school.studentMix)
                put("gradeLevels", JSONArray(school.gradeLevels))
                put("coursesOffered", JSONArray(school.coursesOffered))
                put("activities", JSONArray(school.activities))
                put("sportsFocus", JSONArray(school.sportsFocus))
                put("feeMin", school.feeMin)
                put("feeMax", school.feeMax)
                put("feeCurrency", school.feeCurrency)
                put("feePeriod", school.feePeriod)
                put("rankingScore", school.rankingScore)
                put("districtRank", school.districtRank)
                put("rating", school.rating)
                put("tuitionTier", school.tuitionTier)
                put("capacity", school.capacity)
                put("enrolledCount", school.enrolledCount)
                put("admissionsOpen", school.admissionsOpen)
                put("contactPhone", school.contactPhone ?: JSONObject.NULL)
                put("externalPlaceId", school.externalPlaceId ?: JSONObject.NULL)
                put("websiteUrl", school.websiteUrl ?: JSONObject.NULL)
                put("verificationStatus", school.verificationStatus)
                put("verificationSource", school.verificationSource ?: JSONObject.NULL)
                put("sourceCitations", JSONArray(school.sourceCitations))
                put("googleMapsPlaceRef", school.googleMapsPlaceRef ?: JSONObject.NULL)
                put("googleMapsUrl", school.googleMapsUrl ?: JSONObject.NULL)
                put("admissionWindow", school.admissionWindow ?: JSONObject.NULL)
                put("boardingOption", school.boardingOption ?: JSONObject.NULL)
                put("bannerImageUrl", school.bannerImageUrl ?: JSONObject.NULL)
                put("photoUrls", JSONArray(school.photoUrls))
                put("extraNotes", school.extraNotes ?: JSONObject.NULL)
                put("lastEnrichedByName", school.lastEnrichedByName ?: JSONObject.NULL)
            }
        }

        fun parseSqlSchoolsJson(rawJson: String): List<School> {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return emptyList()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("schools") ?: obj.optJSONArray("data") ?: JSONArray()
                }
                else -> JSONArray()
            }
            val list = mutableListOf<School>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                val name = item.optString("name", "").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                list.add(
                    School(
                        id = id,
                        adminUid = item.optString("adminUid", ""),
                        adminName = item.optString("adminName", ""),
                        name = name,
                        description = item.optString("description", ""),
                        address = item.optString("address", ""),
                        latitude = item.optDouble("latitude", 0.0),
                        longitude = item.optDouble("longitude", 0.0),
                        curriculum = item.optString("curriculum", "Nigerian"),
                        institutionType = item.optString("institutionType", "Primary & Secondary"),
                        faithBased = item.optBoolean("faithBased", false),
                        faithTradition = item.optString("faithTradition", "Secular"),
                        specialSchool = item.optBoolean("specialSchool", false),
                        specialNeedsSupport = item.optString("specialNeedsSupport", ""),
                        studentMix = item.optString("studentMix", "Mixed"),
                        gradeLevels = item.optStringList("gradeLevels"),
                        coursesOffered = item.optStringList("coursesOffered"),
                        activities = item.optStringList("activities"),
                        sportsFocus = item.optStringList("sportsFocus"),
                        feeMin = item.optLong("feeMin", 0L),
                        feeMax = item.optLong("feeMax", 0L),
                        feeCurrency = item.optString("feeCurrency", "NGN"),
                        feePeriod = item.optString("feePeriod", "Per Term"),
                        rankingScore = item.optDouble("rankingScore", 82.0),
                        districtRank = item.optLong("districtRank", 99L),
                        rating = item.optDouble("rating", 4.4),
                        tuitionTier = item.optString("tuitionTier", "Public / Free"),
                        capacity = item.optLong("capacity", 500L),
                        enrolledCount = item.optLong("enrolledCount", 0L),
                        admissionsOpen = item.optBoolean("admissionsOpen", true),
                        contactPhone = item.optNullableString("contactPhone"),
                        externalPlaceId = item.optNullableString("externalPlaceId"),
                        websiteUrl = item.optNullableString("websiteUrl"),
                        verificationStatus = item.optString("verificationStatus", "VERIFIED_ONLINE"),
                        verificationSource = item.optNullableString("verificationSource"),
                        sourceCitations = item.optStringList("sourceCitations"),
                        googleMapsPlaceRef = item.optNullableString("googleMapsPlaceRef"),
                        googleMapsUrl = item.optNullableString("googleMapsUrl"),
                        admissionWindow = item.optNullableString("admissionWindow"),
                        boardingOption = item.optNullableString("boardingOption"),
                        bannerImageUrl = item.optNullableString("bannerImageUrl"),
                        photoUrls = item.optStringList("photoUrls"),
                        extraNotes = item.optNullableString("extraNotes"),
                        lastEnrichedByName = item.optNullableString("lastEnrichedByName")
                    )
                )
            }
            return list
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }
        }

        private fun JSONObject.optStringList(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "").trim()
                if (v.isNotEmpty() && v != "null") result.add(v)
            }
            return result
        }
    }

    private suspend fun getFirebaseBearerToken(): String? {
        return runCatching {
            authProvider()?.currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull()
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        bodyJson: String? = null,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val safeBase = normalizeSafeBaseUrl(baseUrl)
            ?: throw IllegalArgumentException("Invalid Cloud SQL backend URL")
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val token = getFirebaseBearerToken()

        val builder = Request.Builder()
            .url("$safeBase$cleanPath")
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val requestBody = bodyJson?.toRequestBody(JSON_MEDIA_TYPE)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            else -> builder.method(method.uppercase(), requestBody)
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            response.code to responseBody
        }
    }

    suspend fun checkHealth(
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): CloudSqlConnectionStatus {
        return runCatching {
            val (code, body) = executeRequest("GET", "/api/sql/health", baseUrl = baseUrl)
            parseHealthResponse(body, code)
        }.getOrElse { e ->
            Log.w(TAG, "Cloud SQL health check unreachable", e)
            CloudSqlConnectionStatus(
                isConnected = false,
                message = e.localizedMessage ?: "Cloud SQL backend unreachable"
            )
        }
    }

    suspend fun fetchSchoolsFromSql(
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Int? = null,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<List<School>> = runCatching {
        val query = buildString {
            val params = mutableListOf<String>()
            if (lat != null && lng != null) {
                params.add("lat=$lat")
                params.add("lng=$lng")
            }
            if (radiusKm != null) {
                params.add("radiusKm=$radiusKm")
            }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
        val (code, body) = executeRequest("GET", "/api/sql/schools$query", baseUrl = baseUrl)
        check(code in 200..299) { "HTTP $code fetching Cloud SQL schools" }
        val trimmed = body.trim()
        check(trimmed.startsWith("{") || trimmed.startsWith("[")) {
            "Expected JSON from /api/sql/schools"
        }
        parseSqlSchoolsJson(trimmed)
    }

    suspend fun syncSchoolsBatchToSql(
        schools: List<School>,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<Int> = runCatching {
        if (schools.isEmpty()) return@runCatching 0
        val payload = JSONObject().apply {
            val arr = JSONArray()
            schools.take(100).forEach { arr.put(schoolToJson(it)) }
            put("schools", arr)
        }
        val (code, body) = executeRequest(
            method = "POST",
            path = "/api/sql/schools/batch",
            bodyJson = payload.toString(),
            baseUrl = baseUrl
        )
        check(code in 200..299) { "HTTP $code syncing Cloud SQL schools" }
        runCatching { JSONObject(body).optInt("upserted", schools.size) }.getOrDefault(schools.size)
    }

    suspend fun syncUserProfileToSql(
        uid: String,
        role: String,
        displayName: String,
        email: String,
        phone: String?,
        homeAddress: String?,
        homeLat: Double,
        homeLng: Double,
        searchRadiusKm: Int,
        childSummary: String?,
        bannerImageUrl: String? = null,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<Unit> = runCatching {
        val payload = JSONObject().apply {
            put("userId", uid)
            put("role", role)
            put("displayName", displayName)
            put("email", email)
            put("phone", phone ?: JSONObject.NULL)
            put("homeAddress", homeAddress ?: JSONObject.NULL)
            put("homeLat", homeLat)
            put("homeLng", homeLng)
            put("searchRadiusKm", searchRadiusKm)
            put("childSummary", childSummary ?: JSONObject.NULL)
            put("bannerImageUrl", bannerImageUrl ?: JSONObject.NULL)
        }
        val (code, _) = executeRequest("PUT", "/api/sql/users/me", payload.toString(), baseUrl)
        check(code in 200..299) { "HTTP $code syncing profile to Cloud SQL" }
    }

    suspend fun syncEnrollmentToSql(
        enrollmentId: String,
        schoolId: String,
        schoolName: String,
        adminUid: String,
        parentUid: String,
        parentName: String,
        studentName: String,
        gradeApplying: String,
        notes: String?,
        status: String,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<Unit> = runCatching {
        val payload = JSONObject().apply {
            put("id", enrollmentId)
            put("schoolId", schoolId)
            put("schoolName", schoolName)
            put("adminUid", adminUid)
            put("parentUid", parentUid)
            put("parentName", parentName)
            put("studentName", studentName)
            put("gradeApplying", gradeApplying)
            put("notes", notes ?: JSONObject.NULL)
            put("status", status)
        }
        val (code, _) = executeRequest("POST", "/api/sql/enrollments", payload.toString(), baseUrl)
        check(code in 200..299) { "HTTP $code syncing enrollment to Cloud SQL" }
    }

    suspend fun syncMessageToSql(
        conversation: Conversation,
        messageId: String,
        senderUid: String,
        senderName: String,
        senderRole: String,
        text: String,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<Unit> = runCatching {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("schoolId", conversation.schoolId)
            put("schoolName", conversation.schoolName)
            put("parentUid", conversation.parentUid)
            put("parentName", conversation.parentName)
            put("adminUid", conversation.adminUid)
            put("adminName", conversation.adminName)
            put("senderUid", senderUid)
            put("senderName", senderName)
            put("senderRole", senderRole)
            put("text", text)
        }
        val (code, _) = executeRequest(
            method = "POST",
            path = "/api/sql/conversations/${conversation.id}/messages",
            bodyJson = payload.toString(),
            baseUrl = baseUrl
        )
        check(code in 200..299) { "HTTP $code syncing message to Cloud SQL" }
    }

    suspend fun recordVisitToSql(
        visitId: String,
        userId: String,
        userEmail: String,
        displayName: String,
        role: String,
        platform: String = "android",
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<AppVisitStats?> = runCatching {
        val payload = JSONObject().apply {
            put("visitId", visitId)
            put("userId", userId)
            put("userEmail", userEmail)
            put("displayName", displayName)
            put("role", role)
            put("platform", platform)
        }
        val (code, body) = executeRequest("POST", "/api/sql/visits", payload.toString(), baseUrl)
        check(code in 200..299) { "HTTP $code recording visit to Cloud SQL" }
        parseVisitStatsJson(body)
    }

    suspend fun fetchAdminOverviewFromSql(
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<CloudSqlAdminOverview> = runCatching {
        val (code, body) = executeRequest("GET", "/api/sql/admin/overview", baseUrl = baseUrl)
        check(code in 200..299) { "HTTP $code fetching Cloud SQL admin overview" }
        parseAdminOverviewJson(body)
    }

    suspend fun setDelegatedAdminOnSql(
        targetUserId: String,
        targetEmail: String,
        targetDisplayName: String,
        grant: Boolean,
        baseUrl: String = BuildConfig.SKOLQUIK_API_BASE_URL
    ): Result<Unit> = runCatching {
        val payload = JSONObject().apply {
            put("userId", targetUserId)
            put("email", targetEmail)
            put("displayName", targetDisplayName)
            put("action", if (grant) "grant" else "revoke")
        }
        val (code, _) = executeRequest("POST", "/api/sql/admin/roles", payload.toString(), baseUrl)
        check(code in 200..299) { "HTTP $code updating delegated admin on Cloud SQL" }
    }
}

data class CloudSqlAdminOverview(
    val totalVisits: Long = 0L,
    val androidVisits: Long = 0L,
    val webVisits: Long = 0L,
    val totalUsersCount: Int = 0,
    val individualsCount: Int = 0,
    val schoolAccountsCount: Int = 0,
    val schoolsCount: Int = 0
)

fun parseVisitStatsJson(rawJson: String): AppVisitStats? {
    val trimmed = rawJson.trim()
    if (!trimmed.startsWith("{")) return null
    return runCatching {
        val obj = JSONObject(trimmed)
        val visitsObj = obj.optJSONObject("visits") ?: obj
        AppVisitStats(
            id = "visits_summary",
            totalVisits = visitsObj.optLong("totalVisits", 0L),
            androidVisits = visitsObj.optLong("androidVisits", 0L),
            webVisits = visitsObj.optLong("webVisits", 0L)
        )
    }.getOrNull()
}

fun parseAdminOverviewJson(rawJson: String): CloudSqlAdminOverview {
    val trimmed = rawJson.trim()
    check(trimmed.startsWith("{")) { "Expected JSON from /api/sql/admin/overview" }
    val obj = JSONObject(trimmed)
    val visitsObj = obj.optJSONObject("visits") ?: obj
    return CloudSqlAdminOverview(
        totalVisits = visitsObj.optLong("totalVisits", obj.optLong("totalVisits", 0L)),
        androidVisits = visitsObj.optLong("androidVisits", obj.optLong("androidVisits", 0L)),
        webVisits = visitsObj.optLong("webVisits", obj.optLong("webVisits", 0L)),
        totalUsersCount = obj.optInt("totalUsersCount", 0),
        individualsCount = obj.optInt("individualsCount", 0),
        schoolAccountsCount = obj.optInt("schoolAccountsCount", 0),
        schoolsCount = obj.optInt("schoolsCount", 0)
    )
}


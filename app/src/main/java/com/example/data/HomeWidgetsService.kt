package com.example.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class GoogleNewsTopic(val label: String) {
    EDUCATION("Education"),
    NIGERIA("Nigeria News"),
    SCIENCE_TECH("Science & Tech"),
    INNOVATION("Innovation")
}

data class GoogleNewsArticle(
    val id: String,
    val title: String,
    val source: String,
    val publishedAt: String,
    val url: String,
    val topic: GoogleNewsTopic
)

data class DailyForecastItem(
    val dayLabel: String,
    val weatherCode: Int,
    val minTempC: Int,
    val maxTempC: Int
)

data class PixelWeatherSnapshot(
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Int,
    val apparentTempC: Int,
    val highTempC: Int,
    val lowTempC: Int,
    val humidityPct: Int,
    val windKmh: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val conditionLabel: String,
    val dailyForecast: List<DailyForecastItem>
)

object HomeWidgetsService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val rssUrlsByTopic = mapOf(
        GoogleNewsTopic.EDUCATION to "https://news.google.com/rss/search?q=education+schools+universities&hl=en-US&gl=US&ceid=US:en",
        GoogleNewsTopic.NIGERIA to "https://news.google.com/rss?hl=en-NG&gl=NG&ceid=NG:en",
        GoogleNewsTopic.SCIENCE_TECH to "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-US&gl=US&ceid=US:en",
        GoogleNewsTopic.INNOVATION to "https://news.google.com/rss/search?q=science+technology+innovation+AI&hl=en-US&gl=US&ceid=US:en"
    )

    private val nigeriaPlatformRssFeeds = listOf(
        "https://news.google.com/rss/search?q=Nigeria+(Punch+OR+%22Channels+TV%22+OR+%22Premium+Times%22+OR+Vanguard+OR+%22Arise+News%22)&hl=en-NG&gl=NG&ceid=NG:en",
        "https://www.channelstv.com/feed/",
        "https://www.premiumtimesng.com/feed/",
        "https://punchng.com/feed/"
    )

    private const val GOOGLE_SCIENCE_RSS =
        "https://news.google.com/rss/headlines/section/topic/SCIENCE?hl=en-US&gl=US&ceid=US:en"

    suspend fun fetchGoogleNews(topicFilter: GoogleNewsTopic? = null): Result<List<GoogleNewsArticle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val topicsToFetch = if (topicFilter != null) {
                    listOf(topicFilter)
                } else {
                    GoogleNewsTopic.entries
                }

                val topicBatches = topicsToFetch.map { topic ->
                    async {
                        val primaryUrl = rssUrlsByTopic.getValue(topic)
                        val primaryArticles = fetchRssUrl(primaryUrl, topic)
                        when (topic) {
                            GoogleNewsTopic.SCIENCE_TECH -> {
                                val scienceArticles = fetchRssUrl(GOOGLE_SCIENCE_RSS, GoogleNewsTopic.SCIENCE_TECH)
                                interleaveArticles(primaryArticles, scienceArticles).take(10)
                            }
                            GoogleNewsTopic.NIGERIA -> {
                                val platformBatches = nigeriaPlatformRssFeeds.map { feedUrl ->
                                    async { fetchRssUrl(feedUrl, GoogleNewsTopic.NIGERIA) }
                                }.awaitAll()
                                interleaveArticleLists(listOf(primaryArticles) + platformBatches).take(12)
                            }
                            else -> primaryArticles.take(10)
                        }
                    }
                }.awaitAll()

                val combined = interleaveArticleLists(topicBatches)
                    .distinctBy { it.title.lowercase(Locale.US) }
                check(combined.isNotEmpty()) { "No Google News articles returned" }
                combined
            }
        }

    private fun fetchRssUrl(url: String, topic: GoogleNewsTopic): List<GoogleNewsArticle> {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SkolquikAndroid/2.0 (GoogleNewsReader)")
                .get()
                .build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) emptyList() else parseGoogleNewsRssXml(body, topic)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseGoogleNewsRssXml(xml: String, topic: GoogleNewsTopic): List<GoogleNewsArticle> {
        val articles = mutableListOf<GoogleNewsArticle>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideItem = false
            var title = ""
            var link = ""
            var pubDate = ""
            var source = ""
            var idx = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase(Locale.US).orEmpty()
                        if (tag == "item") {
                            insideItem = true
                            title = ""
                            link = ""
                            pubDate = ""
                            source = ""
                        } else if (insideItem) {
                            when (tag) {
                                "title" -> title = parser.nextText().trim()
                                "link" -> link = parser.nextText().trim()
                                "pubdate" -> pubDate = parser.nextText().trim()
                                "source" -> source = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name?.equals("item", ignoreCase = true) == true && insideItem) {
                            insideItem = false
                            if (title.isNotBlank() && link.startsWith("http")) {
                                val resolvedSource = source.ifBlank {
                                    title.substringAfterLast(" - ", "").takeIf { title.contains(" - ") }?.trim()
                                        ?: inferNigerianPublisherFromUrl(link)
                                }.ifBlank { "Google News" }
                                val cleanTitle = if (title.endsWith(" - $resolvedSource")) {
                                    title.removeSuffix(" - $resolvedSource").trim()
                                } else {
                                    title
                                }
                                articles.add(
                                    GoogleNewsArticle(
                                        id = "${topic.name}_${idx++}_${cleanTitle.hashCode()}",
                                        title = cleanTitle,
                                        source = resolvedSource,
                                        publishedAt = formatRssDate(pubDate),
                                        url = link,
                                        topic = topic
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Fall back cleanly if RSS payload is malformed
        }
        return articles
    }

    private fun inferNigerianPublisherFromUrl(link: String): String {
        val lower = link.lowercase(Locale.US)
        return when {
            "channelstv.com" in lower -> "Channels TV"
            "premiumtimesng.com" in lower -> "Premium Times"
            "punchng.com" in lower -> "The Punch"
            "vanguardngr.com" in lower -> "Vanguard Nigeria"
            "guardian.ng" in lower -> "The Guardian NG"
            "arise.tv" in lower -> "Arise News"
            "thecable.ng" in lower -> "TheCable"
            else -> ""
        }
    }

    private fun formatRssDate(raw: String): String {
        if (raw.isBlank()) return "Today"
        return try {
            val inFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            val parsed: Date = inFmt.parse(raw) ?: return raw.take(16)
            val diffMs = (System.currentTimeMillis() - parsed.time).coerceAtLeast(0L)
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            when {
                mins < 60 -> "${mins.coerceAtLeast(1)}m ago"
                hours < 24 -> "${hours}h ago"
                else -> SimpleDateFormat("MMM d", Locale.US).format(parsed)
            }
        } catch (_: Exception) {
            raw.take(16)
        }
    }

    private fun interleaveArticles(
        first: List<GoogleNewsArticle>,
        second: List<GoogleNewsArticle>
    ): List<GoogleNewsArticle> = interleaveArticleLists(listOf(first, second))

    private fun interleaveArticleLists(lists: List<List<GoogleNewsArticle>>): List<GoogleNewsArticle> {
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        val out = mutableListOf<GoogleNewsArticle>()
        for (i in 0 until maxLen) {
            lists.forEach { batch ->
                if (i < batch.size) out.add(batch[i])
            }
        }
        return out
    }

    suspend fun fetchWeatherForLocation(
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): Result<PixelWeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                "&timezone=auto&forecast_days=3"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SkolquikAndroid/2.0 (PixelWeatherWidget)")
                .get()
                .build()
            httpClient.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful && body.isNotBlank()) {
                    "Weather service returned HTTP ${response.code}"
                }
                parseOpenMeteoWeatherJson(body, locationLabel, latitude, longitude)
            }
        }
    }

    internal fun parseOpenMeteoWeatherJson(
        jsonBody: String,
        locationLabel: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ): PixelWeatherSnapshot {
        val root = JSONObject(jsonBody)
        val current = root.getJSONObject("current")
        val tempC = current.optDouble("temperature_2m", 26.0).roundToInt()
        val apparentC = current.optDouble("apparent_temperature", tempC.toDouble()).roundToInt()
        val code = current.optInt("weather_code", 0)
        val isDay = current.optInt("is_day", 1) == 1
        val windKmh = current.optDouble("wind_speed_10m", 0.0).roundToInt()
        val humidity = current.optInt("relative_humidity_2m", 65)

        val daily = root.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        val maxTemps = daily?.optJSONArray("temperature_2m_max")
        val minTemps = daily?.optJSONArray("temperature_2m_min")
        val dailyCodes = daily?.optJSONArray("weather_code")

        val forecast = buildList {
            val count = minOf(dates?.length() ?: 0, 3)
            for (i in 0 until count) {
                val rawDate = dates?.optString(i).orEmpty()
                val dayLabel = if (i == 0) "Today" else formatDayLabel(rawDate)
                val minT = minTemps?.optDouble(i, tempC.toDouble())?.roundToInt() ?: tempC
                val maxT = maxTemps?.optDouble(i, tempC.toDouble())?.roundToInt() ?: tempC
                val dayCode = dailyCodes?.optInt(i, code) ?: code
                add(
                    DailyForecastItem(
                        dayLabel = dayLabel,
                        weatherCode = dayCode,
                        minTempC = minT,
                        maxTempC = maxT
                    )
                )
            }
        }

        val todayHigh = forecast.firstOrNull()?.maxTempC ?: tempC
        val todayLow = forecast.firstOrNull()?.minTempC ?: tempC

        return PixelWeatherSnapshot(
            locationLabel = locationLabel.ifBlank { "Current Location" },
            latitude = latitude,
            longitude = longitude,
            temperatureC = tempC,
            apparentTempC = apparentC,
            highTempC = todayHigh,
            lowTempC = todayLow,
            humidityPct = humidity,
            windKmh = windKmh,
            weatherCode = code,
            isDay = isDay,
            conditionLabel = wmoConditionLabel(code, isDay),
            dailyForecast = forecast
        )
    }

    private fun formatDayLabel(isoDate: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate) ?: return isoDate
            SimpleDateFormat("EEE", Locale.US).format(parsed)
        } catch (_: Exception) {
            isoDate.takeLast(5)
        }
    }

    fun wmoConditionLabel(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Sunny" else "Clear Night"
        1 -> if (isDay) "Mostly Clear" else "Mostly Clear Night"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        85, 86 -> "Snow Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Fair"
    }
}

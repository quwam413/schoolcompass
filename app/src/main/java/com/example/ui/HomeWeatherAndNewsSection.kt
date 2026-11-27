package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.GoogleNewsArticle
import com.example.data.GoogleNewsTopic
import com.example.data.PixelWeatherSnapshot
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch

@Composable
fun PixelWeatherWidgetCard(
    weatherState: UiState<PixelWeatherSnapshot>,
    locationLabel: String,
    onRefreshWeather: () -> Unit,
    onUseGpsLocation: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val fusedClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    fun requestCurrentGps() {
        val cts = CancellationTokenSource()
        fun applyGpsWithWords(lat: Double, lng: Double) {
            scope.launch {
                val placeLabel = com.example.data.GoogleMapsPlatformService.resolveHumanReadableLocation(
                    context = context,
                    latitude = lat,
                    longitude = lng
                )
                onUseGpsLocation(lat, lng, placeLabel)
            }
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    applyGpsWithWords(loc.latitude, loc.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            applyGpsWithWords(last.latitude, last.longitude)
                        } else {
                            onRefreshWeather()
                        }
                    }
                }
            }
            .addOnFailureListener {
                onRefreshWeather()
            }
    }

    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            requestCurrentGps()
        } else {
            onRefreshWeather()
        }
    }

    val isNightSnapshot = (weatherState as? UiState.Success)?.data?.isDay == false
    val backgroundBrush = if (isNightSnapshot) {
        Brush.linearGradient(
            listOf(Color(0xFF192744), Color(0xFF283E6B))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFD3E3FD), Color(0xFFA8C7FA))
        )
    }
    val contentColor = if (isNightSnapshot) Color(0xFFF3F6FC) else Color(0xFF0B1E3D)
    val subContentColor = if (isNightSnapshot) Color(0xFFD0DCF5) else Color(0xFF233B61)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundBrush)
            .testTag("pixel_weather_widget")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = (weatherState as? UiState.Success)?.data?.locationLabel ?: locationLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("pixel_weather_location")
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = contentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Pixel Weather",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                                requestCurrentGps()
                            } else {
                                gpsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pixel_weather_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Use device GPS for weather",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onRefreshWeather,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pixel_weather_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh weather",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            when (weatherState) {
                UiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = contentColor
                        )
                        Text(
                            text = "Updating local Pixel Weather for $locationLabel…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subContentColor
                        )
                    }
                }
                is UiState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weather unavailable offline • Tap refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = subContentColor,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefreshWeather) {
                            Text("Retry", color = contentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is UiState.Success -> {
                    val w = weatherState.data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = weatherCodeIcon(w.weatherCode, w.isDay),
                                    contentDescription = w.conditionLabel,
                                    tint = weatherCodeTint(w.weatherCode, w.isDay, contentColor),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Text(
                                text = "${w.temperatureC}°",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                                modifier = Modifier.testTag("pixel_weather_temp")
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = w.conditionLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                text = "Feels ${w.apparentTempC}° • H:${w.highTempC}° L:${w.lowTempC}°",
                                style = MaterialTheme.typography.labelMedium,
                                color = subContentColor
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WaterDrop,
                                        contentDescription = null,
                                        tint = subContentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${w.humidityPct}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subContentColor
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Air,
                                        contentDescription = null,
                                        tint = subContentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${w.windKmh} km/h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subContentColor
                                    )
                                }
                            }
                        }
                    }

                    if (w.dailyForecast.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            w.dailyForecast.forEach { day ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = contentColor.copy(alpha = 0.09f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = day.dayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                        Icon(
                                            imageVector = weatherCodeIcon(day.weatherCode, true),
                                            contentDescription = null,
                                            tint = weatherCodeTint(day.weatherCode, true, contentColor),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "${day.maxTempC}°/${day.minTempC}°",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = subContentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleNewsHomepageSection(
    newsState: UiState<List<GoogleNewsArticle>>,
    selectedTopic: GoogleNewsTopic?,
    onSelectTopic: (GoogleNewsTopic?) -> Unit,
    onRefreshNews: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_news_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A73E8)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Newspaper,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Google News",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Education, Nigeria News, Science & Tech, Innovation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRefreshNews,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("google_news_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh Google News"
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedTopic == null,
                    onClick = { onSelectTopic(null) },
                    label = { Text("All Topics") },
                    modifier = Modifier.testTag("news_topic_all")
                )
            }
            items(GoogleNewsTopic.entries) { topic ->
                FilterChip(
                    selected = selectedTopic == topic,
                    onClick = { onSelectTopic(topic) },
                    label = { Text(topic.label) },
                    modifier = Modifier.testTag("news_topic_${topic.name.lowercase()}")
                )
            }
        }

        when (newsState) {
            UiState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "Loading Google News headlines…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is UiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unable to load Google News right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefreshNews) {
                            Text("Retry")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val filteredArticles = remember(newsState.data, selectedTopic) {
                    if (selectedTopic == null) {
                        newsState.data
                    } else {
                        newsState.data.filter { it.topic == selectedTopic }
                    }
                }
                if (filteredArticles.isEmpty()) {
                    Text(
                        text = "No stories available for this topic yet. Tap refresh to check again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_news_list")
                    ) {
                        items(filteredArticles.take(15), key = { it.id }) { article ->
                            GoogleNewsArticleCard(
                                article = article,
                                onClick = { runCatching { uriHandler.openUri(article.url) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleNewsArticleCard(
    article: GoogleNewsArticle,
    onClick: () -> Unit
) {
    val badgeColor = when (article.topic) {
        GoogleNewsTopic.EDUCATION -> Color(0xFF1A73E8)
        GoogleNewsTopic.NIGERIA -> Color(0xFF008751)
        GoogleNewsTopic.SCIENCE_TECH -> Color(0xFF0F9D58)
        GoogleNewsTopic.INNOVATION -> Color(0xFF8E24AA)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .width(274.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("google_news_card_${article.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = article.topic.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = article.publishedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                minLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open article on Google News",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun weatherCodeIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2 -> Icons.Filled.WbCloudy
    3, 45, 48 -> Icons.Filled.Cloud
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.WaterDrop
}

private fun weatherCodeTint(code: Int, isDay: Boolean, fallback: Color): Color = when {
    (code == 0 || code == 1) && isDay -> Color(0xFFF59E0B)
    code in listOf(95, 96, 99) -> Color(0xFF7C3AED)
    code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> Color(0xFF0284C7)
    else -> fallback
}

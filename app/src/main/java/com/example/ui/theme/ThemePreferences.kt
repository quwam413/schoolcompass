package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

private val Context.themeDataStore by preferencesDataStore(name = "skolquik_theme_settings")

enum class ThemeAppearanceMode(
    val label: String,
    val description: String
) {
    WHITE_DAY("White (Day)", "Crisp pure white canvas at all times"),
    NIGHT_DARK("Night (Dark)", "Sleek dark obsidian canvas at all times"),
    SYSTEM("Phone System", "Follows your Android device day & night setting"),
    SUNRISE_SUNSET("Sunrise & Sunset", "White by day, automatically switches dark at sunset")
}

enum class HighlightHue(
    val label: String,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color
) {
    SKOLQUIK_BLUE(
        label = "Skolquik Blue",
        lightPrimary = Color(0xFF0B66D1),
        lightSecondary = Color(0xFFE07A12),
        lightContainer = Color(0xFFEBF3FE),
        lightOnContainer = Color(0xFF08397A),
        darkPrimary = Color(0xFF7AB8FF),
        darkSecondary = Color(0xFFFFB55E),
        darkContainer = Color(0xFF173154),
        darkOnContainer = Color(0xFFD9EAFF)
    ),
    EMERALD_MINT(
        label = "Emerald Mint",
        lightPrimary = Color(0xFF0E7C66),
        lightSecondary = Color(0xFF1A9E84),
        lightContainer = Color(0xFFE8F6F2),
        lightOnContainer = Color(0xFF08473A),
        darkPrimary = Color(0xFF6BE0C3),
        darkSecondary = Color(0xFF93EBD5),
        darkContainer = Color(0xFF143C33),
        darkOnContainer = Color(0xFFD1FAF0)
    ),
    AMBER_EMBER(
        label = "Amber Gold",
        lightPrimary = Color(0xFFC26A0A),
        lightSecondary = Color(0xFF0B66D1),
        lightContainer = Color(0xFFFFF3E4),
        lightOnContainer = Color(0xFF663400),
        darkPrimary = Color(0xFFFFBE6E),
        darkSecondary = Color(0xFF82BAFF),
        darkContainer = Color(0xFF472C0D),
        darkOnContainer = Color(0xFFFFE5C7)
    ),
    INDIGO_SLATE(
        label = "Indigo Slate",
        lightPrimary = Color(0xFF3949AB),
        lightSecondary = Color(0xFF5C6BC0),
        lightContainer = Color(0xFFECEFFD),
        lightOnContainer = Color(0xFF1A237E),
        darkPrimary = Color(0xFF9FA8FF),
        darkSecondary = Color(0xFFC0C6FF),
        darkContainer = Color(0xFF212859),
        darkOnContainer = Color(0xFFE3E7FF)
    ),
    CORAL_ROSE(
        label = "Coral Rose",
        lightPrimary = Color(0xFFC43B5C),
        lightSecondary = Color(0xFFE05D7C),
        lightContainer = Color(0xFFFDECEF),
        lightOnContainer = Color(0xFF6E1229),
        darkPrimary = Color(0xFFFF98AE),
        darkSecondary = Color(0xFFFFB8C8),
        darkContainer = Color(0xFF4D1928),
        darkOnContainer = Color(0xFFFFD9E2)
    ),
    GRAPHITE_INK(
        label = "Graphite Ink",
        lightPrimary = Color(0xFF27303F),
        lightSecondary = Color(0xFF4E5B70),
        lightContainer = Color(0xFFEEF1F5),
        lightOnContainer = Color(0xFF151B24),
        darkPrimary = Color(0xFFD0D8E6),
        darkSecondary = Color(0xFFAAB6C9),
        darkContainer = Color(0xFF252D3B),
        darkOnContainer = Color(0xFFE9EEF5)
    )
}

data class ThemeSettings(
    val appearanceMode: ThemeAppearanceMode = ThemeAppearanceMode.WHITE_DAY,
    val highlightHue: HighlightHue = HighlightHue.SKOLQUIK_BLUE,
    val autoOnlineDiscovery: Boolean = true,
    val preciseLocationForMap: Boolean = true
)

data class SolarSchedule(
    val sunriseMinutesFromMidnight: Int,
    val sunsetMinutesFromMidnight: Int,
    val isCurrentlyNight: Boolean
) {
    fun formattedSunrise(): String = formatMinutes(sunriseMinutesFromMidnight)
    fun formattedSunset(): String = formatMinutes(sunsetMinutesFromMidnight)

    private fun formatMinutes(totalMinutes: Int): String {
        val clamped = ((totalMinutes % 1440) + 1440) % 1440
        val h = clamped / 60
        val m = clamped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }
}

object SolarThemeCalculator {
    /**
     * Approximates local sunrise and sunset minutes from midnight using NOAA solar equations
     * for the supplied latitude/longitude and calendar date/time.
     */
    fun calculateSchedule(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): SolarSchedule {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR).coerceIn(1, 366)
        val tzOffsetHours = (calendar.timeZone.getOffset(calendar.timeInMillis) / 3_600_000.0)
        val latRad = Math.toRadians(latitude.coerceIn(-66.0, 66.0))

        // Fractional year in radians (gamma)
        val gamma = (2.0 * Math.PI / 365.0) * (dayOfYear - 1)
        val decl = 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )

        val zenith = Math.toRadians(90.833)
        val cosHa = ((cos(zenith) / (cos(latRad) * cos(decl))) - tan(latRad) * tan(decl))
            .coerceIn(-0.98, 0.98)
        val haDeg = Math.toDegrees(acos(cosHa))

        val solarNoonMinutes = 720.0 - 4.0 * longitude - eqTime + tzOffsetHours * 60.0
        val sunriseMinutes = (solarNoonMinutes - 4.0 * haDeg).toInt().coerceIn(240, 540)
        val sunsetMinutes = (solarNoonMinutes + 4.0 * haDeg).toInt().coerceIn(960, 1320)

        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val isNight = nowMinutes < sunriseMinutes || nowMinutes >= sunsetMinutes
        return SolarSchedule(
            sunriseMinutesFromMidnight = sunriseMinutes,
            sunsetMinutesFromMidnight = sunsetMinutes,
            isCurrentlyNight = isNight
        )
    }
}

class ThemePreferencesStore(private val context: Context) {
    private object Keys {
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val HIGHLIGHT_HUE = stringPreferencesKey("highlight_hue")
        val AUTO_ONLINE_DISCOVERY = booleanPreferencesKey("auto_online_discovery")
        val PRECISE_LOCATION = booleanPreferencesKey("precise_location")
    }

    val settingsFlow: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        val mode = prefs[Keys.APPEARANCE_MODE]?.let { raw ->
            ThemeAppearanceMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeAppearanceMode.WHITE_DAY
        val hue = prefs[Keys.HIGHLIGHT_HUE]?.let { raw ->
            HighlightHue.entries.firstOrNull { it.name == raw }
        } ?: HighlightHue.SKOLQUIK_BLUE
        ThemeSettings(
            appearanceMode = mode,
            highlightHue = hue,
            autoOnlineDiscovery = prefs[Keys.AUTO_ONLINE_DISCOVERY] ?: true,
            preciseLocationForMap = prefs[Keys.PRECISE_LOCATION] ?: true
        )
    }

    suspend fun setAppearanceMode(mode: ThemeAppearanceMode) {
        context.themeDataStore.edit { it[Keys.APPEARANCE_MODE] = mode.name }
    }

    suspend fun setHighlightHue(hue: HighlightHue) {
        context.themeDataStore.edit { it[Keys.HIGHLIGHT_HUE] = hue.name }
    }

    suspend fun setAutoOnlineDiscovery(enabled: Boolean) {
        context.themeDataStore.edit { it[Keys.AUTO_ONLINE_DISCOVERY] = enabled }
    }

    suspend fun setPreciseLocation(enabled: Boolean) {
        context.themeDataStore.edit { it[Keys.PRECISE_LOCATION] = enabled }
    }
}

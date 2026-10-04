package com.andermuchael.launcherandroid.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

data class WeatherDay(
    val date: String,
    val min: Double,
    val max: Double,
    val code: Int,
    val rainProbability: Int
)

data class WeatherHour(
    val time: String,
    val temperature: Double,
    val code: Int,
    val rainProbability: Int
)

data class WeatherData(
    val city: String,
    val temperature: Double,
    val apparent: Double,
    val code: Int,
    val days: List<WeatherDay>,
    val hours: List<WeatherHour>
)

private object WeatherMemoryCache {
    var city: String = ""
    var data: WeatherData? = null
    var loadedAt: Long = 0L
}

fun fetchWeather(city: String): WeatherData? {
    val normalizedCity = city.trim()
    val now = System.currentTimeMillis()
    if (normalizedCity.isNotBlank() &&
        WeatherMemoryCache.city.equals(normalizedCity, ignoreCase = true) &&
        WeatherMemoryCache.data != null &&
        now - WeatherMemoryCache.loadedAt < 50 * 60 * 1000L
    ) {
        return WeatherMemoryCache.data
    }
    return runCatching {
        val encoded = URLEncoder.encode(city.trim(), "UTF-8")
        val geoUrl = URL(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=pt&format=json"
        )
        val geo = httpGet(geoUrl) ?: return null
        val results = geo.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val place = results.getJSONObject(0)
        val latitude = place.getDouble("latitude")
        val longitude = place.getDouble("longitude")
        val resolvedCity = place.optString("name", city.trim())
        val country = place.optString("country", "")
        val displayName = if (country.isBlank()) resolvedCity else "$resolvedCity, $country"

        val forecastUrl = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=5"
        )
        val forecast = httpGet(forecastUrl) ?: return null
        val current = forecast.optJSONObject("current") ?: return null
        val hourly = forecast.optJSONObject("hourly") ?: return null
        val hourlyTimes = hourly.optJSONArray("time") ?: return null
        val hourlyTemps = hourly.optJSONArray("temperature_2m") ?: return null
        val hourlyCodes = hourly.optJSONArray("weather_code") ?: return null
        val hourlyRain = hourly.optJSONArray("precipitation_probability") ?: return null
        val daily = forecast.optJSONObject("daily") ?: return null
        val dates = daily.optJSONArray("time") ?: return null
        val max = daily.optJSONArray("temperature_2m_max") ?: return null
        val min = daily.optJSONArray("temperature_2m_min") ?: return null
        val codes = daily.optJSONArray("weather_code") ?: return null
        val rain = daily.optJSONArray("precipitation_probability_max") ?: return null

        val currentTime = current.optString("time")
        val firstHourIndex = hourlyTimes.let { times ->
            (0 until times.length()).firstOrNull { times.optString(it) >= currentTime } ?: 0
        }
        val hours = buildList {
            for (i in firstHourIndex until minOf(firstHourIndex + 12, hourlyTimes.length())) {
                add(
                    WeatherHour(
                        time = hourlyTimes.optString(i),
                        temperature = hourlyTemps.optDouble(i),
                        code = hourlyCodes.optInt(i),
                        rainProbability = hourlyRain.optInt(i)
                    )
                )
            }
        }

        val days = buildList {
            for (i in 0 until minOf(5, dates.length())) {
                add(
                    WeatherDay(
                        date = dates.optString(i),
                        min = min.optDouble(i),
                        max = max.optDouble(i),
                        code = codes.optInt(i),
                        rainProbability = rain.optInt(i)
                    )
                )
            }
        }
        WeatherData(
            city = displayName,
            temperature = current.optDouble("temperature_2m"),
            apparent = current.optDouble("apparent_temperature"),
            code = current.optInt("weather_code"),
            days = days,
            hours = hours
        ).also {
            WeatherMemoryCache.city = normalizedCity
            WeatherMemoryCache.data = it
            WeatherMemoryCache.loadedAt = System.currentTimeMillis()
        }
    }.getOrNull()
}

private fun httpGet(url: URL): JSONObject? {
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 10000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        connect()
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally {
        connection.disconnect()
    }
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Céu limpo"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    51, 53, 55 -> "Garoa"
    56, 57 -> "Garoa congelante"
    61, 63, 65 -> "Chuva"
    66, 67 -> "Chuva congelante"
    71, 73, 75, 77 -> "Neve"
    80, 81, 82 -> "Pancadas de chuva"
    85, 86 -> "Pancadas de neve"
    95 -> "Trovoada"
    96, 99 -> "Trovoada com granizo"
    else -> "Condição desconhecida"
}

fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

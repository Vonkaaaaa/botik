package com.personalbot.service;

import com.personalbot.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public class WeatherService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @SuppressWarnings("unchecked")
    public String getWeatherReport(String city, double lat, double lon) {
        // Enforce Locale.US so floating point numbers use '.' instead of ',' in HTTP URL
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto",
                lat, lon
        );

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(resp.body());
                Map<String, Object> current = (Map<String, Object>) json.get("current");
                Map<String, Object> daily = (Map<String, Object>) json.get("daily");

                if (current != null) {
                    double temp = parseDouble(current.get("temperature_2m"));
                    double feels = parseDouble(current.get("apparent_temperature"));
                    double humidity = parseDouble(current.get("relative_humidity_2m"));
                    double wind = parseDouble(current.get("wind_speed_10m"));
                    int wcode = (int) parseDouble(current.get("weather_code"));

                    String conditionEmoji = getWeatherEmoji(wcode);
                    String conditionText = getWeatherDescription(wcode);

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("🌤️ <b>Погода в г. %s</b>\n", city));
                    sb.append("─────────────────────\n");
                    sb.append(String.format("%s <b>Состояние:</b> %s\n", conditionEmoji, conditionText));
                    sb.append(String.format(Locale.US, "🌡️ <b>Температура:</b> %.1f°C (ощущается как %.1f°C)\n", temp, feels));
                    sb.append(String.format(Locale.US, "💧 <b>Влажность:</b> %.0f%%\n", humidity));
                    sb.append(String.format(Locale.US, "💨 <b>Ветер:</b> %.1f м/с\n", wind));

                    if (daily != null && daily.get("temperature_2m_max") instanceof java.util.List) {
                        java.util.List<?> maxList = (java.util.List<?>) daily.get("temperature_2m_max");
                        java.util.List<?> minList = (java.util.List<?>) daily.get("temperature_2m_min");
                        if (!maxList.isEmpty() && !minList.isEmpty()) {
                            double maxT = parseDouble(maxList.get(0));
                            double minT = parseDouble(minList.get(0));
                            sb.append(String.format(Locale.US, "\n📈 <b>Сегодня:</b> от %.1f°C до %.1f°C", minT, maxT));
                        }
                    }

                    return sb.toString();
                }
            } else {
                System.err.println("[WeatherService] Open-Meteo returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("[WeatherService] Error fetching weather: " + e.getMessage());
        }

        return "⚠️ Не удалось получить данные о погоде для г. " + city + ". Проверьте интернет-соединение.";
    }

    private String getWeatherEmoji(int code) {
        switch (code) {
            case 0: return "☀️";
            case 1: case 2: return "🌤️";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55: return "🌧️";
            case 61: case 63: case 65: return "🌧️";
            case 71: case 73: case 75: case 77: return "❄️";
            case 80: case 81: case 82: return "🌦️";
            case 85: case 86: return "🌨️";
            case 95: case 96: case 99: return "🌩️";
            default: return "🌡️";
        }
    }

    private String getWeatherDescription(int code) {
        switch (code) {
            case 0: return "Ясно";
            case 1: return "Преимущественно ясно";
            case 2: return "Переменная облачность";
            case 3: return "Пасмурно";
            case 45: case 48: return "Туман";
            case 51: case 53: case 55: return "Морось";
            case 61: return "Небольшой дождь";
            case 63: return "Умеренный дождь";
            case 65: return "Сильный дождь";
            case 71: return "Небольшой снег";
            case 73: return "Снегопад";
            case 75: return "Сильный снегопад";
            case 80: case 81: case 82: return "Ливень";
            case 95: case 96: case 99: return "Гроза";
            default: return "Облачно";
        }
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(obj.toString()); } catch (Exception ignored) {}
        }
        return 0.0;
    }
}

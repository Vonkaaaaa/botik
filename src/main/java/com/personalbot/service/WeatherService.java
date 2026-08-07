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
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @SuppressWarnings("unchecked")
    public String getWeatherReport(String city, double lat, double lon) {
        if (lat == 0.0 || lon == 0.0) {
            lat = 49.9935;
            lon = 36.2304;
        }

        // Try wttr.in FIRST (no rate limits, supports city names)
        String wttrResult = fetchWttrInWeather(city);
        if (wttrResult != null) return wttrResult;

        // Fallback: Open-Meteo API
        String openMeteoResult = fetchOpenMeteo(city, lat, lon);
        if (openMeteoResult != null) return openMeteoResult;

        return "\u26A0\uFE0F \u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435 \u043E \u043F\u043E\u0433\u043E\u0434\u0435 \u0434\u043B\u044F \u0433. " + city + ". \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.";
    }

    @SuppressWarnings("unchecked")
    private String fetchWttrInWeather(String city) {
        try {
            // Use English city name for API reliability
            String query = city.contains("\u0425\u0430\u0440\u044C\u043A\u043E\u0432") || city.equalsIgnoreCase("Kharkiv") ? "Kharkiv" : city;
            String url = "https://wttr.in/" + query + "?format=j1";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "curl/7.68.0")
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().startsWith("{")) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(resp.body());
                if (json != null && json.get("current_condition") instanceof java.util.List) {
                    java.util.List<?> condList = (java.util.List<?>) json.get("current_condition");
                    if (!condList.isEmpty() && condList.get(0) instanceof Map) {
                        Map<String, Object> current = (Map<String, Object>) condList.get(0);
                        double temp = parseDouble(current.get("temp_C"));
                        double feels = parseDouble(current.get("FeelsLikeC"));
                        double humidity = parseDouble(current.get("humidity"));
                        double windKmh = parseDouble(current.get("windspeedKmph"));
                        double wind = windKmh / 3.6;

                        // Get weather description
                        String desc = "";
                        if (current.get("lang_ru") instanceof java.util.List) {
                            java.util.List<?> langRu = (java.util.List<?>) current.get("lang_ru");
                            if (!langRu.isEmpty() && langRu.get(0) instanceof Map) {
                                desc = String.valueOf(((Map<String, Object>) langRu.get(0)).get("value"));
                            }
                        }
                        if (desc.isEmpty() && current.get("weatherDesc") instanceof java.util.List) {
                            java.util.List<?> wdList = (java.util.List<?>) current.get("weatherDesc");
                            if (!wdList.isEmpty() && wdList.get(0) instanceof Map) {
                                desc = String.valueOf(((Map<String, Object>) wdList.get(0)).get("value"));
                            }
                        }

                        String emoji = getEmojiFromDesc(desc);

                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("\uD83C\uDF24\uFE0F <b>\u041F\u043E\u0433\u043E\u0434\u0430 \u0432 \u0433. %s</b>\n", city));
                        sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
                        if (!desc.isEmpty()) {
                            sb.append(String.format("%s <b>\u0421\u043E\u0441\u0442\u043E\u044F\u043D\u0438\u0435:</b> %s\n", emoji, desc));
                        }
                        sb.append(String.format(Locale.US, "\uD83C\uDF21\uFE0F <b>\u0422\u0435\u043C\u043F\u0435\u0440\u0430\u0442\u0443\u0440\u0430:</b> %.1f\u00B0C (\u043E\u0449\u0443\u0449\u0430\u0435\u0442\u0441\u044F \u043A\u0430\u043A %.1f\u00B0C)\n", temp, feels));
                        sb.append(String.format(Locale.US, "\uD83D\uDCA7 <b>\u0412\u043B\u0430\u0436\u043D\u043E\u0441\u0442\u044C:</b> %.0f%%\n", humidity));
                        sb.append(String.format(Locale.US, "\uD83D\uDCA8 <b>\u0412\u0435\u0442\u0435\u0440:</b> %.1f \u043C/\u0441\n", wind));

                        return sb.toString();
                    }
                }
            } else {
                System.err.println("[WeatherService] wttr.in returned HTTP " + resp.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[WeatherService] wttr.in error: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String fetchOpenMeteo(String city, double lat, double lon) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto",
                lat, lon
        );

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "PersonalTelegramBot/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(resp.body());
                Map<String, Object> current = (Map<String, Object>) json.get("current");

                if (current != null) {
                    double temp = parseDouble(current.get("temperature_2m"));
                    double feels = parseDouble(current.get("apparent_temperature"));
                    double humidity = parseDouble(current.get("relative_humidity_2m"));
                    double windKmh = parseDouble(current.get("wind_speed_10m"));
                    double wind = windKmh / 3.6;
                    int wcode = (int) parseDouble(current.get("weather_code"));

                    String conditionEmoji = getWeatherEmoji(wcode);
                    String conditionText = getWeatherDescription(wcode);

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("\uD83C\uDF24\uFE0F <b>\u041F\u043E\u0433\u043E\u0434\u0430 \u0432 \u0433. %s</b>\n", city));
                    sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
                    sb.append(String.format("%s <b>\u0421\u043E\u0441\u0442\u043E\u044F\u043D\u0438\u0435:</b> %s\n", conditionEmoji, conditionText));
                    sb.append(String.format(Locale.US, "\uD83C\uDF21\uFE0F <b>\u0422\u0435\u043C\u043F\u0435\u0440\u0430\u0442\u0443\u0440\u0430:</b> %.1f\u00B0C (\u043E\u0449\u0443\u0449\u0430\u0435\u0442\u0441\u044F \u043A\u0430\u043A %.1f\u00B0C)\n", temp, feels));
                    sb.append(String.format(Locale.US, "\uD83D\uDCA7 <b>\u0412\u043B\u0430\u0436\u043D\u043E\u0441\u0442\u044C:</b> %.0f%%\n", humidity));
                    sb.append(String.format(Locale.US, "\uD83D\uDCA8 <b>\u0412\u0435\u0442\u0435\u0440:</b> %.1f \u043C/\u0441\n", wind));

                    Map<String, Object> daily = (Map<String, Object>) json.get("daily");
                    if (daily != null && daily.get("temperature_2m_max") instanceof java.util.List) {
                        java.util.List<?> maxList = (java.util.List<?>) daily.get("temperature_2m_max");
                        java.util.List<?> minList = (java.util.List<?>) daily.get("temperature_2m_min");
                        if (!maxList.isEmpty() && !minList.isEmpty()) {
                            double maxT = parseDouble(maxList.get(0));
                            double minT = parseDouble(minList.get(0));
                            sb.append(String.format(Locale.US, "\n\uD83D\uDCC8 <b>\u0421\u0435\u0433\u043E\u0434\u043D\u044F:</b> \u043E\u0442 %.1f\u00B0C \u0434\u043E %.1f\u00B0C", minT, maxT));
                        }
                    }

                    return sb.toString();
                }
            } else {
                System.err.println("[WeatherService] Open-Meteo returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("[WeatherService] Open-Meteo error: " + e.getMessage());
        }
        return null;
    }

    private String getEmojiFromDesc(String desc) {
        if (desc == null) return "\uD83C\uDF21\uFE0F";
        String lower = desc.toLowerCase();
        if (lower.contains("\u044F\u0441\u043D") || lower.contains("clear") || lower.contains("sunny")) return "\u2600\uFE0F";
        if (lower.contains("\u043E\u0431\u043B\u0430\u0447\u043D") || lower.contains("cloud") || lower.contains("overcast")) return "\u2601\uFE0F";
        if (lower.contains("\u0434\u043E\u0436\u0434") || lower.contains("rain") || lower.contains("\u043C\u043E\u0440\u043E\u0441")) return "\uD83C\uDF27\uFE0F";
        if (lower.contains("\u0441\u043D\u0435\u0433") || lower.contains("snow")) return "\u2744\uFE0F";
        if (lower.contains("\u0442\u0443\u043C\u0430\u043D") || lower.contains("fog") || lower.contains("mist")) return "\uD83C\uDF2B\uFE0F";
        if (lower.contains("\u0433\u0440\u043E\u0437") || lower.contains("thunder")) return "\uD83C\uDF29\uFE0F";
        return "\uD83C\uDF24\uFE0F";
    }

    private String getWeatherEmoji(int code) {
        switch (code) {
            case 0: return "\u2600\uFE0F";
            case 1: case 2: return "\uD83C\uDF24\uFE0F";
            case 3: return "\u2601\uFE0F";
            case 45: case 48: return "\uD83C\uDF2B\uFE0F";
            case 51: case 53: case 55: return "\uD83C\uDF27\uFE0F";
            case 61: case 63: case 65: return "\uD83C\uDF27\uFE0F";
            case 71: case 73: case 75: case 77: return "\u2744\uFE0F";
            case 80: case 81: case 82: return "\uD83C\uDF26\uFE0F";
            case 85: case 86: return "\uD83C\uDF28\uFE0F";
            case 95: case 96: case 99: return "\uD83C\uDF29\uFE0F";
            default: return "\uD83C\uDF21\uFE0F";
        }
    }

    private String getWeatherDescription(int code) {
        switch (code) {
            case 0: return "\u042F\u0441\u043D\u043E";
            case 1: return "\u041F\u0440\u0435\u0438\u043C\u0443\u0449\u0435\u0441\u0442\u0432\u0435\u043D\u043D\u043E \u044F\u0441\u043D\u043E";
            case 2: return "\u041F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u0430\u044F \u043E\u0431\u043B\u0430\u0447\u043D\u043E\u0441\u0442\u044C";
            case 3: return "\u041F\u0430\u0441\u043C\u0443\u0440\u043D\u043E";
            case 45: case 48: return "\u0422\u0443\u043C\u0430\u043D";
            case 51: case 53: case 55: return "\u041C\u043E\u0440\u043E\u0441\u044C";
            case 61: return "\u041D\u0435\u0431\u043E\u043B\u044C\u0448\u043E\u0439 \u0434\u043E\u0436\u0434\u044C";
            case 63: return "\u0423\u043C\u0435\u0440\u0435\u043D\u043D\u044B\u0439 \u0434\u043E\u0436\u0434\u044C";
            case 65: return "\u0421\u0438\u043B\u044C\u043D\u044B\u0439 \u0434\u043E\u0436\u0434\u044C";
            case 71: return "\u041D\u0435\u0431\u043E\u043B\u044C\u0448\u043E\u0439 \u0441\u043D\u0435\u0433";
            case 73: return "\u0421\u043D\u0435\u0433\u043E\u043F\u0430\u0434";
            case 75: return "\u0421\u0438\u043B\u044C\u043D\u044B\u0439 \u0441\u043D\u0435\u0433\u043E\u043F\u0430\u0434";
            case 80: case 81: case 82: return "\u041B\u0438\u0432\u0435\u043D\u044C";
            case 95: case 96: case 99: return "\u0413\u0440\u043E\u0437\u0430";
            default: return "\u041E\u0431\u043B\u0430\u0447\u043D\u043E";
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

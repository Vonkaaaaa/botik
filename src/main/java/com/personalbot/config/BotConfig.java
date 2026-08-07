package com.personalbot.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class BotConfig {
    private static final String CONFIG_FILE = "config.properties";
    private final Properties props = new Properties();

    public BotConfig() {
        loadConfig();
    }

    public void loadConfig() {
        File f = new File(CONFIG_FILE);
        if (f.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("[Config] Error loading config.properties: " + e.getMessage());
            }
        }
    }

    public String getBotToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token != null && !token.trim().isEmpty()) return token.trim();
        return props.getProperty("bot.token", "YOUR_TELEGRAM_BOT_TOKEN").trim();
    }

    public String getBotUsername() {
        return props.getProperty("bot.username", "PersonalBot").trim();
    }

    public String getUserChatId() {
        String cid = System.getenv("USER_CHAT_ID");
        if (cid != null && !cid.trim().isEmpty()) return cid.trim();
        return props.getProperty("user.chat_id", "").trim();
    }

    public void setUserChatId(String chatId) {
        props.setProperty("user.chat_id", chatId);
        saveConfig();
    }

    public String getWeatherCity() {
        return props.getProperty("weather.city", "Харьков").trim();
    }

    public void setWeatherCity(String city, double lat, double lon) {
        props.setProperty("weather.city", city);
        props.setProperty("weather.lat", String.valueOf(lat));
        props.setProperty("weather.lon", String.valueOf(lon));
        saveConfig();
    }

    public double getWeatherLat() {
        try {
            return Double.parseDouble(props.getProperty("weather.lat", "49.9935"));
        } catch (Exception e) {
            return 49.9935;
        }
    }

    public double getWeatherLon() {
        try {
            return Double.parseDouble(props.getProperty("weather.lon", "36.2304"));
        } catch (Exception e) {
            return 36.2304;
        }
    }

    public boolean isMorningReportEnabled() {
        return Boolean.parseBoolean(props.getProperty("morning.report.enabled", "true"));
    }

    public String getMorningReportTime() {
        return props.getProperty("morning.report.time", "08:00").trim();
    }

    private void saveConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            props.store(writer, "Updated Configuration for Telegram Personal Bot");
        } catch (IOException e) {
            System.err.println("[Config] Error saving config.properties: " + e.getMessage());
        }
    }
}

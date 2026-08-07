package com.personalbot.telegram;

import com.personalbot.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class TelegramBot {
    private final String botToken;
    private final HttpClient httpClient;
    private final String baseUrl;
    private long lastUpdateId = 0;

    public TelegramBot(String botToken) {
        this.botToken = botToken;
        this.baseUrl = "https://api.telegram.org/bot" + botToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUpdates(int timeoutSeconds) {
        String url = baseUrl + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=" + timeoutSeconds;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(response.body());
                if (Boolean.TRUE.equals(json.get("ok"))) {
                    List<Map<String, Object>> result = (List<Map<String, Object>>) json.get("result");
                    if (result != null && !result.isEmpty()) {
                        for (Map<String, Object> update : result) {
                            Number uid = (Number) update.get("update_id");
                            if (uid != null && uid.longValue() > lastUpdateId) {
                                lastUpdateId = uid.longValue();
                            }
                        }
                        return result;
                    }
                }
            } else if (response.statusCode() == 404) {
                System.err.println("❌ Ошибка Telegram API (HTTP 404): Токен бота неверен или не существует! Проверьте bot.token в config.properties.");
                try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
            } else {
                System.err.println("[Telegram API] getUpdates returned code " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            // Silently retry on connection timeout / network blip
        }
        return Collections.emptyList();
    }

    public boolean sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null, null);
    }

    public boolean sendMessage(String chatId, String text, Object replyMarkup, String parseMode) {
        if (chatId == null || chatId.isEmpty() || text == null || text.trim().isEmpty()) {
            return false;
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("text", text);
        bodyMap.put("parse_mode", parseMode != null ? parseMode : "HTML");
        bodyMap.put("disable_web_page_preview", true);

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        return executePost("sendMessage", bodyMap);
    }

    public boolean editMessageText(String chatId, long messageId, String text, Object replyMarkup) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("chat_id", chatId);
        bodyMap.put("message_id", messageId);
        bodyMap.put("text", text);
        bodyMap.put("parse_mode", "HTML");
        bodyMap.put("disable_web_page_preview", true);

        if (replyMarkup != null) {
            bodyMap.put("reply_markup", replyMarkup);
        }

        try {
            String jsonBody = JsonUtil.toJson(bodyMap);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/editMessageText"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Ignore harmless "message is not modified" error when content hasn't changed
                if (response.body() != null && response.body().contains("message is not modified")) {
                    return true;
                }
                System.err.println("[Telegram API] editMessageText failed (" + response.statusCode() + "): " + response.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("[Telegram API] Error in editMessageText: " + e.getMessage());
            return false;
        }
    }

    public boolean answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("callback_query_id", callbackQueryId);
        if (text != null && !text.isEmpty()) {
            bodyMap.put("text", text);
        }
        return executePost("answerCallbackQuery", bodyMap);
    }

    private boolean executePost(String method, Map<String, Object> bodyMap) {
        try {
            String jsonBody = JsonUtil.toJson(bodyMap);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + method))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("[Telegram API] " + method + " failed (" + response.statusCode() + "): " + response.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("[Telegram API] Error in " + method + ": " + e.getMessage());
            return false;
        }
    }

    public static Map<String, Object> buildReplyKeyboard(List<List<String>> rows) {
        Map<String, Object> keyboard = new HashMap<>();
        List<List<Map<String, String>>> keyboardRows = new ArrayList<>();

        for (List<String> row : rows) {
            List<Map<String, String>> rowButtons = new ArrayList<>();
            for (String btnText : row) {
                Map<String, String> btn = new HashMap<>();
                btn.put("text", btnText);
                rowButtons.add(btn);
            }
            keyboardRows.add(rowButtons);
        }

        keyboard.put("keyboard", keyboardRows);
        keyboard.put("resize_keyboard", true);
        keyboard.put("persistent", true);
        return keyboard;
    }

    public static Map<String, Object> buildInlineKeyboard(List<List<InlineButton>> rows) {
        Map<String, Object> keyboard = new HashMap<>();
        List<List<Map<String, String>>> keyboardRows = new ArrayList<>();

        for (List<InlineButton> row : rows) {
            List<Map<String, String>> rowButtons = new ArrayList<>();
            for (InlineButton btn : row) {
                Map<String, String> b = new HashMap<>();
                b.put("text", btn.text);
                if (btn.url != null) {
                    b.put("url", btn.url);
                } else if (btn.callbackData != null) {
                    b.put("callback_data", btn.callbackData);
                }
                rowButtons.add(b);
            }
            keyboardRows.add(rowButtons);
        }

        keyboard.put("inline_keyboard", keyboardRows);
        return keyboard;
    }

    public static class InlineButton {
        public String text;
        public String callbackData;
        public String url;

        public InlineButton(String text, String callbackData) {
            this.text = text;
            this.callbackData = callbackData;
        }

        public InlineButton(String text, String callbackData, String url) {
            this.text = text;
            this.callbackData = callbackData;
            this.url = url;
        }
    }
}

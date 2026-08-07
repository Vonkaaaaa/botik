package com.personalbot.telegram;

import com.personalbot.config.BotConfig;
import com.personalbot.service.*;

import java.util.*;

public class CommandHandler {
    private final TelegramBot bot;
    private final BotConfig config;
    private final CurrencyService currencyService;
    private final WeatherService weatherService;
    private final ReminderService reminderService;
    private final HabitTrackerService habitService;
    private final TorrentParserService torrentService;
    private final SchedulerService schedulerService;
    private final TransportService transportService;

    public CommandHandler(TelegramBot bot, BotConfig config, CurrencyService currencyService,
                          WeatherService weatherService, ReminderService reminderService,
                          HabitTrackerService habitService, TorrentParserService torrentService,
                          SchedulerService schedulerService, TransportService transportService) {
        this.bot = bot;
        this.config = config;
        this.currencyService = currencyService;
        this.weatherService = weatherService;
        this.reminderService = reminderService;
        this.habitService = habitService;
        this.torrentService = torrentService;
        this.schedulerService = schedulerService;
        this.transportService = transportService;
    }

    @SuppressWarnings("unchecked")
    public void processUpdate(Map<String, Object> update) {
        if (update.containsKey("message")) {
            Map<String, Object> msg = (Map<String, Object>) update.get("message");
            handleMessage(msg);
        } else if (update.containsKey("callback_query")) {
            Map<String, Object> cb = (Map<String, Object>) update.get("callback_query");
            handleCallbackQuery(cb);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(Map<String, Object> msg) {
        Map<String, Object> chat = (Map<String, Object>) msg.get("chat");
        if (chat == null) return;

        String chatId = String.valueOf(chat.get("id"));
        String text = msg.get("text") != null ? String.valueOf(msg.get("text")).trim() : "";

        // Auto-register owner Chat ID on first use if not configured
        if (config.getUserChatId().isEmpty()) {
            config.setUserChatId(chatId);
            System.out.println("[CommandHandler] Registered primary chat ID: " + chatId);
        }

        if (text.isEmpty()) return;

        // Command routing
        if (text.startsWith("/start")) {
            sendWelcomeMessage(chatId);
        } else if (text.startsWith("/help")) {
            sendHelpMessage(chatId);
        } else if (text.equalsIgnoreCase("💵 Курсы валют") || text.startsWith("/rates") || text.startsWith("/crypto")) {
            sendCurrencyRates(chatId);
        } else if (text.equalsIgnoreCase("⏰ Напоминания") || text.startsWith("/reminders")) {
            bot.sendMessage(chatId, reminderService.getFormattedActiveReminders(), null, "HTML");
        } else if (text.startsWith("/remind")) {
            String args = text.length() > 7 ? text.substring(7).trim() : "";
            bot.sendMessage(chatId, reminderService.addReminderFromInput(args), null, "HTML");
        } else if (text.startsWith("/del_")) {
            String id = text.substring(5).trim();
            if (reminderService.deleteReminder(id)) {
                bot.sendMessage(chatId, "🗑️ Напоминание удалено.", null, "HTML");
            } else {
                bot.sendMessage(chatId, "❌ Напоминание не найдено.", null, "HTML");
            }
        } else if (text.equalsIgnoreCase("🎯 Привычки") || text.startsWith("/habits")) {
            sendHabitsList(chatId);
        } else if (text.startsWith("/addhabit")) {
            String name = text.length() > 9 ? text.substring(9).trim() : "";
            bot.sendMessage(chatId, habitService.addHabit(name), null, "HTML");
        } else if (text.startsWith("/delhabit_")) {
            String id = text.substring(10).trim();
            bot.sendMessage(chatId, habitService.deleteHabit(id), null, "HTML");
        } else if (text.equalsIgnoreCase("🌤️ Погода")) {
            sendWeatherReport(chatId, config.getWeatherCity());
        } else if (text.startsWith("/weather")) {
            String city = text.length() > 8 ? text.substring(8).trim() : "";
            if (city.isEmpty()) city = config.getWeatherCity();
            sendWeatherReport(chatId, city);
        } else if (text.equalsIgnoreCase("🍿 Новинки") || text.startsWith("/torrents") || text.startsWith("/news")) {
            bot.sendMessage(chatId, torrentService.getLatestReleasesFormatted(), null, "HTML");
        } else if (text.equalsIgnoreCase("🚎 Троллейбус 52") || text.startsWith("/troll") || text.startsWith("/bus52") || text.startsWith("/transport")) {
            String stopId = transportService.getDefaultStopId();
            String report = transportService.getArrivalReport(stopId);
            bot.sendMessage(chatId, report, transportService.getStopsInlineKeyboard(stopId), "HTML");
        } else if (text.equalsIgnoreCase("🌅 Утренний отчёт") || text.startsWith("/morning")) {
            schedulerService.sendMorningBriefing(chatId);
        } else if (text.equalsIgnoreCase("⚙️ Настройки") || text.startsWith("/settings")) {
            sendSettingsMessage(chatId);
        } else {
            // Default response
            bot.sendMessage(chatId, "💡 Не распознал команду. Воспользуйтесь меню ниже или введите /help.", getMainMenuKeyboard(), "HTML");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCallbackQuery(Map<String, Object> cb) {
        String cbId = String.valueOf(cb.get("id"));
        String data = cb.get("data") != null ? String.valueOf(cb.get("data")) : "";
        Map<String, Object> msg = (Map<String, Object>) cb.get("message");
        if (msg == null) return;

        Map<String, Object> chat = (Map<String, Object>) msg.get("chat");
        String chatId = String.valueOf(chat.get("id"));
        long messageId = msg.get("message_id") instanceof Number ? ((Number) msg.get("message_id")).longValue() : 0;

        if (data.equals("refresh_rates")) {
            bot.answerCallbackQuery(cbId, "🔄 Курсы обновлены!");
            bot.editMessageText(chatId, messageId, currencyService.getFormattedRates(), getRatesInlineKeyboard());
        } else if (data.startsWith("habit_done_")) {
            String habitId = data.substring(11);
            String resultText = habitService.markCompleted(habitId);
            bot.answerCallbackQuery(cbId, "✅ Привычка отмечена!");
            bot.sendMessage(chatId, resultText, null, "HTML");
            bot.editMessageText(chatId, messageId, habitService.getFormattedHabitsList(), habitService.getHabitInlineKeyboard());
        } else if (data.startsWith("habit_already_")) {
            bot.answerCallbackQuery(cbId, "ℹ️ Уже выполнено сегодня!");
        } else if (data.startsWith("stop_select_")) {
            String stopId = data.substring(12);
            transportService.setDefaultStopId(stopId);
            bot.answerCallbackQuery(cbId, "🚏 Остановка обновлена!");
            String report = transportService.getArrivalReport(stopId);
            bot.editMessageText(chatId, messageId, report, transportService.getStopsInlineKeyboard(stopId));
        }
    }

    private void sendWelcomeMessage(String chatId) {
        String msg = "👋 <b>Привет! Я твой персональный Telegram-помощник (Харьков 🇺🇦).</b>\n\n" +
                "Я умею:\n" +
                "• 💵 Отправлять курсы валют (₴ UAH) и криптовалют\n" +
                "• ⏰ Напоминать о важных делах\n" +
                "• 🎯 Трекать твои ежедневные привычки и стрики\n" +
                "• 🌤️ Показывать погоду в Харькове и утренний отчёт\n" +
                "• 🚎 Расписание и прибытие Маршрута №52 по остановкам\n" +
                "• 🍿 Парсить новинки торрентов, игр и новостей\n\n" +
                "Пользуйся удобными кнопками меню ниже! 👇";
        bot.sendMessage(chatId, msg, getMainMenuKeyboard(), "HTML");
    }

    private void sendHelpMessage(String chatId) {
        String msg = "<b>📖 Справка по командам:</b>\n" +
                "─────────────────────\n\n" +
                "<b>💵 Валюта и Крипта:</b>\n" +
                "• /rates — свежие курсы в ₴ UAH\n\n" +
                "<b>⏰ Напоминания:</b>\n" +
                "• <code>/remind 15m Текст</code> — напомнить через 15 минут\n" +
                "• <code>/remind 18:30 Текст</code> — напомнить в 18:30\n" +
                "• /reminders — список активных напоминаний\n\n" +
                "<b>🎯 Привычки:</b>\n" +
                "• <code>/addhabit Название</code> — добавить привычку\n" +
                "• /habits — посмотреть и отметить привычки\n\n" +
                "<b>🌤️ Погода и Отчёт:</b>\n" +
                "• /weather — погода в Харькове\n" +
                "• /morning — вызвать утренний отчёт вручную\n\n" +
                "<b>🚎 Транспорт Харькова:</b>\n" +
                "• /troll — расписание и время прибытия №52 на твою остановку\n\n" +
                "<b>🍿 Новинки:</b>\n" +
                "• /torrents — свежие релизы игр/торрентов";
        bot.sendMessage(chatId, msg, getMainMenuKeyboard(), "HTML");
    }

    private void sendCurrencyRates(String chatId) {
        bot.sendMessage(chatId, currencyService.getFormattedRates(), getRatesInlineKeyboard(), "HTML");
    }

    private void sendHabitsList(String chatId) {
        bot.sendMessage(chatId, habitService.getFormattedHabitsList(), habitService.getHabitInlineKeyboard(), "HTML");
    }

    private void sendWeatherReport(String chatId, String city) {
        String report = weatherService.getWeatherReport(city, config.getWeatherLat(), config.getWeatherLon());
        bot.sendMessage(chatId, report, null, "HTML");
    }

    private void sendSettingsMessage(String chatId) {
        String msg = String.format(
                "⚙️ <b>Текущие настройки бота:</b>\n" +
                        "─────────────────────\n" +
                        "• <b>Chat ID:</b> <code>%s</code>\n" +
                        "• <b>Город погоды:</b> %s (%.2f, %.2f)\n" +
                        "• <b>Утренний отчёт:</b> %s в <b>%s</b>\n\n" +
                        "<i>Для деплоя на Render/Docker все параметры задаются через Environment Variables: BOT_TOKEN, USER_CHAT_ID, DATABASE_URL и др.</i>",
                config.getUserChatId(),
                config.getWeatherCity(), config.getWeatherLat(), config.getWeatherLon(),
                config.isMorningReportEnabled() ? "Включён ✅" : "Выключен ❌",
                config.getMorningReportTime()
        );
        bot.sendMessage(chatId, msg, null, "HTML");
    }

    private Object getMainMenuKeyboard() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("💵 Курсы валют", "⏰ Напоминания"));
        rows.add(Arrays.asList("🎯 Привычки", "🌤️ Погода"));
        rows.add(Arrays.asList("🚎 Троллейбус 52", "🍿 Новинки"));
        rows.add(Arrays.asList("🌅 Утренний отчёт", "⚙️ Настройки"));
        return TelegramBot.buildReplyKeyboard(rows);
    }

    private Object getRatesInlineKeyboard() {
        List<List<TelegramBot.InlineButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(new TelegramBot.InlineButton("🔄 Обновить курсы", "refresh_rates")));
        return TelegramBot.buildInlineKeyboard(rows);
    }
}

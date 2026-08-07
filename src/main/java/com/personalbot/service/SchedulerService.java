package com.personalbot.service;

import com.personalbot.config.BotConfig;
import com.personalbot.telegram.TelegramBot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final TelegramBot bot;
    private final BotConfig config;
    private final ReminderService reminderService;
    private final HabitTrackerService habitService;
    private final WeatherService weatherService;
    private final CurrencyService currencyService;

    private String lastMorningReportDate = "";

    public SchedulerService(TelegramBot bot, BotConfig config, ReminderService reminderService,
                            HabitTrackerService habitService, WeatherService weatherService,
                            CurrencyService currencyService) {
        this.bot = bot;
        this.config = config;
        this.reminderService = reminderService;
        this.habitService = habitService;
        this.weatherService = weatherService;
        this.currencyService = currencyService;
    }

    public void startSchedulers() {
        // 1. Check reminders every 10 seconds
        scheduler.scheduleAtFixedRate(this::checkReminders, 5, 10, TimeUnit.SECONDS);

        // 2. Check morning report schedule every 30 seconds
        scheduler.scheduleAtFixedRate(this::checkMorningReport, 10, 30, TimeUnit.SECONDS);

        System.out.println("[SchedulerService] Background schedulers active (Reminders + Morning Report).");
    }

    private void checkReminders() {
        String chatId = config.getUserChatId();
        if (chatId == null || chatId.isEmpty()) return;

        List<ReminderService.Reminder> dueReminders = reminderService.checkAndGetDueReminders();
        for (ReminderService.Reminder r : dueReminders) {
            String msg = String.format("⏰ <b>НАПОМИНАНИЕ!</b>\n─────────────────────\n\n📌 <b>%s</b>", r.text);
            bot.sendMessage(chatId, msg);
        }
    }

    private void checkMorningReport() {
        if (!config.isMorningReportEnabled()) return;

        String chatId = config.getUserChatId();
        if (chatId == null || chatId.isEmpty()) return;

        String targetTime = config.getMorningReportTime(); // e.g. "08:00"
        String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        if (currentTime.equals(targetTime) && !currentDate.equals(lastMorningReportDate)) {
            lastMorningReportDate = currentDate;
            sendMorningBriefing(chatId);
        }
    }

    public void sendMorningBriefing(String chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌅 <b>Доброе утро! Ваш утренний отчёт:</b>\n");
        sb.append("═════════════════════\n\n");

        // 1. Weather
        sb.append(weatherService.getWeatherReport(config.getWeatherCity(), config.getWeatherLat(), config.getWeatherLon()));
        sb.append("\n\n");

        // 2. Habits
        sb.append(habitService.getFormattedHabitsList());
        sb.append("\n");

        // 3. Currency summary
        sb.append(currencyService.getFormattedRates());

        bot.sendMessage(chatId, sb.toString(), habitService.getHabitInlineKeyboard(), "HTML");
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}

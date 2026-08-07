package com.personalbot;

import com.personalbot.config.BotConfig;
import com.personalbot.database.DatabaseManager;
import com.personalbot.service.*;
import com.personalbot.telegram.CommandHandler;
import com.personalbot.telegram.TelegramBot;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🚀 Starting Personal Telegram Bot...");
        System.out.println("=================================================");

        // 1. Load configuration
        BotConfig config = new BotConfig();
        String botToken = config.getBotToken();

        if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_TELEGRAM_BOT_TOKEN")) {
            System.err.println("\n❌ ОШИБКА: Токен Telegram бота не установлен!");
            System.err.println("═════════════════════════════════════════════════");
            System.err.println("Пожалуйста, откройте файл 'config.properties' и укажите ваш токен от @BotFather:");
            System.err.println("   bot.token=123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ");
            System.err.println("\nИли задайте переменную окружения BOT_TOKEN.");
            System.err.println("═════════════════════════════════════════════════\n");
            return;
        }

        // 2. Initialize Database (PostgreSQL if DATABASE_URL present, SQLite if jar present, or local JSON storage)
        DatabaseManager dbManager = new DatabaseManager();

        // 3. Initialize Telegram Bot client & Services
        TelegramBot bot = new TelegramBot(botToken);
        CurrencyService currencyService = new CurrencyService();
        WeatherService weatherService = new WeatherService();
        ReminderService reminderService = new ReminderService(dbManager);
        HabitTrackerService habitService = new HabitTrackerService(dbManager);
        TorrentParserService torrentService = new TorrentParserService();
        TransportService transportService = new TransportService();

        // 4. Initialize Scheduler Service (Reminders + Morning Report)
        SchedulerService schedulerService = new SchedulerService(
                bot, config, reminderService, habitService, weatherService, currencyService
        );
        schedulerService.startSchedulers();

        // 5. Command Handler
        CommandHandler commandHandler = new CommandHandler(
                bot, config, currencyService, weatherService, reminderService,
                habitService, torrentService, schedulerService, transportService
        );

        System.out.println("✅ Telegram Bot initialized successfully!");
        System.out.println("🤖 Bot is active and listening for messages...");

        // Add Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down Personal Telegram Bot...");
            schedulerService.stop();
        }));

        // 6. Main Long-Polling Loop
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<Map<String, Object>> updates = bot.getUpdates(20);
                for (Map<String, Object> update : updates) {
                    commandHandler.processUpdate(update);
                }
            } catch (Exception e) {
                System.err.println("[Main] Error in long polling loop: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}

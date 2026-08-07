package com.personalbot;

import com.personalbot.config.BotConfig;
import com.personalbot.database.DatabaseManager;
import com.personalbot.service.*;
import com.personalbot.telegram.CommandHandler;
import com.personalbot.telegram.TelegramBot;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Starting Personal Telegram Bot...");
        System.out.println("=================================================");

        // 0. Start lightweight HTTP health-check server for Render
        startHealthCheckServer();

        // 1. Load configuration
        BotConfig config = new BotConfig();
        String botToken = config.getBotToken();

        if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_TELEGRAM_BOT_TOKEN")) {
            System.err.println("\nERROR: Bot token is not set!");
            System.err.println("Set BOT_TOKEN environment variable or edit config.properties.");
            return;
        }

        // 2. Initialize Database
        DatabaseManager dbManager = new DatabaseManager();

        // 3. Initialize Telegram Bot client & Services
        TelegramBot bot = new TelegramBot(botToken);
        CurrencyService currencyService = new CurrencyService();
        WeatherService weatherService = new WeatherService();
        ReminderService reminderService = new ReminderService(dbManager);
        HabitTrackerService habitService = new HabitTrackerService(dbManager);
        TorrentParserService torrentService = new TorrentParserService();
        TransportService transportService = new TransportService();

        // 4. Initialize Scheduler Service
        SchedulerService schedulerService = new SchedulerService(
                bot, config, reminderService, habitService, weatherService, currencyService
        );
        schedulerService.startSchedulers();

        // 5. Command Handler
        CommandHandler commandHandler = new CommandHandler(
                bot, config, currencyService, weatherService, reminderService,
                habitService, torrentService, schedulerService, transportService
        );

        System.out.println("Telegram Bot initialized successfully!");
        System.out.println("Bot is active and listening for messages...");

        // Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Personal Telegram Bot...");
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

    /**
     * Starts a minimal HTTP server on $PORT so Render's health check passes.
     * Without this, Render kills the container with 503.
     */
    private static void startHealthCheckServer() {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                String response = "OK - Telegram Bot is running";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            });
            server.createContext("/health", exchange -> {
                String response = "{\"status\":\"UP\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            });
            server.setExecutor(null);
            server.start();
            System.out.println("[HealthCheck] HTTP server listening on port " + port);
        } catch (Exception e) {
            System.err.println("[HealthCheck] Could not start HTTP server: " + e.getMessage());
        }
    }
}

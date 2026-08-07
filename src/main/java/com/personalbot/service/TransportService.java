package com.personalbot.service;

import com.personalbot.telegram.TelegramBot;
import com.personalbot.telegram.TelegramBot.InlineButton;

import java.util.*;

/**
 * Kharkiv Public Transport Service - Route #52 arrival calculator by stop.
 * Custom stops configured for user (Улица Станковая (Саша), Улица Канадская в обе стороны (АТБ), Переулок Закарпатский в обе стороны (Ярик)).
 */
public class TransportService {

    public static final List<StopInfo> STOPS = Arrays.asList(
            new StopInfo("1", "ст. м. Индустриальная (ул. 12-го Апреля)", 0, "к 759-му мкрн"),
            new StopInfo("2", "Улица Станковая (Саша)", 4, "к 759-му мкрн"),
            new StopInfo("3", "Переулок Закарпатский (Ярик) ➔ к 759-му мкрн", 7, "к 759-му мкрн"),
            new StopInfo("3b", "Переулок Закарпатский (Ярик) ➔ к Индустриальной", 11, "к ст. м. Индустриальная"),
            new StopInfo("4", "Улица Канадская (АТБ) ➔ к 759-му мкрн", 11, "к 759-му мкрн"),
            new StopInfo("5", "Улица Канадская (АТБ) ➔ к Индустриальной", 14, "к ст. м. Индустриальная"),
            new StopInfo("6", "757-й микрорайон", 16, "к 759-му мкрн"),
            new StopInfo("7", "759-й микрорайон (конечная)", 18, "конечная")
    );

    public static class StopInfo {
        public String id;
        public String name;
        public int offsetMinutes;
        public String direction;

        public StopInfo(String id, String name, int offsetMinutes, String direction) {
            this.id = id;
            this.name = name;
            this.offsetMinutes = offsetMinutes;
            this.direction = direction;
        }
    }

    private String userDefaultStopId = "4"; // Default: Улица Канадская (АТБ)

    public String getDefaultStopId() {
        return userDefaultStopId;
    }

    public void setDefaultStopId(String stopId) {
        this.userDefaultStopId = stopId;
    }

    public StopInfo getStopById(String id) {
        for (StopInfo s : STOPS) {
            if (s.id.equals(id)) return s;
        }
        return STOPS.get(0);
    }

    /**
     * Get arrival schedule report for a specific stop ID.
     */
    public String getArrivalReport(String stopId) {
        StopInfo stop = getStopById(stopId);

        Calendar cal = Calendar.getInstance();
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        int currentMin = cal.get(Calendar.MINUTE);
        int nowTotalMin = currentHour * 60 + currentMin;

        StringBuilder sb = new StringBuilder();
        sb.append("🚎 <b>Маршрут №52 — Прибытие на остановку</b>\n");
        sb.append("─────────────────────\n");
        sb.append("🚏 <b>Остановка:</b> <code>").append(stop.name).append("</code>\n");
        sb.append("📍 <b>Направление:</b> ").append(stop.direction).append("\n\n");

        // Service hours: 06:00 to 21:30, interval 15 min
        int startMin = 6 * 60;     // 06:00
        int endMin = 21 * 60 + 30; // 21:30
        int interval = 15;

        List<Integer> upcomingTripTimes = new ArrayList<>();

        for (int departure = startMin; departure <= endMin; departure += interval) {
            int stopArrival = departure + stop.offsetMinutes;
            if (stopArrival >= nowTotalMin) {
                upcomingTripTimes.add(stopArrival);
                if (upcomingTripTimes.size() >= 3) break;
            }
        }

        if (upcomingTripTimes.isEmpty()) {
            sb.append("🌙 <b>Движение на сегодня завершено.</b>\n");
            sb.append("Первый рейс завтра: <b>").append(formatTime(startMin + stop.offsetMinutes)).append("</b>\n\n");
        } else {
            sb.append("⏱️ <b>Ближайшие рейсы:</b>\n");
            for (int i = 0; i < upcomingTripTimes.size(); i++) {
                int arrivalTime = upcomingTripTimes.get(i);
                int waitMin = arrivalTime - nowTotalMin;
                String timeStr = formatTime(arrivalTime);

                if (i == 0) {
                    if (waitMin == 0) {
                        sb.append("  • 🚘 <b>").append(timeStr).append("</b> (<b>СЕЙЧАС на остановке!</b>)\n");
                    } else {
                        sb.append("  • 🚍 <b>").append(timeStr).append("</b> (через <b>").append(waitMin).append(" мин</b>)\n");
                    }
                } else {
                    sb.append("  • 🔹 <b>").append(timeStr).append("</b> (через ").append(waitMin).append(" мин)\n");
                }
            }
            sb.append("\n");
        }

        sb.append("📊 <i>Интервал: 15 минут | Время работы: 06:00 - 21:30</i>\n");
        sb.append("🔗 <a href=\"https://www.eway.in.ua/ua/cities/kharkiv/routes/bus/52\">Онлайн-табло EasyWay</a>");

        return sb.toString();
    }

    /**
     * Build inline keyboard for quick stop selection.
     */
    public Object getStopsInlineKeyboard(String currentStopId) {
        List<List<InlineButton>> rows = new ArrayList<>();

        for (StopInfo stop : STOPS) {
            boolean isSelected = stop.id.equals(currentStopId);
            String prefix = isSelected ? "✅ " : "🚏 ";
            String btnText = prefix + stop.name;
            rows.add(Collections.singletonList(new InlineButton(btnText, "stop_select_" + stop.id)));
        }

        rows.add(Collections.singletonList(new InlineButton("🔄 Обновить время", "stop_select_" + currentStopId)));
        return TelegramBot.buildInlineKeyboard(rows);
    }

    private String formatTime(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
    }
}

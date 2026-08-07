package com.personalbot.service;

import com.personalbot.database.DatabaseManager;
import com.personalbot.util.JsonUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReminderService {
    private static final String DATA_FILE = "data/reminders.json";
    private final DatabaseManager dbManager;
    private final List<Reminder> localReminders = new CopyOnWriteArrayList<>();

    public ReminderService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        if (!dbManager.isDbAvailable()) {
            loadJsonData();
        }
    }

    public static class Reminder {
        public String id;
        public String text;
        public long triggerTimeMs;
        public long createdAtMs;
        public boolean triggered;

        public Reminder() {}

        public Reminder(String id, String text, long triggerTimeMs) {
            this.id = id;
            this.text = text;
            this.triggerTimeMs = triggerTimeMs;
            this.createdAtMs = System.currentTimeMillis();
            this.triggered = false;
        }
    }

    public String addReminderFromInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "❌ Укажите время и текст напоминания. Пример: <code>/remind 15m Позвонить врачу</code> или <code>/remind 18:30 Купить продукты</code>";
        }

        input = input.trim();
        String timePart = "";
        String textPart = "";

        int spaceIdx = input.indexOf(' ');
        if (spaceIdx > 0) {
            timePart = input.substring(0, spaceIdx).trim();
            textPart = input.substring(spaceIdx + 1).trim();
        } else {
            return "❌ Пожалуйста, добавьте текст напоминания после времени!";
        }

        long triggerMs = parseTimeToMs(timePart);
        if (triggerMs <= 0) {
            return "❌ Не удалось распознать время: <b>" + timePart + "</b>.\nИспользуйте формат <code>10m</code> (10 минут), <code>2h</code> (2 часа), <code>1d</code> (1 день) или <code>18:30</code> (конкретное время).";
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        Reminder r = new Reminder(id, textPart, triggerMs);

        if (dbManager.isDbAvailable()) {
            saveReminderToDb(r);
        } else {
            localReminders.add(r);
            saveJsonData();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return String.format("✅ <b>Напоминание установлено!</b>\n\n📝 <b>Текст:</b> %s\n⏰ <b>Время срабатывания:</b> %s", textPart, sdf.format(new java.util.Date(triggerMs)));
    }

    public List<Reminder> checkAndGetDueReminders() {
        List<Reminder> due = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (dbManager.isDbAvailable()) {
            String selectSql = "SELECT id, text, trigger_time_ms, created_at_ms, triggered FROM reminders WHERE triggered = false AND trigger_time_ms <= ?";
            String updateSql = "UPDATE reminders SET triggered = true WHERE id = ?";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {

                selectStmt.setLong(1, now);
                ResultSet rs = selectStmt.executeQuery();

                while (rs.next()) {
                    Reminder r = new Reminder();
                    r.id = rs.getString("id");
                    r.text = rs.getString("text");
                    r.triggerTimeMs = rs.getLong("trigger_time_ms");
                    r.createdAtMs = rs.getLong("created_at_ms");
                    r.triggered = true;
                    due.add(r);

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, r.id);
                        updateStmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                System.err.println("[ReminderService] Error checking due reminders: " + e.getMessage());
            }
        } else {
            boolean changed = false;
            for (Reminder r : localReminders) {
                if (!r.triggered && r.triggerTimeMs <= now) {
                    r.triggered = true;
                    due.add(r);
                    changed = true;
                }
            }
            if (changed) saveJsonData();
        }

        return due;
    }

    public String getFormattedActiveReminders() {
        List<Reminder> active = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (dbManager.isDbAvailable()) {
            String selectSql = "SELECT id, text, trigger_time_ms, created_at_ms, triggered FROM reminders WHERE triggered = false AND trigger_time_ms > ? ORDER BY trigger_time_ms ASC";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(selectSql)) {

                stmt.setLong(1, now);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    Reminder r = new Reminder();
                    r.id = rs.getString("id");
                    r.text = rs.getString("text");
                    r.triggerTimeMs = rs.getLong("trigger_time_ms");
                    r.createdAtMs = rs.getLong("created_at_ms");
                    r.triggered = rs.getBoolean("triggered");
                    active.add(r);
                }
            } catch (SQLException e) {
                System.err.println("[ReminderService] Error loading active reminders: " + e.getMessage());
            }
        } else {
            for (Reminder r : localReminders) {
                if (!r.triggered && r.triggerTimeMs > now) {
                    active.add(r);
                }
            }
            active.sort(Comparator.comparingLong(a -> a.triggerTimeMs));
        }

        if (active.isEmpty()) {
            return "⏰ <b>Активных напоминаний нет.</b>\nЧтобы добавить, введите: <code>/remind 30m Текст</code>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⏰ <b>Ваши активные напоминания:</b>\n");
        sb.append("─────────────────────\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");

        for (int i = 0; i < active.size(); i++) {
            Reminder r = active.get(i);
            long diffMin = Math.max(0, (r.triggerTimeMs - now) / (60 * 1000));
            sb.append(String.format("%d. <b>%s</b>\n   🕒 %s (через %d мин) [/del_%s]\n\n",
                    (i + 1), r.text, sdf.format(new java.util.Date(r.triggerTimeMs)), diffMin, r.id));
        }

        return sb.toString();
    }

    public boolean deleteReminder(String id) {
        if (dbManager.isDbAvailable()) {
            String sql = "DELETE FROM reminders WHERE id = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[ReminderService] Error deleting reminder: " + e.getMessage());
                return false;
            }
        } else {
            boolean removed = localReminders.removeIf(r -> r.id.equalsIgnoreCase(id));
            if (removed) saveJsonData();
            return removed;
        }
    }

    private void saveReminderToDb(Reminder r) {
        String sql = "INSERT INTO reminders (id, text, trigger_time_ms, created_at_ms, triggered) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, r.id);
            stmt.setString(2, r.text);
            stmt.setLong(3, r.triggerTimeMs);
            stmt.setLong(4, r.createdAtMs);
            stmt.setBoolean(5, r.triggered);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ReminderService] Error saving reminder to DB: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadJsonData() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try {
            String content = Files.readString(Paths.get(DATA_FILE));
            Object parsed = JsonUtil.parse(content);
            if (parsed instanceof List) {
                localReminders.clear();
                List<Object> list = (List<Object>) parsed;
                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        Reminder r = new Reminder();
                        r.id = String.valueOf(map.get("id"));
                        r.text = String.valueOf(map.get("text"));
                        r.triggerTimeMs = map.get("triggerTimeMs") instanceof Number ? ((Number) map.get("triggerTimeMs")).longValue() : 0;
                        r.createdAtMs = map.get("createdAtMs") instanceof Number ? ((Number) map.get("createdAtMs")).longValue() : 0;
                        r.triggered = Boolean.TRUE.equals(map.get("triggered"));
                        localReminders.add(r);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ReminderService] Error loading reminders JSON: " + e.getMessage());
        }
    }

    private synchronized void saveJsonData() {
        try {
            File dir = new File("data");
            if (!dir.exists()) dir.mkdirs();

            List<Map<String, Object>> list = new ArrayList<>();
            for (Reminder r : localReminders) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", r.id);
                map.put("text", r.text);
                map.put("triggerTimeMs", r.triggerTimeMs);
                map.put("createdAtMs", r.createdAtMs);
                map.put("triggered", r.triggered);
                list.add(map);
            }

            String json = JsonUtil.toJson(list);
            Files.writeString(Paths.get(DATA_FILE), json);
        } catch (Exception e) {
            System.err.println("[ReminderService] Error saving reminders JSON: " + e.getMessage());
        }
    }

    private long parseTimeToMs(String timeStr) {
        long now = System.currentTimeMillis();
        timeStr = timeStr.toLowerCase().trim();

        Pattern relPattern = Pattern.compile("^(\\d+)([smhd])$");
        Matcher m = relPattern.matcher(timeStr);
        if (m.matches()) {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2);
            switch (unit) {
                case "s": return now + val * 1000;
                case "m": return now + val * 60 * 1000;
                case "h": return now + val * 60 * 60 * 1000;
                case "d": return now + val * 24 * 60 * 60 * 1000;
            }
        }

        Pattern clockPattern = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
        Matcher cm = clockPattern.matcher(timeStr);
        if (cm.matches()) {
            int hour = Integer.parseInt(cm.group(1));
            int min = Integer.parseInt(cm.group(2));

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (cal.getTimeInMillis() <= now) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return cal.getTimeInMillis();
        }

        if (timeStr.matches("^\\d+$")) {
            long min = Long.parseLong(timeStr);
            return now + min * 60 * 1000;
        }

        return 0;
    }
}

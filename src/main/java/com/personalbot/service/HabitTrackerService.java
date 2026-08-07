package com.personalbot.service;

import com.personalbot.database.DatabaseManager;
import com.personalbot.telegram.TelegramBot.InlineButton;
import com.personalbot.telegram.TelegramBot;
import com.personalbot.util.JsonUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class HabitTrackerService {
    private static final String DATA_FILE = "data/habits.json";
    private final DatabaseManager dbManager;
    private final List<Habit> localHabits = new CopyOnWriteArrayList<>();

    public HabitTrackerService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        if (!dbManager.isDbAvailable()) {
            loadJsonData();
        }
    }

    public static class Habit {
        public String id;
        public String name;
        public int streak;
        public String lastCompletedDate;

        public Habit() {}

        public Habit(String id, String name) {
            this.id = id;
            this.name = name;
            this.streak = 0;
            this.lastCompletedDate = "";
        }
    }

    public String addHabit(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "❌ Название привычки не может быть пустым. Пример: <code>/addhabit 10,000 шагов</code>";
        }
        name = name.trim();
        String id = UUID.randomUUID().toString().substring(0, 8);
        Habit h = new Habit(id, name);

        if (dbManager.isDbAvailable()) {
            String sql = "INSERT INTO habits (id, name, streak, last_completed_date) VALUES (?, ?, ?, ?)";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, h.id);
                stmt.setString(2, h.name);
                stmt.setInt(3, h.streak);
                stmt.setString(4, h.lastCompletedDate);
                stmt.executeUpdate();
                return "✅ Привычка <b>«" + name + "»</b> успешно добавлена!";
            } catch (SQLException e) {
                System.err.println("[HabitTrackerService] Error adding habit: " + e.getMessage());
                return "❌ Ошибка сохранения привычки.";
            }
        } else {
            localHabits.add(h);
            saveJsonData();
            return "✅ Привычка <b>«" + name + "»</b> успешно добавлена!";
        }
    }

    public String markCompleted(String id) {
        String today = getTodayDateStr();
        String yesterday = getYesterdayDateStr();

        List<Habit> habits = getAllHabits();
        for (Habit h : habits) {
            if (h.id.equalsIgnoreCase(id)) {
                if (today.equals(h.lastCompletedDate)) {
                    return "ℹ️ Привычка <b>«" + h.name + "»</b> уже отмечена сегодня!";
                }

                if (yesterday.equals(h.lastCompletedDate)) {
                    h.streak += 1;
                } else {
                    h.streak = 1;
                }
                h.lastCompletedDate = today;

                if (dbManager.isDbAvailable()) {
                    String sql = "UPDATE habits SET streak = ?, last_completed_date = ? WHERE id = ?";
                    try (Connection conn = dbManager.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, h.streak);
                        stmt.setString(2, h.lastCompletedDate);
                        stmt.setString(3, h.id);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        System.err.println("[HabitTrackerService] Error updating habit: " + e.getMessage());
                    }
                } else {
                    saveJsonData();
                }

                return String.format("🎉 <b>Отлично!</b> Привычка <b>«%s»</b> выполнена сегодня!\n🔥 <b>Стрик:</b> %d %s подряд!",
                        h.name, h.streak, getDaysWord(h.streak));
            }
        }
        return "❌ Привычка не найдена.";
    }

    public String deleteHabit(String id) {
        if (dbManager.isDbAvailable()) {
            String sql = "DELETE FROM habits WHERE id = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                if (stmt.executeUpdate() > 0) return "🗑️ Привычка удалена.";
            } catch (SQLException e) {
                System.err.println("[HabitTrackerService] Error deleting habit: " + e.getMessage());
            }
        } else {
            boolean removed = localHabits.removeIf(h -> h.id.equalsIgnoreCase(id));
            if (removed) {
                saveJsonData();
                return "🗑️ Привычка удалена.";
            }
        }
        return "❌ Привычка не найдена.";
    }

    public List<Habit> getAllHabits() {
        if (dbManager.isDbAvailable()) {
            List<Habit> list = new ArrayList<>();
            String sql = "SELECT id, name, streak, last_completed_date FROM habits ORDER BY name ASC";
            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Habit h = new Habit();
                    h.id = rs.getString("id");
                    h.name = rs.getString("name");
                    h.streak = rs.getInt("streak");
                    h.lastCompletedDate = rs.getString("last_completed_date");
                    list.add(h);
                }
            } catch (SQLException e) {
                System.err.println("[HabitTrackerService] Error loading habits: " + e.getMessage());
            }
            return list;
        } else {
            return localHabits;
        }
    }

    public String getFormattedHabitsList() {
        List<Habit> habits = getAllHabits();
        if (habits.isEmpty()) {
            return "🎯 <b>Ваш трекер привычек пуст.</b>\nДобавьте первую привычку командой:\n<code>/addhabit Выпить 2L воды</code>";
        }

        String today = getTodayDateStr();
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>Ваши привычки на сегодня:</b>\n");
        sb.append("─────────────────────\n");

        for (int i = 0; i < habits.size(); i++) {
            Habit h = habits.get(i);
            boolean done = today.equals(h.lastCompletedDate);
            String status = done ? "✅" : "❌";
            sb.append(String.format("%d. %s <b>%s</b>\n   🔥 Стрик: <b>%d %s</b> [/delhabit_%s]\n\n",
                    (i + 1), status, h.name, h.streak, getDaysWord(h.streak), h.id));
        }

        return sb.toString();
    }

    public Map<String, Object> getHabitInlineKeyboard() {
        List<Habit> habits = getAllHabits();
        if (habits.isEmpty()) return null;

        String today = getTodayDateStr();
        List<List<InlineButton>> rows = new ArrayList<>();

        for (Habit h : habits) {
            boolean done = today.equals(h.lastCompletedDate);
            String btnText = (done ? "✅ " : "☐ ") + h.name + " (🔥 " + h.streak + ")";
            String cbData = done ? "habit_already_" + h.id : "habit_done_" + h.id;
            rows.add(Collections.singletonList(new InlineButton(btnText, cbData)));
        }

        return TelegramBot.buildInlineKeyboard(rows);
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadJsonData() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try {
            String content = Files.readString(Paths.get(DATA_FILE));
            Object parsed = JsonUtil.parse(content);
            if (parsed instanceof List) {
                localHabits.clear();
                List<Object> list = (List<Object>) parsed;
                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        Habit h = new Habit();
                        h.id = String.valueOf(map.get("id"));
                        h.name = String.valueOf(map.get("name"));
                        h.streak = map.get("streak") instanceof Number ? ((Number) map.get("streak")).intValue() : 0;
                        h.lastCompletedDate = map.get("lastCompletedDate") != null ? String.valueOf(map.get("lastCompletedDate")) : "";
                        localHabits.add(h);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[HabitTrackerService] Error loading habits JSON: " + e.getMessage());
        }
    }

    private synchronized void saveJsonData() {
        try {
            File dir = new File("data");
            if (!dir.exists()) dir.mkdirs();

            List<Map<String, Object>> list = new ArrayList<>();
            for (Habit h : localHabits) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", h.id);
                map.put("name", h.name);
                map.put("streak", h.streak);
                map.put("lastCompletedDate", h.lastCompletedDate);
                list.add(map);
            }

            String json = JsonUtil.toJson(list);
            Files.writeString(Paths.get(DATA_FILE), json);
        } catch (Exception e) {
            System.err.println("[HabitTrackerService] Error saving habits JSON: " + e.getMessage());
        }
    }

    private String getTodayDateStr() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
    }

    private String getYesterdayDateStr() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    private String getDaysWord(int n) {
        if (n % 10 == 1 && n % 100 != 11) return "день";
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return "дня";
        return "дней";
    }
}

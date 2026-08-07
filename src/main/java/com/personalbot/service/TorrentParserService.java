package com.personalbot.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TorrentParserService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    // Default public RSS feeds for tech, torrents, games
    private final String[] RSS_URLS = new String[]{
            "https://torrentfreak.com/feed/",
            "https://rutor.is/rss.php",
            "https://www.gameranx.com/feed/"
    };

    public static class FeedItem {
        public String title;
        public String link;
        public String pubDate;

        public FeedItem(String title, String link, String pubDate) {
            this.title = title;
            this.link = link;
            this.pubDate = pubDate;
        }
    }

    public String getLatestReleasesFormatted() {
        List<FeedItem> items = fetchLatestItems();
        if (items.isEmpty()) {
            return "🍿 <b>Новинки торрентов / Игр / Технологий</b>\n\n⚠️ <i>Не удалось получить свежие данные с RSS-ленты. Попробуйте позже.</i>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🍿 <b>Свежие новинки торрентов & Игр (RSS)</b>\n");
        sb.append("─────────────────────\n\n");

        for (int i = 0; i < Math.min(8, items.size()); i++) {
            FeedItem item = items.get(i);
            String title = cleanText(item.title);
            String link = item.link != null ? item.link.trim() : "#";
            String date = item.pubDate != null ? item.pubDate.replaceAll(" \\+0000| GMT", "") : "";

            sb.append(String.format("🔹 <b><a href=\"%s\">%s</a></b>\n", link, title));
            if (!date.isEmpty()) {
                sb.append(String.format("   📅 <i>%s</i>\n", date));
            }
            sb.append("\n");
        }

        sb.append("💡 <i>Нажмите на заголовок, чтобы открыть страницу релиза.</i>");
        return sb.toString();
    }

    private List<FeedItem> fetchLatestItems() {
        List<FeedItem> result = new ArrayList<>();
        for (String urlStr : RSS_URLS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    List<FeedItem> parsed = parseRssXml(resp.body());
                    if (!parsed.isEmpty()) {
                        result.addAll(parsed);
                        break; // Successfully loaded from first working RSS
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<FeedItem> parseRssXml(String xml) {
        List<FeedItem> items = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return items;

        Pattern itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = itemPattern.matcher(xml);

        while (m.find()) {
            String itemXml = m.group(1);
            String title = extractTag(itemXml, "title");
            String link = extractTag(itemXml, "link");
            String pubDate = extractTag(itemXml, "pubDate");

            if (!title.isEmpty()) {
                items.add(new FeedItem(title, link, pubDate));
            }
        }
        return items;
    }

    private String extractTag(String xml, String tagName) {
        Pattern p = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        if (m.find()) {
            String content = m.group(1);
            if (content.contains("<![CDATA[")) {
                content = content.replace("<![CDATA[", "").replace("]]>", "");
            }
            return content.trim();
        }
        return "";
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replace("<", "&lt;").replace(">", "&gt;").replaceAll("\\s+", " ").trim();
    }
}

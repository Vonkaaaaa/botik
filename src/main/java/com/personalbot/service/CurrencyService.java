package com.personalbot.service;

import com.personalbot.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class CurrencyService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @SuppressWarnings("unchecked")
    public String getFormattedRates() {
        StringBuilder sb = new StringBuilder();
        sb.append("💵 <b>Курсы валют и криптовалют (Харьков / UAH)</b>\n");
        sb.append("─────────────────────\n\n");

        // Fiat Rates (USD base)
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/USD"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(resp.body());
                Map<String, Object> rates = (Map<String, Object>) json.get("rates");
                if (rates != null) {
                    double uah = parseDouble(rates.get("UAH"));
                    double eur = parseDouble(rates.get("EUR"));
                    double rub = parseDouble(rates.get("RUB"));

                    double eurUah = (uah > 0 && eur > 0) ? (uah / eur) : 0;

                    sb.append("<b>💱 Фиатные валюты:</b>\n");
                    sb.append(String.format("• 🇺🇸 USD / UAH: <b>%.2f ₴</b>\n", uah));
                    sb.append(String.format("• 🇪🇺 EUR / UAH: <b>%.2f ₴</b>\n", eurUah));
                    sb.append(String.format("• 🇪🇺 EUR / USD: <b>%.4f $</b>\n", (eur > 0 ? 1 / eur : 0)));
                    if (rub > 0) {
                        sb.append(String.format("• 🇺🇸 USD / RUB: <b>%.2f ₽</b>\n", rub));
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("⚠️ <i>Не удалось получить фиатные курсы</i>\n\n");
            }
        } catch (Exception e) {
            sb.append("⚠️ <i>Ошибка запроса валют: ").append(e.getMessage()).append("</i>\n\n");
        }

        // Crypto Rates (BTC, ETH, TON, SOL, USDT) in USD & UAH
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,the-open-network,solana,tether&vs_currencies=usd,uah"))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> json = (Map<String, Object>) JsonUtil.parse(resp.body());
                if (json != null) {
                    sb.append("<b>🪙 Криптовалюта:</b>\n");

                    appendCrypto(sb, "₿ Bitcoin (BTC)", json.get("bitcoin"));
                    appendCrypto(sb, "Ξ Ethereum (ETH)", json.get("ethereum"));
                    appendCrypto(sb, "💎 Toncoin (TON)", json.get("the-open-network"));
                    appendCrypto(sb, "◎ Solana (SOL)", json.get("solana"));
                    appendCrypto(sb, "₮ Tether (USDT)", json.get("tether"));
                }
            } else {
                sb.append("⚠️ <i>Не удалось загрузить крипто-курсы</i>\n");
            }
        } catch (Exception e) {
            sb.append("⚠️ <i>Ошибка запроса криптовалют: ").append(e.getMessage()).append("</i>\n");
        }

        sb.append("\n<i>Обновлено: ").append(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date())).append("</i>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendCrypto(StringBuilder sb, String title, Object data) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            double usd = parseDouble(map.get("usd"));
            double uah = parseDouble(map.get("uah"));
            if (usd >= 100) {
                sb.append(String.format("• <b>%s</b>: $%,.2f (%,.0f ₴)\n", title, usd, uah));
            } else {
                sb.append(String.format("• <b>%s</b>: $%.2f (%.2f ₴)\n", title, usd, uah));
            }
        }
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj != null) {
            try { return Double.parseDouble(obj.toString()); } catch (Exception ignored) {}
        }
        return 0.0;
    }
}

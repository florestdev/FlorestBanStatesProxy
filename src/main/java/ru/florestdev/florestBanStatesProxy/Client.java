package ru.florestdev.florestBanStatesProxy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Client {

    // Ссылка на главный класс для доступа к логгеру
    private final FlorestBanStatesProxy plugin;
    private final Map<String, GeoInfo> cache = new ConcurrentHashMap<>();

    // Передаем плагин через конструктор
    public Client(FlorestBanStatesProxy plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<GeoInfo> getInfo(String ip) {
        // 1. Проверка кэша (синхронно, так как это быстро)
        if (cache.containsKey(ip)) {
            plugin.log.info("[FBS] Cache hit for {}", ip);
            return CompletableFuture.completedFuture(cache.get(ip));
        }

        // 2. Асинхронный запрос
        return CompletableFuture.supplyAsync(() -> {
            plugin.log.info("[FBS] Cache miss → requesting ipwho.is for {}", ip);

            try {
                URL url = new URL("https://ipwho.is/" + ip);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(3000);
                con.setReadTimeout(3000);
                con.setRequestProperty("User-Agent", "FlorestBanStates");

                if (con.getResponseCode() != 200) {
                    plugin.log.error("[FBS] ipwho.is returned error code: {}", con.getResponseCode());
                    return null;
                }

                try (InputStreamReader reader = new InputStreamReader(con.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    if (!json.has("success") || !json.get("success").getAsBoolean()) {
                        plugin.log.warn("[FBS] ipwho.is could not find info for IP: {}", ip);
                        return null;
                    }

                    String countryCode = json.get("country_code").getAsString();
                    String region = json.get("region").getAsString();

                    GeoInfo info = new GeoInfo(countryCode, region);

                    // Кэшируем результат перед возвратом
                    cache.put(ip, info);
                    return info;
                }
            } catch (Exception ex) {
                plugin.log.error("[FBS] Error while requesting GeoIP for {}", ip, ex);
                return null;
            }
        }); // Здесь можно добавить , executor, если у вас есть свой пул потоков
    }

    public void clearCache() {
        cache.clear();
        plugin.log.info("[FBS] GeoIP cache cleared.");
    }
}
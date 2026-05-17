package org.nakii.valmora.module.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.nakii.valmora.Valmora;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches Minecraft player skin data from Mojang or mineskin.org.
 *
 * <p>All network I/O runs off the main thread; callbacks are always
 * invoked back on the main thread.
 */
public final class SkinResolver {

    public interface Callback {
        void onSuccess(String texture, String signature);
        void onFailure(String reason);
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String MINESKIN_URL = "https://api.mineskin.org/generate/url";
    private static final String USER_AGENT   = "Valmora-NPC/1.0 (contact: server-admin)";

    private SkinResolver() {}

    /**
     * Fetches the skin for a real Minecraft player by name.
     * Makes two Mojang API calls (name → UUID → signed profile).
     */
    public static void fetch(String playerName, Valmora plugin, Callback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Step 1: name → UUID
                String profileJson = get(PROFILE_URL + playerName);
                if (profileJson == null) {
                    mainThread(plugin, () -> callback.onFailure("Player '" + playerName + "' not found on Mojang."));
                    return;
                }
                JsonObject profileObj = JsonParser.parseString(profileJson).getAsJsonObject();
                String uuid = profileObj.get("id").getAsString();

                // Step 2: UUID → signed profile with textures
                String sessionJson = get(SESSION_URL + uuid + "?unsigned=false");
                if (sessionJson == null) {
                    mainThread(plugin, () -> callback.onFailure("Could not fetch profile for UUID " + uuid));
                    return;
                }
                JsonObject sessionObj = JsonParser.parseString(sessionJson).getAsJsonObject();
                for (var element : sessionObj.getAsJsonArray("properties")) {
                    JsonObject prop = element.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        String value     = prop.get("value").getAsString();
                        String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                        mainThread(plugin, () -> callback.onSuccess(value, signature));
                        return;
                    }
                }
                mainThread(plugin, () -> callback.onFailure("No texture property in Mojang profile."));

            } catch (Exception e) {
                mainThread(plugin, () -> callback.onFailure("HTTP error: " + e.getMessage()));
            }
        });
    }

    /**
     * Uploads an arbitrary skin image URL to mineskin.org to obtain a
     * Mojang-signed texture value+signature pair that Minecraft clients accept.
     *
     * <p>This takes a few seconds as mineskin.org must communicate with Mojang.
     */
    public static void fetchFromUrl(String imageUrl, Valmora plugin, Callback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String body = "{\"url\":\"" + imageUrl + "\",\"variant\":\"classic\"}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(MINESKIN_URL))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() == 429) {
                    mainThread(plugin, () -> callback.onFailure("Mineskin.org rate limit reached — wait a moment and try again."));
                    return;
                }
                if (res.statusCode() != 200) {
                    mainThread(plugin, () -> callback.onFailure("Mineskin.org returned HTTP " + res.statusCode() + ": " + res.body()));
                    return;
                }

                JsonObject root    = JsonParser.parseString(res.body()).getAsJsonObject();
                JsonObject texture = root.getAsJsonObject("data").getAsJsonObject("texture");
                String value     = texture.get("value").getAsString();
                String signature = texture.get("signature").getAsString();
                mainThread(plugin, () -> callback.onSuccess(value, signature));

            } catch (Exception e) {
                mainThread(plugin, () -> callback.onFailure("Failed to process skin URL: " + e.getMessage()));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 204 || res.statusCode() == 404) return null;
        if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode() + " from " + url);
        return res.body();
    }

    private static void mainThread(Valmora plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}

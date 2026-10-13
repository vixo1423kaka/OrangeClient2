package com.example.orangeclient.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LicenseManager {
    // >>> ZMIEN NA ADRES SWOJEGO SERWERA LICENCJI <<<
    private static final String API_URL = "https://Vixo.pythonanywhere.com/validate";

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orangeclient_license.json");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private static volatile boolean valid = false;
    private static volatile boolean checking = false;
    private static String savedKey = "";

    private LicenseManager() { }

    public static boolean isValid() { return valid; }
    public static boolean isChecking() { return checking; }
    public static String getSavedKey() { return savedKey; }

    /** Wywolaj raz przy starcie (OrangeClient.onInitializeClient). */
    public static void init() {
        try {
            if (Files.exists(FILE)) {
                JsonObject o = GSON.fromJson(Files.readString(FILE), JsonObject.class);
                if (o != null && o.has("key")) savedKey = o.get("key").getAsString();
            }
        } catch (Exception ignored) { }
        if (!savedKey.isEmpty()) validate(savedKey);
    }

    /** Future zwraca null gdy klucz OK, w przeciwnym razie tekst bledu. */
    public static CompletableFuture<String> validate(String key) {
        checking = true;
        String k = key.trim();
        JsonObject body = new JsonObject();
        body.addProperty("key", k);
        body.addProperty("hwid", hwid());

        HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    checking = false;
                    try {
                        JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
                        if (o.get("valid").getAsBoolean()) {
                            valid = true;
                            save(k);
                            return (String) null;
                        }
                        valid = false;
                        return o.has("message") ? o.get("message").getAsString() : "Nieprawidlowy klucz";
                    } catch (Exception e) {
                        valid = false;
                        return "Blad odpowiedzi serwera";
                    }
                })
                .exceptionally(e -> {
                    checking = false;
                    valid = false;
                    return "Brak polaczenia z serwerem licencji";
                });
    }

    private static void save(String key) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("key", key);
            Files.writeString(FILE, o.toString());
            savedKey = key;
        } catch (Exception ignored) { }
    }

    private static String hwid() {
        try {
            String raw = System.getProperty("user.name") + System.getProperty("os.name")
                    + System.getProperty("os.arch") + System.getenv("COMPUTERNAME")
                    + System.getenv("PROCESSOR_IDENTIFIER") + Runtime.getRuntime().availableProcessors();
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Pokazuje okno licencji, jesli klucz nie jest aktywny. */
    public static void requireLicense(MinecraftClient mc) {
        if (valid || checking) return;
        if (!(mc.currentScreen instanceof LicenseScreen)) {
            mc.setScreen(new LicenseScreen(mc.currentScreen));
        }
    }
}

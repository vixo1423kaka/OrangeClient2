package com.example.orangeclient.friend;

import com.example.orangeclient.OrangeClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Lista znajomych. Kazdy modul bojowy (KillAura, TriggerBot itp.) ma przed wybraniem celu
 * sprawdzic {@link #isFriend(Entity)} i pominac znajomych; moduly renderujace uzywaja
 * tego samego do podswietlenia ich innym kolorem.
 */
public final class Friends {
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** klucz: nick malymi literami, wartosc: nick tak jak zostal wpisany */
    private static final Map<String, String> FRIENDS = new TreeMap<>();

    private Friends() { }

    public static boolean isValidNick(String nick) {
        return nick != null && NICK.matcher(nick).matches();
    }

    public static boolean isFriend(String nick) {
        return nick != null && FRIENDS.containsKey(nick.toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(Entity e) {
        return e instanceof PlayerEntity p && isFriend(p.getGameProfile().getName());
    }

    public static boolean add(String nick) {
        if (!isValidNick(nick) || isFriend(nick)) return false;
        FRIENDS.put(nick.toLowerCase(Locale.ROOT), nick);
        save();
        return true;
    }

    public static void remove(String nick) {
        if (FRIENDS.remove(nick.toLowerCase(Locale.ROOT)) != null) save();
    }

    public static List<String> all() {
        return new ArrayList<>(FRIENDS.values());
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("orangeclient-friends.json");
    }

    private static void save() {
        try {
            JsonArray arr = new JsonArray();
            FRIENDS.values().forEach(arr::add);
            Files.writeString(file(), GSON.toJson(arr));
        } catch (Exception e) {
            OrangeClient.LOGGER.error("Nie udalo sie zapisac listy friends", e);
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            FRIENDS.clear();
            for (JsonElement el : JsonParser.parseString(Files.readString(f)).getAsJsonArray()) {
                String n = el.getAsString();
                if (isValidNick(n)) FRIENDS.put(n.toLowerCase(Locale.ROOT), n);
            }
        } catch (Exception e) {
            OrangeClient.LOGGER.error("Nie udalo sie wczytac listy friends", e);
        }
    }
}

package com.example.orangeclient.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lista ID blokow (np. "minecraft:diamond_ore") edytowana w osobnym oknie (BlockPickerScreen),
 * dlatego w ClickGui jest ukryta. Zapisywana w configu jako tablica; stary format (tekst po przecinku) tez sie wczyta.
 */
public class BlockListSetting extends Setting {
    private final Set<String> ids = new LinkedHashSet<>();
    private int version;

    public BlockListSetting(String name) {
        super(name);
        this.hidden = true;
    }

    private static String normalize(String s) {
        s = s.trim().toLowerCase();
        if (s.isEmpty()) return "";
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    public boolean contains(String id) { return ids.contains(id); }

    public Set<String> ids() { return Collections.unmodifiableSet(ids); }

    /** Zmienia sie przy kazdej modyfikacji listy (do odswiezania cache w module). */
    public int version() { return version; }

    public void add(String id) {
        if (ids.add(id)) version++;
    }

    public void remove(String id) {
        if (ids.remove(id)) version++;
    }

    public void toggle(String id) {
        if (!ids.remove(id)) ids.add(id);
        version++;
    }

    public void clear() {
        if (!ids.isEmpty()) {
            ids.clear();
            version++;
        }
    }

    @Override
    public JsonElement toJson() {
        JsonArray a = new JsonArray();
        for (String id : ids) a.add(id);
        return a;
    }

    @Override
    public void fromJson(JsonElement e) {
        ids.clear();
        if (e.isJsonArray()) {
            for (JsonElement el : e.getAsJsonArray()) {
                if (!el.isJsonPrimitive()) continue;
                String id = normalize(el.getAsString());
                if (!id.isEmpty()) ids.add(id);
            }
        } else if (e.isJsonPrimitive()) {
            // migracja ze starego pola tekstowego: "diamond_ore, ancient_debris"
            for (String part : ((JsonPrimitive) e).getAsString().split(",")) {
                String id = normalize(part);
                if (!id.isEmpty()) ids.add(id);
            }
        }
        version++;
    }
}

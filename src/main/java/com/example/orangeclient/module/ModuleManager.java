package com.example.orangeclient.module;

import com.example.orangeclient.OrangeClient;
import com.example.orangeclient.module.combat.AimAssist;
import com.example.orangeclient.module.combat.AutoRod;
import com.example.orangeclient.module.combat.AutoWeb;
import com.example.orangeclient.module.combat.Hitbox;
import com.example.orangeclient.module.combat.KillAura;
import com.example.orangeclient.module.combat.KnockbackDisplacement;
import com.example.orangeclient.module.combat.TriggerBot;
import com.example.orangeclient.module.fun.Spammer;
import com.example.orangeclient.module.movement.FakeLag;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.module.movement.GuiMove;
import com.example.orangeclient.module.movement.NoJumpDelay;
import com.example.orangeclient.module.movement.Sprint;
import com.example.orangeclient.module.player.AntiTrap;
import com.example.orangeclient.module.player.AutoDripstone;
import com.example.orangeclient.module.player.AutoTool;
import com.example.orangeclient.module.player.ErrorKiller;
import com.example.orangeclient.module.player.FastBreak;
import com.example.orangeclient.module.player.FastPlace;
import com.example.orangeclient.module.player.NoPush;
import com.example.orangeclient.module.render.BlockESP;
import com.example.orangeclient.module.render.ESP;
import com.example.orangeclient.module.render.Fullbright;
import com.example.orangeclient.module.render.GuiModule;
import com.example.orangeclient.module.render.Hud;
import com.example.orangeclient.module.render.ItemESP;
import com.example.orangeclient.module.render.LogoutSpots;
import com.example.orangeclient.module.render.NameProtect;
import com.example.orangeclient.module.render.Nametags;
import com.example.orangeclient.module.render.Removals;
import com.example.orangeclient.module.render.Tracers;
import com.example.orangeclient.setting.ActionSetting;
import com.example.orangeclient.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loading = false;

    private ModuleManager() { }

    public static void init() {
        MODULES.add(new TriggerBot());
        MODULES.add(new KillAura());
        MODULES.add(new AimAssist());
        MODULES.add(new AutoRod());
        MODULES.add(new AutoWeb());
        MODULES.add(new Hitbox());
        MODULES.add(new KnockbackDisplacement());
        MODULES.add(new Freecam());
        MODULES.add(new FakeLag());
        MODULES.add(new GuiMove());
        MODULES.add(new NoJumpDelay());
        MODULES.add(new Sprint());
        MODULES.add(new FastPlace());
        MODULES.add(new FastBreak());
        MODULES.add(new NoPush());
        MODULES.add(new AntiTrap());
        MODULES.add(new AutoTool());
        MODULES.add(new AutoDripstone());
        MODULES.add(new ErrorKiller());
        MODULES.add(new ESP());
        MODULES.add(new ItemESP());
        MODULES.add(new BlockESP());
        MODULES.add(new LogoutSpots());
        MODULES.add(new Tracers());
        MODULES.add(new Nametags());
        MODULES.add(new Fullbright());
        MODULES.add(new NameProtect());
        MODULES.add(new Removals());
        MODULES.add(new Spammer());
        MODULES.add(new Hud());
        MODULES.add(new GuiModule());
        load();
    }

    public static List<Module> all() { return MODULES; }

    /** True podczas wczytywania configu (wtedy nie pokazujemy powiadomien). */
    public static boolean isLoading() { return loading; }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (type.isInstance(m)) return (T) m;
        return null;
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category == c) out.add(m);
        return out;
    }

    public static void onPreTick(MinecraftClient mc) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            m.onPreTick(mc);
        }
    }

    public static void onTick(MinecraftClient mc) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            m.onTick(mc);
        }
    }

    public static void onWorldRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            m.onWorldRender(ctx, mc);
        }
    }

    public static void onHudRender(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            m.onHudRender(ctx, mc);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("orangeclient.json");
    }

    public static void save() {
        if (loading) return;
        try {
            JsonObject root = new JsonObject();
            for (Module m : MODULES) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.isEnabledRaw());
                o.addProperty("bind", m.getBind() == null ? "" : m.getBind().getTranslationKey());
                o.addProperty("bindMode", m.getBindMode().name());
                JsonObject s = new JsonObject();
                for (Setting st : m.settings) {
                    if (st instanceof ActionSetting) continue;
                    s.add(st.name, st.toJson());
                }
                o.add("settings", s);
                root.add(m.name, o);
            }
            Files.writeString(file(), GSON.toJson(root));
        } catch (Exception e) {
            OrangeClient.LOGGER.error("Nie udalo sie zapisac configu", e);
        }
    }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        loading = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            // migracja: NoEntityInteraction zostal przemianowany na AntiTrap
            if (root.has("NoEntityInteraction") && !root.has("AntiTrap")) {
                root.add("AntiTrap", root.remove("NoEntityInteraction"));
            }
            for (Module m : MODULES) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting st : m.settings) {
                        JsonElement e = s.get(st.name);
                        if (e != null) st.fromJson(e);
                    }
                }
                if (o.has("bind")) {
                    String bs = o.get("bind").getAsString();
                    try { m.setBind(bs.isEmpty() ? null : InputUtil.fromTranslationKey(bs)); } catch (Exception ignored) { }
                }
                if (o.has("bindMode")) {
                    try { m.setBindMode(BindMode.valueOf(o.get("bindMode").getAsString())); } catch (Exception ignored) { }
                }
                if (o.has("enabled") && o.get("enabled").getAsBoolean()) m.setEnabled(true);
            }
        } catch (Exception e) {
            OrangeClient.LOGGER.error("Nie udalo sie wczytac configu", e);
        } finally {
            loading = false;
        }
    }
}

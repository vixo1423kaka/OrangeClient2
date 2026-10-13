package com.example.orangeclient.module;

import com.example.orangeclient.gui.Notifications;
import com.example.orangeclient.license.LicenseManager;
import com.example.orangeclient.setting.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    // --- bind (klawisz lub przycisk myszy) + tryb
    private InputUtil.Key bind;
    private BindMode bindMode = BindMode.TOGGLE;
    /** Stan klawisza w poprzedniej klatce (do wykrywania wcisniecia/puszczenia). */
    boolean bindDown;

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    /** Modul jest aktywny tylko z wazna licencja. */
    public boolean isEnabled() { return enabled && LicenseManager.isValid(); }

    /** Surowy stan (do zapisu configu, niezalezny od licencji). */
    public boolean isEnabledRaw() { return enabled; }

    public InputUtil.Key getBind() { return bind; }
    public void setBind(InputUtil.Key key) { this.bind = key; }
    public BindMode getBindMode() { return bindMode; }
    public void setBindMode(BindMode mode) { this.bindMode = mode; }

    /**
     * Krotka nazwa bindu: przyciski myszy jako M1..M9 (M4/M5 = boczne), klawisze np. R, F5, LShift, RCtrl, Space.
     * Bierzemy ja z klucza tlumaczenia, wiec nie zalezy od jezyka gry.
     */
    public String getBindName() {
        if (bind == null) return "";
        if (bind.getCategory() == InputUtil.Type.MOUSE) return "M" + (bind.getCode() + 1);

        String tk = bind.getTranslationKey();                       // np. key.keyboard.left.shift
        String path = tk.startsWith("key.keyboard.") ? tk.substring("key.keyboard.".length()) : tk;
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("\\.")) {
            switch (part) {
                case "left" -> sb.append('L');
                case "right" -> sb.append('R');
                case "control" -> sb.append("Ctrl");
                case "keypad" -> sb.append("KP");
                default -> {
                    if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    /** Etykieta na karcie modułu (przycieta, zeby sie miescila). */
    public String getBindLabel() {
        String s = getBindName();
        return s.length() > 7 ? s.substring(0, 7) : s;
    }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        if (e && !ModuleManager.isLoading() && !LicenseManager.isValid()) return; // brak licencji
        enabled = e;
        if (!ModuleManager.isLoading()) Notifications.push(name, e);
        if (e) onEnable(); else onDisable();
        ModuleManager.save();
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() { }
    protected void onDisable() { }
    /** Wywolywane na poczatku ticku, jeszcze przed odczytem klawiszy gracza. */
    public void onPreTick(MinecraftClient mc) { }
    public void onTick(MinecraftClient mc) { }
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) { }
    public void onHudRender(DrawContext ctx, MinecraftClient mc) { }
}

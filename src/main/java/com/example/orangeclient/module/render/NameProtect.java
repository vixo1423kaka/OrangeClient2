package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.StringSetting;
import net.minecraft.client.MinecraftClient;

/**
 * NameProtect: Twoj nick jest podmieniany na wpisany tekst wszedzie tam, gdzie gra rysuje tekst
 * (czat, tab, nametagi, HUD, GUI). Dziala tylko wizualnie po stronie klienta - serwer dalej widzi prawdziwy nick.
 */
public class NameProtect extends Module {
    private final StringSetting name = add(new StringSetting("Name", "Protected", 16));

    public NameProtect() {
        super("NameProtect", Category.RENDER);
    }

    /** Wywolywane z mixina dla kazdego rysowanego tekstu - musi byc tanie. */
    public static String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        NameProtect m = ModuleManager.get(NameProtect.class);
        if (m == null || !m.isEnabled()) return text;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return text;
        String real = mc.getSession().getUsername();
        if (real == null || real.isEmpty() || !text.contains(real)) return text;

        String fake = m.name.get().isEmpty() ? "Protected" : m.name.get();
        return text.replace(real, fake);
    }
}

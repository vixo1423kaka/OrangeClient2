package com.example.orangeclient.module.render;

import com.example.orangeclient.gui.HudEditorScreen;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.ActionSetting;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.ModeSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Modul "HUD": lista wlaczonych modulow i powiadomienia, kazde z wlasnym rogiem ekranu
 * (Anchor) oraz odsunieciem od krawedzi w pikselach (X / Y).
 * Pozycje ustawiasz przeciaganiem myszka: przycisk "Edit HUD" w ustawieniach modulu (HudEditorScreen).
 * Gdy modul jest wylaczony: lista sie nie pokazuje, a powiadomienia maja domyslne miejsce (prawy dol).
 */
public class Hud extends Module {
    private static final String[] ANCHORS = {"Top Left", "Top Right", "Bottom Left", "Bottom Right"};
    private static final String[] SAMPLE_LIST = {"TriggerBot", "ESP", "Fullbright"};

    private final ActionSetting editHud = add(new ActionSetting("Edit HUD", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new HudEditorScreen(mc.currentScreen));
    }));

    // lista modulow
    private final BoolSetting list = add(new BoolSetting("ModuleList", true));
    private final ModeSetting listAnchor = add(new ModeSetting("ListAnchor", "Top Right", ANCHORS));
    private final NumberSetting listX = add(new NumberSetting("ListX", 4, 0, 3000, 1));
    private final NumberSetting listY = add(new NumberSetting("ListY", 4, 0, 3000, 1));
    private final BoolSetting sort = add(new BoolSetting("Sort", true));
    private final BoolSetting bar = add(new BoolSetting("Bar", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF7A00, true));

    // powiadomienia
    private final BoolSetting notifs = add(new BoolSetting("Notifications", true));
    private final ModeSetting notifAnchor = add(new ModeSetting("NotifAnchor", "Bottom Right", ANCHORS));
    private final NumberSetting notifX = add(new NumberSetting("NotifX", 8, 0, 3000, 1));
    private final NumberSetting notifY = add(new NumberSetting("NotifY", 8, 0, 3000, 1));

    /** Ta sama czcionka co w menu (assets/orangeclient/font/ui.json); bez niej zwykla czcionka MC. */
    private static final Identifier FONT_ID = Identifier.of("orangeclient", "ui");
    private static final Identifier FONT_FILE = Identifier.of("orangeclient", "font/ui.ttf");
    private static Boolean customFont;

    private static MutableText font(String s) {
        if (customFont == null) {
            customFont = MinecraftClient.getInstance().getResourceManager().getResource(FONT_FILE).isPresent();
        }
        MutableText m = Text.literal(s);
        return customFont ? m.styled(st -> st.withFont(FONT_ID)) : m;
    }

    public Hud() {
        super("HUD", Category.RENDER);
        // pozycje ustawia sie przeciagajac w "Edit HUD", wiec suwaki i kotwice chowamy z menu (config dalej je zapisuje)
        for (Setting s : new Setting[]{listAnchor, listX, listY, notifAnchor, notifX, notifY}) s.hidden = true;
    }

    // ------------------------------------------------------------------ powiadomienia (czytane przez Notifications)

    private static Hud active() {
        Hud h = ModuleManager.get(Hud.class);
        return h != null && h.isEnabled() ? h : null;
    }

    public static boolean notifVisible() { Hud h = active(); return h == null || h.notifs.get(); }
    public static boolean notifRight() { Hud h = active(); return h == null || h.notifAnchor.get().endsWith("Right"); }
    public static boolean notifBottom() { Hud h = active(); return h == null || h.notifAnchor.get().startsWith("Bottom"); }
    public static int notifX() { Hud h = active(); return h == null ? 8 : (int) h.notifX.get(); }
    public static int notifY() { Hud h = active(); return h == null ? 8 : (int) h.notifY.get(); }

    // ------------------------------------------------------------------ edytor pozycji (HudEditorScreen)

    public boolean listEnabled() { return list.get(); }
    public boolean notifsEnabled() { return notifs.get(); }

    /** Lewy gorny rog powiadomienia o rozmiarze w x h wg aktualnych ustawien (bez wzgledu na to, czy modul jest wlaczony). */
    public int[] notifPos(int sw, int sh, int w, int h) {
        boolean right = notifAnchor.get().endsWith("Right"), bottom = notifAnchor.get().startsWith("Bottom");
        int mx = (int) notifX.get(), my = (int) notifY.get();
        return new int[]{right ? sw - mx - w : mx, bottom ? sh - my - h : my};
    }

    /** Ustawia powiadomienie tak, zeby jego lewy gorny rog byl w (x, y): kotwica = najblizszy rog ekranu. */
    public void setNotifPos(int sw, int sh, int x, int y, int w, int h) {
        boolean right = x + w / 2 > sw / 2, bottom = y + h / 2 > sh / 2;
        notifAnchor.set((bottom ? "Bottom " : "Top ") + (right ? "Right" : "Left"));
        notifX.set(right ? sw - x - w : x);
        notifY.set(bottom ? sh - y - h : y);
    }

    /** Pudelko listy {x1, y1, x2, y2} (razem z paskiem) wg aktualnych ustawien. */
    public int[] listBox(int sw, int sh, int textW, int rows) {
        boolean right = listAnchor.get().endsWith("Right"), bottom = listAnchor.get().startsWith("Bottom");
        int ox = (int) listX.get(), oy = (int) listY.get();
        int x1 = right ? sw - ox - textW - 3 : ox - 4;
        int y1 = bottom ? sh - oy - rows * 10 : oy - 1;
        return new int[]{x1, y1, x1 + textW + 7, y1 + rows * 10};
    }

    /** Ustawia liste tak, zeby lewy gorny rog jej pudelka byl w (x1, y1): kotwica = najblizszy rog ekranu. */
    public void setListPos(int sw, int sh, int x1, int y1, int textW, int rows) {
        int x2 = x1 + textW + 7, y2 = y1 + rows * 10;
        boolean right = (x1 + x2) / 2 > sw / 2, bottom = (y1 + y2) / 2 > sh / 2;
        listAnchor.set((bottom ? "Bottom " : "Top ") + (right ? "Right" : "Left"));
        listX.set(right ? sw - x2 + 4 : x1 + 4);
        listY.set(bottom ? sh - y2 : y1 + 1);
    }

    public int listWidth(TextRenderer tr, List<String> names) {
        int w = 0;
        for (String n : names) w = Math.max(w, tr.getWidth(font(n)));
        return w;
    }

    /** Nazwy do narysowania w liscie: wlaczone moduly (bez HUD), opcjonalnie posortowane od najdluzszej. */
    private List<String> activeNames(TextRenderer tr) {
        List<String> names = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && m != this) names.add(m.name);
        }
        sortNames(tr, names);
        return names;
    }

    private void sortNames(TextRenderer tr, List<String> names) {
        if (sort.get()) names.sort((a, b) -> tr.getWidth(font(b)) - tr.getWidth(font(a)));
    }

    /** Jak activeNames, ale gdy nic nie jest wlaczone pokazuje przykladowe nazwy (do edytora). */
    public List<String> listPreviewNames(TextRenderer tr) {
        List<String> names = activeNames(tr);
        if (names.isEmpty()) {
            for (String s : SAMPLE_LIST) names.add(s);
            sortNames(tr, names);
        }
        return names;
    }

    // ------------------------------------------------------------------ lista modulow

    @Override
    public void onHudRender(DrawContext ctx, MinecraftClient mc) {
        if (!list.get()) return;
        drawList(ctx, mc.textRenderer, activeNames(mc.textRenderer));
    }

    public void drawList(DrawContext ctx, TextRenderer tr, List<String> names) {
        boolean right = listAnchor.get().endsWith("Right");
        boolean bottom = listAnchor.get().startsWith("Bottom");
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int ox = (int) listX.get(), oy = (int) listY.get();
        int argb = color.argb();

        for (int i = 0; i < names.size(); i++) {
            MutableText label = font(names.get(i));
            int w = tr.getWidth(label);
            int y = bottom ? sh - oy - 9 - i * 10 : oy + i * 10;

            if (right) {
                int edge = sw - ox;
                int x = edge - w;
                if (bar.get()) {
                    ctx.fill(x - 3, y - 1, edge + 2, y + 9, 0x66000000);      // tlo wiersza
                    ctx.fill(edge + 2, y - 1, edge + 4, y + 9, argb);          // pasek po prawej
                }
                ctx.drawTextWithShadow(tr, label, x, y, argb);
            } else {
                int x = ox;
                if (bar.get()) {
                    ctx.fill(x - 2, y - 1, x + w + 3, y + 9, 0x66000000);
                    ctx.fill(x - 4, y - 1, x - 2, y + 9, argb);                // pasek po lewej
                }
                ctx.drawTextWithShadow(tr, label, x, y, argb);
            }
        }
    }
}

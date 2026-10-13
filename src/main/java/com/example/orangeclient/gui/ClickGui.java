package com.example.orangeclient.gui;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.BindMode;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.ActionSetting;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.module.render.GuiModule;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.ModeSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.setting.Setting;
import com.example.orangeclient.setting.Slidable;
import com.example.orangeclient.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClickGui extends Screen {
    private static final int MAX_COL_W = 118, MIN_COL_W = 84, COL_GAP = 12, ROW_H = 18, SET_H = 14;

    // paleta (ARGB)
    private static final int ORANGE = 0xFFFF7A00;
    private static final int OUTLINE = 0xFF2B221B;
    private static final int TEXT_GREY = 0xFFBDBDBD;
    private static final int TEXT_DIM = 0xFF8C837B;

    private static final Identifier ORANGE_TEX = Identifier.of("orangeclient", "textures/gui/orange.png");
    private static final Identifier GLOW_TEX = Identifier.of("orangeclient", "textures/gui/glow.png");
    private static final int GLOW_SIZE = 512;
    /** Rozmiar tekstury orange.png (px). Jesli podmienisz grafike na inna, zmien tutaj. */
    private static final int TEX_SIZE = 512;
    /** Jaka czesc tekstury zajmuje sama pomarancz (reszta to poswiata). */
    private static final float ORANGE_SHARE = 0.46f;

    private enum Tab {
        MODULES("Modules"), CONFIGS("Configs"), FRIENDS("Friends");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static Tab currentTab = Tab.MODULES;
    private static final Set<Module> EXPANDED = new HashSet<>();
    private static String query = "";

    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right, Slidable slider) {
        boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Hit dragging;

    // --- bindowanie: srodkowy klik na module otwiera okno wyboru klawisza i trybu
    private record BindHit(int x1, int y1, int x2, int y2, Module mod) {
        boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }

    private final List<BindHit> bindHits = new ArrayList<>();
    private final List<Hit> editHits = new ArrayList<>();   // klikalne "Hold" / "Toggle" na karcie w trakcie edycji
    private Module bindModule;          // modul edytowany na karcie (null = brak edycji); w tym stanie czekamy na klawisz

    // pozycja panelu (przesuniecie wzgledem srodka ekranu) - pamietana miedzy otwarciami
    private static float offX, offY;
    private boolean movingPanel;
    private int panelX, panelY, panelW, panelH;
    private int centerX, centerY;          // pozycja panelu wysrodkowanego (baza dla offX/offY)
    private double grabX, grabY;           // gdzie w panelu zlapales naglowek
    private static final int HEADER_H = 28;
    private static final int TAB_H = 28;          // dolny pasek z zakladkami
    private static final long OPEN_MS = 520L;     // czas animacji otwarcia menu
    private static final long CLOSE_MS = 300L;    // czas animacji zamykania menu
    private boolean closing, closed;
    private long closeStart;
    private float dispX, dispY, lift;             // pozycja panelu pokazywana na ekranie (wygladzona) i "uniesienie"
    private boolean posInit;

    // --- animacje
    private final long openStart = Util.getMeasuringTimeMs();
    private long lastFrame;
    private float dt = 0.016f;                                  // czas klatki w sekundach
    private long tabAnimStart;                                  // start animacji przejscia miedzy zakladkami
    private int slideDir = 1;                                   // kierunek wsuwania zawartosci
    private float pillIdx = currentTab.ordinal();               // pozycja (animowana) podswietlenia zakladki
    private final Map<Module, Float> expandAnim = new HashMap<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();   // postep rozwijania ustawien modulu 0..1

    private enum Focus { NONE, SEARCH, FRIEND, SETTING }

    private Focus focus = Focus.NONE;
    private StringSetting editingSetting;   // pole tekstowe edytowane w tej chwili (Focus.SETTING)
    private static String friendInput = "";

    // pole wyszukiwania
    private int sbx1, sby1, sbx2, sby2;

    // obszar zawartosci + przewijanie
    private int colW = MAX_COL_W;
    private int viewX1, viewY1, viewX2, viewY2;
    private int contentHitStart;
    private float scrollY, maxScroll;

    // komunikat w zakladce Friends
    private String statusMsg = "";
    private boolean statusOk;
    private long statusUntil;

    public ClickGui() {
        super(Text.literal("Orange Client"));
    }


    // ------------------------------------------------------------------ czcionka menu (Instrument Sans, OFL)

    /** Pliki czcionek: assets/orangeclient/font/ui.ttf i ui_bold.ttf (definicje: ui.json, ui_bold.json). */
    private static final Identifier FONT_ID = Identifier.of("orangeclient", "ui");
    private static final Identifier FONT_BOLD_ID = Identifier.of("orangeclient", "ui_bold");
    private static final Identifier FONT_FILE = Identifier.of("orangeclient", "font/ui.ttf");
    /** Czy czcionka jest w modzie - jak jej nie ma, menu uzywa zwyklej czcionki Minecrafta. */
    private final boolean customFont = net.minecraft.client.MinecraftClient.getInstance()
            .getResourceManager().getResource(FONT_FILE).isPresent();

    // ------------------------------------------------------------------ dzwiek klikniecia

    private static final SoundEvent CLICK_SOUND = SoundEvent.of(Identifier.of("orangeclient", "gui.click"));
    private static final float CLICK_VOLUME = 0.8f;

    private void playClick() {
        if (client == null) return;
        client.getSoundManager().play(PositionedSoundInstance.master(CLICK_SOUND, 1.0f, CLICK_VOLUME));
    }

    private MutableText fnt(String s) { return fnt(Text.literal(s)); }

    private MutableText fnt(Text t) {
        MutableText m = t.copy();
        if (!customFont) return m;
        return m.styled(st -> st.isBold() ? st.withBold(false).withFont(FONT_BOLD_ID) : st.withFont(FONT_ID));
    }

    /** trimToWidth z uwzglednieniem czcionki menu. */
    private String trim(String s, int maxW) {
        while (s.length() > 1 && textRenderer.getWidth(fnt(s)) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        bindHits.clear();
        editHits.clear();
        long now = Util.getMeasuringTimeMs();
        dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        // ekran poza menu jest w 100% przezroczysty (widac swiat gry) - nic tu nie rysujemy

        // --- pozycja panelu (mozna go przesuwac za nagolwek)
        int pw = Math.min(width - 10, Math.max(420, Math.min(620, (int) (width * 0.68f))));
        int ph = Math.min(height - 10, Math.max(240, Math.min(340, (int) (height * 0.7f))));
        int cxC = (width - pw) / 2, cyC = (height - ph) / 2;
        // limity luzne: panel moze czesciowo wyjechac poza ekran, ale naglowek (uchwyt) zawsze zostaje widoczny
        int tx = Math.max(-(pw - 80), Math.min(width - 80, Math.round(cxC + offX)));
        int ty0 = Math.max(-12, Math.min(height - 36, Math.round(cyC + offY)));
        offX = tx - cxC;
        offY = ty0 - cyC;

        // plynne podazanie panelu za kursorem (zamiast skokow) + lekkie "uniesienie" podczas przeciagania
        if (!posInit) { dispX = tx; dispY = ty0; posInit = true; }
        float follow = 1f - (float) Math.exp(-dt * 18f);
        dispX += (tx - dispX) * follow;
        dispY += (ty0 - dispY) * follow;
        if (Math.abs(tx - dispX) < 0.05f) dispX = tx;
        if (Math.abs(ty0 - dispY) < 0.05f) dispY = ty0;
        lift += ((movingPanel ? 1f : 0f) - lift) * (1f - (float) Math.exp(-dt * 14f));
        int px = Math.round(dispX), py = Math.round(dispY);
        panelX = px; panelY = py; panelW = pw; panelH = ph;
        centerX = cxC; centerY = cyC;
        int r = 18;

        // Powrot do starego charakteru animacji: krotki, plynny slide + zoom,
        // ale z delikatnym overshootem i lagodnym fade-in dla bardziej premium efektu.
        float op = Math.min(1f, (now - openStart) / (float) OPEN_MS);
        float baseEase = 1f - (1f - op) * (1f - op) * (1f - op);
        float oe = op >= 1f ? 1f : Math.min(1.035f,
                baseEase + (float) (Math.sin(op * Math.PI) * 0.035f * (1f - op)));
        float fade = op * op * (3f - 2f * op);
        ctx.fill(0, 0, width, height, ((int) (0x78 * fade) << 24));
        var m = ctx.getMatrices();
        m.push();
        float pcx = px + pw / 2f, pcy = py + ph / 2f;
        // Przy zamykaniu panel nadal zapada sie do srodka jak w starej animacji.
        float cp = closing ? Math.min(1f, (now - closeStart) / (float) CLOSE_MS) : 0f;
        float ce = cp * cp * cp;
        float os = (0.82f + 0.18f * oe) * (1f + 0.02f * lift) * Math.max(0.02f, 1f - ce);
        m.translate(pcx, pcy + (1f - oe) * 16f + ce * 12f, 0f);
        m.scale(os, os, 1f);
        m.translate(-pcx, -pcy, 0f);

        // --- poswiata + tlo (gradient, ~50% przezroczystosci) + ramka
        for (int i = 8; i >= 1; i--) {
            float f = 1f - i / 9f;
            int a = (int) (0x48 * f * f);
            roundRing(ctx, px - i, py - i, px + pw + i, py + ph + i, r + i, (a << 24) | 0xFF7A00);
        }
        roundRectGradient(ctx, px + 1, py + 1, px + pw - 1, py + ph - 1, r - 1, 0x8A25160C, 0x7A0A0705, true);
        roundRing(ctx, px, py, px + pw, py + ph, r, ORANGE);

        // --- kregaca sie pomarancz za cala reszta
        drawOrange(ctx, px + pw / 2f, py + ph / 2f, ph);

        // --- dolny pasek zakladek (zaokraglony tylko na dole)
        int barY1 = py + ph - TAB_H;
        roundRectGradientBottom(ctx, px + 1, barY1, px + pw - 1, py + ph - 1, r - 1, 0x40000000, 0x70000000);
        fadeLine(ctx, px + 10, px + pw - 10, barY1, 0xFF7A00, 0xC0);

        // --- naglowek (uchwyt do przesuwania): pomaranczowa poswiata + linia akcentu
        roundRectGradient(ctx, px + 1, py + 1, px + pw - 1, py + HEADER_H, r - 1, 0x40FF7A00, 0x12FF7A00, false);
        fadeLine(ctx, px + 10, px + pw - 10, py + HEADER_H, 0xFF7A00, 0xC0);
        // Animated specular sweep: a bright highlight glides across the header every 1.8s.
        float sweep = (now % 1800L) / 1800f;
        int sweepX = px + 12 + Math.round((pw - 24) * sweep);
        fadeLine(ctx, Math.max(px + 10, sweepX - 18), Math.min(px + pw - 10, sweepX + 18),
                py + HEADER_H - 1, 0xFFFFD19A, 0xE8);
        drawIcon(ctx, px + 17, py + 15, 15f);
        m.push();
        m.translate(px + 30, py + 9, 0f);
        m.scale(1.2f, 1.2f, 1f);
        ctx.drawTextWithShadow(textRenderer, fnt(Text.literal("Orange Client").formatted(Formatting.BOLD)), 0, 0, ORANGE);
        m.pop();

        // --- zakladki na dole (podswietlenie plynnie przesuwa sie miedzy nimi)
        Tab[] tabs = Tab.values();
        int tabW = 76, tabGap = 8;
        int tabsTotal = tabs.length * tabW + (tabs.length - 1) * tabGap;
        int tabsX = px + (pw - tabsTotal) / 2;
        int ty = barY1 + 5;
        float targetIdx = currentTab.ordinal();
        pillIdx += (targetIdx - pillIdx) * (1f - (float) Math.exp(-dt * 16f));
        if (Math.abs(targetIdx - pillIdx) < 0.003f) pillIdx = targetIdx;
        int pillX = tabsX + Math.round(pillIdx * (tabW + tabGap));
        roundRectGradient(ctx, pillX, ty, pillX + tabW, ty + 18, 9, 0xFFEC7D10, 0xFFBF5A07, true);
        roundRing(ctx, pillX, ty, pillX + tabW, ty + 18, 9, 0x55FFD9A8);

        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int bx1 = tabsX + i * (tabW + tabGap), bx2 = bx1 + tabW;
            boolean selected = t == currentTab;
            boolean hover = mouseX >= bx1 && mouseX < bx2 && mouseY >= ty && mouseY < ty + 18;
            if (!selected && hover) roundRect(ctx, bx1, ty, bx2, ty + 18, 9, 0x2AFF7A00);
            Text label = selected ? Text.literal(t.label).formatted(Formatting.BOLD) : Text.literal(t.label);
            ctx.drawCenteredTextWithShadow(textRenderer, fnt(label), (bx1 + bx2) / 2, ty + 5,
                    selected ? 0xFFFFFFFF : (hover ? 0xFFFFC890 : TEXT_GREY));
            hits.add(new Hit(bx1, ty, bx2, ty + 18, () -> {
                if (t == currentTab) return;
                slideDir = t.ordinal() > currentTab.ordinal() ? 1 : -1;
                currentTab = t;
                scrollY = 0;
                tabAnimStart = Util.getMeasuringTimeMs();
            }, null, null));
        }

        // --- pole Search...
        sbx2 = px + pw - 12;
        sbx1 = sbx2 - Math.min(120, pw / 3);
        sby1 = py + 5;
        sby2 = sby1 + 18;
        roundRect(ctx, sbx1, sby1, sbx2, sby2, 9, focus == Focus.SEARCH ? ORANGE : OUTLINE);
        roundRect(ctx, sbx1 + 1, sby1 + 1, sbx2 - 1, sby2 - 1, 8, 0xB00A0705);
        if (query.isEmpty() && focus != Focus.SEARCH) {
            ctx.drawCenteredTextWithShadow(textRenderer, fnt("Search..."), (sbx1 + sbx2) / 2, sby1 + 5, TEXT_DIM);
        } else {
            boolean caret = focus == Focus.SEARCH && (Util.getMeasuringTimeMs() / 500L) % 2 == 0;
            ctx.drawTextWithShadow(textRenderer, fnt(query + (caret ? "_" : "")), sbx1 + 8, sby1 + 5, 0xFFFFFFFF);
        }

        // --- zawartosc (przewijana, przycinana); przy zmianie zakladki wsuwa sie z boku
        float tp = Math.min(1f, (now - tabAnimStart) / 240f);
        float te = 1f - (1f - tp) * (1f - tp) * (1f - tp);
        int slide = Math.round(slideDir * (1f - te) * 40f);

        int cx1 = px + 16;
        int cy1 = py + HEADER_H + 8;
        int availW = pw - 32;
        viewX1 = cx1 - 4;
        viewY1 = cy1 - 4;
        viewX2 = px + pw - 8;
        viewY2 = barY1 - 4;
        contentHitStart = hits.size();

        if (currentTab == Tab.MODULES || currentTab == Tab.FRIENDS) {
            int top = cy1 - (int) scrollY;
            ctx.enableScissor(viewX1, viewY1, viewX2, viewY2);
            int bottom;
            if (currentTab == Tab.MODULES) {
                int n = Category.values().length;   // miejsce na wszystkie kategorie w jednym rzedzie
                colW = Math.max(MIN_COL_W, Math.min(MAX_COL_W, (availW - COL_GAP * (n - 1)) / n));
                bottom = drawModules(ctx, cx1 + slide, top, availW, mouseX, mouseY);
            } else {
                bottom = drawFriends(ctx, cx1 + slide, top, availW, mouseX, mouseY);
            }
            ctx.disableScissor();
            maxScroll = Math.max(0f, (bottom - top) - (viewY2 - cy1) + 4);
            scrollY = Math.max(0f, Math.min(maxScroll, scrollY));

            // pasek przewijania
            if (maxScroll > 0f) {
                int trackY1 = viewY1 + 2, trackY2 = viewY2 - 2, trackH = trackY2 - trackY1;
                int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + maxScroll))));
                int thumbY = trackY1 + (int) ((trackH - thumbH) * (scrollY / maxScroll));
                roundRect(ctx, viewX2 - 3, trackY1, viewX2 - 1, trackY2, 1, 0x25FFFFFF);
                roundRect(ctx, viewX2 - 3, thumbY, viewX2 - 1, thumbY + thumbH, 1, ORANGE);
            }
        } else {
            maxScroll = 0;
            ctx.enableScissor(viewX1, viewY1, viewX2, viewY2);
            ctx.drawCenteredTextWithShadow(textRenderer, fnt(currentTab.label + " - coming soon"),
                    px + pw / 2 + slide, viewY2 - 18, TEXT_DIM);
            ctx.disableScissor();
        }

        m.pop();   // koniec animacji otwarcia

        if (closing && cp >= 1f) finishClose();
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        if (client != null) client.setScreen(null);
    }

    /** Plynny postep rozwijania ustawien modulu (0 = zwiniete, 1 = rozwiniete). */
    /** Ile ustawien modulu pokazujemy w menu (bez ukrytych). */
    private static int shownSettings(Module mod) {
        int n = 0;
        for (Setting st : mod.settings) if (!st.hidden) n++;
        return n;
    }

    private float expandProgress(Module mod) {
        float target = EXPANDED.contains(mod) ? 1f : 0f;
        float cur = expandAnim.getOrDefault(mod, target);       // pierwszy raz: bez animacji
        cur += (target - cur) * (1f - (float) Math.exp(-dt * 16f));
        if (Math.abs(target - cur) < 0.004f) cur = target;
        expandAnim.put(mod, cur);
        return cur;
    }

    private int drawModules(DrawContext ctx, int startX, int startY, int availW, int mouseX, int mouseY) {
        String q = query.trim().toLowerCase();

        // kategorie do pokazania: wszystkie (takze puste), a przy wyszukiwaniu tylko z trafieniami
        List<Category> cats = new ArrayList<>();
        List<List<Module>> lists = new ArrayList<>();
        for (Category c : Category.values()) {
            List<Module> list = new ArrayList<>();
            for (Module mod : ModuleManager.byCategory(c)) {
                if (q.isEmpty() || mod.name.toLowerCase().contains(q)) list.add(mod);
            }
            if (!q.isEmpty() && list.isEmpty()) continue;
            cats.add(c);
            lists.add(list);
        }

        int perRow = Math.max(1, (availW + COL_GAP) / (colW + COL_GAP));
        int rowTop = startY;
        int rowBottom = startY;

        for (int ci = 0; ci < cats.size(); ci++) {
            int col = ci % perRow;
            if (col == 0 && ci > 0) {
                rowTop = rowBottom + 8;
                rowBottom = rowTop;
            }
            int x = startX + col * (colW + COL_GAP);
            int y = rowTop;
            Category c = cats.get(ci);
            List<Module> list = lists.get(ci);

            ctx.drawTextWithShadow(textRenderer, fnt(Text.literal(c.label).formatted(Formatting.BOLD)), x + 2, y, ORANGE);
            ctx.fill(x + 2, y + 10, x + colW - 2, y + 11, 0x40FF7A00);
            y += 14;

            if (list.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, fnt("No modules yet"), x + 4, y + 2, TEXT_DIM);
                y += 14;
            }

            for (Module mod : list) {
                boolean expanded = EXPANDED.contains(mod);
                boolean hover = mouseX >= x && mouseX < x + colW && mouseY >= y && mouseY < y + ROW_H;
                boolean editing = bindModule == mod;
                if (editing) {
                    drawBindEditor(ctx, x, y, mod, mouseX, mouseY);
                } else {
                    drawCard(ctx, x, y, colW, ROW_H, mod, mod.isEnabled(), hover, !(mod instanceof GuiModule));
                    drawModuleLabel(ctx, mod, x, y);
                    hits.add(new Hit(x, y, x + colW, y + ROW_H, mod instanceof GuiModule ? null : mod::toggle, () -> {
                        if (!mod.settings.isEmpty()) {
                            if (!EXPANDED.remove(mod)) EXPANDED.add(mod);
                        }
                    }, null));
                }
                bindHits.add(new BindHit(x, y, x + colW, y + ROW_H, mod));
                y += ROW_H + 3;

                // animowane rozwijanie ustawien: odslaniane przez przycinanie do rosnacej wysokosci
                float prog = mod.settings.isEmpty() ? 0f : expandProgress(mod);
                if (prog > 0f) {
                    float eased = prog * prog * (3f - 2f * prog);
                    int fullH = 2 + shownSettings(mod) * (SET_H + 2);
                    int animH = Math.round(fullH * eased);
                    if (animH > 0) {
                        ctx.enableScissor(x, y, x + colW, y + animH);
                        int sx = x + 6, sw = colW - 6;
                        int sy = y;
                        for (Setting s : mod.settings) {
                            if (s.hidden) continue;
                            boolean visible = sy + SET_H <= y + animH;       // klikalne tylko gdy w pelni odslonieto
                            boolean sh = visible && mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + SET_H;
                            if (s instanceof BoolSetting bs) {
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, bs.get() ? 0x99FF7A00 : OUTLINE);
                                roundRect(ctx, sx + 1, sy + 1, sx + sw - 1, sy + SET_H - 1, 6, sh ? 0xE01C1510 : 0xCC120D09);
                                ctx.drawTextWithShadow(textRenderer, fnt(bs.name), sx + 5, sy + 3, bs.get() ? 0xFFFFFFFF : TEXT_GREY);
                                drawSwitch(ctx, sx + sw - 19, sy + 3, bs.get(), ORANGE);
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, bs::toggle, null, null));
                            } else if (s instanceof NumberSetting ns) {
                                double frac = (ns.get() - ns.min) / (ns.max - ns.min);
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, OUTLINE);
                                roundRect(ctx, sx + 1, sy + 1, sx + sw - 1, sy + SET_H - 1, 6, sh ? 0xE01C1510 : 0xCC120D09);
                                int fw = (int) ((sw - 2) * frac);
                                if (fw > 0) {
                                    roundRectGradient(ctx, sx + 1, sy + 1, sx + 1 + Math.max(fw, 4), sy + SET_H - 1, 6, 0xFFE8780C, 0xFFB45006, true);
                                }
                                ctx.drawTextWithShadow(textRenderer, fnt(ns.name), sx + 5, sy + 3, 0xFFFFFFFF);
                                String val = format(ns.get());
                                ctx.drawTextWithShadow(textRenderer, fnt(val), sx + sw - 5 - textRenderer.getWidth(fnt(val)), sy + 3, 0xFFFFFFFF);
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, null, null, ns));
                            } else if (s instanceof ColorSetting cs) {
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, OUTLINE);
                                int bx = sx + 1, bw = sw - 2;
                                hueStrip(ctx, bx, sy + 1, bx + bw, sy + SET_H - 1, 6);   // pasek odcienia (przyciemniony, zeby tekst byl czytelny)
                                if (cs.allowsBlack()) {                                         // czarna strefa na poczatku paska
                                    int zw = (int) (bw * ColorSetting.BLACK_ZONE);
                                    roundRect(ctx, bx, sy + 1, bx + zw, sy + SET_H - 1, 6, 0xFF050505);
                                    ctx.fill(bx + zw - 4, sy + 1, bx + zw, sy + SET_H - 1, 0xFF050505);
                                    ctx.fill(bx + zw, sy + 2, bx + zw + 1, sy + SET_H - 2, 0x66FFFFFF);
                                }
                                int mx = (cs.allowsBlack() && cs.rgb() == 0)
                                        ? bx + 3
                                        : bx + 5 + (int) (cs.markerFraction() * (bw - 12));
                                roundRect(ctx, mx, sy + 2, mx + 2, sy + SET_H - 2, 1, 0xFFFFFFFF);          // znacznik
                                ctx.drawTextWithShadow(textRenderer, fnt(cs.name), sx + 5, sy + 3, 0xFFFFFFFF);
                                int swx = sx + sw - 13;                                            // probka aktualnego koloru
                                roundRect(ctx, swx - 1, sy + 2, swx + 9, sy + SET_H - 2, 3, cs.rgb() == 0 ? 0xFF8A8A8A : 0xFF000000);
                                roundRect(ctx, swx, sy + 3, swx + 8, sy + SET_H - 3, 2, cs.argb());
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, null, cs::nextPreset, cs));
                            } else if (s instanceof ModeSetting ms) {
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, OUTLINE);
                                roundRect(ctx, sx + 1, sy + 1, sx + sw - 1, sy + SET_H - 1, 6, sh ? 0xE01C1510 : 0xCC120D09);
                                ctx.drawTextWithShadow(textRenderer, fnt(ms.name), sx + 5, sy + 3, 0xFFFFFFFF);
                                String mv = ms.get();
                                ctx.drawTextWithShadow(textRenderer, fnt(mv), sx + sw - 5 - textRenderer.getWidth(fnt(mv)), sy + 3, ORANGE);
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, ms::next, ms::prev, null));
                            } else if (s instanceof StringSetting ts) {
                                boolean fe = focus == Focus.SETTING && editingSetting == ts;
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, fe ? ORANGE : OUTLINE);
                                roundRect(ctx, sx + 1, sy + 1, sx + sw - 1, sy + SET_H - 1, 6, sh ? 0xE01C1510 : 0xCC120D09);
                                if (ts.get().isEmpty() && !fe) {
                                    ctx.drawTextWithShadow(textRenderer, fnt(ts.name + "..."), sx + 5, sy + 3, TEXT_GREY);
                                } else {
                                    String caret = fe && (Util.getMeasuringTimeMs() / 500L) % 2 == 0 ? "_" : "";
                                    String disp = ts.get();
                                    int maxW = sw - 12;
                                    while (!disp.isEmpty() && textRenderer.getWidth(fnt(disp + caret)) > maxW) disp = disp.substring(1);
                                    ctx.drawTextWithShadow(textRenderer, fnt(disp + caret), sx + 5, sy + 3, 0xFFFFFFFF);
                                }
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, () -> { focus = Focus.SETTING; editingSetting = ts; }, null, null));
                            } else if (s instanceof ActionSetting as) {
                                roundRect(ctx, sx, sy, sx + sw, sy + SET_H, 7, sh ? ORANGE : 0x99FF7A00);
                                roundRect(ctx, sx + 1, sy + 1, sx + sw - 1, sy + SET_H - 1, 6, sh ? 0xE01C1510 : 0xCC120D09);
                                MutableText at = fnt(as.name);
                                ctx.drawTextWithShadow(textRenderer, at, sx + (sw - textRenderer.getWidth(at)) / 2, sy + 3, 0xFFFFFFFF);
                                if (visible) hits.add(new Hit(sx, sy, sx + sw, sy + SET_H, as.action, null, null));
                            }
                            sy += SET_H + 2;
                        }
                        ctx.disableScissor();
                    }
                    y += animH;
                }
            }
            rowBottom = Math.max(rowBottom, y);
        }
        return rowBottom;
    }

    private int drawFriends(DrawContext ctx, int x, int y0, int availW, int mouseX, int mouseY) {
        int y = y0, h = 16, btnW = 40;
        int fieldW = Math.max(60, Math.min(150, availW - btnW - 6));

        // pole na nick
        boolean focused = focus == Focus.FRIEND;
        roundRect(ctx, x, y, x + fieldW, y + h, 8, focused ? ORANGE : OUTLINE);
        roundRect(ctx, x + 1, y + 1, x + fieldW - 1, y + h - 1, 7, 0x990A0705);
        if (friendInput.isEmpty() && !focused) {
            ctx.drawTextWithShadow(textRenderer, fnt("Enter nick..."), x + 8, y + 4, TEXT_DIM);
        } else {
            boolean caret = focused && (Util.getMeasuringTimeMs() / 500L) % 2 == 0;
            ctx.drawTextWithShadow(textRenderer, fnt(friendInput + (caret ? "_" : "")), x + 8, y + 4, 0xFFFFFFFF);
        }
        hits.add(new Hit(x, y, x + fieldW, y + h, () -> focus = Focus.FRIEND, null, null));

        // przycisk Add
        int bx = x + fieldW + 6;
        boolean bh = mouseX >= bx && mouseX < bx + btnW && mouseY >= y && mouseY < y + h;
        roundRectGradient(ctx, bx, y, bx + btnW, y + h, 8, bh ? 0xFFF28414 : 0xFFEC7D10, bh ? 0xFFC96308 : 0xFFBF5A07, true);
        ctx.drawCenteredTextWithShadow(textRenderer, fnt("Add"), bx + btnW / 2, y + 4, 0xFFFFFFFF);
        hits.add(new Hit(bx, y, bx + btnW, y + h, this::addFriendFromInput, null, null));
        y += h + 4;

        if (Util.getMeasuringTimeMs() < statusUntil) {
            ctx.drawTextWithShadow(textRenderer, fnt(statusMsg), x + 2, y, statusOk ? 0xFF7CE07C : 0xFFFF6B6B);
        }
        y += 12;

        List<String> all = Friends.all();
        ctx.drawTextWithShadow(textRenderer, fnt(Text.literal("Friends (" + all.size() + ")").formatted(Formatting.BOLD)), x + 2, y, ORANGE);
        y += 13;

        String q = query.trim().toLowerCase();
        int rowW = Math.min(200, availW);
        int shown = 0;
        for (String name : all) {
            if (!q.isEmpty() && !name.toLowerCase().contains(q)) continue;
            shown++;
            roundRect(ctx, x, y, x + rowW, y + h, 8, OUTLINE);
            roundRect(ctx, x + 1, y + 1, x + rowW - 1, y + h - 1, 7, 0xCC120D09);
            ctx.drawTextWithShadow(textRenderer, fnt(name), x + 8, y + 4, 0xFFFFFFFF);
            int rx1 = x + rowW - 18;
            boolean rh = mouseX >= rx1 && mouseX < x + rowW && mouseY >= y && mouseY < y + h;
            ctx.drawCenteredTextWithShadow(textRenderer, fnt("x"), x + rowW - 10, y + 4, rh ? 0xFFFF5555 : TEXT_DIM);
            hits.add(new Hit(rx1, y, x + rowW, y + h, () -> Friends.remove(name), null, null));
            y += h + 3;
        }
        if (shown == 0) {
            ctx.drawTextWithShadow(textRenderer, fnt(all.isEmpty() ? "No friends yet" : "No match"), x + 2, y + 2, TEXT_DIM);
            y += 14;
        }
        y += 4;
        ctx.drawTextWithShadow(textRenderer, fnt("Friends get their own color in Tracers & ESP."), x + 2, y, TEXT_DIM);
        return y + 12;
    }

    private void addFriendFromInput() {
        String n = friendInput.trim();
        if (!Friends.isValidNick(n)) {
            setStatus("Invalid nick (3-16 chars: a-z, 0-9, _)", false);
        } else if (Friends.isFriend(n)) {
            setStatus(n + " is already a friend", false);
        } else {
            Friends.add(n);
            friendInput = "";
            setStatus("Added " + n, true);
        }
    }

    private void setStatus(String msg, boolean ok) {
        statusMsg = msg;
        statusOk = ok;
        statusUntil = Util.getMeasuringTimeMs() + 3000L;
    }

    private void drawCard(DrawContext ctx, int x, int y, int w, int h, Module mod, boolean enabled, boolean hover, boolean showSwitch) {
        float target = hover ? 1f : 0f;
        float amount = hoverAnim.getOrDefault(mod, 0f);
        amount += (target - amount) * (1f - (float) Math.exp(-dt * 22f));
        if (Math.abs(target - amount) < 0.008f) amount = target;
        hoverAnim.put(mod, amount);

        // Soft hover halo fades in/out independently of the enabled state.
        if (amount > 0.01f) {
            int alpha = Math.round(0x45 * amount);
            roundRing(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 10, (alpha << 24) | 0xFF8A32);
        }
        roundRect(ctx, x, y, x + w, y + h, 9, enabled ? 0xFFFF8C1A : OUTLINE);
        if (enabled) {
            roundRectGradient(ctx, x + 1, y + 1, x + w - 1, y + h - 1, 8,
                    blendArgb(0xE8E8780C, 0xF0FF8E1F, amount), blendArgb(0xE8B85206, 0xF0D96A0A, amount), true);
        } else {
            roundRectGradient(ctx, x + 1, y + 1, x + w - 1, y + h - 1, 8,
                    blendArgb(0xCC1A120C, 0xE0261A12, amount), blendArgb(0xCC110B08, 0xE01A110B, amount), true);
        }
        if (showSwitch) drawSwitch(ctx, x + w - 20, y + (h - 7) / 2, enabled, 0x99000000);
    }

    private static int blendArgb(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 255) * (1f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Maly przelacznik 14x7 (onTrack = kolor toru w stanie ON). */
    private static void drawSwitch(DrawContext c, int x, int y, boolean on, int onTrack) {
        roundRect(c, x, y, x + 14, y + 7, 3.5f, on ? onTrack : 0xFF3A2D24);
        int kx = on ? x + 8 : x + 1;
        roundRect(c, kx, y + 1, kx + 5, y + 6, 2.5f, on ? 0xFFFFFFFF : 0xFF8C837B);
    }

    private void drawIcon(DrawContext ctx, float cx, float cy, float fruitPx) {
        float scale = (fruitPx / ORANGE_SHARE) / TEX_SIZE;
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(scale, scale, 1f);
        ctx.drawTexture(RenderLayer::getGuiTextured, ORANGE_TEX,
                -TEX_SIZE / 2, -TEX_SIZE / 2, 0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        m.pop();
    }

    /** Pomarancz (z poswiata w teksturze) obracajaca sie na srodku panelu. */
    private void drawOrange(DrawContext ctx, float cx, float cy, int panelH) {
        long t = Util.getMeasuringTimeMs();
        float angle = (t % 14000L) / 14000f * 360f;                  // pelny obrot co 14 s
        float pulse = 1f + 0.03f * (float) Math.sin(t / 900.0);       // delikatne "oddychanie"
        float fruitPx = panelH * 0.36f * pulse;                       // srednica samej pomaranczy na ekranie
        float scale = (fruitPx / ORANGE_SHARE) / TEX_SIZE;

        var m = ctx.getMatrices();

        // pomaranczowa poswiata za owocem (nie obraca sie, lekko pulsuje)
        float glowPx = panelH * 1.12f * (1f + 0.07f * (float) Math.sin(t / 700.0));
        float gs = glowPx / GLOW_SIZE;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(gs, gs, 1f);
        ctx.drawTexture(RenderLayer::getGuiTextured, GLOW_TEX,
                -GLOW_SIZE / 2, -GLOW_SIZE / 2, 0f, 0f, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE);
        m.pop();

        m.push();
        m.translate(cx, cy, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        m.scale(scale, scale, 1f);
        ctx.drawTexture(RenderLayer::getGuiTextured, ORANGE_TEX,
                -TEX_SIZE / 2, -TEX_SIZE / 2, 0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        m.pop();
    }

    // ------------------------------------------------------------------ helpery rysowania

    /** Ile pikseli ekranu przypada na 1 piksel GUI - ksztalty rysujemy w rozdzielczosci ekranu, wiec rogi sa gladkie. */
    private static int pxScale() {
        double sf = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
        return Math.max(1, (int) Math.round(sf));
    }

    private static int scaleAlpha(int color, float f) {
        int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        int ar = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int ag = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int ab = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Poziomy odcinek (piksele ekranu) z wygladzonymi, czesciowo przezroczystymi koncami. */
    private static void span(DrawContext c, int y, float xa, float xb, int color) {
        if (xb - xa <= 0.01f) return;
        int ia = (int) Math.ceil(xa), ib = (int) Math.floor(xb);
        if (ia > ib) {                                   // oba konce w jednym pikselu
            c.fill(ib, y, ib + 1, y + 1, scaleAlpha(color, xb - xa));
            return;
        }
        if (ib > ia) c.fill(ia, y, ib, y + 1, color);
        float l = ia - xa, rr = xb - ib;
        if (l > 0.02f) c.fill(ia - 1, y, ia, y + 1, scaleAlpha(color, l));
        if (rr > 0.02f) c.fill(ib, y, ib + 1, y + 1, scaleAlpha(color, rr));
    }

    /** Pionowy odcinek z wygladzonymi koncami (do paska odcienia). */
    private static void vspan(DrawContext c, int x, float ya, float yb, int color) {
        if (yb - ya <= 0.01f) return;
        int ia = (int) Math.ceil(ya), ib = (int) Math.floor(yb);
        if (ia > ib) {
            c.fill(x, ib, x + 1, ib + 1, scaleAlpha(color, yb - ya));
            return;
        }
        if (ib > ia) c.fill(x, ia, x + 1, ib, color);
        float t = ia - ya, bt = yb - ib;
        if (t > 0.02f) c.fill(x, ia - 1, x + 1, ia, scaleAlpha(color, t));
        if (bt > 0.02f) c.fill(x, ib, x + 1, ib + 1, scaleAlpha(color, bt));
    }

    /** O ile wciete jest wnetrze ksztaltu w wierszu k (liczac od krawedzi) przy promieniu R - zaokraglenie rogu. */
    private static float cornerInset(float R, int k) {
        if (k >= R) return 0f;
        float dy = R - (k + 0.5f);
        return R - (float) Math.sqrt(Math.max(0f, R * R - dy * dy));
    }

    /** Wypelniony zaokraglony prostokat (opcjonalnie z pionowym gradientem), promien r w pikselach GUI. */
    private static void shape(DrawContext c, int x1, int y1, int x2, int y2, float r,
                              boolean roundTop, boolean roundBottom, int top, int bottom) {
        int S = pxScale();
        int X1 = x1 * S, Y1 = y1 * S, X2 = x2 * S, Y2 = y2 * S;
        int W = X2 - X1, H = Y2 - Y1;
        if (W <= 0 || H <= 0) return;
        float R = Math.max(0f, Math.min(r * S, Math.min(W, H) / 2f));
        int rc = (int) Math.min(Math.ceil(R), H / 2);
        int tr = roundTop ? rc : 0, br = roundBottom ? rc : 0;
        boolean solid = top == bottom;

        var m = c.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < tr; i++) {
            float in = cornerInset(R, i);
            span(c, Y1 + i, X1 + in, X2 - in, solid ? top : lerpColor(top, bottom, (i + 0.5f) / H));
        }
        for (int j = 0; j < br; j++) {
            int i = H - 1 - j;
            float in = cornerInset(R, j);
            span(c, Y1 + i, X1 + in, X2 - in, solid ? top : lerpColor(top, bottom, (i + 0.5f) / H));
        }
        int a = tr, b = H - br;
        if (b > a) {
            if (solid) {
                c.fill(X1, Y1 + a, X2, Y1 + b, top);
            } else {
                for (int i = a; i < b; i += 4) {                  // gradient w paskach po 4 px ekranu
                    int e = Math.min(b, i + 4);
                    c.fill(X1, Y1 + i, X2, Y1 + e, lerpColor(top, bottom, ((i + e) / 2f) / H));
                }
            }
        }
        m.pop();
    }

    static void roundRect(DrawContext c, int x1, int y1, int x2, int y2, float r, int color) {
        shape(c, x1, y1, x2, y2, r, true, true, color, color);
    }

    /** Zaokraglony prostokat z pionowym gradientem (roundBottom=false: zaokraglone tylko gorne rogi). */
    static void roundRectGradient(DrawContext c, int x1, int y1, int x2, int y2, float r,
                                          int top, int bottom, boolean roundBottom) {
        shape(c, x1, y1, x2, y2, r, true, roundBottom, top, bottom);
    }

    /** Pionowy gradient, zaokraglone tylko dolne rogi (dolny pasek zakladek). */
    private static void roundRectGradientBottom(DrawContext c, int x1, int y1, int x2, int y2, float r, int top, int bottom) {
        shape(c, x1, y1, x2, y2, r, false, true, top, bottom);
    }

    /** Pozioma linia zanikajaca ku koncom (najjasniejsza w srodku). */
    private static void fadeLine(DrawContext c, int x1, int x2, int y, int rgb, int maxAlpha) {
        int S = pxScale();
        int X1 = x1 * S, W = (x2 - x1) * S, Y = y * S;
        int T = Math.max(1, Math.round(S * 0.75f));
        var m = c.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < W; i += 3) {
            float t = 1f - Math.abs(i / (float) W * 2f - 1f);
            int al = (int) (maxAlpha * t);
            c.fill(X1 + i, Y, Math.min(X1 + W, X1 + i + 3), Y + T, (al << 24) | (rgb & 0xFFFFFF));
        }
        m.pop();
    }

    /** Samo obramowanie zaokraglonego prostokata - wnetrze zostaje nietkniete. */
    static void roundRing(DrawContext c, int x1, int y1, int x2, int y2, float r, int color) {
        int S = pxScale();
        int T = Math.max(1, Math.round(S * 0.75f));
        int X1 = x1 * S, Y1 = y1 * S, X2 = x2 * S, Y2 = y2 * S;
        int W = X2 - X1, H = Y2 - Y1;
        if (W <= 0 || H <= 0) return;
        if (W <= 2 * T || H <= 2 * T) { shape(c, x1, y1, x2, y2, r, true, true, color, color); return; }
        float R = Math.max(0f, Math.min(r * S, Math.min(W, H) / 2f));
        float Ri = Math.max(0f, R - T);
        int rows = Math.min(Math.max((int) Math.ceil(R), T), H / 2);

        var m = c.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int k = 0; k < rows; k++) {
            ringRow(c, Y1 + k, k, X1, X2, T, R, Ri, color);
            ringRow(c, Y2 - 1 - k, k, X1, X2, T, R, Ri, color);
        }
        if (H - rows > rows) {
            c.fill(X1, Y1 + rows, X1 + T, Y2 - rows, color);
            c.fill(X2 - T, Y1 + rows, X2, Y2 - rows, color);
        }
        m.pop();
    }

    private static void ringRow(DrawContext c, int y, int k, int X1, int X2, int T, float R, float Ri, int color) {
        float oi = cornerInset(R, k);
        float oL = X1 + oi, oR = X2 - oi;
        if (k < T) {                                   // gorna / dolna krawedz: pelny odcinek
            span(c, y, oL, oR, color);
            return;
        }
        float ii = cornerInset(Ri, k - T);
        float iL = X1 + T + ii, iR = X2 - T - ii;
        span(c, y, oL, Math.max(oL, iL), color);
        span(c, y, Math.min(oR, iR), oR, color);
    }

    /** Pasek odcienia (HSB) w zaokraglonym polu - rysowany w rozdzielczosci ekranu, konce zaokraglone. */
    private static void hueStrip(DrawContext c, int x1, int y1, int x2, int y2, float r) {
        int S = pxScale();
        int X1 = x1 * S, Y1 = y1 * S, X2 = x2 * S, Y2 = y2 * S;
        int W = X2 - X1, H = Y2 - Y1;
        if (W <= 0 || H <= 0) return;
        float R = Math.max(0f, Math.min(r * S, Math.min(W, H) / 2f));
        var m = c.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < W; i++) {
            int e = Math.min(i, W - 1 - i);
            float in = cornerInset(R, e);
            int col = 0xFF000000 | (Color.HSBtoRGB(i / (float) W, 0.85f, 0.60f) & 0xFFFFFF);
            vspan(c, X1 + i, Y1 + in, Y2 - in, col);
        }
        m.pop();
    }

    private static String format(double v) {
        return (v == Math.rint(v)) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    private static void applySlider(Hit h, double mouseX) {
        double frac = (mouseX - h.x1()) / (double) (h.x2() - h.x1());
        frac = Math.max(0, Math.min(1, frac));
        h.slider().setFraction(frac);
    }

    /** Nazwa modulu + (gdy zbindowany) maly znacznik z klawiszem, np. "M5" albo "R". */
    private void drawModuleLabel(DrawContext ctx, Module mod, int x, int y) {
        String label = mod.name;
        int labelCol = mod.isEnabled() ? 0xFFFFFFFF : TEXT_GREY;
        int left = x + 8;
        int right = x + colW - (mod instanceof GuiModule ? 8 : 24);   // tu zaczyna sie przelacznik (modul GUI go nie ma)
        int avail = right - left;

        String tag = null;
        float ts = 0.7f;
        int tagW = 0;
        if (mod.getBind() != null) {
            tag = mod.getBindLabel();
            tagW = Math.round(textRenderer.getWidth(fnt(tag)) * ts);
        }

        // najpierw rezerwujemy miejsce na znacznik, nazwe zmniejszamy / przycinamy tylko gdy trzeba
        int labelAvail = tag != null ? avail - tagW - 4 : avail;
        float ls = 1f;
        int labelW = textRenderer.getWidth(fnt(label));
        if (labelW > labelAvail) {
            ls = Math.max(0.75f, labelAvail / (float) labelW);
            if (labelW * ls > labelAvail) label = trim(label, (int) (labelAvail / ls));
        }

        var mm = ctx.getMatrices();
        mm.push();
        mm.translate(left, y + 5 + (8 - 8 * ls) / 2f, 0f);
        mm.scale(ls, ls, 1f);
        ctx.drawTextWithShadow(textRenderer, fnt(label), 0, 0, labelCol);
        mm.pop();

        if (tag != null) {
            mm.push();
            mm.translate(right - tagW, y + (ROW_H - 8 * ts) / 2f, 0f);
            mm.scale(ts, ts, 1f);
            ctx.drawTextWithShadow(textRenderer, fnt(tag), 0, 0, mod.isEnabled() ? 0xFFFFE0C0 : 0xFFE0A060);
            mm.pop();
        }
    }

    // ------------------------------------------------------------------ bindowanie na karcie modulu

    /**
     * Srodkowy klik na module zamienia jego nazwe na "Hold / Toggle ...":
     * klik w Hold albo Toggle ustawia tryb, a kropki to miejsce na klawisz - wcisnij klawisz
     * (lub boczny przycisk myszy), zeby go przypisac. ESC usuwa bind.
     */
    private void drawBindEditor(DrawContext ctx, int x, int y, Module mod, int mouseX, int mouseY) {
        roundRect(ctx, x, y, x + colW, y + ROW_H, 9, ORANGE);
        roundRectGradient(ctx, x + 1, y + 1, x + colW - 1, y + ROW_H - 1, 8, 0xF02A1A0F, 0xF00F0A07, true);

        String hold = "Hold", toggle = "Toggle", sep = " / ", dots = "...";
        int gap = 5;
        int wHold = textRenderer.getWidth(fnt(hold));
        int wTog = textRenderer.getWidth(fnt(toggle));
        int wSep = textRenderer.getWidth(fnt(sep));
        int wDots = textRenderer.getWidth(fnt(dots));
        int total = wHold + wSep + wTog + gap + wDots;

        float s = Math.min(1f, (colW - 10) / (float) total);          // na waskich kartach zmniejszamy tekst
        float ox = x + 6 + Math.max(0f, (colW - 12 - total * s) / 2f);
        float oy = y + (ROW_H - 8 * s) / 2f;

        int holdX1 = Math.round(ox), holdX2 = Math.round(ox + wHold * s);
        int togX1 = Math.round(ox + (wHold + wSep) * s), togX2 = Math.round(ox + (wHold + wSep + wTog) * s);
        boolean isHold = mod.getBindMode() == BindMode.HOLD;
        boolean hoverHold = mouseX >= holdX1 - 2 && mouseX < holdX2 + 2 && mouseY >= y && mouseY < y + ROW_H;
        boolean hoverTog = mouseX >= togX1 - 2 && mouseX < togX2 + 2 && mouseY >= y && mouseY < y + ROW_H;

        var mm = ctx.getMatrices();
        mm.push();
        mm.translate(ox, oy, 0f);
        mm.scale(s, s, 1f);
        int cHold = isHold ? 0xFFFFA23A : (hoverHold ? 0xFFFFC890 : TEXT_GREY);
        int cTog = !isHold ? 0xFFFFA23A : (hoverTog ? 0xFFFFC890 : TEXT_GREY);
        ctx.drawTextWithShadow(textRenderer, fnt(hold), 0, 0, cHold);
        ctx.drawTextWithShadow(textRenderer, fnt(sep), wHold, 0, TEXT_DIM);
        ctx.drawTextWithShadow(textRenderer, fnt(toggle), wHold + wSep, 0, cTog);
        if (isHold) ctx.fill(0, 9, wHold, 10, ORANGE);                    // podkreslenie wybranego trybu
        else ctx.fill(wHold + wSep, 9, wHold + wSep + wTog, 10, ORANGE);
        String d = (Util.getMeasuringTimeMs() / 400L) % 2 == 0 ? dots : "..";   // migajace kropki = czekam na klawisz
        ctx.drawTextWithShadow(textRenderer, fnt(d), wHold + wSep + wTog + gap, 0, ORANGE);
        mm.pop();

        editHits.add(new Hit(holdX1 - 2, y, holdX2 + 2, y + ROW_H, () -> mod.setBindMode(BindMode.HOLD), null, null));
        editHits.add(new Hit(togX1 - 2, y, togX2 + 2, y + ROW_H, () -> mod.setBindMode(BindMode.TOGGLE), null, null));
    }

    private void handleBindKey(int keyCode, int scanCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {          // srodkowy klik na module + ESC = usuniecie bindu
            bindModule.setBind(null);
        } else {
            bindModule.setBind(InputUtil.fromKeyCode(keyCode, scanCode));
        }
        bindModule = null;
        ModuleManager.save();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (Util.getMeasuringTimeMs() - openStart < OPEN_MS) return true;   // trwa animacja otwarcia
        playClick();                                                         // klik gdziekolwiek w menu = dzwiek
        if (bindModule != null) {                                            // edycja bindu na karcie
            if (button >= 3) {                                               // boczny przycisk myszy = bind
                bindModule.setBind(InputUtil.Type.MOUSE.createFromCode(button));
                bindModule = null;
                ModuleManager.save();
                return true;
            }
            if (button == 0) {
                for (Hit h : editHits) {
                    if (h.contains(mouseX, mouseY)) {
                        h.left().run();                                      // Hold / Toggle
                        ModuleManager.save();
                        return true;
                    }
                }
            }
            if (button != 2) {                                               // klik gdziekolwiek indziej zamyka edycje
                bindModule = null;
                return true;
            }
            bindModule = null;      // srodkowy: zamknij, a nizej ewentualnie otworz dla kliknietego modulu
        }
        boolean inSearch = mouseX >= sbx1 && mouseX < sbx2 && mouseY >= sby1 && mouseY < sby2;
        focus = inSearch ? Focus.SEARCH : Focus.NONE;
        if (inSearch) return true;

        // srodkowy przycisk myszy na module -> okno bindowania
        if (button == 2 && mouseX >= viewX1 && mouseX < viewX2 && mouseY >= viewY1 && mouseY < viewY2) {
            for (BindHit b : bindHits) {
                if (b.contains(mouseX, mouseY)) {
                    bindModule = b.mod();
                    focus = Focus.NONE;
                    return true;
                }
            }
        }

        // przesuwanie menu: lapiesz za nagłówek
        if (button == 0 && mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= panelY && mouseY < panelY + HEADER_H) {
            movingPanel = true;
            grabX = mouseX - panelX;
            grabY = mouseY - panelY;
            return true;
        }

        boolean inView = mouseX >= viewX1 && mouseX < viewX2 && mouseY >= viewY1 && mouseY < viewY2;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (i >= contentHitStart && !inView) continue;   // element przewinięty poza panel
            if (!h.contains(mouseX, mouseY)) continue;
            if (button == 0 && h.left() != null) h.left().run();
            if (button == 1 && h.right() != null) h.right().run();
            if (button == 0 && h.slider() != null) {
                dragging = h;
                applySlider(h, mouseX);
            }
            ModuleManager.save();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (closing) return true;
        scrollY = Math.max(0f, Math.min(maxScroll, scrollY - (float) verticalAmount * 14f));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (movingPanel) {
            // pozycja bezwzgledna: panel zawsze jest dokladnie pod kursorem (niezaleznie od skali GUI)
            offX = (float) (mouseX - grabX - centerX);
            offY = (float) (mouseY - grabY - centerY);
            return true;
        }
        if (dragging != null) {
            applySlider(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        movingPanel = false;
        ModuleManager.save();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (closing) return true;
        if (bindModule != null) return true;
        if (focus == Focus.SEARCH && chr >= 32 && chr != 127 && query.length() < 24) {
            query += chr;
            return true;
        }
        if (focus == Focus.SETTING && editingSetting != null && chr >= 32 && chr != 127
                && editingSetting.get().length() < editingSetting.maxLen) {
            editingSetting.set(editingSetting.get() + chr);
            return true;
        }
        if (focus == Focus.FRIEND && friendInput.length() < 16
                && (Character.isLetterOrDigit(chr) && chr < 128 || chr == '_')) {
            friendInput += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (bindModule != null) {
            handleBindKey(keyCode, scanCode);
            return true;
        }
        if (focus != Focus.NONE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == Focus.SEARCH && !query.isEmpty()) query = query.substring(0, query.length() - 1);
                if (focus == Focus.FRIEND && !friendInput.isEmpty()) friendInput = friendInput.substring(0, friendInput.length() - 1);
                if (focus == Focus.SETTING && editingSetting != null && !editingSetting.get().isEmpty()) {
                    editingSetting.set(editingSetting.get().substring(0, editingSetting.get().length() - 1));
                }
                return true;
            }
            if (focus == Focus.SETTING && editingSetting != null && Screen.isPaste(keyCode) && client != null) {
                String clip = client.keyboard.getClipboard().replaceAll("[\\r\\n]", " ");
                editingSetting.set(editingSetting.get() + clip);
                return true;
            }
            if (Screen.isPaste(keyCode) && client != null) {          // Ctrl+V
                String clip = client.keyboard.getClipboard();
                if (focus == Focus.FRIEND) {
                    String f = clip.replaceAll("[^A-Za-z0-9_]", "");
                    friendInput = (friendInput + f).substring(0, Math.min(16, friendInput.length() + f.length()));
                } else {
                    query = (query + clip).substring(0, Math.min(24, query.length() + clip.length()));
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == Focus.FRIEND) addFriendFromInput();
                else focus = Focus.NONE;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focus = Focus.NONE;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        ModuleManager.save();
        if (closing) return;
        closing = true;
        closeStart = Util.getMeasuringTimeMs();
        movingPanel = false;
        dragging = null;
        bindModule = null;
        focus = Focus.NONE;
    }

    @Override
    public void tick() {
        // zabezpieczenie, gdyby render nie domknal animacji
        if (closing && Util.getMeasuringTimeMs() - closeStart > CLOSE_MS + 150L) finishClose();
    }
}

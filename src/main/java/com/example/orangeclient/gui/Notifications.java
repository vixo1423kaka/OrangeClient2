package com.example.orangeclient.gui;

import com.example.orangeclient.module.render.Hud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Powiadomienia "Modul wlaczony / wylaczony" (pozycja z modulu HUD, domyslnie prawy dol ekranu). */
public final class Notifications {
    public static final int H = 18;
    private static final int GAP = 4, MAX = 5;
    private static final long IN_MS = 220L, HOLD_MS = 1700L, OUT_MS = 280L;
    private static final int GREEN = 0xFF55E07A, RED = 0xFFFF5F5F;

    private static final Identifier FONT_ID = Identifier.of("orangeclient", "ui");
    private static final Identifier FONT_FILE = Identifier.of("orangeclient", "font/ui.ttf");
    private static Boolean customFont;

    private static final class Notif {
        String name;
        boolean on;
        long born;
        float y = -1f;
    }

    private static final List<Notif> LIST = new ArrayList<>();
    private static long lastFrame;

    private Notifications() { }

    public static void push(String name, boolean on) {
        LIST.removeIf(n -> n.name.equals(name));      // ten sam modul: nowe powiadomienie zastepuje stare
        Notif n = new Notif();
        n.name = name;
        n.on = on;
        n.born = Util.getMeasuringTimeMs();
        LIST.add(n);
        while (LIST.size() > MAX) LIST.remove(0);
    }

    private static MutableText font(Text t) {
        if (customFont == null) {
            customFont = MinecraftClient.getInstance().getResourceManager().getResource(FONT_FILE).isPresent();
        }
        MutableText m = t.copy();
        return customFont ? m.styled(st -> st.withFont(FONT_ID)) : m;
    }

    private static MutableText label(String name, boolean on) {
        int statusCol = on ? GREEN : RED;
        return font(Text.literal(name))
                .append(font(Text.literal(on ? "  enabled" : "  disabled")).styled(st -> st.withColor(statusCol & 0xFFFFFF)));
    }

    public static int cardWidth(TextRenderer tr, String name, boolean on) {
        return 8 + 6 + 6 + tr.getWidth(label(name, on)) + 10;
    }

    /** Rysuje jedna karte powiadomienia w (x, y) o szerokosci w (z cardWidth) - uzywane tez w edytorze HUD. */
    public static void drawCard(DrawContext ctx, TextRenderer tr, int x, int y, int w, String name, boolean on) {
        int statusCol = on ? GREEN : RED;
        ClickGui.roundRect(ctx, x, y, x + w, y + H, 9, 0xAAFF7A00);
        ClickGui.roundRectGradient(ctx, x + 1, y + 1, x + w - 1, y + H - 1, 8, 0xF0241710, 0xF00F0A07, true);
        ClickGui.roundRect(ctx, x + 8, y + 6, x + 14, y + 12, 3, statusCol);
        ctx.drawTextWithShadow(tr, label(name, on), x + 20, y + 5, 0xFFFFFFFF);
    }

    public static void render(DrawContext ctx) {
        if (LIST.isEmpty() || !Hud.notifVisible()) {
            lastFrame = 0;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        final long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        LIST.removeIf(n -> now - n.born > IN_MS + HOLD_MS + OUT_MS);

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int count = LIST.size();
        boolean right = Hud.notifRight(), bottom = Hud.notifBottom();
        // po zmianie rozmiaru okna powiadomienie nie moze wyjechac poza ekran
        int mx = Math.min(Hud.notifX(), Math.max(0, sw - 40));
        int my = Math.min(Hud.notifY(), Math.max(0, sh - H));
        for (int i = 0; i < count; i++) {
            Notif n = LIST.get(i);
            // najnowsze powiadomienie przy krawedzi; reszta ukladana w kierunku srodka ekranu
            float targetY = bottom ? sh - my - H - (count - 1 - i) * (H + GAP)
                                   : my + (count - 1 - i) * (H + GAP);
            if (n.y < 0) n.y = targetY;
            else n.y += (targetY - n.y) * (1f - (float) Math.exp(-dt * 14f));

            int w = cardWidth(tr, n.name, n.on);

            // wsuwanie z prawej krawedzi, na koncu wysuwanie
            long age = now - n.born;
            float off = 0f;
            if (age < IN_MS) {
                float p = 1f - age / (float) IN_MS;
                off = p * p * p;
            } else if (age > IN_MS + HOLD_MS) {
                float p = (age - IN_MS - HOLD_MS) / (float) OUT_MS;
                off = p * p * p;
            }
            int slide = Math.round(off * (w + mx + 6));
            int x = right ? sw - mx - w + slide : mx - slide;
            int y = Math.round(n.y);

            drawCard(ctx, tr, x, y, w, n.name, n.on);
        }
    }
}

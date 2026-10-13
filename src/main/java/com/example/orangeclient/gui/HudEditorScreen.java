package com.example.orangeclient.gui;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.render.Hud;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Edytor pozycji HUD: pokazuje przykladowe powiadomienie (i liste modulow), ktore lapiesz myszka
 * i przesuwasz gdzie chcesz. ESC zapisuje i wraca do menu.
 */
public class HudEditorScreen extends Screen {
    private enum Target { NONE, NOTIF, LIST }

    private final Screen parent;
    private Target dragging = Target.NONE;
    private int grabX, grabY;

    // pudelka elementow z ostatniej klatki (do klikania)
    private boolean hasNotif, hasList;
    private int nx1, ny1, nx2, ny2;
    private int lx1, ly1, lx2, ly2;
    private int listTextW, listRows;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean in(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        TextRenderer tr = textRenderer;
        Hud hud = ModuleManager.get(Hud.class);
        hasNotif = false;
        hasList = false;

        if (hud != null) {
            if (hud.listEnabled()) {
                List<String> names = hud.listPreviewNames(tr);
                listTextW = hud.listWidth(tr, names);
                listRows = names.size();
                int[] b = hud.listBox(width, height, listTextW, listRows);
                lx1 = b[0]; ly1 = b[1]; lx2 = b[2]; ly2 = b[3];
                hasList = true;
                hud.drawList(ctx, tr, names);
                drawFrame(ctx, lx1, ly1, lx2, ly2,
                        dragging == Target.LIST || (dragging == Target.NONE && in(mouseX, mouseY, lx1, ly1, lx2, ly2)));
            }
            if (hud.notifsEnabled()) {
                int w = Notifications.cardWidth(tr, "Example", true);
                int[] p = hud.notifPos(width, height, w, Notifications.H);
                nx1 = p[0]; ny1 = p[1]; nx2 = nx1 + w; ny2 = ny1 + Notifications.H;
                hasNotif = true;
                Notifications.drawCard(ctx, tr, nx1, ny1, w, "Example", true);
                drawFrame(ctx, nx1, ny1, nx2, ny2,
                        dragging == Target.NOTIF || (dragging == Target.NONE && in(mouseX, mouseY, nx1, ny1, nx2, ny2)));
            }
        }

        String title = "Edit HUD";
        String hint = "Drag the elements with the mouse, ESC to finish";
        ctx.drawCenteredTextWithShadow(tr, title, width / 2, 14, 0xFFFF7A00);
        ctx.drawCenteredTextWithShadow(tr, hint, width / 2, 28, 0xFFBDBDBD);
        if (hud != null && !hud.isEnabled()) {
            ctx.drawCenteredTextWithShadow(tr, "HUD module is off - turn it on to use these positions", width / 2, 42, 0xFFFF5F5F);
        }
        if (hud != null && !hasNotif && !hasList) {
            ctx.drawCenteredTextWithShadow(tr, "Notifications and ModuleList are both off in HUD settings", width / 2, height / 2, 0xFFBDBDBD);
        }
    }

    private static void drawFrame(DrawContext ctx, int x1, int y1, int x2, int y2, boolean active) {
        int c = active ? 0xFFFF7A00 : 0x66FFFFFF;
        ctx.fill(x1 - 2, y1 - 2, x2 + 2, y1 - 1, c);
        ctx.fill(x1 - 2, y2 + 1, x2 + 2, y2 + 2, c);
        ctx.fill(x1 - 2, y1 - 1, x1 - 1, y2 + 1, c);
        ctx.fill(x2 + 1, y1 - 1, x2 + 2, y2 + 1, c);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hasNotif && in(mouseX, mouseY, nx1, ny1, nx2, ny2)) {          // powiadomienie jest na wierzchu
                dragging = Target.NOTIF;
                grabX = (int) mouseX - nx1;
                grabY = (int) mouseY - ny1;
                return true;
            }
            if (hasList && in(mouseX, mouseY, lx1, ly1, lx2, ly2)) {
                dragging = Target.LIST;
                grabX = (int) mouseX - lx1;
                grabY = (int) mouseY - ly1;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != Target.NONE && button == 0) {
            Hud hud = ModuleManager.get(Hud.class);
            if (hud == null) return true;
            if (dragging == Target.NOTIF) {
                int w = nx2 - nx1, h = ny2 - ny1;
                int x = Math.max(0, Math.min(width - w, (int) mouseX - grabX));
                int y = Math.max(0, Math.min(height - h, (int) mouseY - grabY));
                hud.setNotifPos(width, height, x, y, w, h);
            } else {
                int w = lx2 - lx1, h = ly2 - ly1;
                int x = Math.max(0, Math.min(width - w, (int) mouseX - grabX));
                int y = Math.max(0, Math.min(height - h, (int) mouseY - grabY));
                hud.setListPos(width, height, x, y, listTextW, listRows);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging != Target.NONE) {
            dragging = Target.NONE;
            ModuleManager.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        ModuleManager.save();
        if (client != null) client.setScreen(parent);
    }
}

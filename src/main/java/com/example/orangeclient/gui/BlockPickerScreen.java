package com.example.orangeclient.gui;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Okno wyboru blokow: siatka wszystkich blokow z gry. Klik = dodaj / usun z listy,
 * wyszukiwarka (po nazwie lub ID), "Selected" pokazuje tylko wybrane, "Clear" czysci liste.
 * ESC lub "Done" zapisuje i wraca do menu.
 */
public class BlockPickerScreen extends Screen {
    private static final int ORANGE = 0xFFFF7A00;
    private static final int OUTLINE = 0xFF2B221B;
    private static final int TEXT_GREY = 0xFFBDBDBD;
    private static final int TEXT_DIM = 0xFF8C837B;
    private static final int CELL = 20, STEP = 22;

    private record Entry(String id, String name, String lowerName, ItemStack stack) { }

    private static List<Entry> ENTRIES;

    private static List<Entry> entries() {
        if (ENTRIES == null) {
            List<Entry> l = new ArrayList<>();
            for (Block b : Registries.BLOCK) {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                Identifier id = Registries.BLOCK.getId(b);
                Item item = b.asItem();
                ItemStack st = item == Items.AIR ? null : new ItemStack(item);
                String name = b.getName().getString();
                l.add(new Entry(id.toString(), name, name.toLowerCase(), st));
            }
            l.sort(Comparator.comparing(Entry::lowerName));
            ENTRIES = l;
        }
        return ENTRIES;
    }

    private final Screen parent;
    private final BlockListSetting setting;

    private String query = "";
    private boolean selectedOnly = false;
    private List<Entry> filtered = new ArrayList<>();
    private float scroll, maxScroll;

    // uklad (liczony w init)
    private int px, py, pw, ph;
    private int gx1, gy1, gx2, gy2, cols;
    private int searchX1, searchX2, selX1, selX2, clrX1, clrX2, rowY, doneX1, doneX2, footY;

    public BlockPickerScreen(Screen parent, BlockListSetting setting) {
        super(Text.literal("Select blocks"));
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 340);
        ph = Math.min(height - 20, 260);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        rowY = py + 22;
        clrX2 = px + pw - 8;
        clrX1 = clrX2 - 38;
        selX2 = clrX1 - 4;
        selX1 = selX2 - 58;
        searchX1 = px + 8;
        searchX2 = selX1 - 4;

        gx1 = px + 8;
        gy1 = py + 44;
        gx2 = px + pw - 8;
        gy2 = py + ph - 26;
        cols = Math.max(1, (gx2 - gx1 - 6) / STEP);

        footY = py + ph - 20;
        doneX2 = px + pw - 8;
        doneX1 = doneX2 - 48;
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries()) {
            if (selectedOnly && !setting.contains(e.id())) continue;
            if (!q.isEmpty() && !e.lowerName().contains(q) && !e.id().contains(q)) continue;
            out.add(e);
        }
        filtered = out;
        scroll = 0;
    }

    private static boolean in(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private void button(DrawContext ctx, int x1, int y1, int x2, int y2, String label, boolean on, boolean hover) {
        ctx.fill(x1, y1, x2, y2, on || hover ? ORANGE : OUTLINE);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, on ? 0xFF4A2A0A : (hover ? 0xFF1C1510 : 0xFF120D09));
        TextRenderer tr = textRenderer;
        ctx.drawCenteredTextWithShadow(tr, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2 + 1, 0xFFFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = textRenderer;
        ctx.fill(0, 0, width, height, 0xAA000000);

        // panel
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, OUTLINE);
        ctx.fill(px, py, px + pw, py + ph, 0xF0120D09);
        ctx.fill(px, py, px + pw, py + 2, ORANGE);
        ctx.drawTextWithShadow(tr, "Select blocks", px + 8, py + 8, ORANGE);

        // wyszukiwarka
        ctx.fill(searchX1, rowY, searchX2, rowY + 16, OUTLINE);
        ctx.fill(searchX1 + 1, rowY + 1, searchX2 - 1, rowY + 15, 0xFF0A0705);
        boolean caret = (System.currentTimeMillis() / 500L) % 2 == 0;
        if (query.isEmpty()) {
            ctx.drawTextWithShadow(tr, "Search block...", searchX1 + 5, rowY + 4, TEXT_DIM);
        } else {
            String disp = query + (caret ? "_" : "");
            while (disp.length() > 1 && tr.getWidth(disp) > searchX2 - searchX1 - 10) disp = disp.substring(1);
            ctx.drawTextWithShadow(tr, disp, searchX1 + 5, rowY + 4, 0xFFFFFFFF);
        }
        button(ctx, selX1, rowY, selX2, rowY + 16, "Selected", selectedOnly, in(mouseX, mouseY, selX1, rowY, selX2, rowY + 16));
        button(ctx, clrX1, rowY, clrX2, rowY + 16, "Clear", false, in(mouseX, mouseY, clrX1, rowY, clrX2, rowY + 16));

        // siatka
        int viewH = gy2 - gy1;
        int rows = (filtered.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * STEP - viewH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        Entry hovered = null;
        ctx.enableScissor(gx1, gy1, gx2, gy2);
        int firstRow = (int) (scroll / STEP);
        int lastRow = Math.min(rows - 1, (int) ((scroll + viewH) / STEP));
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= filtered.size()) break;
                Entry e = filtered.get(i);
                int x = gx1 + c * STEP;
                int y = gy1 + r * STEP - (int) scroll;
                boolean sel = setting.contains(e.id());
                boolean hov = in(mouseX, mouseY, x, y, x + CELL, y + CELL) && in(mouseX, mouseY, gx1, gy1, gx2, gy2);
                if (hov) hovered = e;
                ctx.fill(x, y, x + CELL, y + CELL, sel ? ORANGE : (hov ? 0xFF6A5A4A : 0xFF241C16));
                ctx.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, sel ? 0xFF4A2A0A : (hov ? 0xFF2A211A : 0xFF14100C));
                if (e.stack() != null) {
                    ctx.drawItem(e.stack(), x + 2, y + 2);
                } else {
                    String ch = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
                    ctx.drawCenteredTextWithShadow(tr, ch, x + CELL / 2, y + 6, TEXT_GREY);
                }
            }
        }
        ctx.disableScissor();

        if (filtered.isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, selectedOnly ? "No blocks selected" : "No blocks found",
                    (gx1 + gx2) / 2, (gy1 + gy2) / 2 - 4, TEXT_DIM);
        }

        // pasek przewijania
        if (maxScroll > 0) {
            int tx1 = gx2 - 3;
            ctx.fill(tx1, gy1, gx2, gy2, 0xFF1C1510);
            int th = Math.max(12, (int) (viewH * (viewH / (float) (rows * STEP))));
            int ty = gy1 + (int) ((viewH - th) * (scroll / maxScroll));
            ctx.fill(tx1, ty, gx2, ty + th, ORANGE);
        }

        // stopka
        ctx.drawTextWithShadow(tr, setting.ids().size() + " selected", px + 8, footY + 4, TEXT_GREY);
        button(ctx, doneX1, footY, doneX2, footY + 16, "Done", false, in(mouseX, mouseY, doneX1, footY, doneX2, footY + 16));

        // podpowiedz (rysowana na samym wierzchu, nad ikonami przedmiotow)
        if (hovered != null) {
            String l1 = hovered.name();
            String l2 = hovered.id();
            int w = Math.max(tr.getWidth(l1), tr.getWidth(l2)) + 10;
            int h = 24;
            int tx = Math.min(mouseX + 10, width - w - 2);
            int ty = Math.max(2, Math.min(mouseY - 4, height - h - 2));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            ctx.fill(tx, ty, tx + w, ty + h, ORANGE);
            ctx.fill(tx + 1, ty + 1, tx + w - 1, ty + h - 1, 0xFF0A0705);
            ctx.drawTextWithShadow(tr, l1, tx + 5, ty + 3, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, l2, tx + 5, ty + 13, TEXT_DIM);
            ctx.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (in(mx, my, doneX1, footY, doneX2, footY + 16)) { close(); return true; }
            if (in(mx, my, selX1, rowY, selX2, rowY + 16)) { selectedOnly = !selectedOnly; refilter(); return true; }
            if (in(mx, my, clrX1, rowY, clrX2, rowY + 16)) { setting.clear(); if (selectedOnly) refilter(); return true; }
            if (in(mx, my, gx1, gy1, gx2 - 4, gy2)) {
                int c = ((int) mx - gx1) / STEP;
                int r = ((int) (my - gy1 + scroll)) / STEP;
                int xIn = ((int) mx - gx1) % STEP, yIn = ((int) (my - gy1 + scroll)) % STEP;
                int i = r * cols + c;
                if (c < cols && xIn < CELL && yIn < CELL && i >= 0 && i < filtered.size()) {
                    setting.toggle(filtered.get(i).id());
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (float) verticalAmount * STEP * 2));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= 32 && chr != 127 && query.length() < 40) {
            query += chr;
            refilter();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            refilter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        ModuleManager.save();
        if (client != null) client.setScreen(parent);
    }
}

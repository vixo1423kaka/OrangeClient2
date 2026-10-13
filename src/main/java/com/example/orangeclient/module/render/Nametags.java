package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Nametags extends Module {
    private final BoolSetting nick = add(new BoolSetting("Nick", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", true));
    private final BoolSetting health = add(new BoolSetting("Health", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", true));
    private final BoolSetting mainHand = add(new BoolSetting("MainHand", true));
    private final BoolSetting offHand = add(new BoolSetting("OffHand", true));
    private final BoolSetting enchants = add(new BoolSetting("Enchants", true));
    private final BoolSetting hideVanilla = add(new BoolSetting("HideVanilla", true));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1));
    private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 256, 16));

    private static final Set<String> SINGLE_LEVEL = Set.of("mending", "silk_touch", "infinity", "flame",
            "channeling", "multishot", "aqua_affinity", "binding_curse", "vanishing_curse");

    private record Tag(PlayerEntity player, float x, float y, double dist) { }

    private final List<Tag> tags = new ArrayList<>();

    public Nametags() {
        super("Nametags", Category.RENDER);
    }

    /** Czy mixin ma schowac vanillowe nametagi graczy. */
    public boolean hidesVanilla() { return isEnabled() && hideVanilla.get(); }

    @Override
    protected void onDisable() { tags.clear(); }

    /** Faza swiata: rzutujemy pozycje glow graczy na ekran. */
    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        tags.clear();
        // Macierz widoku budujemy sami z obrotu kamery (tak jak robi to vanilla),
        // zamiast polegac na ctx.matrixStack(), ktory w 1.21.4 nie zawsze zawiera obrot kamery.
        Camera camera = ctx.camera();
        Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
        Matrix4f mvp = new Matrix4f(ctx.projectionMatrix()).mul(view);
        Vec3d cam = camera.getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (PlayerEntity e : mc.world.getPlayers()) {
            if (e == mc.player || !e.isAlive()) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > range.get()) continue;

            Vec3d rel = e.getLerpedPos(td).add(0, e.getHeight() + 0.3, 0).subtract(cam);
            Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
            mvp.transform(v);
            if (v.w <= 0.01f) continue; // za plecami
            float nx = v.x / v.w, ny = v.y / v.w;
            if (nx < -1.5f || nx > 1.5f || ny < -1.5f || ny > 1.5f) continue;

            tags.add(new Tag(e, (nx * 0.5f + 0.5f) * sw, (0.5f - ny * 0.5f) * sh, dist));
        }
    }

    /** Faza HUD: rysujemy tagi (od najdalszych, blizsze na wierzchu). */
    @Override
    public void onHudRender(DrawContext ctx, MinecraftClient mc) {
        if (tags.isEmpty()) return;
        List<Tag> sorted = new ArrayList<>(tags);
        tags.clear();
        sorted.sort((a, b) -> Double.compare(b.dist(), a.dist()));
        for (Tag t : sorted) draw(ctx, mc, t);
    }

    private void draw(DrawContext ctx, MinecraftClient mc, Tag t) {
        TextRenderer tr = mc.textRenderer;
        PlayerEntity p = t.player();
        MatrixStack ms = ctx.getMatrices();

        ms.push();
        ms.translate(t.x(), t.y(), 0);
        float s = (float) scale.get();
        ms.scale(s, s, 1f);

        int y = 0;

        // linia: nick + ping + hp
        Text header = buildHeader(mc, p);
        if (header != null) {
            header = uiFont(header);
            int w = tr.getWidth(header);
            rounded(ctx, -w / 2 - 5, y - 12, w / 2 + 5, y + 1, 4.5f, 0xA8000000);
            rounded(ctx, -w / 2 - 4, y - 12, w / 2 + 4, y - 10, 1.5f, 0xDDFF7A00);
            ctx.drawTextWithShadow(tr, header, -w / 2, y - 9, 0xFFFFFFFF);
            y -= 12;
        }

        // rzad przedmiotow (+ enchanty nad kazdym)
        List<ItemStack> stacks = collectStacks(p);
        int n = stacks.size();
        for (int i = 0; i < n; i++) {
            ItemStack st = stacks.get(i);
            int ix = -n * 9 + i * 18;
            ctx.drawItem(st, ix + 1, y - 17);
            ctx.drawStackOverlay(tr, st, ix + 1, y - 17);

            if (!enchants.get()) continue;
            List<Text> lines = enchantLines(st);
            for (int k = 0; k < lines.size(); k++) {
                Text line = lines.get(k);
                int lw = tr.getWidth(line);
                ms.push();
                ms.translate(ix + 9, y - 17 - 5 * (k + 1), 0);
                ms.scale(0.5f, 0.5f, 1f);
                ctx.drawTextWithShadow(tr, uiFont(line), -lw / 2, 0, 0xFFFFFFFF);
                ms.pop();
            }
        }

        ms.pop();
    }

    private static final Identifier UI_FONT = Identifier.of("orangeclient", "ui");
    private static final Identifier UI_BOLD_FONT = Identifier.of("orangeclient", "ui_bold");

    private static Text uiFont(Text text) {
        return text.copy().styled(style -> style.isBold()
                ? style.withBold(false).withFont(UI_BOLD_FONT)
                : style.withFont(UI_FONT));
    }

    /** Compact rounded backing, avoiding the sharp square nametag plate. */
    private static void rounded(DrawContext ctx, float x1, float y1, float x2, float y2, float radius, int color) {
        int top = (int) Math.floor(y1), bottom = (int) Math.ceil(y2);
        for (int y = top; y < bottom; y++) {
            float dy = Math.min(y + 0.5f - y1, y2 - (y + 0.5f));
            float inset = dy >= radius ? 0 : radius - (float) Math.sqrt(Math.max(0, radius * radius - (radius - dy) * (radius - dy)));
            int left = (int) Math.ceil(x1 + inset), right = (int) Math.floor(x2 - inset);
            if (right > left) ctx.fill(left, y, right, y + 1, color);
        }
    }

    private Text buildHeader(MinecraftClient mc, PlayerEntity p) {
        MutableText out = Text.empty();
        boolean any = false;

        if (nick.get()) {
            out.append(Text.literal(p.getName().getString()).formatted(Formatting.WHITE));
            any = true;
        }
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (entry != null) {
                int ms = entry.getLatency();
                Formatting c = ms < 100 ? Formatting.GREEN : ms < 200 ? Formatting.YELLOW : Formatting.RED;
                if (any) out.append(" ");
                out.append(Text.literal(ms + "ms").formatted(c));
                any = true;
            }
        }
        if (health.get()) {
            float hp = p.getHealth() + p.getAbsorptionAmount();
            float ratio = p.getHealth() / Math.max(1f, p.getMaxHealth());
            Formatting c = ratio > 0.66f ? Formatting.GREEN : ratio > 0.33f ? Formatting.YELLOW : Formatting.RED;
            if (any) out.append(" ");
            out.append(Text.literal(String.valueOf(Math.round(hp))).formatted(c));
            any = true;
        }
        return any ? out : null;
    }

    private List<ItemStack> collectStacks(PlayerEntity p) {
        List<ItemStack> list = new ArrayList<>();
        if (mainHand.get()) addIfPresent(list, p.getMainHandStack());
        if (armor.get()) {
            addIfPresent(list, p.getEquippedStack(EquipmentSlot.HEAD));
            addIfPresent(list, p.getEquippedStack(EquipmentSlot.CHEST));
            addIfPresent(list, p.getEquippedStack(EquipmentSlot.LEGS));
            addIfPresent(list, p.getEquippedStack(EquipmentSlot.FEET));
        }
        if (offHand.get()) addIfPresent(list, p.getOffHandStack());
        return list;
    }

    private static void addIfPresent(List<ItemStack> list, ItemStack st) {
        if (!st.isEmpty()) list.add(st);
    }

    private static List<Text> enchantLines(ItemStack st) {
        List<Text> out = new ArrayList<>();
        ItemEnchantmentsComponent comp = st.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (comp.isEmpty()) return out;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> en : comp.getEnchantmentEntries()) {
            String id = en.getKey().getKey().map(k -> k.getValue().getPath()).orElse("?");
            int lvl = en.getIntValue();
            String label = abbr(id) + (lvl == 1 && SINGLE_LEVEL.contains(id) ? "" : " " + lvl);
            out.add(Text.literal(label).formatted(id.endsWith("_curse") ? Formatting.RED : Formatting.AQUA));
            if (out.size() >= 6) break;
        }
        return out;
    }

    private static String abbr(String id) {
        return switch (id) {
            case "protection" -> "Prot";
            case "fire_protection" -> "FireP";
            case "blast_protection" -> "Blast";
            case "projectile_protection" -> "Proj";
            case "feather_falling" -> "Feather";
            case "thorns" -> "Thorns";
            case "respiration" -> "Resp";
            case "aqua_affinity" -> "Aqua";
            case "depth_strider" -> "Depth";
            case "frost_walker" -> "Frost";
            case "soul_speed" -> "Soul";
            case "swift_sneak" -> "Sneak";
            case "unbreaking" -> "Unb";
            case "mending" -> "Mend";
            case "sharpness" -> "Sharp";
            case "smite" -> "Smite";
            case "bane_of_arthropods" -> "Bane";
            case "knockback" -> "KB";
            case "fire_aspect" -> "FireA";
            case "looting" -> "Loot";
            case "sweeping_edge" -> "Sweep";
            case "efficiency" -> "Eff";
            case "silk_touch" -> "Silk";
            case "fortune" -> "Fort";
            case "power" -> "Power";
            case "punch" -> "Punch";
            case "flame" -> "Flame";
            case "infinity" -> "Inf";
            case "binding_curse" -> "Bind";
            case "vanishing_curse" -> "Van";
            default -> {
                String c = Character.toUpperCase(id.charAt(0)) + id.substring(1);
                yield c.length() > 6 ? c.substring(0, 6) : c;
            }
        };
    }
}

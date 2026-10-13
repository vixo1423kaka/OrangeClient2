package com.example.orangeclient.module.render;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pokazuje miejsca, w ktorych gracz NAGLE zniknal ci z pola widzenia (np. wylogowal sie):
 * polprzezroczysty box (jak ESP) z nickiem nad nim. Punkt zostaje do wyjscia z serwera.
 *
 * Punkt powstaje, gdy gracz, ktorego widziales, znika ze swiata, ale NIE gdy:
 *  - rzucil perle Endu (zanim punkt powstanie, sprawdzamy, czy ten gracz nie rzucil perly - to byl teleport),
 *  - zginal (encja znika po animacji smierci),
 *  - byl daleko (MaxDistance) - wtedy po prostu wyszedl poza zasieg renderowania,
 *  - zniknal razem z calym swiatem (zmiana wymiaru, respawn).
 * Punkty sa przypisane do wymiaru, w ktorym gracz zniknal.
 */
public class LogoutSpots extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 512, 32, 2048, 32));
    /** Zniknal z odleglosci wiekszej niz to = wyszedl poza zasieg renderu, a nie "zniknal nagle". */
    private final NumberSetting maxDistance = add(new NumberSetting("MaxDistance", 40, 8, 128, 4));
    /** Jak dlugo po zobaczeniu rzuconej perly uznajemy zniknieciegracza za teleport (sekundy). */
    private final NumberSetting pearlWindow = add(new NumberSetting("PearlWindow", 12, 2, 60, 1));
    private final NumberSetting fillAlpha = add(new NumberSetting("FillAlpha", 70, 0, 200, 5));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1));
    private final BoolSetting distance = add(new BoolSetting("Distance", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF7A00, true));
    private final ColorSetting friendColor = add(new ColorSetting("Friend Color", 0x55FFFF, true));

    /** Stan gracza z poprzedniego ticku (zeby wiedziec, gdzie byl, zanim zniknal). */
    private record Seen(String name, Box box, double dist, boolean dead, RegistryKey<World> dim) { }

    private record Spot(UUID id, String name, Box box, boolean friend, RegistryKey<World> dim) { }

    private record Label(String text, int rgb, float x, float y) { }

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Long> pearlTick = new HashMap<>();   // gracz -> tick, w ktorym widzielismy jego perle
    private final List<Spot> spots = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private ClientPlayNetworkHandler lastHandler;
    private ClientWorld lastWorld;
    private ClientPlayerEntity lastPlayer;
    private long tickCount;

    public LogoutSpots() {
        super("LogoutSpots", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        // wlaczenie w trakcie gry: sledzenie od nowa, ale zapisane punkty zostaja
        seen.clear();
        pearlTick.clear();
    }

    @Override
    protected void onDisable() { labels.clear(); }

    /** Wyjscie z serwera / wejscie na inny: kasujemy wszystko. */
    private void resetAll() {
        seen.clear();
        pearlTick.clear();
        spots.clear();
        labels.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh != lastHandler) {          // inny serwer albo wyjscie z serwera (zmiana wymiaru NIE zmienia handlera)
            resetAll();
            lastHandler = nh;
        }
        if (mc.world == null || mc.player == null || nh == null) return;
        tickCount++;

        // zmiana wymiaru / respawn: encje znikaja hurtowo - to nie sa wylogowania
        if (mc.world != lastWorld || mc.player != lastPlayer) {
            seen.clear();
            pearlTick.clear();
            lastWorld = mc.world;
            lastPlayer = mc.player;
        }

        // 1) perly Endu: zapisujemy, kto rzucil perle (jej wlasciciel)
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;
            UUID owner = null;
            Entity o = pearl.getOwner();
            if (o != null) {
                owner = o.getUuid();
            } else {                                   // awaryjnie: najblizszy gracz przy perle
                double best = 6.0;
                for (PlayerEntity p : mc.world.getPlayers()) {
                    double d = p.getPos().distanceTo(pearl.getPos());
                    if (d < best) {
                        best = d;
                        owner = p.getUuid();
                    }
                }
            }
            if (owner != null) pearlTick.put(owner, tickCount);
        }

        // 2) gracze widoczni teraz (kogo mamy w zasiegu renderu)
        RegistryKey<World> dim = mc.world.getRegistryKey();
        Vec3d me = mc.player.getPos();
        Map<UUID, Seen> current = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            boolean dead = p.isDead() || p.getHealth() <= 0f;
            current.put(id, new Seen(p.getName().getString(), p.getBoundingBox(), me.distanceTo(p.getPos()), dead, dim));
            spots.removeIf(sp -> sp.id().equals(id));  // wrocil w pole widzenia - stary punkt niepotrzebny
        }

        // 3) kto byl w poprzednim ticku, a teraz go nie ma = nagle zniknal
        long pearlTicks = (long) (pearlWindow.get() * 20);
        for (Map.Entry<UUID, Seen> en : seen.entrySet()) {
            UUID id = en.getKey();
            if (current.containsKey(id)) continue;
            Seen s = en.getValue();

            if (s.dead()) continue;                                    // zginal
            if (s.dist() > maxDistance.get()) continue;                // wyszedl poza zasieg renderu
            Long pt = pearlTick.get(id);
            if (pt != null && tickCount - pt <= pearlTicks) continue;  // rzucil perle - to teleport, nie wylogowanie

            spots.removeIf(sp -> sp.id().equals(id));
            spots.add(new Spot(id, s.name(), s.box(), Friends.isFriend(s.name()), s.dim()));
        }
        seen.clear();
        seen.putAll(current);

        pearlTick.values().removeIf(t -> tickCount - t > 20L * 120);   // porzadki
    }

    /** Faza swiata: wypelnione boxy + obrys, a pozycje napisow rzutujemy na ekran. */
    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        labels.clear();
        if (spots.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Matrix4f m = ms.peek().getPositionMatrix();

        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
        Matrix4f mvp = new Matrix4f(ctx.projectionMatrix()).mul(view);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int alpha = Math.max(0, Math.min(255, (int) fillAlpha.get()));
        Vec3d me = mc.player.getPos();

        List<Spot> vis = new ArrayList<>();
        List<Double> dists = new ArrayList<>();
        RegistryKey<World> dim = mc.world.getRegistryKey();
        for (Spot sp : spots) {
            if (!sp.dim().equals(dim)) continue;                       // punkt z innego wymiaru
            double d = me.distanceTo(sp.box().getCenter());
            if (d > range.get()) continue;
            vis.add(sp);
            dists.add(d);
        }
        if (vis.isEmpty()) return;

        BufferBuilder fill = RenderUtil.beginFill();
        for (Spot sp : vis) {
            int rgb = sp.friend() ? friendColor.rgb() : color.rgb();
            RenderUtil.fillBox(fill, m, cam, sp.box(), (alpha << 24) | rgb);
        }
        RenderUtil.endFill(fill);

        BufferBuilder lines = RenderUtil.begin();
        for (Spot sp : vis) {
            int rgb = sp.friend() ? friendColor.rgb() : color.rgb();
            RenderUtil.box(lines, m, cam, sp.box(), 0xFF000000 | rgb);
        }
        RenderUtil.end(lines);

        for (int i = 0; i < vis.size(); i++) {
            Spot sp = vis.get(i);
            Box b = sp.box();
            Vec3d rel = new Vec3d((b.minX + b.maxX) / 2, b.maxY + 0.3, (b.minZ + b.maxZ) / 2).subtract(cam);
            Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
            mvp.transform(v);
            if (v.w <= 0.01f) continue;                       // za plecami
            float nx = v.x / v.w, ny = v.y / v.w;
            if (nx < -1.5f || nx > 1.5f || ny < -1.5f || ny > 1.5f) continue;

            String text = sp.name() + (distance.get() ? " " + (int) Math.round(dists.get(i)) + "m" : "");
            int rgb = sp.friend() ? friendColor.rgb() : color.rgb();
            labels.add(new Label(text, rgb, (nx * 0.5f + 0.5f) * sw, (0.5f - ny * 0.5f) * sh));
        }
    }

    /** Faza HUD: nick nad boxem. */
    @Override
    public void onHudRender(DrawContext ctx, MinecraftClient mc) {
        if (labels.isEmpty()) return;
        List<Label> copy = new ArrayList<>(labels);
        labels.clear();

        TextRenderer tr = mc.textRenderer;
        MatrixStack ms = ctx.getMatrices();
        float s = (float) scale.get();
        for (Label l : copy) {
            int w = tr.getWidth(l.text());
            ms.push();
            ms.translate(l.x(), l.y(), 0);
            ms.scale(s, s, 1f);
            ctx.fill(-w / 2 - 2, -11, w / 2 + 2, 0, 0x90000000);
            ctx.drawTextWithShadow(tr, l.text(), -w / 2, -9, 0xFF000000 | l.rgb());
            ms.pop();
        }
    }
}

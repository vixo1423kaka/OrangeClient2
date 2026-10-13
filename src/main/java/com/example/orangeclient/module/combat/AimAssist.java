package com.example.orangeclient.module.combat;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ModeSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Plynnie dociaga celownik do najblizszego (katowo) przeciwnika w zasiegu i polu widzenia.
 * Dziala co klatke, wiec ruch jest gladki; nie wysyla zadnych dodatkowych pakietow -
 * serwer widzi zwykly ruch kamery.
 */
public class AimAssist extends Module {
    /** Maksymalny dystans do hitboxa celu (w blokach). */
    private final NumberSetting range = add(new NumberSetting("Range", 4.5, 1.0, 8.0, 0.1));
    /** Calkowity kat widzenia (stopnie) - cel musi byc w tym stozku wokol celownika. */
    private final NumberSetting fov = add(new NumberSetting("FOV", 70, 10, 180, 5));
    /** Sila przyciagania (im wiecej, tym szybciej celownik dochodzi do celu). */
    private final NumberSetting speed = add(new NumberSetting("Speed", 6, 1, 30, 0.5));
    /** Maks. obrot na jedna klatke w stopniach - ogranicza gwaltowne szarpniecia. */
    private final NumberSetting maxStep = add(new NumberSetting("MaxStep", 8, 1, 40, 0.5));
    /** Losowe odchylenie punktu celowania (w stopniach), zeby ruch nie byl idealny. */
    private final NumberSetting randomness = add(new NumberSetting("Randomness", 0.4, 0, 3, 0.1));
    /** Jaki punkt hitboxa wybieramy. */
    private final ModeSetting aimPoint = add(new ModeSetting("AimPoint", "Closest", "Closest", "Chest", "Head"));
    private final BoolSetting onlyWeapon = add(new BoolSetting("OnlyWeapon", false));
    private final BoolSetting onlyPlayers = add(new BoolSetting("OnlyPlayers", true));
    private final BoolSetting ignoreFriends = add(new BoolSetting("IgnoreFriends", true));
    private final BoolSetting throughWalls = add(new BoolSetting("ThroughWalls", false));

    private long lastFrame;
    /** Pozioma/pionowa losowa poprawka, odswiezana co jakis czas (a nie co klatke - bez drgan). */
    private double noiseYaw, noisePitch;
    private long nextNoise;

    public AimAssist() {
        super("AimAssist", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        lastFrame = 0;
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0.016 : (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        dt = Math.min(dt, 0.1);

        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;
        if (Freecam.isActive() || p.isUsingItem() || p.isSpectator() || p.isDead()) return;
        if (onlyWeapon.get()) {
            ItemStack held = p.getMainHandStack();
            if (!held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES)) return;
        }

        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        double halfFov = fov.get() / 2.0;

        Vec3d bestPoint = null;
        double bestAngle = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator()) continue;
            if (onlyPlayers.get() && !(e instanceof PlayerEntity)) continue;
            if (ignoreFriends.get() && Friends.isFriend(e)) continue;
            if (!throughWalls.get() && !p.canSee(e)) continue;

            Box box = e.getBoundingBox();
            if (distanceToBox(eye, box) > range.get()) continue;

            Vec3d point = pickPoint(eye, look, box);
            double angle = angleBetween(look, point.subtract(eye));
            if (angle > halfFov || angle >= bestAngle) continue;
            bestAngle = angle;
            bestPoint = point;
        }
        if (bestPoint == null) return;

        // losowa poprawka zmienia sie co 120-300 ms
        long ms = now / 1_000_000L;
        if (ms >= nextNoise) {
            double r = randomness.get();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            noiseYaw = (rnd.nextDouble() - 0.5) * 2 * r;
            noisePitch = (rnd.nextDouble() - 0.5) * 2 * r;
            nextNoise = ms + 120 + rnd.nextInt(180);
        }

        Vec3d d = bestPoint.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0 + noiseYaw);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(d.y, horiz)) + noisePitch);

        float dYaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dPitch = targetPitch - p.getPitch();

        // wygladzanie niezalezne od FPS: ulamek drogi pokonywany w tej klatce
        double factor = 1.0 - Math.exp(-speed.get() * dt);
        double step = maxStep.get();
        double moveYaw = MathHelper.clamp(dYaw * factor, -step, step);
        double movePitch = MathHelper.clamp(dPitch * factor, -step, step);
        if (Math.abs(moveYaw) < 0.001 && Math.abs(movePitch) < 0.001) return;

        // changeLookDirection mnozy przez 0.15 (jak ruch myszy) i poprawia poprzednie katy (brak drgan kamery)
        p.changeLookDirection(moveYaw / 0.15, movePitch / 0.15);
    }

    /** Punkt hitboxa, do ktorego celujemy. */
    private Vec3d pickPoint(Vec3d eye, Vec3d look, Box box) {
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double h = box.maxY - box.minY;
        switch (aimPoint.get()) {
            case "Head":
                return new Vec3d(cx, box.minY + h * 0.9, cz);
            case "Chest":
                return new Vec3d(cx, box.minY + h * 0.65, cz);
            default: {
                // najblizszy punkt hitboxa wzgledem aktualnego kierunku patrzenia - najmniejszy ruch
                double dist = eye.distanceTo(new Vec3d(cx, (box.minY + box.maxY) / 2, cz));
                Vec3d onRay = eye.add(look.multiply(dist));
                double m = 0.08; // maly margines od krawedzi, zeby nie celowac w sam brzeg
                return new Vec3d(
                        MathHelper.clamp(onRay.x, box.minX + m, box.maxX - m),
                        MathHelper.clamp(onRay.y, box.minY + m, box.maxY - m),
                        MathHelper.clamp(onRay.z, box.minZ + m, box.maxZ - m));
            }
        }
    }

    private static double angleBetween(Vec3d a, Vec3d b) {
        double la = a.length(), lb = b.length();
        if (la == 0 || lb == 0) return 0;
        return Math.toDegrees(Math.acos(MathHelper.clamp(a.dotProduct(b) / (la * lb), -1.0, 1.0)));
    }

    private static double distanceToBox(Vec3d p, Box b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

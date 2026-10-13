package com.example.orangeclient.module.combat;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import com.example.orangeclient.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

/**
 * Powieksza hitboxy encji w celowniku (z kazdej strony o maks. Size bloku), wiec latwiej w nie trafic.
 *
 * Dlaczego poprzednia wersja nie dzialala na serwerach:
 *  - nadpisywala getTargetingMargin dla KAZDEGO trafienia, wiec zawsze uzywala pelnego Size
 *    (np. 0.3) - serwerowy anticheat widzi, ze promien patrzenia nie trafia w prawdziwy hitbox;
 *  - nie pilnowala, zeby prawdziwy hitbox byl w zasiegu (serwer liczy dystans do prawdziwego boxa);
 *  - psula tez kolizje pociskow po stronie klienta (getTargetingMargin uzywa ProjectileUtil).
 *
 * Teraz: vanilla trafienie zawsze ma pierwszenstwo, a gdy go brak, uzywamy NAJMNIEJSZEGO marginesu
 * (0..Size), przy ktorym promien trafia, i tylko jesli prawdziwy hitbox jest w zasiegu.
 */
public class Hitbox extends Module {
    /** Maksymalny margines w blokach z kazdej strony (vanilla = 0). */
    private final NumberSetting size = add(new NumberSetting("Size", 0.1, 0.0, 1.0, 0.01));
    /** Uzywa najmniejszego marginesu, przy ktorym trafiasz (zamiast zawsze pelnego Size). */
    private final BoolSetting adaptive = add(new BoolSetting("Adaptive", true));
    private final BoolSetting onlyPlayers = add(new BoolSetting("OnlyPlayers", true));
    private final BoolSetting ignoreFriends = add(new BoolSetting("IgnoreFriends", true));

    public Hitbox() {
        super("Hitbox", Category.COMBAT);
    }


    /** Gdy F3+B jest wlaczone, pokazuje bazowy hitbox na bialo i rozszerzony na czerwono. */
    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        if (!isEnabled() || mc.world == null || mc.player == null || ctx.matrixStack() == null) return;
        if (!mc.getEntityRenderDispatcher().shouldRenderHitboxes()) return;
        Matrix4f matrix = ctx.matrixStack().peek().getPositionMatrix();
        Vec3d camera = ctx.camera().getPos();
        BufferBuilder normalLines = RenderUtil.begin(1.5f);
        BufferBuilder expandedLines = RenderUtil.begin(3.5f);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            float margin = extra(entity);
            if (margin <= 0.0f) continue;
            RenderUtil.box(normalLines, matrix, camera, entity.getBoundingBox(), 0xFFFFFFFF);
            RenderUtil.box(expandedLines, matrix, camera, entity.getBoundingBox().expand(margin), 0xFFFF3030);
        }
        RenderUtil.end(normalLines);
        RenderUtil.end(expandedLines);
    }

    /** Maksymalny margines dla danej encji (0, gdy modul nie dotyczy tej encji). */
    public static float extra(Entity e) {
        Hitbox m = ModuleManager.get(Hitbox.class);
        if (m == null || !m.isEnabled()) return 0f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == mc.player || !(e instanceof LivingEntity)) return 0f;
        if (m.onlyPlayers.get() && !(e instanceof PlayerEntity)) return 0f;
        if (m.ignoreFriends.get() && Friends.isFriend(e)) return 0f;
        return (float) m.size.get();
    }

    /**
     * Wywolywane z GameRenderer.findCrosshairTarget (na RETURN). Zwraca vanillowy wynik,
     * a gdy nie trafiono w encje - probuje trafic powiekszonym hitboxem.
     */
    public static HitResult adjust(HitResult vanilla, Entity camera, double blockRange, double entityRange, float tickDelta) {
        Hitbox m = ModuleManager.get(Hitbox.class);
        if (m == null || !m.isEnabled()) return vanilla;
        if (vanilla != null && vanilla.getType() == HitResult.Type.ENTITY) return vanilla;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || camera == null) return vanilla;
        double maxMargin = m.size.get();
        if (maxMargin <= 0.0) return vanilla;

        Vec3d eye = camera.getCameraPosVec(tickDelta);
        Vec3d dir = camera.getRotationVec(tickDelta);

        // promien jest odrobine dluzszy niz zasieg - powiekszony box moze byc trafiony dalej niz prawdziwy
        double limit = entityRange + maxMargin;
        // blok przed encja nadal ja zaslania
        if (vanilla != null && vanilla.getType() == HitResult.Type.BLOCK) {
            limit = Math.min(limit, vanilla.getPos().distanceTo(eye));
        }
        Vec3d end = eye.add(dir.multiply(limit));
        Box search = camera.getBoundingBox().stretch(dir.multiply(limit)).expand(1.0 + maxMargin);

        Entity best = null;
        Vec3d bestPos = null;
        double bestDist = limit * limit;

        for (Entity e : mc.world.getOtherEntities(camera, search, x -> !x.isSpectator() && x.canHit())) {
            if (extra(e) <= 0f) continue;
            Box real = e.getBoundingBox();
            // serwer liczy zasieg do PRAWDZIWEGO hitboxa - nie wychodzimy poza niego
            if (distanceToBox(eye, real) > entityRange) continue;

            double margin = m.adaptive.get() ? minimalMargin(real, eye, end, maxMargin) : maxMargin;
            if (margin < 0.0) continue;
            Vec3d hit = real.expand(margin).raycast(eye, end).orElse(null);
            if (hit == null) continue;

            double d = eye.squaredDistanceTo(hit);
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestPos = hit;
            }
        }
        return best == null ? vanilla : new EntityHitResult(best, bestPos);
    }

    /** Najmniejszy margines z zakresu (0, max], przy ktorym promien trafia w box; -1 gdy nawet max nie trafia. */
    private static double minimalMargin(Box real, Vec3d from, Vec3d to, double max) {
        if (real.expand(max).raycast(from, to).isEmpty()) return -1.0;
        double lo = 0.0, hi = max;
        for (int i = 0; i < 10; i++) {
            double mid = (lo + hi) * 0.5;
            if (real.expand(mid).raycast(from, to).isPresent()) hi = mid; else lo = mid;
        }
        return hi;
    }

    private static double distanceToBox(Vec3d p, Box b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

package com.example.orangeclient.module.combat;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trzymasz wedke i klikasz prawym -> haczyk leci w najblizszego gracza.
 *
 * Tuz przed vanillowym rzutem (doItemUse) ustawiamy yaw/pitch tak, zeby tor lotu spławika
 * (grawitacja 0.03, opor 0.92) trafil w PRZEWIDYWANA pozycje celu. Predykcja:
 *  - predkosc celu liczona ze sredniej z ostatnich ticków (a nie z jednego ticku - klient wygladza pozycje),
 *  - ruch w poziomie liniowo, w pionie z grawitacja gracza (skoki / spadanie, z ograniczeniem do podlogi),
 *  - kompensacja pingu + reczny Lead (opoznienie miedzy tym co widzisz a tym co ma serwer),
 *  - trafienie sprawdzane na calym odcinku lotu w tickach (a nie tylko w punktach), z marginesem
 *    jak przy kolizji spławika z encja.
 */
public class AutoRod extends Module {
    /** Maksymalny dystans do celu (w blokach). */
    private final NumberSetting range = add(new NumberSetting("Range", 15, 3, 30, 1));
    private final BoolSetting ignoreFriends = add(new BoolSetting("IgnoreFriends", true));
    /** Celuje tez w graczy, ktorych nie widac (za blokami). */
    private final BoolSetting throughWalls = add(new BoolSetting("ThroughWalls", false));
    /** Wyprzedza ruch celu (poziom + skoki/spadanie). */
    private final BoolSetting predict = add(new BoolSetting("Predict", true));
    /** Dolicza Twoj ping do czasu wyprzedzenia. */
    private final BoolSetting pingComp = add(new BoolSetting("PingComp", true));
    /** Dodatkowe wyprzedzenie w tickach (1 tick = 50 ms) - dostroj, jesli haczyk laduje za/przed celem. */
    private final NumberSetting lead = add(new NumberSetting("Lead", 1, -5, 10, 0.5));
    /** Po rzucie przywraca poprzedni kat patrzenia (kamera sie nie szarpie). */
    private final BoolSetting silent = add(new BoolSetting("Silent", true));

    private static final int HISTORY = 4;          // ile ostatnich pozycji pamietamy (3 odstepy)
    private static final int MAX_TICKS = 100;      // maks. dlugosc symulacji lotu
    private static final double MARGIN = 0.4;      // margines kolizji spławika z encja (0.3 + polowa spławika)

    private final Map<UUID, ArrayDeque<Vec3d>> history = new HashMap<>();
    private boolean pending;
    private float oldYaw, oldPitch;

    public AutoRod() {
        super("AutoRod", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        history.clear();
        pending = false;
    }

    @Override
    protected void onDisable() {
        afterUse(MinecraftClient.getInstance());
        history.clear();
    }

    /** Zabezpieczenie: jesli z jakiegos powodu nie przywrocilismy katow po rzucie. */
    @Override
    public void onPreTick(MinecraftClient mc) {
        afterUse(mc);
    }

    /** Co tick zapisujemy pozycje graczy, z tego liczymy gladka predkosc. */
    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            history.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity e : mc.world.getPlayers()) {
            if (e == mc.player) continue;
            UUID id = e.getUuid();
            seen.add(id);
            ArrayDeque<Vec3d> h = history.computeIfAbsent(id, k -> new ArrayDeque<>());
            Vec3d pos = e.getPos();
            // teleport / respawn - stara historia jest bezuzyteczna
            if (!h.isEmpty() && h.peekLast().squaredDistanceTo(pos) > 9.0) h.clear();
            h.addLast(pos);
            while (h.size() > HISTORY) h.removeFirst();
        }
        history.keySet().retainAll(seen);
    }

    /** Wolane na poczatku MinecraftClient#doItemUse. */
    public void beforeUse(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;
        if (Freecam.isActive() || p.isSpectator() || !p.isAlive()) return;
        if (!p.getMainHandStack().isOf(Items.FISHING_ROD) && !p.getOffHandStack().isOf(Items.FISHING_ROD)) return;
        if (p.fishHook != null) return; // wedka zarzucona - vanilla zwija

        Entity target = findTarget(mc, p);
        if (target == null) return;

        float[] rot = solve(mc, p, target);
        if (rot == null) return; // poza zasiegiem rzutu

        oldYaw = p.getYaw();
        oldPitch = p.getPitch();
        pending = silent.get();
        p.setYaw(rot[0]);
        p.setPitch(rot[1]);
    }

    /** Wolane na koncu doItemUse (i co tick jako zabezpieczenie). */
    public void afterUse(MinecraftClient mc) {
        if (!pending) return;
        pending = false;
        PlayerEntity p = mc != null ? mc.player : null;
        if (p == null) return;
        p.setYaw(oldYaw);
        p.setPitch(oldPitch);
    }

    private Entity findTarget(MinecraftClient mc, PlayerEntity p) {
        Entity best = null;
        double bestDist = range.get();
        for (PlayerEntity e : mc.world.getPlayers()) {
            if (e == p || !e.isAlive() || e.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.isFriend(e)) continue;
            if (!throughWalls.get() && !p.canSee(e)) continue;
            double d = p.distanceTo(e);
            if (d > bestDist) continue;
            bestDist = d;
            best = e;
        }
        return best;
    }

    // ------------------------------------------------------------------ predykcja

    /** Predkosc celu (blok/tick) - srednia z historii, awaryjnie roznica z ostatniego ticku. */
    private Vec3d velocity(Entity e) {
        ArrayDeque<Vec3d> h = history.get(e.getUuid());
        if (h == null || h.size() < 2) {
            return new Vec3d(e.getX() - e.prevX, e.getY() - e.prevY, e.getZ() - e.prevZ);
        }
        return h.peekLast().subtract(h.peekFirst()).multiply(1.0 / (h.size() - 1));
    }

    /** Wysokosc podlogi pod celem (do ograniczenia przewidywanego spadania). */
    private static double groundY(MinecraftClient mc, Entity e) {
        Vec3d from = new Vec3d(e.getX(), e.getY() + 0.1, e.getZ());
        Vec3d to = from.add(0, -30, 0);
        BlockHitResult r = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
        return r.getType() == HitResult.Type.MISS ? e.getY() - 30 : r.getPos().y;
    }

    /** Przewidywane polozenie srodka hitboxa celu w tickach 0..n. */
    private Vec3d[] buildPath(MinecraftClient mc, Entity t, int n) {
        Box box = t.getBoundingBox();
        Vec3d c0 = box.getCenter();
        Vec3d[] path = new Vec3d[n + 1];
        path[0] = c0;

        Vec3d v = predict.get() ? velocity(t) : Vec3d.ZERO;
        double hx = v.x, hz = v.z;
        double hl = Math.sqrt(hx * hx + hz * hz);
        if (hl > 1.0) { hx /= hl; hz /= hl; }

        boolean air = predict.get() && !t.isOnGround();
        double vy = air ? MathHelper.clamp(v.y, -3.0, 3.0) : 0.0;
        double halfH = (box.maxY - box.minY) / 2.0;
        double floor = air ? groundY(mc, t) + halfH : c0.y;
        double y = c0.y;

        for (int i = 1; i <= n; i++) {
            if (air) {
                y += vy;
                vy = (vy - 0.08) * 0.98;           // grawitacja gracza
                if (y <= floor) { y = floor; vy = 0; air = false; }
            }
            path[i] = new Vec3d(c0.x + hx * i, y, c0.z + hz * i);
        }
        return path;
    }

    private static Vec3d at(Vec3d[] path, double t) {
        t = MathHelper.clamp(t, 0.0, path.length - 1.001);
        int i = (int) t;
        return path[i].lerp(path[i + 1], t - i);
    }

    private double leadTicks(MinecraftClient mc, PlayerEntity p) {
        double l = lead.get();
        if (pingComp.get()) {
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            PlayerListEntry me = nh != null ? nh.getPlayerListEntry(p.getUuid()) : null;
            if (me != null) l += me.getLatency() / 50.0;
        }
        return Math.max(-5.0, l);
    }

    /** Wylicza {yaw, pitch}, przy ktorych spławik trafi w przewidywana pozycje celu; null, gdy sie nie da. */
    private float[] solve(MinecraftClient mc, PlayerEntity p, Entity t) {
        Vec3d eye = p.getEyePos();
        double leadT = leadTicks(mc, p);
        Vec3d[] path = buildPath(mc, t, MAX_TICKS + (int) Math.ceil(Math.max(leadT, 0)) + 3);

        Box box = t.getBoundingBox();
        double halfW = (box.maxX - box.minX) / 2.0;
        double halfH = (box.maxY - box.minY) / 2.0;

        Vec3d aim = at(path, Math.max(leadT, 0));
        float yaw = 0, pitch = 0;
        double[] r = null;
        // iteracje: czas lotu zalezy od pitcha, a punkt wyprzedzenia od czasu lotu
        for (int iter = 0; iter < 4; iter++) {
            Vec3d d = aim.subtract(eye);
            yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
            r = bestPitch(eye, yaw, path, leadT, halfW, halfH);
            pitch = (float) r[0];
            aim = at(path, r[1] - 0.5 + leadT);
        }
        boolean hit = r[3] > 0.5;
        return (hit || r[2] <= 1.2) ? new float[]{yaw, pitch} : null;
    }

    /**
     * Przeszukuje pitche i symuluje lot spławika. Trafienie = odcinek lotu w danym ticku przecina
     * hitbox celu (powiekszony o margines). Zwraca {pitch, tick, dystansDoSrodka, trafil(0/1)}.
     */
    private static double[] bestPitch(Vec3d eye, float yaw, Vec3d[] path, double leadT, double halfW, double halfH) {
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double ew = 2 * (halfW + MARGIN), eh = 2 * (halfH + MARGIN);

        double bestPitch = 0, bestTick = 1, bestDist = Double.MAX_VALUE;
        boolean bestHit = false;

        for (double pitch = -89; pitch <= 89; pitch += 0.25) {
            double pr = Math.toRadians(pitch);
            // predkosc startowa jak w konstruktorze FishingBobberEntity
            double hs = 0.6 * Math.cos(pr) + 0.5;
            double vx = fx * hs, vy = -Math.tan(pr) * hs, vz = fz * hs;
            double x = eye.x + fx * 0.3, y = eye.y, z = eye.z + fz * 0.3;

            for (int tick = 1; tick <= MAX_TICKS; tick++) {
                Vec3d prev = new Vec3d(x, y, z);
                vy -= 0.03;
                x += vx; y += vy; z += vz;
                vx *= 0.92; vy *= 0.92; vz *= 0.92;
                Vec3d cur = new Vec3d(x, y, z);

                Vec3d c = at(path, tick - 0.5 + leadT);
                Box b = Box.of(c, ew, eh, ew);
                boolean hit = b.contains(prev) || b.contains(cur) || b.raycast(prev, cur).isPresent();
                double dist = pointSegmentDist(c, prev, cur);

                if (hit ? (!bestHit || dist < bestDist) : (!bestHit && dist < bestDist)) {
                    bestHit = hit;
                    bestDist = dist;
                    bestPitch = pitch;
                    bestTick = tick;
                }
                if (y < eye.y - 80) break;
            }
        }
        return new double[]{bestPitch, bestTick, bestDist, bestHit ? 1 : 0};
    }

    private static double pointSegmentDist(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        double t = len2 == 0 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.multiply(t)));
    }
}

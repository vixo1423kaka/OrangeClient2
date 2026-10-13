package com.example.orangeclient.module.combat;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Bije automatycznie, gdy celownik jest na przeciwniku. Odstep miedzy uderzeniami ustawiasz w opcjach. */
public class TriggerBot extends Module {
    /** Minimalny odstep miedzy uderzeniami w milisekundach (dokladnosc ~50 ms, bo sprawdzamy co tick). */
    private final NumberSetting delay = add(new NumberSetting("Delay", 100, 0, 1000, 10));
    /** Losowe dodatkowe opoznienie 0..Jitter ms przy kazdym uderzeniu (zeby nie bylo idealnie rowno). */
    private final NumberSetting jitter = add(new NumberSetting("Jitter", 50, 0, 300, 10));
    /** Czeka az naladuje sie cooldown ataku (pelna sila uderzenia, jak w 1.9+). */
    private final BoolSetting waitCooldown = add(new BoolSetting("WaitCooldown", true));
    private final NumberSetting cooldownMin = add(new NumberSetting("MinCooldown", 0.9, 0.1, 1.0, 0.05));
    private final BoolSetting onlyPlayers = add(new BoolSetting("OnlyPlayers", true));
    private final BoolSetting ignoreFriends = add(new BoolSetting("IgnoreFriends", true));
    /** Bije tylko, gdy uderzenie bedzie krytyczne (spadasz, nie sprintujesz, cooldown >= 0.9). */
    private final BoolSetting crits = add(new BoolSetting("OnlyCrits", false));
    /** Bije tylko mieczem / toporem w rece. */
    private final BoolSetting onlyWeapon = add(new BoolSetting("OnlyWeapon", false));
    /** Bije tez wtedy, gdy miedzy Toba a celem jest blok (celownik "widzi" encje przez sciane). */
    private final BoolSetting throughWalls = add(new BoolSetting("ThroughWalls", false));

    private long lastHit;
    private long nextDelayMs;

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        lastHit = 0;
        nextDelayMs = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.isActive() || p.isUsingItem()) return;

        Entity target = null;
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr && hr.getType() == HitResult.Type.ENTITY) {
            target = ehr.getEntity();
        } else if (throughWalls.get()) {
            target = raycastEntityIgnoringBlocks(mc, p);
        }
        if (target == null) return;
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        if (onlyPlayers.get() && !(target instanceof PlayerEntity)) return;
        if (ignoreFriends.get() && Friends.isFriend(target)) return;

        if (onlyWeapon.get()) {
            ItemStack held = p.getMainHandStack();
            if (!held.isIn(ItemTags.SWORDS) && !held.isIn(ItemTags.AXES)) return;
        }
        if (crits.get() && !(p.fallDistance > 0.0 && !p.isOnGround() && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasVehicle() && !p.isSprinting()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && p.getAttackCooldownProgress(0.5f) > 0.9f)) return;
        double minC = Math.min(1.0, cooldownMin.get() + ThreadLocalRandom.current().nextDouble() * 0.04);
        if (waitCooldown.get() && p.getAttackCooldownProgress(0.5f) < minC) return;

        long now = System.currentTimeMillis();
        if (now - lastHit < delay.get() + nextDelayMs) return;

        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        lastHit = now;
        int j = (int) jitter.get();
        nextDelayMs = j > 0 ? ThreadLocalRandom.current().nextInt(j + 1) : 0;
    }

    /** Najblizsza encja na linii patrzenia w zasiegu ataku - bloki sie nie licza. */
    private static Entity raycastEntityIgnoringBlocks(MinecraftClient mc, PlayerEntity p) {
        double reach = p.getEntityInteractionRange();
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1.0f).multiply(reach));
        Box search = p.getBoundingBox().stretch(p.getRotationVec(1.0f).multiply(reach)).expand(1.0);

        Entity best = null;
        double bestDist = reach * reach;
        for (Entity e : mc.world.getOtherEntities(p, search, x -> !x.isSpectator() && x.canHit())) {
            Vec3d hit = e.getBoundingBox().raycast(eye, end).orElse(null);
            if (hit == null) continue;
            double d = eye.squaredDistanceTo(hit);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}

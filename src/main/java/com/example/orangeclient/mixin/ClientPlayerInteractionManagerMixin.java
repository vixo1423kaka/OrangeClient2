package com.example.orangeclient.mixin;

import com.example.orangeclient.module.combat.KnockbackDisplacement;
import com.example.orangeclient.module.player.AntiTrap;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Unique
    private boolean orange$kbRotated = false;
    /** Tick gracza (age), w ktorym ostatnio wyslalismy przesuniety yaw. */
    @Unique
    private int orange$kbLastTick = Integer.MIN_VALUE / 2;

    /**
     * Kolejnosc pakietow: look(yaw + offset) -> interact(attack) -> look(prawdziwy yaw).
     * Pakiet ataku wysyla oryginalna attackEntity, wiec jej nie anulujemy.
     */
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void orange$kbBefore(PlayerEntity player, Entity target, CallbackInfo ci) {
        orange$kbRotated = false;

        KnockbackDisplacement m = KnockbackDisplacement.active();
        if (m == null) return;
        if (!(player instanceof ClientPlayerEntity self)) return;
        if (target == null || !target.isAlive() || target.isRemoved()) return;
        int offset = m.getOffset();
        if (offset == 0) return;
        // limit pakietow: max 1 przesuniecie na MinInterval tickow (min. 1 na tick)
        if (self.age - orange$kbLastTick < Math.max(1, m.getMinInterval())) return;
        // nie ruszamy rotacji, gdy serwer czeka na teleport / gracz jest w pojezdzie / spectator
        if (self.hasVehicle() || self.isSpectator() || self.isDead()) return;
        if (orange$eyeDistanceToBox(self, target) > m.getRange()) return;

        orange$kbLastTick = self.age;
        orange$sendLook(self, self.getYaw() + offset, self.getPitch());
        orange$kbRotated = true;
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void orange$kbAfter(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!orange$kbRotated) return;
        orange$kbRotated = false;
        if (player instanceof ClientPlayerEntity self) {
            orange$sendLook(self, self.getYaw(), self.getPitch());
        }
    }

    /** AntiTrap: PPM na blok z GUI (skrzynka, tabliczka...) nie wysyla interakcji, wiec nic sie nie otwiera. */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void orange$antiTrapNoGui(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                      CallbackInfoReturnable<ActionResult> cir) {
        if (AntiTrap.blocksGui(player, hitResult)) cir.setReturnValue(ActionResult.FAIL);
    }

    @Unique
    private static void orange$sendLook(ClientPlayerEntity self, float yaw, float pitch) {
        if (self.networkHandler == null) return;
        // od 1.21.2 konstruktor ma 4 parametry: yaw, pitch, onGround, horizontalCollision
        self.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f),
                self.isOnGround(), self.horizontalCollision));
    }

    @Unique
    private static double orange$eyeDistanceToBox(ClientPlayerEntity self, Entity target) {
        Box b = target.getBoundingBox();
        Vec3d eye = self.getEyePos();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

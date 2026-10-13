package com.example.orangeclient.mixin;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.module.player.NoPush;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique private Vec3d orange$pushVelocity;
    @Unique private Vec3d orange$fluidVelocity;

    /** Freecam: ruch myszy obraca kamere, a nie gracza. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity && Freecam.isActive()) {
            Freecam.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }

    /**
     * pushAwayFrom zmienia predkosc OBU encji naraz, wiec nie anulujemy metody
     * (inni gracze dalej sie odpychaja), tylko zapamietujemy predkosc lokalnego gracza
     * przed i przywracamy ja po wywolaniu.
     */
    @Inject(method = "pushAwayFrom", at = @At("HEAD"))
    private void orange$pushHead(Entity other, CallbackInfo ci) {
        orange$pushVelocity = null;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        NoPush np = ModuleManager.get(NoPush.class);
        if (p == null || np == null || !np.blocksEntityPush()) return;
        if ((Object) this == p || other == p) orange$pushVelocity = p.getVelocity();
    }

    @Inject(method = "pushAwayFrom", at = @At("RETURN"))
    private void orange$pushReturn(Entity other, CallbackInfo ci) {
        if (orange$pushVelocity == null) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.setVelocity(orange$pushVelocity);
        orange$pushVelocity = null;
    }

    /** updateMovementInFluid zmienia predkosc tylko przez nurt wody/lawy - cofamy to. */
    @Inject(method = "updateMovementInFluid", at = @At("HEAD"))
    private void orange$fluidHead(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir) {
        orange$fluidVelocity = null;
        NoPush np = ModuleManager.get(NoPush.class);
        if (np != null && np.blocksLiquidPush() && (Object) this instanceof ClientPlayerEntity) {
            orange$fluidVelocity = ((Entity) (Object) this).getVelocity();
        }
    }

    @Inject(method = "updateMovementInFluid", at = @At("RETURN"))
    private void orange$fluidReturn(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (orange$fluidVelocity == null) return;
        ((Entity) (Object) this).setVelocity(orange$fluidVelocity);
        orange$fluidVelocity = null;
    }
}

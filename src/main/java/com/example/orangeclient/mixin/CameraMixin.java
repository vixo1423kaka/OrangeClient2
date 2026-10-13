package com.example.orangeclient.mixin;

import com.example.orangeclient.module.movement.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    /** Freecam: po zwyklym ustawieniu kamery przenosimy ja w wolne miejsce. */
    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void orange$freecamUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                      boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Vec3d p = Freecam.cameraPos(tickDelta);
        setPos(p.x, p.y, p.z);
        setRotation(Freecam.cameraYaw(), Freecam.cameraPitch());
    }

    /** Freecam: kamera "w trzeciej osobie", dzieki temu widac model gracza, a reka nie jest rysowana. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$freecamThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive()) cir.setReturnValue(true);
    }
}

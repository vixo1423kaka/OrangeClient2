package com.example.orangeclient.mixin;

import com.example.orangeclient.module.render.Removals;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void orange$noFire(CallbackInfo ci) {
        if (Removals.noFire()) ci.cancel();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void orange$noWater(CallbackInfo ci) {
        if (Removals.noWater()) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void orange$noWall(CallbackInfo ci) {
        if (Removals.noWall()) ci.cancel();
    }
}

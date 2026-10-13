package com.example.orangeclient.mixin;

import com.example.orangeclient.module.render.Removals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Nakladki tekstur na ekranie: dynia i powder snow. */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$overlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        String path = texture.getPath();
        if (path.contains("pumpkinblur") && Removals.noPumpkin()) ci.cancel();
        else if (path.contains("powder_snow") && Removals.noPowderSnow()) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$portal(CallbackInfo ci) {
        if (Removals.noPortal()) ci.cancel();
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$vignette(CallbackInfo ci) {
        if (Removals.noVignette()) ci.cancel();
    }
}

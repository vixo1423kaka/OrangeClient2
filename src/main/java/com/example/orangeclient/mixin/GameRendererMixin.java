package com.example.orangeclient.mixin;

import com.example.orangeclient.module.combat.Hitbox;
import com.example.orangeclient.module.player.AntiTrap;
import com.example.orangeclient.module.render.Removals;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$noHurtCam(CallbackInfo ci) {
        if (Removals.noHurtCam()) ci.cancel();
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$noTotem(CallbackInfo ci) {
        if (Removals.noTotem()) ci.cancel();
    }

    /**
     * AntiTrap: jesli vanilla trafilo w encje, liczymy celownik jeszcze raz
     * tylko dla blokow - encje przestaja zaslaniac to, co jest za nimi.
     */
    @Inject(method = "findCrosshairTarget", at = @At("RETURN"), cancellable = true)
    private void orange$antiTrap(Entity camera, double blockRange, double entityRange, float tickDelta,
                                            CallbackInfoReturnable<HitResult> cir) {
        // Hitbox najpierw (w tym samym wstrzyknieciu, zeby kolejnosc byla przewidywalna)
        HitResult original = cir.getReturnValue();
        HitResult hr = Hitbox.adjust(original, camera, blockRange, entityRange, tickDelta);
        if (hr != original) cir.setReturnValue(hr);

        if (hr != null && hr.getType() == HitResult.Type.ENTITY
                && (AntiTrap.active() || AntiTrap.hides(((EntityHitResult) hr).getEntity()))) {
            cir.setReturnValue(camera.raycast(blockRange, tickDelta, false));
        }

        // AntiTrap: kopiemy plotek/cobweb pod graczem "przez" cobweb - celownik idzie wprost na pulapke,
        // zamiast na cobweb, w ktory vanilla by trafilo (dzieki temu cobweb zostaje nietkniety).
        // Musi byc na samym koncu, bo vanilla przelicza celownik po naszym onPreTick.
        BlockHitResult forced = AntiTrap.forcedHit();
        if (forced != null) cir.setReturnValue(forced);
    }
}

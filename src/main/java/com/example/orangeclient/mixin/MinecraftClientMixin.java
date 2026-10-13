package com.example.orangeclient.mixin;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.combat.AutoRod;
import com.example.orangeclient.module.player.ErrorKiller;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** ErrorKiller: przechwytuje prawy klik z blokiem w reku (odpowiednik mixina z oryginalnego moda). */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void orange$errorKiller(CallbackInfo ci) {
        ErrorKiller ek = ModuleManager.get(ErrorKiller.class);
        if (ek != null && ek.isEnabled() && ek.tryIntercept((MinecraftClient) (Object) this)) {
            ci.cancel();
        }
    }

    /** AutoRod: tuz przed rzutem wedka ustawia kat w strone najblizszego gracza. */
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void orange$autoRodBefore(CallbackInfo ci) {
        AutoRod ar = ModuleManager.get(AutoRod.class);
        if (ar != null && ar.isEnabled()) ar.beforeUse((MinecraftClient) (Object) this);
    }

    /** AutoRod: po rzucie przywraca poprzedni kat patrzenia (tryb Silent). */
    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void orange$autoRodAfter(CallbackInfo ci) {
        AutoRod ar = ModuleManager.get(AutoRod.class);
        if (ar != null && ar.isEnabled()) ar.afterUse((MinecraftClient) (Object) this);
    }
}

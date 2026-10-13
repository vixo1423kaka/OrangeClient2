package com.example.orangeclient.mixin;

import com.example.orangeclient.module.render.Removals;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    /** Removals > Weather: tylko po stronie klienta (nie dotykamy serwera w singleplayerze). */
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$noRain(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && Removals.noWeather()) cir.setReturnValue(0f);
    }
}

package com.example.orangeclient.mixin;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.combat.KillAura;
import com.example.orangeclient.module.render.Nametags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** Chowa vanillowy nametag graczy, gdy wlaczony jest modul Nametags (opcja HideVanilla). */
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void orange$hideVanillaLabel(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof PlayerEntity)) return;
        Nametags nt = ModuleManager.get(Nametags.class);
        if (nt != null && nt.hidesVanilla()) cir.setReturnValue(false);
    }

    /**
     * KillAura (Silent): kamera zostaje na miejscu, ale model WLASNEGO gracza (3. osoba) jest rysowany
     * z rotacja wysylana do serwera (opcja SilentModel).
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"), require = 0)
    private void orange$silentRotationModel(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        KillAura ka = ModuleManager.get(KillAura.class);
        if (ka == null || !ka.isRenderRotating()) return;
        state.bodyYaw = ka.renderYaw(tickDelta);
        state.yawDegrees = 0f;
        state.pitch = ka.renderPitch(tickDelta);
    }
}

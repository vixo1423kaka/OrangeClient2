package com.example.orangeclient.mixin;

import com.example.orangeclient.module.player.AntiTrap;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** AntiTrap: obrazy, item framey i minecarty nie sa w ogole kwalifikowane do renderowania. */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void orange$antiTrapShouldRender(Entity entity, Frustum frustum, double x, double y, double z,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (AntiTrap.hides(entity)) cir.setReturnValue(false);
    }

    /** Zabezpieczenie: encja, ktora renderuje sie mimo shouldRender (np. minecart, w ktorym jedziesz). */
    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void orange$antiTrapRender(Entity entity, double x, double y, double z, float tickDelta,
                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                       CallbackInfo ci) {
        if (AntiTrap.hides(entity)) ci.cancel();
    }
}

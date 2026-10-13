package com.example.orangeclient.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumpingCooldown")
    int getJumpingCooldown();

    @Accessor("jumpingCooldown")
    void setJumpingCooldown(int value);

    /** Vanillowy skok (nadaje predkosc w gore + boost ze sprintu). */
    @Invoker("jump")
    void invokeJump();
}

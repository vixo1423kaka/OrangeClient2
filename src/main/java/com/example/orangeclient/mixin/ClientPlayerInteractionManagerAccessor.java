package com.example.orangeclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
    @Accessor("blockBreakingCooldown")
    int getBlockBreakingCooldown();

    @Accessor("blockBreakingCooldown")
    void setBlockBreakingCooldown(int value);

    @Accessor("currentBreakingProgress")
    float getCurrentBreakingProgress();

    @Accessor("currentBreakingProgress")
    void setCurrentBreakingProgress(float value);

    @Accessor("breakingBlock")
    boolean isBreakingBlock();
}

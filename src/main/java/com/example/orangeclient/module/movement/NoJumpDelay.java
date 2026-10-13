package com.example.orangeclient.module.movement;

import com.example.orangeclient.mixin.LivingEntityAccessor;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import net.minecraft.client.MinecraftClient;

/** Usuwa opoznienie miedzy skokami (vanilla: 10 tickow) - przy trzymanej spacji skaczesz od razu po wyladowaniu. */
public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT);
    }

    @Override
    public void onPreTick(MinecraftClient mc) {
        reset(mc);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        reset(mc);
    }

    private static void reset(MinecraftClient mc) {
        if (mc.player == null) return;
        LivingEntityAccessor acc = (LivingEntityAccessor) mc.player;
        if (acc.getJumpingCooldown() != 0) acc.setJumpingCooldown(0);
    }
}

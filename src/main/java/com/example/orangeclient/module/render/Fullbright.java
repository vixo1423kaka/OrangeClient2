package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", Category.RENDER);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 240) {
            // efekt tylko po stronie klienta, serwer o nim nie wie
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}

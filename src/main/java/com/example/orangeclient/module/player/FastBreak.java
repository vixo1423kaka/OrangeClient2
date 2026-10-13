package com.example.orangeclient.module.player;

import com.example.orangeclient.mixin.ClientPlayerInteractionManagerAccessor;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;

/**
 * Szybsze kopanie:
 *  - Delay: przerwa (w tickach) miedzy zniszczeniem jednego bloku a rozpoczeciem kolejnego (vanilla = 5, 0 = bez przerwy);
 *  - Progress: po osiagnieciu tego postepu kopania blok jest konczony od razu (1.0 = wylaczone).
 *    Vanilla serwer akceptuje zniszczenie od ok. 70% postepu, wiec 0.7 jest najbezpieczniejsza wartoscia.
 */
public class FastBreak extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 0, 0, 5, 1));
    private final NumberSetting progress = add(new NumberSetting("Progress", 0.7, 0.5, 1.0, 0.05));

    public FastBreak() {
        super("FastBreak", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator() || Freecam.isActive()) return;

        ClientPlayerInteractionManager im = mc.interactionManager;
        ClientPlayerInteractionManagerAccessor acc = (ClientPlayerInteractionManagerAccessor) im;

        int maxDelay = (int) delay.get();
        if (acc.getBlockBreakingCooldown() > maxDelay) acc.setBlockBreakingCooldown(maxDelay);

        // dobijamy blok tylko gdy faktycznie go kopiemy (trzymany LPM)
        if (progress.get() < 1.0 && acc.isBreakingBlock() && mc.options.attackKey.isPressed()
                && acc.getCurrentBreakingProgress() >= (float) progress.get()) {
            acc.setCurrentBreakingProgress(1.0f);
        }
    }
}

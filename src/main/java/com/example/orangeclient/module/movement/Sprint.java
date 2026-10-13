package com.example.orangeclient.module.movement;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * AutoSprint: biegniesz, gdy idziesz do przodu, bez trzymania klawisza sprintu.
 * Zamiast wymuszac sprint recznie, wciskamy klawisz sprintu przed odczytem inputu gracza,
 * wiec reszte warunkow (glod, sneak, uzywanie przedmiotu, kolizja) sprawdza sam Minecraft.
 */
public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
    }

    @Override
    public void onPreTick(MinecraftClient mc) {
        if (mc.player == null || Freecam.isActive()) return;   // w Freecam klawisze sa odpiete od gracza
        mc.options.sprintKey.setPressed(true);
    }

    @Override
    protected void onDisable() {
        KeyBinding.updatePressedStates();                      // przywroc prawdziwy stan klawiszy
    }
}

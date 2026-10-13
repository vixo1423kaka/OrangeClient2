package com.example.orangeclient.module.player;

import com.example.orangeclient.mixin.MinecraftClientAccessor;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;

public class FastPlace extends Module {
    /** Odstep miedzy uzyciami w tickach: 1 = co tick (najszybciej), 4 = vanilla. */
    private final NumberSetting delay = add(new NumberSetting("Delay", 1, 1, 4, 1));
    /** Przyspiesza tylko gdy trzymasz blok (nie jedzenie, perly itp.). */
    private final BoolSetting onlyBlocks = add(new BoolSetting("OnlyBlocks", true));

    public FastPlace() {
        super("FastPlace", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (onlyBlocks.get()
                && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)
                && !(mc.player.getOffHandStack().getItem() instanceof BlockItem)) return;

        MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;
        int max = (int) delay.get();
        if (acc.getItemUseCooldown() > max) acc.setItemUseCooldown(max);
    }
}

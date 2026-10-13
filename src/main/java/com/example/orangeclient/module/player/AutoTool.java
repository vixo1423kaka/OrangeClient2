package com.example.orangeclient.module.player;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/** Podczas kopania automatycznie wybiera z paska najlepsze narzedzie do bloku, na ktory patrzysz. */
public class AutoTool extends Module {
    /** Po skonczeniu kopania wraca do slotu, ktory trzymales wczesniej. */
    private final BoolSetting switchBack = add(new BoolSetting("SwitchBack", true));
    /** Nie wybiera narzedzi, ktore zaraz sie zepsuja (1 uzycie do konca). */
    private final BoolSetting saveBroken = add(new BoolSetting("AvoidBreaking", true));

    /** Opoznienie (w tickach, 20 = 1 s) od rozpoczecia kopania bloku do zmiany slotu. 0 = od razu. */
    private final NumberSetting delay = add(new NumberSetting("Delay", 0, 0, 20, 1));

    private int prevSlot = -1;
    private int autoSlot = -1;
    /** Ile tickow z rzedu kopiemy ten sam blok (do opoznienia). */
    private int miningTicks = 0;
    private BlockPos lastPos = null;

    public AutoTool() {
        super("AutoTool", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        restore(MinecraftClient.getInstance());
    }

    /** Na poczatku ticku, jeszcze przed obsluga klikniec - dzieki temu kopiemy od razu dobrym narzedziem. */
    @Override
    public void onPreTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            prevSlot = autoSlot = -1;
            miningTicks = 0;
            lastPos = null;
            return;
        }
        BlockState state = null;
        BlockPos pos = null;
        boolean mining = mc.currentScreen == null && !Freecam.isActive() && !mc.player.isCreative()
                && !mc.player.isUsingItem() && mc.options.attackKey.isPressed()
                && mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK;
        if (mining) {
            pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
            state = mc.world.getBlockState(pos);
            if (state.isAir() || state.getHardness(mc.world, pos) < 0) mining = false;
        }

        if (!mining) {
            restore(mc);
            miningTicks = 0;
            lastPos = null;
            return;
        }

        // licznik opoznienia liczy sie od chwili, gdy zaczynasz kopac dany blok
        if (!pos.equals(lastPos)) {
            lastPos = pos;
            miningTicks = 0;
        }
        miningTicks++;
        if (miningTicks <= (int) delay.get()) return;

        PlayerInventory inv = mc.player.getInventory();
        int cur = inv.selectedSlot;
        int best = cur;
        float bestScore = score(mc.player.getInventory().getStack(cur), state);
        for (int i = 0; i < 9; i++) {
            if (i == cur) continue;
            ItemStack st = inv.getStack(i);
            if (saveBroken.get() && st.isDamageable() && st.getMaxDamage() - st.getDamage() <= 1) continue;
            float sc = score(st, state);
            if (sc > bestScore + 0.01f) {
                bestScore = sc;
                best = i;
            }
        }
        if (best != cur) {
            if (prevSlot == -1) prevSlot = cur;
            inv.selectedSlot = best;
            autoSlot = best;
        }
    }

    /** Wyzszy wynik = lepsze narzedzie: najpierw takie, ktore daje drop z bloku, potem szybkosc kopania. */
    private static float score(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);
        return speed + (stack.isSuitableFor(state) ? 1000f : 0f);
    }

    private void restore(MinecraftClient mc) {
        if (prevSlot != -1 && mc != null && mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            // wracamy tylko, jesli gracz sam nie zmienil slotu w miedzyczasie
            if (switchBack.get() && inv.selectedSlot == autoSlot) inv.selectedSlot = prevSlot;
        }
        prevSlot = -1;
        autoSlot = -1;
    }
}

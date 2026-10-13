package com.example.orangeclient.module.player;

import com.example.orangeclient.OrangeClient;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Port moda "Error Killer" (errorkiller 1.11.1 dla MC 1.21.4, autor: sigmapowazna, licencja MIT) na modul klienta.
 *
 * Gdy stawiasz blok i miejsce docelowe jest "blokiem-celem" (ta sama klasa, co w oryginalnym modzie),
 * zwykle uzycie jest anulowane, a zamiast niego - wszystko w jednym ticku:
 *   1) przelaczamy sie na wiadro z woda/lawa z paska i uzywamy je (postawienie plynu),
 *   2) uzywamy je ponownie (odebranie plynu),
 *   3) wracamy na slot z blokiem i stawiamy blok w to samo miejsce.
 */
public class ErrorKiller extends Module {
    /** Klasa "bloku-celu" z oryginalnego moda (net.minecraft.class_2401), rozwiazywana niezaleznie od mapowan. */
    private static Class<?> targetClass;
    private static boolean targetResolved;

    public ErrorKiller() {
        super("ErrorKiller", Category.PLAYER);
    }

    /**
     * Wywolywane z mixina na poczatku MinecraftClient#doItemUse.
     * @return true, jesli zwykle uzycie przedmiotu ma zostac anulowane
     */
    public boolean tryIntercept(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.crosshairTarget == null) return false;
        if (mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return false;

        ItemStack main = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (!(main.getItem() instanceof BlockItem)) return false;

        BlockHitResult target = (BlockHitResult) mc.crosshairTarget;
        BlockPos placePos = target.getBlockPos().offset(target.getSide());
        if (!isTargetBlock(mc.world.getBlockState(placePos).getBlock())) return false;

        run(mc, target, mc.player.getInventory().selectedSlot);
        return true;
    }

    private void run(MinecraftClient mc, BlockHitResult target, int blockSlot) {
        int liquidSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.WATER_BUCKET) || s.isOf(Items.LAVA_BUCKET)) {
                liquidSlot = i;
                break;
            }
        }
        if (liquidSlot == -1) {
            mc.player.sendMessage(Text.literal("§c[Error Killer] No water or lava bucket in hotbar!"), true);
            return;
        }

        int prev = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = liquidSlot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // postaw plyn
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // odbierz plyn
        mc.player.getInventory().selectedSlot = blockSlot;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target);
        mc.player.getInventory().selectedSlot = prev;
    }

    private static boolean isTargetBlock(Block block) {
        if (!targetResolved) {
            targetResolved = true;
            try {
                String name = FabricLoader.getInstance().getMappingResolver()
                        .mapClassName("intermediary", "net.minecraft.class_2401");
                targetClass = Class.forName(name);
            } catch (Throwable t) {
                OrangeClient.LOGGER.error("ErrorKiller: nie udalo sie rozwiazac klasy bloku-celu", t);
            }
        }
        return targetClass != null && targetClass.isInstance(block);
    }
}

package com.example.orangeclient.module.player;

import com.example.orangeclient.mixin.MinecraftClientAccessor;
import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.movement.Freecam;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * AutoDripstone (metoda jak w BK-Dripstone):
 * trzymasz PRAWY PRZYCISK MYSZY i patrzysz na ZAMKNIETA klape (trapdoor) ->
 * modul sam bierze pointed dripstone z hotbara i stawia go POD klapa, klikajac w bok bloku
 * za klapa (bez skradania), po czym wraca na poprzedni slot. Klape otwiera/zamyka zwykly
 * prawy klik, ktory trzymasz - modul tylko go przyspiesza.
 *
 * Jedyne ustawienie: Speed (1-10) - im wyzej, tym szybciej stawia dripstone i klika klape.
 *   Speed 10 = co tick, Speed 7 = tempo vanilli (4 ticki), Speed 1 = co 10 tickow.
 *
 * Wymaga pointed dripstone w hotbarze (sloty 1-9).
 */
public class AutoDripstone extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 10, 1, 10, 1));

    private int placeCooldown;

    public AutoDripstone() {
        super("AutoDripstone", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        placeCooldown = 0;
    }

    /** Speed 10 -> 1 tick, Speed 1 -> 10 tickow. */
    private int interval() {
        return 11 - (int) speed.get();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (placeCooldown > 0) placeCooldown--;
        if (mc.currentScreen != null || Freecam.isActive() || mc.player.isSpectator() || !mc.player.isAlive()) return;
        if (!mc.options.useKey.isPressed()) return;

        if (!(mc.crosshairTarget instanceof BlockHitResult look) || look.getType() != HitResult.Type.BLOCK) return;
        BlockPos trapdoorPos = look.getBlockPos();
        BlockState trapdoor = mc.world.getBlockState(trapdoorPos);
        if (!(trapdoor.getBlock() instanceof TrapdoorBlock)) return;
        if (trapdoor.get(Properties.OPEN)) return; // stawiamy tylko pod zamknieta klapa

        // szybsze klikanie klapy przez vanillowy prawy klik
        MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;
        int max = interval();
        if (acc.getItemUseCooldown() > max) acc.setItemUseCooldown(max);

        if (placeCooldown > 0) return;

        BlockPos below = trapdoorPos.down();
        if (!mc.world.getBlockState(below).isReplaceable()) return;

        Direction facing = trapdoor.get(Properties.HORIZONTAL_FACING);
        BlockPos support = below.offset(facing.getOpposite()); // blok za klapa, na nim "opieramy" dripstone
        if (mc.world.getBlockState(support).isReplaceable()) return;

        PlayerInventory inv = mc.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.POINTED_DRIPSTONE)) { slot = i; break; }
        }
        if (slot == -1) return;

        Vec3d hitPos = new Vec3d(
                support.getX() + 0.5 + facing.getOffsetX() * 0.5,
                support.getY() + 0.5 + facing.getOffsetY() * 0.5,
                support.getZ() + 0.5 + facing.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, facing, support, false);

        int prev = inv.selectedSlot;
        inv.selectedSlot = slot;
        try {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // sam zsynchronizuje slot z serwerem
        } finally {
            inv.selectedSlot = prev;
        }
        placeCooldown = interval();
    }
}

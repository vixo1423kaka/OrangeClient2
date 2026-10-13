package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.gui.BlockPickerScreen;
import com.example.orangeclient.setting.ActionSetting;
import com.example.orangeclient.setting.BlockListSetting;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Obrysy (przez sciany) wokol wybranych blokow.
 *  - kategorie (skrzynie, enderchesty, shulkery, beczki, spawnery, leje) czytamy z listy block entity w chunkach - to tanie;
 *  - przycisk "Add Blocks" otwiera okno z lista wszystkich blokow (BlockPickerScreen), w ktorym dodajesz i usuwasz
 *    bloki - te sa skanowane co sekunde w promieniu max. 32 blokow.
 */
public class BlockESP extends Module {
    private static final int SCAN_CAP = 32;
    private static final int MAX_CUSTOM_RESULTS = 2000;

    private final NumberSetting range = add(new NumberSetting("Range", 32, 8, 64, 8));
    private final BoolSetting chests = add(new BoolSetting("Chests", true));
    private final BoolSetting enderChests = add(new BoolSetting("EnderChests", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulkers", true));
    private final BoolSetting barrels = add(new BoolSetting("Barrels", false));
    private final BoolSetting spawners = add(new BoolSetting("Spawners", true));
    private final BoolSetting hoppers = add(new BoolSetting("Hoppers", false));
    /** Wybrane bloki (ukryte w menu; edytowane przez okno z przycisku "Add Blocks"). Nazwa "Custom" zostaje dla zgodnosci ze starym configiem. */
    private final BlockListSetting blocks = add(new BlockListSetting("Custom"));
    private final ActionSetting addBlocks = add(new ActionSetting("Add Blocks", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new BlockPickerScreen(mc.currentScreen, blocks));
    }));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF7A00, true));
    private final ColorSetting customColor = add(new ColorSetting("Custom Color", 0x55FFFF, true));

    private int parsedVersion = -1;
    private final Set<Block> customBlocks = new HashSet<>();
    private final List<BlockPos> customHits = new ArrayList<>();
    private int scanTimer = 0;

    public BlockESP() {
        super("BlockESP", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        customHits.clear();
        scanTimer = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        parseCustom();
        if (customBlocks.isEmpty()) {
            customHits.clear();
            return;
        }
        if (scanTimer-- > 0) return;
        scanTimer = 20;
        scan(mc);
    }

    private void parseCustom() {
        if (blocks.version() == parsedVersion) return;
        parsedVersion = blocks.version();
        customBlocks.clear();
        for (String id : blocks.ids()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null && Registries.BLOCK.containsId(ident)) customBlocks.add(Registries.BLOCK.get(ident));
        }
        scanTimer = 0;
    }

    private void scan(MinecraftClient mc) {
        customHits.clear();
        int r = Math.min((int) range.get(), SCAN_CAP);
        BlockPos p = mc.player.getBlockPos();
        int minY = Math.max(mc.world.getBottomY(), p.getY() - r);
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight() - 1, p.getY() + r);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = p.getX() - r; x <= p.getX() + r; x++) {
            for (int z = p.getZ() - r; z <= p.getZ() + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (customBlocks.contains(mc.world.getBlockState(pos).getBlock())) {
                        customHits.add(pos.toImmutable());
                        if (customHits.size() >= MAX_CUSTOM_RESULTS) return;
                    }
                }
            }
        }
    }

    private boolean wanted(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return chests.get();          // obejmuje tez trapped chest
        if (be instanceof EnderChestBlockEntity) return enderChests.get();
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get();
        if (be instanceof BarrelBlockEntity) return barrels.get();
        if (be instanceof MobSpawnerBlockEntity) return spawners.get();
        if (be instanceof HopperBlockEntity) return hoppers.get();
        return false;
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        Vec3d cam = ctx.camera().getPos();
        double maxDistSq = range.get() * range.get();
        Vec3d eye = mc.player.getEyePos();

        BufferBuilder b = RenderUtil.begin();

        // block entity z chunkow w poblizu
        int chunkR = ((int) range.get() >> 4) + 1;
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;
        for (int cx = pcx - chunkR; cx <= pcx + chunkR; cx++) {
            for (int cz = pcz - chunkR; cz <= pcz + chunkR; cz++) {
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!wanted(be)) continue;
                    BlockPos bp = be.getPos();
                    if (eye.squaredDistanceTo(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5) > maxDistSq) continue;
                    RenderUtil.box(b, m, cam, new Box(bp), color.argb());
                }
            }
        }

        // bloki z pola Custom (wynik skanu sprzed max. sekundy)
        for (BlockPos bp : customHits) {
            RenderUtil.box(b, m, cam, new Box(bp), customColor.argb());
        }

        RenderUtil.end(b);
    }
}

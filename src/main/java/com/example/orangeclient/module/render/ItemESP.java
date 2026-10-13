package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.ColorSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Obrysy (przez sciany) wokol przedmiotow lezacych na ziemi. */
public class ItemESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 128, 4));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF55, true));

    public ItemESP() {
        super("ItemESP", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx, MinecraftClient mc) {
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickCounter().getTickDelta(false);

        BufferBuilder b = RenderUtil.begin();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity) || !e.isAlive()) continue;
            if (mc.player.distanceTo(e) > range.get()) continue;

            Vec3d lerped = e.getLerpedPos(td);
            Box box = e.getBoundingBox().offset(lerped.subtract(e.getPos()));
            RenderUtil.box(b, m, cam, box, color.argb());
        }
        RenderUtil.end(b);
    }
}

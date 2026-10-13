package com.example.orangeclient.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class RenderUtil {
    private RenderUtil() { }

    public static BufferBuilder begin() {
        return begin(2.0f);
    }

    public static BufferBuilder begin(float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(Math.max(1.0f, Math.min(8.0f, lineWidth)));
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public static void end(BufferBuilder b) {
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** Linia w ukladzie wspolrzednych wzglednych do kamery. */
    public static void lineRel(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d c, int argb) {
        b.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(argb);
        b.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(argb);
    }

    /** Linia w swiecie, przeliczana na uklad kamery. */
    public static void line(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d a, Vec3d c, int argb) {
        lineRel(b, m, a.subtract(cam), c.subtract(cam), argb);
    }

    public static void box(BufferBuilder b, Matrix4f m, Vec3d cam, Box bx, int argb) {
        Vec3d[] p = {
                new Vec3d(bx.minX, bx.minY, bx.minZ), new Vec3d(bx.maxX, bx.minY, bx.minZ),
                new Vec3d(bx.maxX, bx.minY, bx.maxZ), new Vec3d(bx.minX, bx.minY, bx.maxZ),
                new Vec3d(bx.minX, bx.maxY, bx.minZ), new Vec3d(bx.maxX, bx.maxY, bx.minZ),
                new Vec3d(bx.maxX, bx.maxY, bx.maxZ), new Vec3d(bx.minX, bx.maxY, bx.maxZ)
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] e : edges) line(b, m, cam, p[e[0]], p[e[1]], argb);
    }

    // ---------- wypelnione (polprzezroczyste) boxy ----------
    public static BufferBuilder beginFill() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
    }

    public static void endFill(BufferBuilder b) {
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** Wypelnia wszystkie 6 scian boxa kolorem ARGB (alpha = przezroczystosc). */
    public static void fillBox(BufferBuilder b, Matrix4f m, Vec3d cam, Box bx, int argb) {
        float x1 = (float) (bx.minX - cam.x), y1 = (float) (bx.minY - cam.y), z1 = (float) (bx.minZ - cam.z);
        float x2 = (float) (bx.maxX - cam.x), y2 = (float) (bx.maxY - cam.y), z2 = (float) (bx.maxZ - cam.z);
        quad(b, m, argb, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);   // dol
        quad(b, m, argb, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);   // gora
        quad(b, m, argb, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);   // polnoc
        quad(b, m, argb, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);   // poludnie
        quad(b, m, argb, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);   // zachod
        quad(b, m, argb, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);   // wschod
    }

    private static void quad(BufferBuilder b, Matrix4f m, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.vertex(m, ax, ay, az).color(argb);
        b.vertex(m, bx, by, bz).color(argb);
        b.vertex(m, cx, cy, cz).color(argb);
        b.vertex(m, dx, dy, dz).color(argb);
    }
}

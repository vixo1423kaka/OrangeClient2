package com.example.orangeclient.module.movement;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Wolna kamera: ty zostajesz w miejscu (serwer widzi gracza stojacego), a kamere latasz klawiszami ruchu,
 * Spacja = w gore, Shift = w dol. Mysz obraca kamere. Dzialanie zapewniaja CameraMixin i EntityMixin.
 */
public class Freecam extends Module {
    private static Freecam instance;

    /** Predkosc: 1.0 = ok. 10 blokow/s (tyle co lot w creative), max 10.0 = ok. 100 blokow/s. */
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 10.0, 0.1));
    /** Blokuje klikniecia (bicie / uzywanie przedmiotow), zeby nie robic nic gdy patrzysz z kamery. */
    private final BoolSetting blockActions = add(new BoolSetting("BlockActions", true));
    private final BoolSetting stopOnDamage = add(new BoolSetting("StopOnDamage", true));

    private boolean active;
    private Vec3d pos = Vec3d.ZERO, prevPos = Vec3d.ZERO;
    private float yaw, pitch;
    private float lastHealth;

    public Freecam() {
        super("Freecam", Category.MOVEMENT);
        instance = this;
    }

    // ---- API dla mixinow
    public static boolean isActive() { return instance != null && instance.active; }
    public static Vec3d cameraPos(float tickDelta) { return instance.prevPos.lerp(instance.pos, tickDelta); }
    public static float cameraYaw() { return instance.yaw; }
    public static float cameraPitch() { return instance.pitch; }

    public static void look(double dx, double dy) {
        Freecam f = instance;
        if (f == null) return;
        f.yaw += (float) dx * 0.15f;
        f.pitch = MathHelper.clamp(f.pitch + (float) dy * 0.15f, -90f, 90f);
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {      // np. wczytanie configu przy starcie gry
            setEnabled(false);
            return;
        }
        pos = prevPos = mc.player.getEyePos();
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        lastHealth = mc.player.getHealth();
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
        KeyBinding.updatePressedStates();                 // przywroc prawdziwy stan klawiszy
    }

    @Override
    public void onPreTick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }
        if (stopOnDamage.get() && mc.player.getHealth() < lastHealth - 0.01f) {
            setEnabled(false);
            return;
        }
        lastHealth = mc.player.getHealth();

        GameOptions o = mc.options;
        prevPos = pos;

        // ruch kamery: czytamy prawdziwy stan klawiszy
        if (mc.currentScreen == null) {
            long h = mc.getWindow().getHandle();
            double fwd = (down(o.forwardKey, h) ? 1 : 0) - (down(o.backKey, h) ? 1 : 0);
            double side = (down(o.rightKey, h) ? 1 : 0) - (down(o.leftKey, h) ? 1 : 0);
            double up = (down(o.jumpKey, h) ? 1 : 0) - (down(o.sneakKey, h) ? 1 : 0);

            double yawRad = Math.toRadians(yaw);
            Vec3d look = Vec3d.fromPolar(pitch, yaw);
            Vec3d right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
            Vec3d move = look.multiply(fwd).add(right.multiply(side)).add(0, up, 0);
            if (move.lengthSquared() > 1.0E-6) {
                pos = pos.add(move.normalize().multiply(speed.get() * 0.5));
            }
        }

        // gracz ma stac w miejscu: odpinamy od niego klawisze ruchu
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);

        if (blockActions.get()) {
            o.attackKey.setPressed(false);
            o.useKey.setPressed(false);
            while (o.attackKey.wasPressed()) { }
            while (o.useKey.wasPressed()) { }
        }
    }

    private static boolean down(KeyBinding kb, long handle) {
        InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
        if (k.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, k.getCode()) == GLFW.GLFW_PRESS;
        }
        if (k.getCode() == GLFW.GLFW_KEY_UNKNOWN) return false;
        return InputUtil.isKeyPressed(handle, k.getCode());
    }
}

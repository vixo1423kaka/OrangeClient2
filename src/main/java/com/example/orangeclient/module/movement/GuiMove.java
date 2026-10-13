package com.example.orangeclient.module.movement;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * GuiMove: mozesz sie poruszac (WASD, Spacja, Sprint) z otwartym ekwipunkiem / skrzynia / innym oknem przedmiotow.
 * Vanilla zeruje stan klawiszy przy otwarciu ekranu - tu odczytujemy prawdziwy stan klawiszy z GLFW
 * i ustawiamy go tuz przed odczytem inputu gracza (hook w ClientPlayerEntityMixin#tickMovement).
 * Dziala tylko w oknach z przedmiotami (HandledScreen) i nigdy gdy aktywne jest pole tekstowe
 * (wyszukiwarka w creative, nazwa w kowadle itp.) - czat, tabliczki, ksiazki i menu klienta sa pomijane.
 */
public class GuiMove extends Module {
    private final BoolSetting sprint = add(new BoolSetting("Sprint", true));
    private final BoolSetting sneak = add(new BoolSetting("Sneak", false));
    /** Strzalki obracaja kamera, gdy mysz jest zajeta kursorem w oknie. */
    private final BoolSetting arrowLook = add(new BoolSetting("ArrowLook", true));
    /** Stopnie na tick (20 tickow = 1 s), czyli 4 = 80 stopni/s. */
    private final NumberSetting lookSpeed = add(new NumberSetting("LookSpeed", 4, 1, 10, 0.5));

    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) return;      // po zamknieciu okna vanilla sama odswieza stan klawiszy
        GameOptions o = mc.options;
        for (KeyBinding kb : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sprintKey, o.sneakKey}) {
            kb.setPressed(false);
        }
    }

    /** Wywolywane z mixinu na poczatku ClientPlayerEntity#tickMovement (tuz przed odczytem inputu). */
    public void syncKeys(MinecraftClient mc) {
        Screen screen = mc.currentScreen;
        if (screen == null || mc.player == null || mc.getWindow() == null) return;
        if (Freecam.isActive()) return;            // Freecam sam odpina klawisze od gracza
        if (!allowed(screen)) return;

        long h = mc.getWindow().getHandle();
        GameOptions o = mc.options;

        o.forwardKey.setPressed(down(o.forwardKey, h));
        o.backKey.setPressed(down(o.backKey, h));
        o.leftKey.setPressed(down(o.leftKey, h));
        o.rightKey.setPressed(down(o.rightKey, h));
        o.jumpKey.setPressed(down(o.jumpKey, h));
        if (sprint.get()) o.sprintKey.setPressed(down(o.sprintKey, h));
        if (sneak.get()) o.sneakKey.setPressed(down(o.sneakKey, h));

        if (arrowLook.get()) {
            float speed = (float) lookSpeed.get();
            float dYaw = (isKey(h, GLFW.GLFW_KEY_RIGHT) ? speed : 0) - (isKey(h, GLFW.GLFW_KEY_LEFT) ? speed : 0);
            float dPitch = (isKey(h, GLFW.GLFW_KEY_DOWN) ? speed : 0) - (isKey(h, GLFW.GLFW_KEY_UP) ? speed : 0);
            if (dYaw != 0) mc.player.setYaw(mc.player.getYaw() + dYaw);
            if (dPitch != 0) mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dPitch, -90f, 90f));
        }
    }

    /** Okna z przedmiotami, ale nie gdy gracz cos wpisuje. */
    private static boolean allowed(Screen screen) {
        if (!(screen instanceof HandledScreen<?>)) return false;
        return !(screen.getFocused() instanceof TextFieldWidget);
    }

    private static boolean isKey(long handle, int code) {
        return InputUtil.isKeyPressed(handle, code);
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

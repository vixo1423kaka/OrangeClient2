package com.example.orangeclient.module;

import com.example.orangeclient.module.render.GuiModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Obsluga klawiszy przypisanych do modulow (Toggle / Hold). Wywolywane co klatke. */
public final class Keybinds {
    private Keybinds() { }

    public static void poll(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean noScreen = mc.currentScreen == null;   // w czacie / GUI klawisze nie odpalaja modulow

        for (Module m : ModuleManager.all()) {
            InputUtil.Key key = m.getBind();
            if (key == null) {
                m.bindDown = false;
                continue;
            }
            boolean down = isDown(handle, key);
            if (down == m.bindDown) continue;
            m.bindDown = down;

            if (m instanceof GuiModule) {                  // modul GUI: bind otwiera menu (tryb Hold/Toggle nie ma znaczenia)
                if (down && noScreen) GuiModule.requestOpen();
                continue;
            }

            if (m.getBindMode() == BindMode.HOLD) {
                if (down && noScreen) m.setEnabled(true);
                else if (!down) m.setEnabled(false);
            } else if (down && noScreen) {
                m.toggle();
            }
        }
    }

    private static boolean isDown(long handle, InputUtil.Key key) {
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(handle, key.getCode());
    }
}

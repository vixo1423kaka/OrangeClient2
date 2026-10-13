package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Modul "GUI": jego bind (srodkowy klik na module w menu) otwiera menu klienta.
 * Zastepuje klawisz z Controls. Bind nie moze byc pusty, zeby nie zablokowac sobie dostepu do menu.
 */
public class GuiModule extends Module {
    private static final InputUtil.Key DEFAULT_KEY = InputUtil.fromKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT, 0);
    private static boolean pendingOpen;

    public GuiModule() {
        super("GUI", Category.RENDER);
        super.setBind(DEFAULT_KEY);
    }

    /** Wywolywane przez Keybinds po wcisnieciu bindu; menu otwiera sie w nastepnym ticku. */
    public static void requestOpen() { pendingOpen = true; }

    public static boolean consumeOpen() {
        boolean r = pendingOpen;
        pendingOpen = false;
        return r;
    }

    @Override
    public void setBind(InputUtil.Key key) {
        super.setBind(key == null ? DEFAULT_KEY : key);   // usuniecie bindu przywraca domyslny (Right Shift)
    }

    /** To nie przelacznik - modul nigdy nie jest "wlaczony". */
    @Override
    public void setEnabled(boolean e) { }
}

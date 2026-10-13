package com.example.orangeclient.module.fun;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.NumberSetting;
import com.example.orangeclient.setting.StringSetting;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

/** Wysyla na czat wpisana wiadomosc co X sekund. AntiKick dokleja na koncu 3 losowe cyfry. */
public class Spammer extends Module {
    private static final int CHAT_LIMIT = 256;

    private final StringSetting message = add(new StringSetting("Message", "", 200));
    /** Odstep miedzy wiadomosciami w sekundach (1-10). */
    private final NumberSetting delay = add(new NumberSetting("Delay (s)", 3, 1, 10, 1));
    /** Na koniec kazdej wiadomosci dopisuje 3 losowe cyfry (omija filtr powtorzen). */
    private final BoolSetting antiKick = add(new BoolSetting("AntiKick", true));

    private int ticks;

    public Spammer() {
        super("Spammer", Category.FUN);
    }

    @Override
    protected void onEnable() {
        ticks = 0;   // pierwsza wiadomosc leci od razu
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (ticks > 0) {
            ticks--;
            return;
        }

        String msg = message.get().trim();
        if (msg.isEmpty()) return;

        if (antiKick.get()) {
            String suffix = String.format(" %03d", ThreadLocalRandom.current().nextInt(1000));
            if (msg.length() > CHAT_LIMIT - suffix.length()) msg = msg.substring(0, CHAT_LIMIT - suffix.length());
            msg += suffix;
        } else if (msg.length() > CHAT_LIMIT) {
            msg = msg.substring(0, CHAT_LIMIT);
        }

        if (msg.startsWith("/")) mc.player.networkHandler.sendChatCommand(msg.substring(1));
        else mc.player.networkHandler.sendChatMessage(msg);

        ticks = (int) delay.get() * 20;
    }
}

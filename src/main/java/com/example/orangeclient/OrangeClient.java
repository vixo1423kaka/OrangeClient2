package com.example.orangeclient;

import com.example.orangeclient.friend.Friends;
import com.example.orangeclient.gui.ClickGui;
import com.example.orangeclient.gui.Notifications;
import com.example.orangeclient.license.LicenseManager;
import com.example.orangeclient.module.Keybinds;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.render.GuiModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class OrangeClient implements ClientModInitializer {
    public static final String MOD_ID = "orangeclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LicenseManager.init();
        Friends.load();
        ModuleManager.init();

        // Wejscie na serwer / swiat -> okno z kluczem, jesli brak licencji
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> LicenseManager.requireLicense(client)));

        ClientTickEvents.START_CLIENT_TICK.register(ModuleManager::onPreTick);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) LicenseManager.requireLicense(client);
            if (GuiModule.consumeOpen() && client.currentScreen == null && LicenseManager.isValid()) client.setScreen(new ClickGui());
            ModuleManager.onTick(client);
        });

        // bindy modulow (srodkowy klik na module w GUI): sprawdzane co klatke, zeby nie gubic krotkich kliknien
        WorldRenderEvents.START.register(ctx -> Keybinds.poll(MinecraftClient.getInstance()));

        WorldRenderEvents.LAST.register(ModuleManager::onWorldRender);

        // HUD modulow (np. lista wlaczonych modulow - modul "List")
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            ModuleManager.onHudRender(ctx);
            Notifications.render(ctx);
        });
    }
}

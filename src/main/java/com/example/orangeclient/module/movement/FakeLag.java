package com.example.orangeclient.module.movement;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;
import com.example.orangeclient.setting.ModeSetting;
import com.example.orangeclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

import java.util.ArrayDeque;

/**
 * FakeLag: wstrzymuje pakiety wychodzace do serwera na krotki czas, a potem wysyla je wszystkie naraz w tej samej
 * kolejnosci. Dla innych graczy wygladasz, jakbys sie "teleportowal" co jakis czas.
 * Pakiety przechwytuje ClientConnectionMixin (metoda send).
 */
public class FakeLag extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 150, 50, 1000, 10));
    /** Always = zawsze, Near = tylko gdy w poblizu jest inny gracz. */
    private final ModeSetting mode = add(new ModeSetting("Mode", "Always", "Always", "Near"));
    private final NumberSetting nearRange = add(new NumberSetting("NearRange", 8, 2, 20, 1));
    private final BoolSetting releaseOnAttack = add(new BoolSetting("ReleaseOnAttack", true));
    private final BoolSetting releaseOnHurt = add(new BoolSetting("ReleaseOnHurt", true));

    private record Held(Packet<?> packet, PacketCallbacks callbacks) { }

    private final ArrayDeque<Held> queue = new ArrayDeque<>();
    private ClientConnection conn;
    private long firstHeldAt;
    /** true podczas wysylania zakolejkowanych pakietow - mixin ich wtedy nie przechwytuje. */
    private boolean flushing;
    /** Po ataku nie wstrzymujemy reszty tego ticku (np. pakietu machniecia reka). */
    private boolean pausedThisTick;

    public FakeLag() {
        super("FakeLag", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        flush();
    }

    @Override
    public void onPreTick(MinecraftClient mc) {
        pausedThisTick = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            // wyszlismy ze swiata/serwera - nic nie wysylamy, tylko czyscimy
            queue.clear();
            conn = null;
            return;
        }
        if (queue.isEmpty()) return;
        if (releaseOnHurt.get() && mc.player.hurtTime > 0) { flush(); return; }
        if (System.currentTimeMillis() - firstHeldAt >= delay.get()) flush();
    }

    /**
     * Wolane z mixina. Zwraca true, jesli pakiet zostal przejety (mixin ma anulowac wysylke).
     */
    public boolean intercept(ClientConnection connection, Packet<?> packet, PacketCallbacks callbacks) {
        if (flushing) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.isOnThread() || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
        if (connection != mc.getNetworkHandler().getConnection()) return false;

        if (releaseOnAttack.get() && packet instanceof PlayerInteractEntityC2SPacket) {
            // najpierw to, co zalegalo (pozycja), potem atak, a reszta tego ticku leci bez wstrzymywania
            conn = connection;
            flush();
            pausedThisTick = true;
            return false;
        }
        if (pausedThisTick) return false;

        // zachowanie kolejnosci: jesli cos juz czeka, kazdy kolejny pakiet tez musi poczekac
        if (queue.isEmpty() && !shouldLag(mc)) return false;

        if (queue.isEmpty()) firstHeldAt = System.currentTimeMillis();
        conn = connection;
        queue.add(new Held(packet, callbacks));
        return true;
    }

    private boolean shouldLag(MinecraftClient mc) {
        if (mode.is("Always")) return true;
        double r = nearRange.get();
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player) continue;
            if (mc.player.squaredDistanceTo(other) <= r * r) return true;
        }
        return false;
    }

    /** Wysyla wszystkie wstrzymane pakiety w oryginalnej kolejnosci. */
    private void flush() {
        if (queue.isEmpty()) return;
        ClientConnection c = conn;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c == null || mc.getNetworkHandler() == null || c != mc.getNetworkHandler().getConnection() || !c.isOpen()) {
            queue.clear();
            return;
        }
        flushing = true;
        try {
            Held h;
            while ((h = queue.poll()) != null) c.send(h.packet(), h.callbacks(), true);
        } finally {
            flushing = false;
        }
    }
}

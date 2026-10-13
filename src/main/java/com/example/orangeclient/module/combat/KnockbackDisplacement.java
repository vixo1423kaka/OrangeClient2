package com.example.orangeclient.module.combat;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.NumberSetting;

/**
 * Knockback Displacement: na czas ataku wysyla do serwera yaw przesuniety o Offset stopni,
 * a zaraz po ataku prawdziwy yaw - serwer liczy knockback wzgledem przesunietego kierunku,
 * a Twoja kamera lokalnie sie nie rusza. Cala logike robi ClientPlayerInteractionManagerMixin.
 * Tryb Toggle / Hold ustawiasz bindem modulu (srodkowy klik na module w ClickGui).
 */
public class KnockbackDisplacement extends Module {
    /** Przesuniecie yaw w stopniach (-180..180). */
    private final NumberSetting offset = add(new NumberSetting("Offset", 45, -180, 180, 1));
    /** Maksymalna odleglosc oka od hitboxa celu, przy ktorej przesuwamy yaw (poza zasiegiem ataku nie ma sensu). */
    private final NumberSetting range = add(new NumberSetting("Range", 3.5, 2.0, 6.0, 0.1));

    /**
     * Minimalny odstep (w tickach) miedzy dwoma przesunieciami yaw. Kazdy atak z przesunieciem to
     * 2 dodatkowe pakiety ruchu na serwer, a anticheaty (Timer/BadPackets) licza je jak za duzo
     * pakietow na tick i cofaja gracza. Przy spamie klikow/TriggerBocie to byl glowny powod "cofania".
     */
    private final NumberSetting minInterval = add(new NumberSetting("MinInterval", 4, 1, 20, 1));

    public KnockbackDisplacement() {
        super("KBDisplace", Category.COMBAT);
    }

    // ---- API dla mixina
    public static KnockbackDisplacement active() {
        KnockbackDisplacement m = ModuleManager.get(KnockbackDisplacement.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public int getOffset() { return (int) offset.get(); }
    public double getRange() { return range.get(); }
    public int getMinInterval() { return (int) minInterval.get(); }
}

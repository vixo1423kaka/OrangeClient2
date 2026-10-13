package com.example.orangeclient.module.render;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.setting.BoolSetting;

/** Po wlaczeniu wybierasz opcjami, czego NIE chcesz widziec (nakladki, efekty itd.). */
public class Removals extends Module {
    private final BoolSetting fire = add(new BoolSetting("FireOverlay", true));
    private final BoolSetting water = add(new BoolSetting("WaterOverlay", true));
    private final BoolSetting wall = add(new BoolSetting("BlockOverlay", true));
    private final BoolSetting pumpkin = add(new BoolSetting("Pumpkin", true));
    private final BoolSetting powderSnow = add(new BoolSetting("PowderSnow", true));
    private final BoolSetting portal = add(new BoolSetting("Portal", true));
    private final BoolSetting vignette = add(new BoolSetting("Vignette", false));
    private final BoolSetting hurtCam = add(new BoolSetting("HurtCam", false));
    private final BoolSetting totem = add(new BoolSetting("TotemAnim", false));
    private final BoolSetting weather = add(new BoolSetting("Weather", false));

    public Removals() {
        super("Removals", Category.RENDER);
    }

    private static Removals inst() { return ModuleManager.get(Removals.class); }

    public static boolean noFire() { Removals r = inst(); return r != null && r.isEnabled() && r.fire.get(); }
    public static boolean noWater() { Removals r = inst(); return r != null && r.isEnabled() && r.water.get(); }
    public static boolean noWall() { Removals r = inst(); return r != null && r.isEnabled() && r.wall.get(); }
    public static boolean noPumpkin() { Removals r = inst(); return r != null && r.isEnabled() && r.pumpkin.get(); }
    public static boolean noPowderSnow() { Removals r = inst(); return r != null && r.isEnabled() && r.powderSnow.get(); }
    public static boolean noPortal() { Removals r = inst(); return r != null && r.isEnabled() && r.portal.get(); }
    public static boolean noVignette() { Removals r = inst(); return r != null && r.isEnabled() && r.vignette.get(); }
    public static boolean noHurtCam() { Removals r = inst(); return r != null && r.isEnabled() && r.hurtCam.get(); }
    public static boolean noTotem() { Removals r = inst(); return r != null && r.isEnabled() && r.totem.get(); }
    public static boolean noWeather() { Removals r = inst(); return r != null && r.isEnabled() && r.weather.get(); }
}

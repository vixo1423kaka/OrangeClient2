package com.example.orangeclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.awt.Color;

/**
 * Kolor wybierany suwakiem odcienia (lewy przycisk + przeciaganie)
 * albo gotowymi kolorami (prawy przycisk - kolejny preset, w tym bialy).
 */
public class ColorSetting extends Setting implements Slidable {
    private static final int[] PRESETS = {
            0xFF5555, 0xFF7A00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5599FF, 0xAA55FF, 0xFF55FF, 0xFFFFFF
    };

    /** Jak PRESETS, ale z czarnym na koncu (ESP, Tracers). */
    private static final int[] PRESETS_WITH_BLACK = {
            0xFF5555, 0xFF7A00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5599FF, 0xAA55FF, 0xFF55FF, 0xFFFFFF, 0x000000
    };

    /** Poczatek suwaka (0..BLACK_ZONE) = kolor czarny, gdy ustawienie dopuszcza czern. */
    public static final double BLACK_ZONE = 0.08;

    private int rgb;
    private final boolean allowBlack;

    public ColorSetting(String name, int rgb) {
        this(name, rgb, false);
    }

    /** allowBlack = true dodaje czarny do presetow (prawy przycisk myszy). */
    public ColorSetting(String name, int rgb, boolean allowBlack) {
        super(name);
        this.rgb = rgb & 0xFFFFFF;
        this.allowBlack = allowBlack;
    }

    public int rgb() { return rgb; }

    public boolean allowsBlack() { return allowBlack; }

    /** Kolor z pelnym alpha, gotowy do RenderUtil / DrawContext. */
    public int argb() { return 0xFF000000 | rgb; }

    public void set(int rgb) { this.rgb = rgb & 0xFFFFFF; }

    public float hue() {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return hsb[0];
    }

    @Override
    public void setFraction(double frac) {
        frac = Math.max(0, Math.min(1, frac));
        if (allowBlack) {
            if (frac < BLACK_ZONE) {          // lewy skraj suwaka = czarny
                rgb = 0x000000;
                return;
            }
            frac = (frac - BLACK_ZONE) / (1 - BLACK_ZONE);
        }
        float h = (float) frac;
        rgb = Color.HSBtoRGB(h, 1f, 1f) & 0xFFFFFF;
    }

    /** Pozycja znacznika na suwaku (0..1), uwzgledniajaca czarna strefe. */
    public double markerFraction() {
        if (!allowBlack) return hue();
        if (rgb == 0) return 0;
        return BLACK_ZONE + hue() * (1 - BLACK_ZONE);
    }

    public void nextPreset() {
        int[] presets = allowBlack ? PRESETS_WITH_BLACK : PRESETS;
        int idx = -1;
        for (int i = 0; i < presets.length; i++) if (presets[i] == rgb) idx = i;
        rgb = presets[(idx + 1) % presets.length];
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(rgb); }

    @Override
    public void fromJson(JsonElement e) { set(e.getAsInt()); }
}

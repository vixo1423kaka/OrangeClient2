package com.example.orangeclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting implements Slidable {
    public final double min, max, step;
    private double value;

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = def;
    }

    public double get() { return value; }

    public void set(double v) {
        v = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, v));
    }

    @Override
    public void setFraction(double frac) { set(min + frac * (max - min)); }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement e) { set(e.getAsDouble()); }
}

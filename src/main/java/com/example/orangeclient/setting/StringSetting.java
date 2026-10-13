package com.example.orangeclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Ustawienie tekstowe (pole do wpisania w GUI). */
public class StringSetting extends Setting {
    public final int maxLen;
    private String value;

    public StringSetting(String name, String def, int maxLen) {
        super(name);
        this.maxLen = maxLen;
        this.value = def.length() > maxLen ? def.substring(0, maxLen) : def;
    }

    public String get() { return value; }

    public void set(String v) {
        value = v.length() > maxLen ? v.substring(0, maxLen) : v;
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement e) { set(e.getAsString()); }
}

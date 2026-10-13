package com.example.orangeclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Przycisk w menu modulu (nic nie zapisuje w configu). */
public class ActionSetting extends Setting {
    public final Runnable action;

    public ActionSetting(String name, Runnable action) {
        super(name);
        this.action = action;
    }

    @Override
    public JsonElement toJson() { return JsonNull.INSTANCE; }

    @Override
    public void fromJson(JsonElement e) { }
}

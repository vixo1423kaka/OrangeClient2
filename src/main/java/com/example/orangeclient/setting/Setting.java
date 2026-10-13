package com.example.orangeclient.setting;

import com.google.gson.JsonElement;

public abstract class Setting {
    public final String name;
    /** Ukryte w menu (nadal zapisywane w configu), np. pozycja HUD ustawiana przeciaganiem. */
    public boolean hidden;

    protected Setting(String name) {
        this.name = name;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement e);
}

package com.example.orangeclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Ustawienie wyboru jednej opcji z listy (w GUI: lewy klik = nastepna, prawy = poprzednia). */
public class ModeSetting extends Setting {
    public final String[] modes;
    private int index;

    public ModeSetting(String name, String def, String... modes) {
        super(name);
        this.modes = modes;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(def)) index = i;
    }

    public String get() { return modes[index]; }
    public boolean is(String mode) { return modes[index].equals(mode); }

    public void set(String mode) {
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(mode)) index = i;
    }

    public void next() { index = (index + 1) % modes.length; }
    public void prev() { index = (index - 1 + modes.length) % modes.length; }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(modes[index]); }

    @Override
    public void fromJson(JsonElement e) {
        String s = e.getAsString();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) index = i;
    }
}

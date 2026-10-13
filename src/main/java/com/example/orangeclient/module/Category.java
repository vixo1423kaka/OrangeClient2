package com.example.orangeclient.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    FUN("Fun");

    public final String label;

    Category(String label) { this.label = label; }
}

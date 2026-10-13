package com.example.orangeclient.license;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class LicenseScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget keyField;
    private ButtonWidget activateButton;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;

    public LicenseScreen(Screen parent) {
        super(Text.literal("Licencja"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        keyField = new TextFieldWidget(textRenderer, cx - 100, cy - 10, 200, 20, Text.literal("Klucz"));
        keyField.setMaxLength(64);
        keyField.setPlaceholder(Text.literal("Wpisz klucz licencyjny..."));
        addDrawableChild(keyField);
        setInitialFocus(keyField);

        activateButton = ButtonWidget.builder(Text.literal("Aktywuj"), b -> activate())
                .dimensions(cx - 100, cy + 18, 98, 20).build();
        addDrawableChild(activateButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyjdz z gry"), b -> MinecraftClient.getInstance().scheduleStop())
                .dimensions(cx + 2, cy + 18, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Rozlacz"), b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) mc.world.disconnect();
            mc.disconnect(new TitleScreen());
        }).dimensions(cx - 100, cy + 42, 200, 20).build());
    }

    private void activate() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) {
            setStatus("Wpisz klucz!", 0xFFFF5555);
            return;
        }
        activateButton.active = false;
        setStatus("Sprawdzanie...", 0xFFFFFF55);
        LicenseManager.validate(key).thenAccept(err -> MinecraftClient.getInstance().execute(() -> {
            activateButton.active = true;
            if (err == null) {
                MinecraftClient.getInstance().setScreen(parent);
            } else {
                setStatus(err, 0xFFFF5555);
            }
        }));
    }

    private void setStatus(String s, int color) { status = s; statusColor = color; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER / NUM ENTER
            if (activateButton.active) activate();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int cy = height / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Orange Client - wymagana licencja"), cx, cy - 50, 0xFFFF9A2E);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Wpisz klucz, aby uzywac clienta"), cx, cy - 34, 0xFFAAAAAA);
        if (!status.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, cy + 70, statusColor);
    }

    /** Okna nie da sie zamknac ESC-em. */
    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

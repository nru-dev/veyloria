package dev.laakirun.veyloria.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AuthScreen extends Screen {
    private EditBox passwordBox;

    public AuthScreen() {
        super(Component.literal("Veyloria Auth"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        passwordBox = addRenderableWidget(new EditBox(font, centerX - 90, centerY - 10, 180, 20, Component.literal("Password")));
        passwordBox.setMaxLength(64);
        passwordBox.setHint(Component.literal("Password"));

        addRenderableWidget(Button.builder(Component.literal("Register"), button -> submit("register"))
            .bounds(centerX - 90, centerY + 20, 85, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Login"), button -> submit("login"))
            .bounds(centerX + 5, centerY + 20, 85, 20)
            .build());
        setInitialFocus(passwordBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        int centerX = width / 2;
        int centerY = height / 2;
        guiGraphics.drawCenteredString(font, "Veyloria RPG", centerX, centerY - 55, 0xFFFFFF);
        guiGraphics.drawCenteredString(font,
            VeyloriaClientState.instance().registeredAccount() ? "Login to unlock the world" : "Create your RPG account",
            centerX, centerY - 40, 0xAAAAAA);
        if (!VeyloriaClientState.instance().lastError().isBlank()) {
            guiGraphics.drawCenteredString(font, VeyloriaClientState.instance().lastError(), centerX, centerY + 50, 0xFF5555);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !VeyloriaClientState.instance().authRequired();
    }

    private void submit(String mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        String password = passwordBox.getValue().trim();
        if (password.isEmpty()) {
            VeyloriaClientState.instance().setLastError("Password is empty");
            return;
        }
        minecraft.getConnection().sendCommand("veyloria " + mode + " " + password);
    }
}

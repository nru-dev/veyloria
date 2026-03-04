package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.menu.VeyloriaInventoryLayout;
import dev.laakirun.veyloria.common.menu.VeyloriaInventoryMenu;
import dev.laakirun.veyloria.common.network.VeyloriaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class VeyloriaInventoryScreen extends AbstractContainerScreen<VeyloriaInventoryMenu> {
    public VeyloriaInventoryScreen(VeyloriaInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = VeyloriaInventoryLayout.PANEL_WIDTH;
        this.imageHeight = VeyloriaInventoryLayout.PANEL_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        drawBackdrop(guiGraphics, x, y);
        drawHeaders(guiGraphics, x, y);
        drawCurrencyCounter(guiGraphics, x, y);
        drawStatsCard(guiGraphics, x, y);
        drawPlayerCard(guiGraphics, x, y, mouseX, mouseY);
        drawLoadoutFrames(guiGraphics, x, y);
        drawStorageFrames(guiGraphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int weaponSlot = weaponSlotForKey(keyCode);
        if (weaponSlot >= 0) {
            sendActionSlotSelection(weaponSlot);
            return true;
        }
        int consumableSlot = consumableSlotForKey(keyCode);
        if (consumableSlot >= 0) {
            useConsumableSlot(consumableSlot);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean checkHotbarKeyPressed(int keyCode, int scanCode) {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawBackdrop(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fillGradient(x - 12, y - 12, x + this.imageWidth + 12, y + this.imageHeight + 12, 0xF0181B25, 0xEA0A0C12);
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE111720);
        guiGraphics.fillGradient(x + 8, y + 8, x + 176, y + this.imageHeight - 8, 0xDD1D2732, 0xCC121922);
        guiGraphics.fillGradient(x + 184, y + 8, x + this.imageWidth - 8, y + this.imageHeight - 8, 0xD918202B, 0xCC10161F);
        guiGraphics.fill(x + 180, y + 16, x + 181, y + this.imageHeight - 16, 0xAA67788F);
        guiGraphics.fill(x + 8, y + 28, x + 176, y + 29, 0x404F6178);
        guiGraphics.fill(x + 184, y + 28, x + this.imageWidth - 8, y + 29, 0x404F6178);
    }

    private void drawHeaders(GuiGraphics guiGraphics, int x, int y) {
        VeyloriaClientState state = VeyloriaClientState.instance();
        guiGraphics.drawString(this.font, "Loadout", x + 20, y + 14, 0xF3F5F7, false);
        guiGraphics.drawString(this.font, "Backpack", x + 196, y + 14, 0xF3F5F7, false);
        guiGraphics.drawString(this.font, "Level " + state.level(), x + 84, y + 14, 0xC3D0E2, false);
        guiGraphics.drawString(this.font, "XP " + state.xpCurrent() + "/" + state.xpNext(), x + 20, y + 206, 0x9FB0C6, false);
        guiGraphics.drawString(this.font, "Quick Use", x + 20, y + 132, 0x9FB0C6, false);
        guiGraphics.drawString(this.font, "Weapons", x + 68, y + 174, 0x9FB0C6, false);
    }

    private void drawCurrencyCounter(GuiGraphics guiGraphics, int x, int y) {
        String label = "Copper";
        String value = Integer.toString(VeyloriaClientState.instance().copper());
        int boxRight = x + this.imageWidth - 18;
        int boxLeft = boxRight - 92;
        guiGraphics.fill(boxLeft, y + 10, boxRight, y + 28, 0xD26B4A17);
        guiGraphics.fill(boxLeft + 1, y + 11, boxRight - 1, y + 27, 0xC9302412);
        guiGraphics.drawString(this.font, label, boxLeft + 8, y + 15, 0xF4D59A, false);
        guiGraphics.drawString(this.font, value, boxRight - 8 - this.font.width(value), y + 15, 0xFFF2C3, false);
    }

    private void drawStatsCard(GuiGraphics guiGraphics, int x, int y) {
        VeyloriaClientState state = VeyloriaClientState.instance();
        int cardX = x + 66;
        int cardY = y + 118;
        int cardWidth = 74;
        int cardHeight = 62;
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xC2192230);
        guiGraphics.fill(cardX + 1, cardY + 1, cardX + cardWidth - 1, cardY + cardHeight - 1, 0xB0101620);
        guiGraphics.drawString(this.font, "Stats", cardX + 8, cardY + 6, 0xE8EEF7, false);
        guiGraphics.drawString(this.font, "Pow " + state.totalStats().power(), cardX + 8, cardY + 18, 0xF07B6F, false);
        guiGraphics.drawString(this.font, "Vit " + state.totalStats().vitality(), cardX + 8, cardY + 28, 0xE8D582, false);
        guiGraphics.drawString(this.font, "Arm " + state.totalStats().armor(), cardX + 8, cardY + 38, 0xB8C7DA, false);
        guiGraphics.drawString(this.font, "Crit " + state.totalStats().crit(), cardX + 8, cardY + 48, 0x8FD8F3, false);
        guiGraphics.drawString(this.font, "Haste " + state.totalStats().haste(), cardX + 8, cardY + 58, 0xB796F6, false);
    }

    private void drawPlayerCard(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int cardLeft = x + 56;
        int cardTop = y + 34;
        int cardRight = x + 132;
        int cardBottom = y + 126;
        guiGraphics.fill(cardLeft, cardTop, cardRight, cardBottom, 0xC51A2230);
        guiGraphics.fill(cardLeft + 1, cardTop + 1, cardRight - 1, cardBottom - 1, 0xB40E141B);
        guiGraphics.fill(cardLeft + 10, cardTop + 8, cardRight - 10, cardBottom - 10, 0x401D2833);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                cardLeft,
                cardTop,
                cardRight,
                cardBottom,
                VeyloriaInventoryLayout.PLAYER_MODEL_SCALE,
                0.0625F,
                mouseX,
                mouseY,
                this.minecraft.player
            );
        }
    }

    private void drawLoadoutFrames(GuiGraphics guiGraphics, int x, int y) {
        for (VeyloriaInventoryLayout.SlotAnchor anchor : VeyloriaInventoryLayout.LOADOUT_SLOTS) {
            boolean active = anchor.slotIndex() == this.menu.activeActionSlot();
            int borderColor = borderColor(anchor.group(), active);
            int fillColor = fillColor(anchor.group(), active);
            drawSlotFrame(guiGraphics, x + anchor.x(), y + anchor.y(), borderColor, fillColor);

            String label = anchor.label();
            int labelX = x + anchor.x() + 8 - this.font.width(label) / 2;
            int labelY = anchor.group() == VeyloriaInventoryLayout.SlotGroup.WEAPON || anchor.group() == VeyloriaInventoryLayout.SlotGroup.AMMO
                ? y + anchor.y() - 10
                : y + anchor.y() + 19;
            int labelColor = active ? 0xF7D48A : accentColor(anchor.group());
            guiGraphics.drawString(this.font, label, labelX, labelY, labelColor, false);
        }
    }

    private void drawStorageFrames(GuiGraphics guiGraphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(
                    guiGraphics,
                    x + VeyloriaInventoryLayout.STORAGE_GRID_X + column * 18,
                    y + VeyloriaInventoryLayout.STORAGE_GRID_Y + row * 18,
                    0xFF617086,
                    0x90232B38
                );
            }
        }

        for (int column = 0; column < 8; column++) {
            drawSlotFrame(
                guiGraphics,
                x + VeyloriaInventoryLayout.STORAGE_HOTBAR_X + column * 18,
                y + VeyloriaInventoryLayout.STORAGE_HOTBAR_Y,
                0xFF617086,
                0x90232B38
            );
        }

        guiGraphics.drawString(this.font, "Storage", x + VeyloriaInventoryLayout.STORAGE_GRID_X, y + 38, 0xAFC0D3, false);
    }

    private static int borderColor(VeyloriaInventoryLayout.SlotGroup group, boolean active) {
        if (active) {
            return 0xFFF0C55A;
        }
        return switch (group) {
            case WEAPON -> 0xFF7F90A7;
            case AMMO -> 0xFF6B95C2;
            case ARMOR -> 0xFF7A879A;
            case ACCESSORY -> 0xFF8C78B0;
            case CONSUMABLE -> 0xFF6F9574;
        };
    }

    private static int fillColor(VeyloriaInventoryLayout.SlotGroup group, boolean active) {
        if (active) {
            return 0xA06E5412;
        }
        return switch (group) {
            case WEAPON -> 0x90303A48;
            case AMMO -> 0x90304456;
            case ARMOR -> 0x90252D39;
            case ACCESSORY -> 0x90302542;
            case CONSUMABLE -> 0x9025332A;
        };
    }

    private static int accentColor(VeyloriaInventoryLayout.SlotGroup group) {
        return switch (group) {
            case WEAPON -> 0xDDE5F0;
            case AMMO -> 0x9FD3FF;
            case ARMOR -> 0xCAD3E1;
            case ACCESSORY -> 0xD5C0F7;
            case CONSUMABLE -> 0xB6E0B4;
        };
    }

    private static int weaponSlotForKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> PlayerLoadoutData.SLOT_MAIN_WEAPON;
            case GLFW.GLFW_KEY_2 -> PlayerLoadoutData.SLOT_SECONDARY_WEAPON;
            case GLFW.GLFW_KEY_3 -> PlayerLoadoutData.SLOT_RANGED_WEAPON;
            default -> -1;
        };
    }

    private static int consumableSlotForKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_4 -> PlayerLoadoutData.SLOT_CONSUMABLE_1;
            case GLFW.GLFW_KEY_5 -> PlayerLoadoutData.SLOT_CONSUMABLE_2;
            case GLFW.GLFW_KEY_6 -> PlayerLoadoutData.SLOT_CONSUMABLE_3;
            case GLFW.GLFW_KEY_7 -> PlayerLoadoutData.SLOT_CONSUMABLE_4;
            default -> -1;
        };
    }

    private static void drawSlotFrame(GuiGraphics guiGraphics, int x, int y, int borderColor, int fillColor) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, borderColor);
        guiGraphics.fill(x, y, x + 16, y + 16, fillColor);
    }

    private static void sendActionSlotSelection(int actionSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        minecraft.getConnection().send(new VeyloriaNetwork.SelectActionSlotPayload(actionSlot));
        if (minecraft.player != null) {
            minecraft.player.getInventory().selected = PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT;
        }
    }

    private static void useConsumableSlot(int consumableSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        minecraft.getConnection().send(new VeyloriaNetwork.UseConsumablePayload(consumableSlot));
        if (minecraft.player != null) {
            minecraft.player.getInventory().selected = PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT;
        }
    }
}

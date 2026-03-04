package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.item.RpgItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, value = Dist.CLIENT)
public final class VeyloriaClientEvents {
    private VeyloriaClientEvents() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        VeyloriaClientState.instance().reset();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VeyloriaClientState.instance().reset();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        VeyloriaClientState.instance().prune(minecraft.player.tickCount);
        if (VeyloriaClientState.instance().authRequired() && !(minecraft.screen instanceof AuthScreen)) {
            minecraft.setScreen(new AuthScreen());
        }
        if (VeyloriaClientState.instance().authenticated() && minecraft.screen instanceof AuthScreen) {
            minecraft.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        String message = event.getMessage().getString();
        if (message.startsWith("[veyloria:")) {
            handleMarker(message);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(RpgItemData.ROOT_KEY)) {
            return;
        }
        RpgItemData itemData = RpgItemData.fromTag(data.copyTag().getCompound(RpgItemData.ROOT_KEY));
        event.getToolTip().add(Component.literal("Rarity: " + itemData.rarity().name()));
        event.getToolTip().add(Component.literal("Required Level: " + itemData.requiredLevel()));
        event.getToolTip().add(Component.literal("Power: " + itemData.rolledStats().power()));
        event.getToolTip().add(Component.literal("Vitality: " + itemData.rolledStats().vitality()));
        event.getToolTip().add(Component.literal("Armor: " + itemData.rolledStats().armor()));
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int y = minecraft.getWindow().getGuiScaledHeight() - 42;
        guiGraphics.drawString(minecraft.font, "Copper: " + VeyloriaClientState.instance().copper(), width - 88, y, 0xF0A040, false);
        int index = 0;
        for (VeyloriaClientState.Notification notification : VeyloriaClientState.instance().notifications()) {
            guiGraphics.drawCenteredString(minecraft.font, notification.text(), width / 2, y - 14 - index * 10, 0xFFFFFF);
            index++;
        }
    }

    private static void handleMarker(String marker) {
        VeyloriaClientState state = VeyloriaClientState.instance();
        if (marker.startsWith("[veyloria:auth_required]")) {
            boolean registered = marker.contains("registered=true");
            state.setAuthRequired(true, registered);
            return;
        }
        if (marker.startsWith("[veyloria:auth_ok]")) {
            state.setAuthenticated(true);
            return;
        }
        if (marker.startsWith("[veyloria:profile]")) {
            state.setCopper(parseInt(marker, "copper"));
            return;
        }
        if (marker.startsWith("[veyloria:gain]")) {
            int xp = parseInt(marker, "xp");
            int copper = parseInt(marker, "copper");
            state.pushNotification("+" + xp + " EXP, +" + copper + " Copper", tickNow() + 60);
            return;
        }
        if (marker.startsWith("[veyloria:loot]")) {
            String name = parseString(marker, "name");
            int quantity = parseInt(marker, "quantity");
            state.pushNotification("Loot: " + name + " x" + quantity, tickNow() + 80);
            return;
        }
        if (marker.startsWith("[veyloria:error]")) {
            state.setLastError(parseString(marker, "message"));
        }
    }

    private static long tickNow() {
        return Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
    }

    private static int parseInt(String marker, String key) {
        try {
            return Integer.parseInt(parseString(marker, key));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String parseString(String marker, String key) {
        String[] parts = marker.split("\\|");
        for (String part : parts) {
            if (part.startsWith(key + "=")) {
                return part.substring((key + "=").length());
            }
        }
        return "";
    }
}

package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.item.RpgItemData;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
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
        event.getToolTip().add(Component.literal("Редкость: " + rarityName(itemData)).withStyle(rarityColor(itemData)));
        event.getToolTip().add(Component.literal("Требуемый уровень: " + itemData.requiredLevel()));
        if (itemData.rolledStats().power() > 0) {
            event.getToolTip().add(Component.literal("Сила: +" + itemData.rolledStats().power()).withStyle(ChatFormatting.RED));
        }
        if (itemData.rolledStats().vitality() > 0) {
            event.getToolTip().add(Component.literal("Выносливость: +" + itemData.rolledStats().vitality()).withStyle(ChatFormatting.YELLOW));
        }
        if (itemData.rolledStats().crit() > 0) {
            event.getToolTip().add(Component.literal("Ловкость: +" + itemData.rolledStats().crit()).withStyle(ChatFormatting.AQUA));
        }
        if (itemData.rolledStats().haste() > 0) {
            event.getToolTip().add(Component.literal("Интеллект: +" + itemData.rolledStats().haste()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        ChatFormatting armorColor = itemData.armorBoosted() ? ChatFormatting.GREEN : ChatFormatting.WHITE;
        event.getToolTip().add(Component.literal("Броня: +" + itemData.rolledStats().armor()).withStyle(armorColor));
        if (!itemData.weaponType().isBlank()) {
            event.getToolTip().add(Component.literal("Тип оружия: " + weaponLabel(itemData.weaponType())));
            if (itemData.aoeChance() > 0.0D && itemData.aoeTargets() > 0) {
                int percent = (int) Math.round(itemData.aoeChance() * 100.0D);
                event.getToolTip().add(Component.literal("Шанс АоЕ: " + percent + "%, целей: " + itemData.aoeTargets()).withStyle(ChatFormatting.GOLD));
            }
            if (itemData.homingChance() > 0.0D) {
                int percent = (int) Math.round(itemData.homingChance() * 100.0D);
                event.getToolTip().add(Component.literal("Самонаведение: " + percent + "%").withStyle(ChatFormatting.AQUA));
            }
            if (itemData.manaCost() > 0) {
                event.getToolTip().add(Component.literal("Расход маны: " + itemData.manaCost()).withStyle(ChatFormatting.BLUE));
            }
            if (itemData.healPower() > 0) {
                event.getToolTip().add(Component.literal("Сила исцеления: +" + itemData.healPower()).withStyle(ChatFormatting.GREEN));
            }
            if (itemData.aoeHealing()) {
                event.getToolTip().add(Component.literal("Эффект: целительная лужа (10 сек)").withStyle(ChatFormatting.GREEN));
            }
        }
        if (itemData.legendaryEffect()) {
            event.getToolTip().add(Component.literal("Легендарный эффект активен").withStyle(ChatFormatting.GOLD));
        }
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
        int height = minecraft.getWindow().getGuiScaledHeight();
        int y = height - 42;
        int hpCurrent = (int) Math.ceil(Math.max(0.0D, minecraft.player.getHealth()));
        int hpMax = (int) Math.ceil(Math.max(1.0D, minecraft.player.getMaxHealth()));
        VeyloriaClientState.ResourceBars selfBars = VeyloriaClientState.instance().playerBars(minecraft.player.getUUID());
        if (selfBars != null) {
            hpCurrent = selfBars.health();
            hpMax = selfBars.healthMax();
        }
        guiGraphics.drawString(
            minecraft.font,
            "HP: " + hpCurrent + "/" + hpMax,
            12,
            height - 52,
            0xFF4040,
            false
        );
        guiGraphics.drawString(minecraft.font, "Медь: " + VeyloriaClientState.instance().copper(), width - 96, y, 0xF0A040, false);
        if (VeyloriaClientState.instance().manaMax() > 0) {
            guiGraphics.drawString(minecraft.font,
                "Мана: " + VeyloriaClientState.instance().mana() + "/" + VeyloriaClientState.instance().manaMax(),
                width - 120, y - 10, 0x40A0F0, false);
        }
        int index = 0;
        for (VeyloriaClientState.Notification notification : VeyloriaClientState.instance().notifications()) {
            guiGraphics.drawCenteredString(minecraft.font, notification.text(), width / 2, y - 14 - index * 10, 0xFFFFFF);
            index++;
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int hpCurrent = (int) Math.ceil(Math.max(0.0D, player.getHealth()));
        int hpMax = (int) Math.ceil(Math.max(1.0D, player.getMaxHealth()));
        int manaCurrent = 0;
        int manaMax = 0;

        VeyloriaClientState.ResourceBars bars = VeyloriaClientState.instance().playerBars(player.getUUID());
        if (bars != null) {
            hpCurrent = bars.health();
            hpMax = bars.healthMax();
            manaCurrent = bars.mana();
            manaMax = bars.manaMax();
        }

        Component hpBar = buildBar("HP", hpCurrent, hpMax, 10, ChatFormatting.RED);
        Component manaBar = buildBar("MP", manaCurrent, manaMax, 10, ChatFormatting.AQUA);
        event.setContent(Component.empty()
            .append(event.getOriginalContent())
            .append(Component.literal("  "))
            .append(hpBar)
            .append(Component.literal("  "))
            .append(manaBar));
    }

    private static void handleMarker(String marker) {
        VeyloriaClientState state = VeyloriaClientState.instance();
        if (marker.startsWith("[veyloria:profile]")) {
            state.setCopper(parseInt(fieldValue(marker, "copper")));
            state.setMana(parseInt(fieldValue(marker, "mana")), parseInt(fieldValue(marker, "manaMax")));
            return;
        }
        if (marker.startsWith("[veyloria:bars]")) {
            UUID playerUuid = parseUuid(fieldValue(marker, "uuid"));
            if (playerUuid != null) {
                state.setPlayerBars(
                    playerUuid,
                    parseInt(fieldValue(marker, "hp")),
                    parseInt(fieldValue(marker, "hpMax")),
                    parseInt(fieldValue(marker, "mana")),
                    parseInt(fieldValue(marker, "manaMax")),
                    tickNow() + 60
                );
            }
            return;
        }
        if (marker.startsWith("[veyloria:gain]")) {
            int xp = parseInt(fieldValue(marker, "xp"));
            int copper = parseInt(fieldValue(marker, "copper"));
            state.pushNotification("+" + xp + " опыта, +" + copper + " меди", tickNow() + 60);
            return;
        }
        if (marker.startsWith("[veyloria:loot]")) {
            String name = fieldValue(marker, "name");
            int quantity = parseInt(fieldValue(marker, "quantity"));
            state.pushNotification("Добыча: " + name + " x" + quantity, tickNow() + 80);
            return;
        }
        if (marker.startsWith("[veyloria:error]")) {
            state.setLastError(fieldValue(marker, "message"));
        }
    }

    private static long tickNow() {
        return Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String fieldValue(String marker, String key) {
        String needle = "|" + key + "=";
        int start = marker.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = marker.indexOf('|', valueStart);
        if (end < 0) {
            return marker.substring(valueStart);
        }
        return marker.substring(valueStart, end);
    }

    private static UUID parseUuid(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Component buildBar(String label, int current, int max, int segments, ChatFormatting fillColor) {
        int safeSegments = Math.max(4, segments);
        int safeMax = Math.max(0, max);
        double ratio = safeMax <= 0 ? 0.0D : clamp01(current / (double) safeMax);
        int filled = (int) Math.round(ratio * safeSegments);
        if (filled < 0) {
            filled = 0;
        } else if (filled > safeSegments) {
            filled = safeSegments;
        }
        int empty = safeSegments - filled;
        Component fill = Component.literal("#".repeat(filled)).withStyle(fillColor);
        Component gap = Component.literal("-".repeat(empty)).withStyle(ChatFormatting.DARK_GRAY);
        Component values = Component.literal(" " + Math.max(0, current) + "/" + safeMax).withStyle(ChatFormatting.GRAY);
        return Component.empty()
            .append(Component.literal(label + "[").withStyle(ChatFormatting.GRAY))
            .append(fill)
            .append(gap)
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
            .append(values);
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static String rarityName(RpgItemData data) {
        return switch (data.rarity()) {
            case COMMON -> "Обычная";
            case UNCOMMON -> "Необычная";
            case RARE -> "Редкая";
            case EPIC -> "Эпическая";
            case LEGENDARY -> "Легендарная";
        };
    }

    private static ChatFormatting rarityColor(RpgItemData data) {
        return switch (data.rarity()) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.BLUE;
            case EPIC -> ChatFormatting.DARK_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
        };
    }

    private static String weaponLabel(String weaponType) {
        return switch (weaponType) {
            case "sword_2h" -> "Двуручный меч";
            case "axe" -> "Топор защитника";
            case "bow" -> "Лук";
            case "wand" -> "Целительная палочка";
            default -> weaponType;
        };
    }
}

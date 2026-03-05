package dev.laakirun.veyloria.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.network.VeyloriaNetwork;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VeyloriaConstants.MOD_ID, value = Dist.CLIENT)
public final class VeyloriaClientEvents {
    private static final float TARGET_OUTLINE_RED = 0.92F;
    private static final float TARGET_OUTLINE_GREEN = 0.20F;
    private static final float TARGET_OUTLINE_BLUE = 0.20F;
    private static final float TARGET_OUTLINE_ALPHA = 1.0F;
    private static final double TARGET_OUTLINE_INFLATE = 0.06D;
    private static boolean attackKeyWasDown;
    private static int lastAttackIntentTick = Integer.MIN_VALUE / 4;

    private VeyloriaClientEvents() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        VeyloriaClientState.instance().reset();
        attackKeyWasDown = false;
        lastAttackIntentTick = Integer.MIN_VALUE / 4;
        syncUseKeyState(Minecraft.getInstance(), false);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VeyloriaClientState.instance().reset();
        attackKeyWasDown = false;
        lastAttackIntentTick = Integer.MIN_VALUE / 4;
        syncUseKeyState(Minecraft.getInstance(), false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            attackKeyWasDown = false;
            lastAttackIntentTick = Integer.MIN_VALUE / 4;
            VeyloriaClientState.instance().stopAutoConsumableUse();
            syncUseKeyState(minecraft, false);
            return;
        }
        syncMeleeAttackIntent(minecraft);
        syncAutoConsumableUse(minecraft);
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
        boolean levelEnough = VeyloriaClientState.instance().level() >= itemData.requiredLevel();
        event.getToolTip().add(Component.literal("Требуемый уровень: " + itemData.requiredLevel())
            .withStyle(levelEnough ? ChatFormatting.WHITE : ChatFormatting.RED));
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
        if (itemData.equipSlot() != EquipSlot.WEAPON) {
            ChatFormatting armorColor = itemData.armorBoosted() ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            event.getToolTip().add(Component.literal("Броня: +" + itemData.rolledStats().armor()).withStyle(armorColor));
        }
        if (!itemData.weaponType().isBlank()) {
            event.getToolTip().add(Component.literal("Тип оружия: " + weaponLabel(itemData.weaponType())));
            if (itemData.aoeChance() > 0.0D && itemData.aoeTargets() > 0) {
                int percent = (int) Math.round(itemData.aoeChance() * 100.0D);
                event.getToolTip().add(Component.literal("Шанс AOE: " + percent + "%, целей: " + itemData.aoeTargets())
                    .withStyle(ChatFormatting.GOLD));
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
    public static void onRenderHudPre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)
            || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
            || event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
            || event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)
            || event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int barWidth = 182;
        int barHeight = 8;
        int barX = width / 2 - barWidth / 2;
        int hpY = height - 52;
        int manaY = hpY + 11;
        int hpCurrent = (int) Math.ceil(Math.max(0.0D, minecraft.player.getHealth()));
        int hpMax = (int) Math.ceil(Math.max(1.0D, minecraft.player.getMaxHealth()));
        VeyloriaClientState.ResourceBars selfBars = VeyloriaClientState.instance().playerBars(minecraft.player.getUUID());
        if (selfBars != null) {
            hpCurrent = selfBars.health();
            hpMax = selfBars.healthMax();
        }

        drawBar(guiGraphics, barX, hpY, barWidth, barHeight, hpCurrent, hpMax, 0xFFB02020);
        guiGraphics.drawCenteredString(minecraft.font, "HP " + Math.max(0, hpCurrent) + "/" + Math.max(1, hpMax), width / 2, hpY - 9, 0xFF5050);

        int mana = VeyloriaClientState.instance().mana();
        int manaMax = VeyloriaClientState.instance().manaMax();
        if (manaMax > 0) {
            drawBar(guiGraphics, barX, manaY, barWidth, barHeight, mana, manaMax, 0xFF2B7CCF);
            guiGraphics.drawCenteredString(minecraft.font, "MP " + mana + "/" + manaMax, width / 2, manaY - 9, 0x55B8FF);
        }

        int armorValue = Math.max(0, minecraft.player.getArmorValue());
        guiGraphics.drawString(minecraft.font, "Ур. " + minecraft.player.experienceLevel, 12, hpY - 1, 0xF0F0F0, false);
        guiGraphics.drawString(minecraft.font, "Броня: " + armorValue, 12, hpY + 9, 0xD0D0D0, false);
        guiGraphics.drawString(minecraft.font, "Медь: " + VeyloriaClientState.instance().copper(), width - 116, 12, 0xF0A040, false);

        int notificationBaseY = manaMax > 0 ? hpY - 24 : hpY - 14;
        int index = 0;
        for (VeyloriaClientState.Notification notification : VeyloriaClientState.instance().notifications()) {
            guiGraphics.drawCenteredString(minecraft.font, notification.text(), width / 2, notificationBaseY - index * 10, 0xFFFFFF);
            index++;
        }
        drawZoneAnnouncement(guiGraphics, minecraft, width);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        UUID targetUuid = VeyloriaClientState.instance().currentTargetUuid();
        if (targetUuid == null) {
            return;
        }
        LivingEntity target = resolveLockedTarget(minecraft, targetUuid);
        if (target == null) {
            return;
        }
        renderTargetOutline(event, minecraft, target);
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
            state.setProfile(
                parseInt(fieldValue(marker, "level")),
                parseInt(fieldValue(marker, "xpCurrent")),
                parseInt(fieldValue(marker, "xpNext")),
                parseInt(fieldValue(marker, "copper")),
                parseInt(fieldValue(marker, "mana")),
                parseInt(fieldValue(marker, "manaMax")),
                new BaseStats(
                    parseInt(fieldValue(marker, "power")),
                    parseInt(fieldValue(marker, "vitality")),
                    parseInt(fieldValue(marker, "armor")),
                    parseInt(fieldValue(marker, "crit")),
                    parseInt(fieldValue(marker, "haste"))
                )
            );
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
        if (marker.startsWith("[veyloria:target]")) {
            state.setCurrentTarget(parseUuid(fieldValue(marker, "uuid")), tickNow() + 15);
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
            return;
        }
        if (marker.startsWith("[veyloria:zone]")) {
            state.showZoneAnnouncement(
                fieldValue(marker, "name"),
                fieldValue(marker, "range"),
                tickNow()
            );
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

    private static void drawBar(GuiGraphics guiGraphics, int x, int y, int width, int height, int current, int max, int fillColor) {
        int safeMax = Math.max(1, max);
        double ratio = clamp01(Math.max(0, current) / (double) safeMax);
        int fill = (int) Math.round(width * ratio);
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xA0101010);
        guiGraphics.fill(x, y, x + width, y + height, 0xB0000000);
        if (fill > 0) {
            guiGraphics.fill(x, y, x + fill, y + height, fillColor);
        }
    }

    private static void drawZoneAnnouncement(GuiGraphics guiGraphics, Minecraft minecraft, int width) {
        VeyloriaClientState.ZoneAnnouncement announcement = VeyloriaClientState.instance().zoneAnnouncement();
        if (announcement == null) {
            return;
        }
        long age = tickNow() - announcement.shownAtTick();
        if (age < 0 || age >= VeyloriaClientState.ZoneAnnouncement.TOTAL_DURATION_TICKS) {
            return;
        }
        double alpha = zoneAnnouncementAlpha(age);
        if (alpha <= 0.01D) {
            return;
        }

        String title = announcement.zoneName();
        String subtitle = announcement.levelRange().isBlank() ? "" : "Уровни: " + announcement.levelRange();
        int titleWidth = minecraft.font.width(title);
        int subtitleWidth = subtitle.isBlank() ? 0 : minecraft.font.width(subtitle);
        int contentWidth = Math.max(titleWidth, subtitleWidth);
        int boxWidth = contentWidth + 24;
        int boxHeight = subtitle.isBlank() ? 24 : 36;
        int boxX = width / 2 - boxWidth / 2;
        int boxY = 18;

        int bgAlpha = Math.max(0, Math.min(255, (int) Math.round(alpha * 138.0D)));
        int borderAlpha = Math.max(0, Math.min(255, (int) Math.round(alpha * 196.0D)));
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, (bgAlpha << 24));
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, (borderAlpha << 24) | 0xD8C58B);
        guiGraphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, (borderAlpha << 24) | 0xD8C58B);

        int textAlpha = Math.max(0, Math.min(255, (int) Math.round(alpha * 255.0D)));
        int titleColor = (textAlpha << 24) | 0xF7E6BA;
        int subtitleColor = (textAlpha << 24) | 0xCFCFD4;
        guiGraphics.drawCenteredString(minecraft.font, title, width / 2, boxY + 8, titleColor);
        if (!subtitle.isBlank()) {
            guiGraphics.drawCenteredString(minecraft.font, subtitle, width / 2, boxY + 20, subtitleColor);
        }
    }

    private static double zoneAnnouncementAlpha(long age) {
        if (age < VeyloriaClientState.ZoneAnnouncement.FADE_IN_TICKS) {
            return age / (double) VeyloriaClientState.ZoneAnnouncement.FADE_IN_TICKS;
        }
        long fadeOutStart = VeyloriaClientState.ZoneAnnouncement.FADE_IN_TICKS + VeyloriaClientState.ZoneAnnouncement.HOLD_TICKS;
        if (age < fadeOutStart) {
            return 1.0D;
        }
        long fadeOutAge = age - fadeOutStart;
        return 1.0D - (fadeOutAge / (double) VeyloriaClientState.ZoneAnnouncement.FADE_OUT_TICKS);
    }

    private static void syncMeleeAttackIntent(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            attackKeyWasDown = false;
            lastAttackIntentTick = Integer.MIN_VALUE / 4;
            return;
        }
        boolean attackDown = isPhysicalKeyDown(minecraft, minecraft.options.keyAttack);
        boolean canProcess = minecraft.screen == null && minecraft.gameMode != null;
        if (canProcess && attackDown) {
            int tick = minecraft.player.tickCount;
            boolean pressedThisTick = !attackKeyWasDown;
            boolean holdPulse = tick - lastAttackIntentTick >= 2;
            if (pressedThisTick || holdPulse) {
                minecraft.getConnection().send(new VeyloriaNetwork.MeleeAttackIntentPayload());
                lastAttackIntentTick = tick;
            }
        } else if (!attackDown) {
            lastAttackIntentTick = Integer.MIN_VALUE / 4;
        }
        attackKeyWasDown = attackDown;
    }

    private static void renderTargetOutline(RenderLevelStageEvent event, Minecraft minecraft, LivingEntity target) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        AABB box = target.getBoundingBox().inflate(TARGET_OUTLINE_INFLATE)
            .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            box,
            TARGET_OUTLINE_RED,
            TARGET_OUTLINE_GREEN,
            TARGET_OUTLINE_BLUE,
            TARGET_OUTLINE_ALPHA
        );
        buffers.endBatch(RenderType.lines());
    }

    private static LivingEntity resolveLockedTarget(Minecraft minecraft, UUID targetUuid) {
        if (minecraft == null || minecraft.player == null || targetUuid == null) {
            return null;
        }
        return minecraft.player.level().getEntitiesOfClass(
            LivingEntity.class,
            minecraft.player.getBoundingBox().inflate(64.0D),
            entity -> entity.getUUID().equals(targetUuid) && entity.isAlive() && !entity.isRemoved()
        ).stream().findFirst().orElse(null);
    }

    private static void syncAutoConsumableUse(Minecraft minecraft) {
        VeyloriaClientState state = VeyloriaClientState.instance();
        if (!state.isAutoUsingConsumable() || minecraft.player == null || minecraft.gameMode == null) {
            syncUseKeyState(minecraft, false);
            return;
        }

        boolean forceUseKey = false;
        ItemStack offhand = minecraft.player.getOffhandItem();
        ItemStack expected = state.loadoutItem(state.autoConsumableSlot());

        if (minecraft.player.isUsingItem() && minecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            forceUseKey = true;
        } else if (!offhand.isEmpty() && matchesAutoConsumable(offhand, expected)) {
            InteractionResult result = minecraft.gameMode.useItem(minecraft.player, InteractionHand.OFF_HAND);
            forceUseKey = result.consumesAction()
                || (minecraft.player.isUsingItem() && minecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND);
        } else if (offhand.isEmpty()) {
            state.stopAutoConsumableUse();
        }

        if (!forceUseKey && !minecraft.player.isUsingItem() && minecraft.player.getOffhandItem().isEmpty()) {
            state.stopAutoConsumableUse();
        }

        syncUseKeyState(minecraft, forceUseKey && state.isAutoUsingConsumable());
    }

    private static void syncUseKeyState(Minecraft minecraft, boolean forceDown) {
        if (minecraft == null) {
            return;
        }
        KeyMapping keyUse = minecraft.options.keyUse;
        keyUse.setDown(forceDown || isPhysicalKeyDown(minecraft, keyUse));
    }

    private static boolean isPhysicalKeyDown(Minecraft minecraft, KeyMapping keyMapping) {
        if (minecraft == null || keyMapping == null) {
            return false;
        }
        InputConstants.Key key = keyMapping.getKey();
        long window = minecraft.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM, SCANCODE -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        };
    }

    private static boolean matchesAutoConsumable(ItemStack offhand, ItemStack expected) {
        if (offhand.isEmpty()) {
            return false;
        }
        if (!expected.isEmpty()) {
            return ItemStack.isSameItemSameComponents(offhand, expected);
        }
        UseAnim animation = offhand.getUseAnimation();
        return animation == UseAnim.EAT || animation == UseAnim.DRINK;
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

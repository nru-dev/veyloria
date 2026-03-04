package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.targeting.TargetingProfile;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TargetingHudRenderer {
    private static final int FRAME_HALF_SIZE = 14;
    private static final int FRAME_GAP = 6;
    private static final int FRAME_CORNER = 8;
    private static final int FRAME_THICKNESS = 2;
    private static final int FRAME_COLOR = 0xFFE33A3A;
    private static final int FRAME_SHADOW = 0x70A81F1F;
    private static final int TITLE_COLOR = 0xFFF6DADA;
    private static final int SUBTITLE_COLOR = 0xFFDF8A8A;

    private TargetingHudRenderer() {
    }

    public static void render(Minecraft minecraft, GuiGraphics guiGraphics, TargetingProfile profile) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || profile == null) {
            return;
        }
        Player player = minecraft.player;
        UUID targetUuid = VeyloriaClientState.instance().currentTargetUuid();
        if (targetUuid == null) {
            return;
        }

        LivingEntity target = resolveTarget(player, targetUuid, profile.clampedRangeBlocks() + 2.0D);
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        drawCenterLock(guiGraphics, centerX, centerY);
        drawTargetInfo(minecraft, guiGraphics, player, target, centerX, centerY + FRAME_HALF_SIZE + 10);
    }

    private static LivingEntity resolveTarget(Player player, UUID targetUuid, double range) {
        return player.level().getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(range),
            entity -> entity.getUUID().equals(targetUuid)
        ).stream().findFirst().orElse(null);
    }

    private static void drawCenterLock(GuiGraphics guiGraphics, int centerX, int centerY) {
        int left = centerX - FRAME_HALF_SIZE;
        int right = centerX + FRAME_HALF_SIZE;
        int top = centerY - FRAME_HALF_SIZE;
        int bottom = centerY + FRAME_HALF_SIZE;

        guiGraphics.fill(left - FRAME_THICKNESS, top - FRAME_THICKNESS, right + FRAME_THICKNESS, bottom + FRAME_THICKNESS, FRAME_SHADOW);

        drawCorner(guiGraphics, left, top, 1, 1);
        drawCorner(guiGraphics, right, top, -1, 1);
        drawCorner(guiGraphics, left, bottom, 1, -1);
        drawCorner(guiGraphics, right, bottom, -1, -1);
    }

    private static void drawCorner(GuiGraphics guiGraphics, int x, int y, int xDir, int yDir) {
        int innerX = x + xDir * FRAME_GAP;
        int innerY = y + yDir * FRAME_GAP;

        int horizX1 = innerX;
        int horizX2 = innerX + xDir * FRAME_CORNER;
        int horizY1 = innerY;
        int horizY2 = innerY + yDir * FRAME_THICKNESS;
        guiGraphics.fill(Math.min(horizX1, horizX2), Math.min(horizY1, horizY2), Math.max(horizX1, horizX2), Math.max(horizY1, horizY2), FRAME_COLOR);

        int vertX1 = innerX;
        int vertX2 = innerX + xDir * FRAME_THICKNESS;
        int vertY1 = innerY;
        int vertY2 = innerY + yDir * FRAME_CORNER;
        guiGraphics.fill(Math.min(vertX1, vertX2), Math.min(vertY1, vertY2), Math.max(vertX1, vertX2), Math.max(vertY1, vertY2), FRAME_COLOR);
    }

    private static void drawTargetInfo(Minecraft minecraft, GuiGraphics guiGraphics, Player player, LivingEntity target, int centerX, int startY) {
        String title = target.getDisplayName().getString();
        String hp = String.format(Locale.ROOT, "HP %.0f/%.0f", target.getHealth(), target.getMaxHealth());
        double distance = Math.sqrt(player.distanceToSqr(target));
        String subtitle = hp + "  -  " + String.format(Locale.ROOT, "%.1fm", distance);
        guiGraphics.drawCenteredString(minecraft.font, title, centerX, startY, TITLE_COLOR);
        guiGraphics.drawCenteredString(minecraft.font, subtitle, centerX, startY + 10, SUBTITLE_COLOR);
    }
}

package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerStatService {
    public BaseStats totalStats(Player player, CharacterProfile profile) {
        BaseStats total = scaledProfileStats(profile);
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerLoadoutData loadout = VeyloriaServerRuntime.instance().playerLoadoutService().loadout(serverPlayer);
            for (int slot = 0; slot < PlayerLoadoutData.SLOT_COUNT; slot++) {
                if (PlayerLoadoutData.contributesToStats(slot)) {
                    total = total.add(read(loadout.getItem(slot)));
                }
            }
            return total;
        }
        return total.add(read(player.getMainHandItem()));
    }

    public double computePlayerDamage(Player player, CharacterProfile profile) {
        BaseStats stats = totalStats(player, profile);
        return estimatedUngearedDamage(profile.level()) + stats.power() * 0.55D;
    }

    public double computePlayerMaxHealth(Player player, CharacterProfile profile) {
        BaseStats stats = totalStats(player, profile);
        return estimatedUngearedHealth(profile.level())
            + stats.vitality() * (VeyloriaServerRuntime.instance().serverConfig().combat().vitalityHealthBonus() * 0.45D);
    }

    public double mitigateIncomingDamage(Player player, CharacterProfile profile, double incoming, int attackerLevel) {
        BaseStats stats = totalStats(player, profile);
        double armorRating = Math.max(0.0D, stats.armor());
        double tuning = Math.max(0.10D, VeyloriaServerRuntime.instance().serverConfig().combat().armorDamageReductionFactor() * 30.0D);
        double scaledArmor = armorRating * tuning;
        int safeAttackerLevel = Math.max(1, attackerLevel);
        double denominator = Math.max(1.0D, scaledArmor + 85.0D + safeAttackerLevel * 12.0D);
        double armorReduction = Math.min(0.85D, scaledArmor / denominator);
        double mitigated = incoming * (1.0D - armorReduction);
        double effectiveHealth = Math.max(20.0D, computePlayerMaxHealth(player, profile));
        double vanillaScale = 20.0D / effectiveHealth;
        return mitigated * vanillaScale;
    }

    public static double estimatedUngearedDamage(int level) {
        int safeLevel = Math.max(1, level);
        return 2.0D + safeLevel * 0.35D;
    }

    public static double estimatedUngearedHealth(int level) {
        int safeLevel = Math.max(1, level);
        return 22.0D + (safeLevel - 1) * 6.0D;
    }

    private static BaseStats scaledProfileStats(CharacterProfile profile) {
        int level = Math.max(1, profile.level());
        int bonus = level - 1;
        BaseStats base = profile.baseStats();
        return new BaseStats(
            base.power() + bonus * 2,
            base.vitality() + bonus * 2,
            base.armor() + bonus,
            base.crit() + bonus / 10,
            base.haste() + bonus / 10
        );
    }

    private BaseStats read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return BaseStats.ZERO;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(RpgItemData.ROOT_KEY)) {
            return BaseStats.ZERO;
        }
        return RpgItemData.fromTag(data.copyTag().getCompound(RpgItemData.ROOT_KEY)).rolledStats();
    }
}

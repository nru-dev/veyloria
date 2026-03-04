package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerStatService {
    public BaseStats totalStats(Player player, CharacterProfile profile) {
        BaseStats total = scaledProfileStats(profile);
        total = total.add(read(player.getMainHandItem()));
        total = total.add(read(player.getOffhandItem()));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.HEAD)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.CHEST)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.LEGS)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.FEET)));
        return total;
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

    public double mitigateIncomingDamage(Player player, CharacterProfile profile, double incoming) {
        BaseStats stats = totalStats(player, profile);
        double factor = VeyloriaServerRuntime.instance().serverConfig().combat().armorDamageReductionFactor();
        double reduction = Math.min(0.75D, stats.armor() * factor);
        return incoming * (1.0D - reduction);
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

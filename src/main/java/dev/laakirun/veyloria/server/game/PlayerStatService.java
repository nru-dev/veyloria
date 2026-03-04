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
        BaseStats total = profile.baseStats();
        total = total.add(read(player.getMainHandItem()));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.HEAD)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.CHEST)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.LEGS)));
        total = total.add(read(player.getItemBySlot(EquipmentSlot.FEET)));
        return total;
    }

    public double computePlayerDamage(Player player, CharacterProfile profile) {
        BaseStats stats = totalStats(player, profile);
        double baseDamage = VeyloriaServerRuntime.instance().serverConfig().combat().playerBaseDamage();
        return baseDamage + stats.power() * 1.5D;
    }

    public double computePlayerMaxHealth(Player player, CharacterProfile profile) {
        BaseStats stats = totalStats(player, profile);
        double baseHealth = VeyloriaServerRuntime.instance().serverConfig().combat().playerBaseHealth();
        return baseHealth + stats.vitality() * VeyloriaServerRuntime.instance().serverConfig().combat().vitalityHealthBonus();
    }

    public double mitigateIncomingDamage(Player player, CharacterProfile profile, double incoming) {
        BaseStats stats = totalStats(player, profile);
        double factor = VeyloriaServerRuntime.instance().serverConfig().combat().armorDamageReductionFactor();
        double reduction = Math.min(0.75D, stats.armor() * factor);
        return incoming * (1.0D - reduction);
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

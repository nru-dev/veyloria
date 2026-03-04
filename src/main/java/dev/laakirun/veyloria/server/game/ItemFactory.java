package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import net.minecraft.core.component.DataComponents;
import dev.laakirun.veyloria.server.content.ItemTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemFactory {
    public ItemStack create(ItemTemplate template, int quantity) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(template.vanillaIconItem()));
        ItemStack stack = new ItemStack(item, Math.max(1, quantity));
        BaseStats rolledStats = template.baseStats().scale(template.rarity().multiplier());
        CompoundTag tag = new CompoundTag();
        tag.put(RpgItemData.ROOT_KEY, new RpgItemData(
            template.code(),
            template.category(),
            template.rarity(),
            template.requiredLevel(),
            template.equipSlot(),
            rolledStats
        ).toTag());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(template.name()));
        return stack;
    }
}

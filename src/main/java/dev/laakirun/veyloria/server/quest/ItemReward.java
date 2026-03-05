package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.server.npc.NpcReward;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record ItemReward(String itemId, int count) implements NpcReward {
    public ItemReward {
        itemId = itemId == null ? "minecraft:air" : itemId;
        count = Math.max(1, count);
    }

    @Override
    public String typeId() {
        return "item";
    }

    @Override
    public void apply(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(Items.AIR);
        if (item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}

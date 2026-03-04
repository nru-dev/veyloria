package dev.laakirun.veyloria.common.registry;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.menu.VeyloriaInventoryMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VeyloriaMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(BuiltInRegistries.MENU, VeyloriaConstants.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<VeyloriaInventoryMenu>> VEYLORIA_INVENTORY =
        MENUS.register("veyloria_inventory", () -> IMenuTypeExtension.create(VeyloriaInventoryMenu::new));

    private VeyloriaMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

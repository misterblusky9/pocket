package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PocketSized.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<AtelierMenu>> ATELIER =
            MENUS.register("atelier", () -> new MenuType<>(AtelierMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}

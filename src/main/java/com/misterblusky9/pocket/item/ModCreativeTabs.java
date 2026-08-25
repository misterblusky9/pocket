package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PocketSized.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pocket"))
                    .icon(() -> new ItemStack(ModItems.COMPRESSION_GUN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.EMPTY_BOX.get());
                        output.accept(ModItems.DISPLAY_CASE.get());
                        output.accept(ModItems.BRASS_DISPLAY_CASE.get());
                        output.accept(ModItems.BRASS_DISPLAY_PLATE.get());
                        output.accept(ModItems.ANDESITE_DISPLAY_CASE.get());
                        output.accept(ModItems.ANDESITE_DISPLAY_PLATE.get());
                        output.accept(ModItems.CREATIVE_SHRINK_RAY.get());
                        output.accept(ModItems.COMPRESSION_GUN.get());
                        output.accept(ModItems.PORTABLE_SUBSPACE_COMPRESSOR.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {}
}

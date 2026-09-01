package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PocketSized.MOD_ID);

    public static final DeferredItem<PocketCaseItem> POCKETED_SUBLEVEL = ITEMS.register(
            "pocketed_sublevel", () -> new PocketCaseItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<EmptyBoxItem> EMPTY_BOX = ITEMS.register(
            "empty_box", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<EmptyBoxItem> DISPLAY_BOTTLE = ITEMS.register(
            "display_bottle", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<EmptyBoxItem> BRASS_DISPLAY_CASE = ITEMS.register(
            "brass_display_case", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<EmptyBoxItem> BRASS_DISPLAY_PLATE = ITEMS.register(
            "brass_display_plate", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<EmptyBoxItem> ANDESITE_DISPLAY_CASE = ITEMS.register(
            "andesite_display_case", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<EmptyBoxItem> ANDESITE_DISPLAY_PLATE = ITEMS.register(
            "andesite_display_plate", () -> new EmptyBoxItem(new Item.Properties())
    );

    public static final DeferredItem<CreativeShrinkRayItem> CREATIVE_SHRINK_RAY = ITEMS.register(
            "creative_shrink_ray", () -> new CreativeShrinkRayItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final DeferredItem<CompressionGunItem> COMPRESSION_GUN = ITEMS.register(
            "compression_gun",

            () -> new CompressionGunItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final DeferredItem<BlockItem> PORTABLE_SUBSPACE_COMPRESSOR = ITEMS.register(
            "portable_subspace_compressor",
            () -> new BlockItem(ModBlocks.PORTABLE_SUBSPACE_COMPRESSOR.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> SUBSPACE_RECYCLER = ITEMS.register(
            "subspace_recycler",
            () -> new BlockItem(ModBlocks.SUBSPACE_RECYCLER.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> STATIC_SUBSPACE_COMPRESSOR = ITEMS.register(
            "static_subspace_compressor",
            () -> new BlockItem(ModBlocks.STATIC_SUBSPACE_COMPRESSOR.get(), new Item.Properties())
    );

    public static final DeferredItem<TweezersItem> TWEEZERS = ITEMS.register(
            "tweezers", () -> new TweezersItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<ColliderWandItem> COLLIDER_WAND = ITEMS.register(
            "collider_wand", () -> new ColliderWandItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<TheMoonItem> THE_MOON =
            ITEMS.register("the_moon", () -> new TheMoonItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}

package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;


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

    public static final DeferredItem<LiveCaseItem> LIVE_CASE = ITEMS.register(
            "live_case", () -> new LiveCaseItem(new Item.Properties().stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
    );

    public static final DeferredItem<CreativeShrinkRayItem> CREATIVE_SHRINK_RAY = ITEMS.register(
            "creative_shrink_ray", () -> new CreativeShrinkRayItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final DeferredItem<CompressionGunItem> COMPRESSION_GUN = ITEMS.register(
            "compression_gun",
            () -> new CompressionGunItem(new Item.Properties().stacksTo(1).durability(60), false)
    );

    public static final DeferredItem<CompressionGunItem> PEARLESCENT_COMPRESSION_GUN = ITEMS.register(
            "pearlescent_compression_gun",
            () -> new CompressionGunItem(new Item.Properties().stacksTo(1).durability(60).rarity(Rarity.UNCOMMON), true)
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

    public static final DeferredItem<SelfResizeDeviceItem> SELF_RESIZE_DEVICE = ITEMS.register(
            "self_resize_device", () -> new SelfResizeDeviceItem(new Item.Properties().stacksTo(1).durability(16))
    );

    public static final DeferredItem<ColliderWandItem> COLLIDER_WAND = ITEMS.register(
            "collider_wand", () -> new ColliderWandItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<PocketKnifeItem> POCKET_KNIFE = ITEMS.register(
            "pocket_knife", () -> new PocketKnifeItem(new Item.Properties().durability(100))
    );

    public static final DeferredItem<TheMoonItem> THE_MOON =
            ITEMS.register("the_moon", () -> new TheMoonItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SWITCH_BEARING = ITEMS.register(
            "switch_bearing",
            () -> new BlockItem(ModBlocks.SWITCH_BEARING.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> HELM_BEARING = ITEMS.register(
            "helm_bearing",
            () -> new BlockItem(ModBlocks.HELM_BEARING.get(), new Item.Properties())
    );

    public static final DeferredItem<HotGlueGunItem> GLUE_GUN = ITEMS.register(
            "glue_gun", () -> new HotGlueGunItem(new Item.Properties().stacksTo(1).durability(100))
    );

    public static final DeferredItem<MagnifyingGlassItem> MAGNIFYING_GLASS = ITEMS.register(
            "magnifying_glass", () -> new MagnifyingGlassItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<BlockItem> SWITCH_PISTON = ITEMS.register(
            "switch_piston",
            () -> new BlockItem(ModBlocks.SWITCH_PISTON.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> BLUEPRINT_CASING = ITEMS.register(
            "blueprint_casing",
            () -> new BlockItem(ModBlocks.BLUEPRINT_CASING.get(), new Item.Properties())
    );

    static {
        ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "diagram_tile"), BLUEPRINT_CASING.getId());
        ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "blueprint_tile"), BLUEPRINT_CASING.getId());
    }

    public static final DeferredItem<BlockItem> FLEXIBLE_COUPLING = ITEMS.register(
            "flexible_coupling",
            () -> new BlockItem(ModBlocks.FLEXIBLE_COUPLING.get(), new Item.Properties())
    );

    public static final DeferredItem<FlexibleShaftItem> FLEXIBLE_SHAFT = ITEMS.register(
            "flexible_shaft", () -> new FlexibleShaftItem(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> COPYCAT_FACADE = ITEMS.register(
            "copycat_facade",
            () -> new BlockItem(ModBlocks.COPYCAT_FACADE.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> ATELIER = ITEMS.register(
            "atelier",
            () -> new BlockItem(ModBlocks.ATELIER.get(), new Item.Properties())
    );

    public static final DeferredItem<HattersStandItem> HATTERS_STAND = ITEMS.register(
            "hatters_stand", () -> new HattersStandItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<BacktankItem.BacktankBlockItem> SUBSPACE_HARNESS_PLACEABLE = ITEMS.register(
            "subspace_harness_placeable",
            () -> new BacktankItem.BacktankBlockItem(
                    ModBlocks.SUBSPACE_HARNESS.get(), ModItems.SUBSPACE_HARNESS::get, new Item.Properties())
    );

    public static final DeferredItem<BacktankItem> SUBSPACE_HARNESS = ITEMS.register(
            "subspace_harness",
            () -> new BacktankItem(
                    AllArmorMaterials.COPPER,
                    new Item.Properties(),
                    ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "subspace_harness"),
                    SUBSPACE_HARNESS_PLACEABLE)
    );

    private ModItems() {}
}

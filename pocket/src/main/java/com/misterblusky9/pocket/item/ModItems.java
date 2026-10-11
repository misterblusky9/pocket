package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

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
            "creative_shrink_ray", () -> new CreativeShrinkRayItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final DeferredItem<CompressionGunItem> COMPRESSION_GUN = ITEMS.register(
            "compression_gun",
            () -> new CompressionGunItem(new Item.Properties().stacksTo(1).durability(512), false)
    );

    public static final DeferredItem<CompressionGunItem> PEARLESCENT_COMPRESSION_GUN = ITEMS.register(
            "pearlescent_compression_gun",
            () -> new CompressionGunItem(new Item.Properties().stacksTo(1).durability(512).rarity(Rarity.UNCOMMON), true)
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

    public static final DeferredItem<BlockItem> SWITCH_PISTON = ITEMS.register(
            "switch_piston",
            () -> new BlockItem(ModBlocks.SWITCH_PISTON.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> BLUEPRINT_TILE = ITEMS.register(
            "blueprint_tile",
            () -> new BlockItem(ModBlocks.BLUEPRINT_TILE.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                            final List<Component> tooltip, final TooltipFlag flag) {
                    tooltip.add(Component.translatable("block.pocket.blueprint_tile.tooltip.summary")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
    );

    static {
        ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "diagram_tile"), BLUEPRINT_TILE.getId());
    }

    public static final DeferredItem<BlockItem> COPYCAT_FACADE = ITEMS.register(
            "copycat_facade",
            () -> new BlockItem(ModBlocks.COPYCAT_FACADE.get(), new Item.Properties())
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

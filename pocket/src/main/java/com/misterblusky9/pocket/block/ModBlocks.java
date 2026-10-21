package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.foundation.data.SharedProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PocketSized.MOD_ID);

    public static final DeferredHolder<Block, PortableSubspaceCompressorBlock> PORTABLE_SUBSPACE_COMPRESSOR =
            BLOCKS.register(
                    "portable_subspace_compressor",
                    () -> new PortableSubspaceCompressorBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .noOcclusion()
                    )
            );

    public static final DeferredHolder<Block, SubspaceRecyclerBlock> SUBSPACE_RECYCLER =
            BLOCKS.register(
                    "subspace_recycler",
                    () -> new SubspaceRecyclerBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                                    .mapColor(MapColor.METAL)
                    )
            );

    public static final DeferredHolder<Block, StaticSubspaceCompressorBlock> STATIC_SUBSPACE_COMPRESSOR =
            BLOCKS.register(
                    "static_subspace_compressor",
                    () -> new StaticSubspaceCompressorBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                    .mapColor(MapColor.METAL)
                                    .noOcclusion()
                    )
            );

    public static final DeferredHolder<Block, SwitchBearingBlock> SWITCH_BEARING =
            BLOCKS.register(
                    "switch_bearing",
                    () -> new SwitchBearingBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                                    .mapColor(MapColor.PODZOL)
                                    .noOcclusion()
                    )
            );

    public static final DeferredHolder<Block, HelmBearingBlock> HELM_BEARING =
            BLOCKS.register(
                    "helm_bearing",
                    () -> new HelmBearingBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                                    .mapColor(MapColor.PODZOL)
                                    .noOcclusion()
                    )
            );

    public static final DeferredHolder<Block, SwitchPistonBlock> SWITCH_PISTON =
            BLOCKS.register(
                    "switch_piston",
                    () -> new SwitchPistonBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                                    .mapColor(MapColor.PODZOL)
                                    .noOcclusion()
                    )
            );

    public static final DeferredHolder<Block, CasingBlock> BLUEPRINT_CASING =
            BLOCKS.register(
                    "blueprint_casing",
                    () -> new CasingBlock(BlockBehaviour.Properties.ofFullCopy(SharedProperties.stone()).sound(SoundType.WOOD))
            );

    static {
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "diagram_tile"), BLUEPRINT_CASING.getId());
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "blueprint_tile"), BLUEPRINT_CASING.getId());
    }

    public static final DeferredHolder<Block, FlexibleCouplingBlock> FLEXIBLE_COUPLING =
            BLOCKS.register(
                    "flexible_coupling",
                    () -> new FlexibleCouplingBlock(BlockBehaviour.Properties.ofFullCopy(SharedProperties.softMetal()))
            );

    public static final DeferredHolder<Block, CopycatFacadeBlock> COPYCAT_FACADE =
            BLOCKS.register(
                    "copycat_facade",
                    () -> new CopycatFacadeBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
                                    .noOcclusion()
                                    .mapColor(MapColor.NONE)
                                    .isValidSpawn((state, level, pos, type) -> false)
                    )
            );

    public static final DeferredHolder<Block, SubspaceHarnessBlock> SUBSPACE_HARNESS =
            BLOCKS.register(
                    "subspace_harness",
                    () -> new SubspaceHarnessBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK))
            );

    public static final DeferredHolder<Block, AtelierBlock> ATELIER =
            BLOCKS.register(
                    "atelier",
                    () -> new AtelierBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN)
                                    .mapColor(MapColor.PODZOL)
                                    .forceSolidOn()
                    )
            );

    public static final DeferredHolder<Block, BlueprintEncasedShaftBlock> BLUEPRINT_ENCASED_SHAFT =
            BLOCKS.register("blueprint_encased_shaft",
                    () -> new BlueprintEncasedShaftBlock(
                            BlockBehaviour.Properties.ofFullCopy(com.simibubi.create.AllBlocks.ANDESITE_ENCASED_SHAFT.get())));

    public static final DeferredHolder<Block, BlueprintEncasedCogwheelBlock> BLUEPRINT_ENCASED_COGWHEEL =
            BLOCKS.register("blueprint_encased_cogwheel",
                    () -> new BlueprintEncasedCogwheelBlock(
                            BlockBehaviour.Properties.ofFullCopy(com.simibubi.create.AllBlocks.ANDESITE_ENCASED_COGWHEEL.get()), false));

    public static final DeferredHolder<Block, BlueprintEncasedCogwheelBlock> BLUEPRINT_ENCASED_LARGE_COGWHEEL =
            BLOCKS.register("blueprint_encased_large_cogwheel",
                    () -> new BlueprintEncasedCogwheelBlock(
                            BlockBehaviour.Properties.ofFullCopy(com.simibubi.create.AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL.get()), true));

    private ModBlocks() {}
}

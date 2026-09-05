package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.Registries;
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

    private ModBlocks() {}
}

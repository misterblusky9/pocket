package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
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

    private ModBlocks() {}
}

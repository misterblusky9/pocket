package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortableSubspaceCompressorBlockEntity>>
            PORTABLE_SUBSPACE_COMPRESSOR = BLOCK_ENTITIES.register(
                    "portable_subspace_compressor",
                    () -> BlockEntityType.Builder.of(
                            PortableSubspaceCompressorBlockEntity::new,
                            ModBlocks.PORTABLE_SUBSPACE_COMPRESSOR.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubspaceRecyclerBlockEntity>>
            SUBSPACE_RECYCLER = BLOCK_ENTITIES.register(
                    "subspace_recycler",
                    () -> BlockEntityType.Builder.of(
                            SubspaceRecyclerBlockEntity::new,
                            ModBlocks.SUBSPACE_RECYCLER.get()
                    ).build(null)
            );

    private ModBlockEntities() {}
}

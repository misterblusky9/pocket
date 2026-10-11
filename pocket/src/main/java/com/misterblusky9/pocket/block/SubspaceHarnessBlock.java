package com.misterblusky9.pocket.block;

import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SubspaceHarnessBlock extends BacktankBlock {
    public SubspaceHarnessBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends BacktankBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SUBSPACE_HARNESS.get();
    }
}

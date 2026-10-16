package com.misterblusky9.pocket.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BlueprintEncasedShaftBlock extends EncasedShaftBlock {
    public BlueprintEncasedShaftBlock(Properties properties) {
        super(properties, ModBlocks.BLUEPRINT_CASING::get);
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BLUEPRINT_ENCASED_SHAFT.get();
    }
}

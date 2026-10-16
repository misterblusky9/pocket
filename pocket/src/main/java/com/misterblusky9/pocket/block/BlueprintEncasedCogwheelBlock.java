package com.misterblusky9.pocket.block;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BlueprintEncasedCogwheelBlock extends EncasedCogwheelBlock {
    public BlueprintEncasedCogwheelBlock(Properties properties, boolean large) {
        super(properties, large, ModBlocks.BLUEPRINT_CASING::get);
    }

    @Override
    public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType() {
        return isLargeCog()
                ? ModBlockEntities.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get()
                : ModBlockEntities.BLUEPRINT_ENCASED_COGWHEEL.get();
    }
}

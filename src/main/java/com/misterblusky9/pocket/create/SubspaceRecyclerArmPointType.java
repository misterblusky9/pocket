package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SubspaceRecyclerArmPointType extends ArmInteractionPointType {
    @Override
    public boolean canCreatePoint(final Level level, final BlockPos pos, final BlockState state) {
        return state.is(ModBlocks.SUBSPACE_RECYCLER.get());
    }

    @Override
    public ArmInteractionPoint createPoint(final Level level, final BlockPos pos, final BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state);
    }
}

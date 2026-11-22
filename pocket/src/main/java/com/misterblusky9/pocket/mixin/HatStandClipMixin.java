package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.block.HatStandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockGetter.class)
public interface HatStandClipMixin {
    @Overwrite
    default @Nullable BlockHitResult clipWithInteractionOverride(Vec3 start, Vec3 end, BlockPos pos, VoxelShape shape,
                                                                 BlockState state) {
        BlockGetter level = (BlockGetter) this;
        if (state.getBlock() instanceof HatStandBlock block) return block.clip(level, start, end, pos, state, shape);
        BlockHitResult hit = shape.clip(start, end, pos);
        if (hit == null) return null;
        BlockHitResult interaction = state.getInteractionShape(level, pos).clip(start, end, pos);
        return interaction != null && interaction.getLocation().subtract(start).lengthSqr()
                < hit.getLocation().subtract(start).lengthSqr() ? hit.withDirection(interaction.getDirection()) : hit;
    }
}

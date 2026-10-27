package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SableColliderShapes {
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape CELL = Shapes.block();

    public static VoxelShape of(BlockGetter level, BlockState state) {
        VoxelShape shape = SHAPES.get(state);
        if (shape == null) {
            shape = bake(level, state);
            SHAPES.put(state, shape);
        }
        return shape;
    }

    private static VoxelShape bake(BlockGetter level, BlockState state) {
        BlockGetter isolated = new Isolated(level, state);
        VoxelShape shape = state.getBlock() instanceof BlockSubLevelCollisionShape extension
                ? extension.getSubLevelCollisionShape(isolated, state)
                : state.getCollisionShape(isolated, BlockPos.ZERO, SableCollisionContext.get());
        if (shape.isEmpty()) return Shapes.empty();
        return Shapes.join(shape, CELL, BooleanOp.AND).optimize();
    }

    private record Isolated(BlockGetter level, BlockState state) implements BlockGetter {
        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return BlockPos.ZERO.equals(pos) ? this.state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return BlockPos.ZERO.equals(pos) ? this.state.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return this.level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.level.getMinBuildHeight();
        }
    }

    private SableColliderShapes() {}
}

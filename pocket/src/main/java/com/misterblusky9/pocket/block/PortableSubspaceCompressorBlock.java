package com.misterblusky9.pocket.block;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PortableSubspaceCompressorBlock extends HorizontalKineticBlock
        implements IBE<PortableSubspaceCompressorBlockEntity> {
    public PortableSubspaceCompressorBlock(final Properties properties) {
        super(properties);
    }

    public static Direction mechanicalInputFace(final BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean hasShaftTowards(
            final LevelReader world,
            final BlockPos pos,
            final BlockState state,
            final Direction face
    ) {
        return face == mechanicalInputFace(state);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return mechanicalInputFace(state).getAxis();
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction preferred = this.getPreferredHorizontalFacing(context);
        if (preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            final Direction horizontal = context.getHorizontalDirection();
            return this.defaultBlockState().setValue(
                    HORIZONTAL_FACING,
                    context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                            ? horizontal.getOpposite()
                            : horizontal
            );
        }
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, preferred);
    }

    @Override
    public VoxelShape getShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final CollisionContext context
    ) {
        return portableEngineShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final CollisionContext context
    ) {
        return portableEngineShape(state);
    }

    private static final VoxelShape PORTABLE_ENGINE_NORTH_SOUTH = Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(3, 2, 1, 13, 14, 15)
    );

    private static final VoxelShape PORTABLE_ENGINE_EAST_WEST = Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(1, 2, 3, 15, 14, 13)
    );

    private static VoxelShape portableEngineShape(final BlockState state) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case EAST, WEST -> PORTABLE_ENGINE_EAST_WEST;
            default -> PORTABLE_ENGINE_NORTH_SOUTH;
        };
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    @Override
    public Class<PortableSubspaceCompressorBlockEntity> getBlockEntityClass() {
        return PortableSubspaceCompressorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PortableSubspaceCompressorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.PORTABLE_SUBSPACE_COMPRESSOR.get();
    }
}

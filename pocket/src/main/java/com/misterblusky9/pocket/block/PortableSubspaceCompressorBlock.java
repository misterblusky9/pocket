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
    public PortableSubspaceCompressorBlock(Properties properties) {
        super(properties);
    }

    public static Direction mechanicalInputFace(BlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public boolean hasShaftTowards(
            LevelReader world,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        return face == mechanicalInputFace(state);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return mechanicalInputFace(state).getAxis();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = this.getPreferredHorizontalFacing(context);
        if (preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            Direction horizontal = context.getHorizontalDirection();
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
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return portableEngineShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
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

    private static VoxelShape portableEngineShape(BlockState state) {
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

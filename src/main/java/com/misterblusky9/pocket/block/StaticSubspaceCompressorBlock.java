package com.misterblusky9.pocket.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.index.SimBlockShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public final class StaticSubspaceCompressorBlock extends KineticBlock
        implements IBE<StaticSubspaceCompressorBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final MapCodec<StaticSubspaceCompressorBlock> CODEC =
            simpleCodec(StaticSubspaceCompressorBlock::new);

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    public StaticSubspaceCompressorBlock(final Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static Direction mechanicalInputFace(final BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void createBlockStateDefinition(
            final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection();

        if (context.getPlayer() != null && !context.getPlayer().isShiftKeyDown()) {
            direction = direction.getOpposite();
        }

        return defaultBlockState().setValue(FACING, direction);
    }

    @Override
    public boolean hasShaftTowards(
            final LevelReader level,
            final BlockPos pos,
            final BlockState state,
            final Direction face
    ) {
        return face == mechanicalInputFace(state);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(
            final BlockState oldState,
            final BlockState newState
    ) {
        return oldState.getBlock() == newState.getBlock()
                && oldState.getValue(FACING) == newState.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final CollisionContext context
    ) {
        return SimBlockShapes.LASER_POINTER.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final CollisionContext context
    ) {
        return SimBlockShapes.LASER_POINTER.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getBlockSupportShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos
    ) {
        return Shapes.block();
    }

    @Override
    public @NotNull BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<StaticSubspaceCompressorBlockEntity> getBlockEntityClass() {
        return StaticSubspaceCompressorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StaticSubspaceCompressorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STATIC_SUBSPACE_COMPRESSOR.get();
    }
}

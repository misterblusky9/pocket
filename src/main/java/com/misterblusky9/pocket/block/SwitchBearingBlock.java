package com.misterblusky9.pocket.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SwitchBearingBlock extends DirectionalKineticBlock
        implements IBE<SwitchBearingBlockEntity> {
    public static final MapCodec<SwitchBearingBlock> CODEC = simpleCodec(SwitchBearingBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int PULSE_TICKS = 2;

    public SwitchBearingBlock(final Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected int getSignal(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final Direction side
    ) {
        return pocketPower(level, pos, state);
    }

    @Override
    protected int getDirectSignal(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final Direction side
    ) {
        return side == state.getValue(FACING) ? pocketPower(level, pos, state) : 0;
    }

    private static int pocketPower(final BlockGetter level, final BlockPos pos, final BlockState state) {
        return level.getBlockEntity(pos) instanceof final SwitchControllerBlockEntity controller
                ? controller.getRedstonePower()
                : (state.getValue(POWERED) ? 15 : 0);
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    public static void updateRedstoneNeighbours(
            final BlockState state,
            final Level level,
            final BlockPos pos
    ) {
        final Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), block);
    }

    @Override
    public boolean hasShaftTowards(
            final LevelReader world,
            final BlockPos pos,
            final BlockState state,
            final Direction face
    ) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean showCapacityWithAnnotation() {
        return true;
    }

    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        final InteractionResult resultType = super.onWrenched(state, context);
        if (!context.getLevel().isClientSide && resultType.consumesAction()) {
            final BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
            if (be instanceof SwitchBearingBlockEntity bearing) {
                bearing.disassemble();
            }
        }
        return resultType;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack stack,
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final InteractionHand hand,
            final BlockHitResult hitResult
    ) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }
        if (stack.isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            withBlockEntityDo(level, pos, be -> {
                if (be.isRunning()) {
                    be.disassemble();
                    return;
                }
                be.assembleNextTick = true;
            });
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Class<SwitchBearingBlockEntity> getBlockEntityClass() {
        return SwitchBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SwitchBearingBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SWITCH_BEARING.get();
    }
}

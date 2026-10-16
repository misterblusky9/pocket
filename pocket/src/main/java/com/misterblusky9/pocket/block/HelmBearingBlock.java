package com.misterblusky9.pocket.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.api.IDirectionalAnalogOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class HelmBearingBlock extends DirectionalKineticBlock
        implements IBE<HelmBearingBlockEntity>, IDirectionalAnalogOutput {
    public static final MapCodec<HelmBearingBlock> CODEC = simpleCodec(HelmBearingBlock::new);

    public HelmBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean hasShaftTowards(
            LevelReader world,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean showCapacityWithAnnotation() {
        return true;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        InteractionResult result = super.onWrenched(state, context);
        if (!context.getLevel().isClientSide && result.consumesAction()) {
            BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
            if (be instanceof HelmBearingBlockEntity bearing) {
                bearing.disassemble();
            }
        }
        return result;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
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
                } else {
                    be.assembleNextTick = true;
                }
            });
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignalFrom(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction side
    ) {
        HelmBearingBlockEntity be = getBlockEntity(level, pos);
        if (be == null || be.angleInput == null) {
            return 0;
        }

        Direction facing = state.getValue(FACING);
        if (side == facing) {
            return be.held ? 15 : 0;
        }

        int value = be.getAnalogAngleSignal();
        if (value == 0) {
            return 0;
        }

        Direction right = rightSide(facing);
        Direction left = right.getOpposite();

        if (side == right && value > 0) {
            return value;
        }
        if (side == left && value < 0) {
            return -value;
        }
        return 0;
    }

    private static Direction rightSide(Direction facing) {
        if (facing.getAxis().isVertical()) {
            return facing == Direction.UP ? Direction.EAST : Direction.WEST;
        }
        return facing.getClockWise();
    }

    @Override
    public Class<HelmBearingBlockEntity> getBlockEntityClass() {
        return HelmBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HelmBearingBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HELM_BEARING.get();
    }
}

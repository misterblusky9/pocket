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

    public HelmBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
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
        final InteractionResult result = super.onWrenched(state, context);
        if (!context.getLevel().isClientSide && result.consumesAction()) {
            final BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
            if (be instanceof HelmBearingBlockEntity bearing) {
                bearing.disassemble();
            }
        }
        return result;
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
                } else {
                    be.assembleNextTick = true;
                }
            });
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignalFrom(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Direction side
    ) {
        final HelmBearingBlockEntity be = getBlockEntity(level, pos);
        if (be == null || be.angleInput == null) {
            return 0;
        }

        final Direction facing = state.getValue(FACING);
        if (side == facing) {
            return be.held ? 15 : 0;
        }

        if (Math.abs(be.getAngle()) < 0.99F) {
            return 0;
        }

        final float frac = net.minecraft.util.Mth.clamp(
                be.targetAngleToUpdate / be.angleInput.getValue(), -1.0F, 1.0F);
        int value = (int) (frac < 0 ? Math.floor(frac * 15.0F) : Math.ceil(frac * 15.0F));
        value *= (int) be.directionConvert(1.0F);

        final Direction right = rightSide(facing);
        final Direction left = right.getOpposite();

        if (side == right && value > 0) {
            return value;
        }
        if (side == left && value < 0) {
            return -value;
        }
        return 0;
    }

    private static Direction rightSide(final Direction facing) {
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

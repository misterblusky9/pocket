package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimBlockShapes;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlexibleCouplingBlock extends WrenchableDirectionalBlock
        implements IBE<FlexibleCouplingBlockEntity>, BlockSubLevelAssemblyListener, IWrenchable, IRotate {
    public FlexibleCouplingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.FLEXIBLE_COUPLING.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(SimTags.Items.DESTROYS_ROPE)
                || !(level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity shaft)
                || !shaft.isLinked()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            FlexibleCouplingBlockEntity partner = shaft.getPartner();
            if (!player.hasInfiniteMaterials()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.FLEXIBLE_SHAFT.get()));
            }
            shaft.unlink();
            if (partner != null) {
                partner.unlink();
            }
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SimBlockShapes.SMALL_SPRING.get(state.getValue(FACING));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state,
                                   Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                        boolean isMoving) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kbe)) {
            return;
        }
        kbe.preventSpeedUpdate = 0;
        if (oldState.getBlock() != state.getBlock()
                || state.hasBlockEntity() != oldState.hasBlockEntity()
                || getRotationAxis(state) != getRotationAxis(oldState)) {
            return;
        }
        kbe.preventSpeedUpdate = 2;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity shaft ? shaft.getRedstoneOutput() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity shaft) {
            shaft.updateRedstoneInput();
        }
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos,
                                              int flags, int count) {
        if (level.isClientSide()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe) || kbe.preventSpeedUpdate > 0) {
            return;
        }
        kbe.warnOfMovement();
        kbe.clearKineticInformation();
        kbe.updateSpeed = true;
    }

    public static boolean tryAdjust(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity shaft) {
            String error = shaft.tryChangeLengthOrError(level, player.isShiftKeyDown() ? -0.25 : 0.25);
            if (error == null) {
                sendLengthMessage("new_length", SimColors.SUCCESS_LIME, shaft, player);
                return true;
            }
            sendLengthMessage(error, SimColors.NUH_UH_RED, shaft, player);
        }
        return false;
    }

    private static void sendLengthMessage(String suffix, int color,
                                          FlexibleCouplingBlockEntity shaft, Player player) {
        SimLang.translate("spring." + suffix, String.format("%.2f", shaft.desiredLength))
                .color(color).sendStatus(player);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public Class<FlexibleCouplingBlockEntity> getBlockEntityClass() {
        return FlexibleCouplingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FlexibleCouplingBlockEntity> getBlockEntityType() {
        return ModBlockEntities.FLEXIBLE_COUPLING.get();
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel newLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(oldPos) instanceof FlexibleCouplingBlockEntity shaft) {
            shaft.assembling = true;
        }
    }

    @Override
    public void afterMove(ServerLevel oldLevel, ServerLevel newLevel, BlockState state,
                          BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(newPos) instanceof FlexibleCouplingBlockEntity shaft) {
            FlexibleCouplingBlockEntity partner = shaft.getPartner();
            if (partner != null) {
                SubLevel subLevel = Sable.HELPER.getContaining(newLevel, newPos);
                partner.setPartnerPos(newPos, subLevel != null ? subLevel.getUniqueId() : null);
            }
        }
    }

}

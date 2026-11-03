package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

public abstract class BuildHostBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected BuildHostBlock(Properties properties) {
        super(properties);
    }

    public abstract boolean activatedBy(ItemStack stack);

    public abstract int scaffoldSize();

    public abstract BlockState scaffold();

    public abstract Vec3 anchor(BlockState state);

    public abstract Quaterniond orientation(BlockState state);

    protected abstract BlockEntityType<? extends BuildHostBlockEntity> blockEntityType();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        SubLevel selected = Sable.HELPER.getContaining(context.getLevel(), context.getClickedPos());
        return selected != null && context.getLevel().getBlockState(selected.getPlot().getCenterBlock())
                .is(ModBlocks.POCKET_DUMMY.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BuildHostBlockEntity host) || !activatedBy(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player.isShiftKeyDown() || host.isActive()) return ItemInteractionResult.CONSUME;
        if (!player.mayBuild()) return ItemInteractionResult.FAIL;
        return host.assemble(player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof BuildHostBlockEntity host) return host.take(player);
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !moving && !CrossScaleWelds.assemblyMoveActive()
                && level.getBlockEntity(pos) instanceof BuildHostBlockEntity host) host.removeChild();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void afterMove(ServerLevel original, ServerLevel destination, BlockState state,
                          BlockPos oldPos, BlockPos newPos) {
        if (destination.getBlockEntity(newPos) instanceof BuildHostBlockEntity host) host.moved();
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide) return createTickerHelper(type, blockEntityType(), BuildHostBlockEntity::clientTick);
        return createTickerHelper(type, blockEntityType(), BuildHostBlockEntity::tick);
    }
}

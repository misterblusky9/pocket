package com.misterblusky9.pocket.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class SubspaceRecyclerBlock extends KineticBlock
        implements IBE<SubspaceRecyclerBlockEntity>, ICogWheel {
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 6, 16),
            box(2, 6, 2, 14, 16, 14)
    );

    public SubspaceRecyclerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(
            final BlockState state,
            final BlockGetter level,
            final BlockPos pos,
            final CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    public boolean hasShaftTowards(
            final LevelReader level,
            final BlockPos pos,
            final BlockState state,
            final Direction face
    ) {
        return face == Direction.DOWN;
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
        if (!stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        withBlockEntityDo(level, pos, recycler -> {
            boolean emptyOutput = true;
            IItemHandlerModifiable inv = recycler.outputInv;
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                final ItemStack stackInSlot = inv.getStackInSlot(slot);
                if (!stackInSlot.isEmpty()) emptyOutput = false;
                player.getInventory().placeItemBackInInventory(stackInSlot);
                inv.setStackInSlot(slot, ItemStack.EMPTY);
            }

            if (emptyOutput) {
                inv = recycler.inputInv;
                for (int slot = 0; slot < inv.getSlots(); slot++) {
                    player.getInventory().placeItemBackInInventory(inv.getStackInSlot(slot));
                    inv.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }

            recycler.setChanged();
            recycler.sendData();
        });

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void updateEntityAfterFallOn(final BlockGetter level, final Entity entity) {
        super.updateEntityAfterFallOn(level, entity);

        if (entity.level().isClientSide) return;
        if (!(entity instanceof final ItemEntity itemEntity)) return;
        if (!entity.isAlive()) return;

        SubspaceRecyclerBlockEntity recycler = null;
        for (final BlockPos pos : Iterate.hereAndBelow(entity.blockPosition()))
            if (recycler == null) recycler = getBlockEntity(level, pos);

        if (recycler == null) return;

        final IItemHandler capability = recycler.getLevel()
                .getCapability(Capabilities.ItemHandler.BLOCK, recycler.getBlockPos(), null);
        if (capability == null) return;

        final ItemStack remainder = capability.insertItem(0, itemEntity.getItem(), false);
        if (remainder.isEmpty()) itemEntity.discard();
        if (remainder.getCount() < itemEntity.getItem().getCount()) itemEntity.setItem(remainder);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
        return false;
    }

    @Override
    public Class<SubspaceRecyclerBlockEntity> getBlockEntityClass() {
        return SubspaceRecyclerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SubspaceRecyclerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SUBSPACE_RECYCLER.get();
    }
}

package com.misterblusky9.pocket.block;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class CopycatFacadeBlock extends CopycatBlock {
    private static final int MAX_CONNECTED = 4096;

    public CopycatFacadeBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack stack,
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final InteractionHand hand,
            final BlockHitResult hit
    ) {
        if (player != null && player.mayBuild() && !stack.isEmpty()
                && level.getBlockEntity(pos) instanceof final CopycatBlockEntity origin
                && origin.hasCustomMaterial()) {
            BlockState accepted = getAcceptedBlockState(level, pos, stack, hit.getDirection());
            if (accepted != null) {
                accepted = prepareMaterial(level, pos, state, player, hand, hit, accepted);
            }
            if (accepted != null && origin.getMaterial().is(accepted.getBlock())) {
                if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
                final int filled = spreadMaterial(level, pos, hit, accepted, stack, player);
                if (filled > 0) {
                    level.playSound(null, pos, accepted.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.85F);
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private int spreadMaterial(
            final Level level,
            final BlockPos pos,
            final BlockHitResult hit,
            final BlockState material,
            final ItemStack stack,
            final Player player
    ) {
        final Direction[] nearby = Direction.values();
        Arrays.sort(nearby, Comparator.comparingDouble(direction ->
                Vec3.atCenterOf(pos.relative(direction)).distanceToSqr(hit.getLocation())));
        for (final Direction direction : nearby) {
            final BlockPos next = pos.relative(direction);
            if (canFill(level, next) && fill(level, next, material, stack, player)) return 1;
        }

        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        frontier.add(pos);
        visited.add(pos);
        int filled = 0;
        while (!frontier.isEmpty() && visited.size() <= MAX_CONNECTED) {
            final BlockPos current = frontier.removeFirst();
            for (final Direction direction : Direction.values()) {
                final BlockPos next = current.relative(direction);
                if (!visited.add(next) || !level.getBlockState(next).is(this)) continue;
                if (!(level.getBlockEntity(next) instanceof final CopycatBlockEntity copycat)) continue;
                if (copycat.hasCustomMaterial() && !copycat.getMaterial().is(material.getBlock())) continue;
                if (!copycat.hasCustomMaterial()) {
                    if (!fill(level, next, material, stack, player)) return filled;
                    filled++;
                }
                if (visited.size() < MAX_CONNECTED) frontier.addLast(next);
            }
        }
        return filled;
    }

    private boolean canFill(final Level level, final BlockPos pos) {
        return level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof final CopycatBlockEntity copycat
                && !copycat.hasCustomMaterial();
    }

    private static boolean fill(
            final Level level,
            final BlockPos pos,
            final BlockState material,
            final ItemStack stack,
            final Player player
    ) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof final CopycatBlockEntity copycat)
                || copycat.hasCustomMaterial()) return false;
        copycat.setMaterial(material);
        copycat.setConsumedItem(stack);
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return ModBlockEntities.COPYCAT_FACADE.get();
    }

    @Override
    public boolean canConnectTexturesToward(
            final BlockAndTintGetter reader,
            final BlockPos fromPos,
            final BlockPos toPos,
            final BlockState state
    ) {
        return true;
    }

    @Override
    public boolean canFaceBeOccluded(final BlockState state, final Direction face) {
        return true;
    }

    @Override
    public boolean supportsExternalFaceHiding(final BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(
            final BlockGetter level,
            final BlockPos pos,
            final BlockState state,
            final BlockState neighborState,
            final Direction dir
    ) {
        final BlockState material = getMaterial(level, pos);
        final BlockState neighbour = neighborState.getBlock() instanceof CopycatBlock
                ? getMaterial(level, pos.relative(dir))
                : neighborState;
        return material.isSolidRender(level, pos) || neighbour.skipRendering(material, dir.getOpposite());
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType pathComputationType) {
        return false;
    }
}

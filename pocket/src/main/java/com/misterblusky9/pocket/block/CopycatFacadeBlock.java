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

    public CopycatFacadeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (player != null && player.mayBuild() && !stack.isEmpty()
                && level.getBlockEntity(pos) instanceof CopycatBlockEntity origin
                && origin.hasCustomMaterial()) {
            BlockState accepted = getAcceptedBlockState(level, pos, stack, hit.getDirection());
            if (accepted != null) {
                accepted = prepareMaterial(level, pos, state, player, hand, hit, accepted);
            }
            if (accepted != null && origin.getMaterial().is(accepted.getBlock())) {
                if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
                int filled = spreadMaterial(level, pos, hit, accepted, stack, player);
                if (filled > 0) {
                    level.playSound(null, pos, accepted.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.85F);
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private int spreadMaterial(
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            BlockState material,
            ItemStack stack,
            Player player
    ) {
        Direction[] nearby = Direction.values();
        Arrays.sort(nearby, Comparator.comparingDouble(direction ->
                Vec3.atCenterOf(pos.relative(direction)).distanceToSqr(hit.getLocation())));
        for (Direction direction : nearby) {
            BlockPos next = pos.relative(direction);
            if (canFill(level, next) && fill(level, next, material, stack, player)) return 1;
        }

        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        frontier.add(pos);
        visited.add(pos);
        int filled = 0;
        while (!frontier.isEmpty() && visited.size() <= MAX_CONNECTED) {
            BlockPos current = frontier.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next) || !level.getBlockState(next).is(this)) continue;
                if (!(level.getBlockEntity(next) instanceof CopycatBlockEntity copycat)) continue;
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

    private boolean canFill(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat
                && !copycat.hasCustomMaterial();
    }

    private static boolean fill(
            Level level,
            BlockPos pos,
            BlockState material,
            ItemStack stack,
            Player player
    ) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat)
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
            BlockAndTintGetter reader,
            BlockPos fromPos,
            BlockPos toPos,
            BlockState state
    ) {
        return true;
    }

    @Override
    public boolean canFaceBeOccluded(BlockState state, Direction face) {
        return true;
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            BlockState neighborState,
            Direction dir
    ) {
        BlockState material = getMaterial(level, pos);
        BlockState neighbour = neighborState.getBlock() instanceof CopycatBlock
                ? getMaterial(level, pos.relative(dir))
                : neighborState;
        return material.isSolidRender(level, pos) || neighbour.skipRendering(material, dir.getOpposite());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}

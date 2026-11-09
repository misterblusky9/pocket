package com.misterblusky9.pocket.build;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PocketDummyBlock extends Block {
    private static final ThreadLocal<Integer> EDITING = ThreadLocal.withInitial(() -> 0);

    public static void beginEdit() { EDITING.set(EDITING.get() + 1); }

    public static void endEdit() {
        int depth = EDITING.get() - 1;
        if (depth == 0) EDITING.remove();
        else EDITING.set(depth);
    }

    public static boolean editing() { return EDITING.get() > 0; }

    public static final MapCodec<PocketDummyBlock> CODEC = simpleCodec(PocketDummyBlock::new);

    public PocketDummyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!(Sable.HELPER.getContaining(server, pos) instanceof ServerSubLevel child))
            return InteractionResult.PASS;
        BuildHostBlockEntity host = BuildSites.hostOf(child);
        return host == null ? InteractionResult.PASS : host.take(player);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel child) || !BuildSites.isAttached(child))
            level.removeBlock(pos, false);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }
}

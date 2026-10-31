package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlock;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3d;

public final class SlimeWeldMerge {
    public static boolean eligible(Level level, WeldRecord record) {
        if (level == null || record == null || record.worldAnchored()) return false;
        if (record.smallSubLevel().equals(record.bigSubLevel())) return false;
        SubLevel small = Sable.HELPER.getContaining(level, record.smallPos());
        SubLevel big = Sable.HELPER.getContaining(level, record.bigPos());
        if (small == null || big == null || small.isRemoved() || big.isRemoved()
                || !record.smallSubLevel().equals(small.getUniqueId())
                || !record.bigSubLevel().equals(big.getUniqueId())
                || !Pym.scale().isSettled(small) || !Pym.scale().isSettled(big)
                || !ScaleBounds.same(Pym.scale().of(small), Pym.scale().of(big))) return false;

        Direction a = record.smallFacing();
        Direction b = record.bigFacing();
        if (a.getAxis().isHorizontal() != b.getAxis().isHorizontal()
                || (a.getAxis().isVertical() && a == b)) return false;

        BlockPos aGlue = record.smallPos().relative(a);
        BlockPos bGlue = record.bigPos().relative(b);
        if (aGlue.equals(bGlue) || !support(level, record.smallPos(), a)
                || !support(level, record.bigPos(), b)
                || !level.getBlockState(aGlue).canBeReplaced()
                || !level.getBlockState(bGlue).canBeReplaced()
                || Sable.HELPER.getContaining(level, aGlue) != small
                || Sable.HELPER.getContaining(level, bGlue) != big) return false;

        double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level,
                record.smallPos().getCenter(), record.bigPos().getCenter());
        double range = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF();
        if (!Double.isFinite(distanceSquared) || distanceSquared > range * range) return false;

        return true;
    }

    private static boolean support(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.getBlockSupportShape(level, pos).getFaceShape(facing).isEmpty()
                && MergingGlueBlock.canAttach(level, pos.relative(facing), facing.getOpposite());
    }

    public static void convert(ServerPlayer player, WeldRecord record, InteractionHand hand) {
        if (player == null || player.isSpectator() || record == null
                || !player.getItemInHand(hand).is(Items.SLIME_BALL)) return;
        ServerLevel level = player.serverLevel();
        WeldStore store = WeldStore.get(level);
        if (WeldMergeMigration.isQueued(level, record.smallSubLevel(), record.bigSubLevel())
                || store.get(record.weldId()) == null || !eligible(level, record)) return;
        if (!(Sable.HELPER.getContaining(level, record.smallPos()) instanceof ServerSubLevel)
                || !(Sable.HELPER.getContaining(level, record.bigPos()) instanceof ServerSubLevel)) return;

        BlockPos smallGlue = record.smallPos().relative(record.smallFacing());
        BlockPos bigGlue = record.bigPos().relative(record.bigFacing());
        if (!mayPlace(player, level, hand, smallGlue, record.smallFacing())
                || !mayPlace(player, level, hand, bigGlue, record.bigFacing())) return;

        MergingGlueBlockEntity controller = place(level, smallGlue, bigGlue, record.smallFacing());
        if (controller == null) return;
        MergingGlueBlockEntity partner = place(level, bigGlue, smallGlue, record.bigFacing());
        if (partner == null) {
            clear(level, smallGlue);
            return;
        }
        if (!record.smallSubLevel().equals(Sable.HELPER.getContaining(level, smallGlue) == null
                    ? null : Sable.HELPER.getContaining(level, smallGlue).getUniqueId())
                || !record.bigSubLevel().equals(Sable.HELPER.getContaining(level, bigGlue) == null
                    ? null : Sable.HELPER.getContaining(level, bigGlue).getUniqueId())) {
            clear(level, smallGlue);
            clear(level, bigGlue);
            return;
        }

        try {
            controller.startControlling(partner);
            if (!controller.isController()) {
                clear(level, smallGlue);
                clear(level, bigGlue);
                return;
            }
        } catch (RuntimeException failure) {
            clear(level, smallGlue);
            clear(level, bigGlue);
            return;
        }
        WeldRuntime.drop(record.weldId());
        store.remove(record.weldId());
        CrossScaleWeldSync.broadcast(level);
        WeldMergeMigration.remember(level, record.smallSubLevel(), record.bigSubLevel());
        player.awardStat(Stats.ITEM_USED.get(Items.SLIME_BALL));
    }

    private static boolean mayPlace(ServerPlayer player, ServerLevel level, InteractionHand hand,
                                    BlockPos pos, Direction facing) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, facing, player.getItemInHand(hand))) return false;
        BlockState state = SimBlocks.MERGING_GLUE.getDefaultState()
                .setValue(MergingGlueBlock.FACING, facing);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        try {
            level.setBlock(pos, state, Block.UPDATE_NONE);
            return !EventHooks.onBlockPlace(player, snapshot, facing);
        } finally {
            snapshot.restore(Block.UPDATE_NONE);
        }
    }

    private static MergingGlueBlockEntity place(ServerLevel level, BlockPos pos,
                                                BlockPos partner, Direction facing) {
        if (!level.getBlockState(pos).canBeReplaced()) return null;
        BlockState state = SimBlocks.MERGING_GLUE.getDefaultState()
                .setValue(MergingGlueBlock.FACING, facing);
        if (!level.setBlockAndUpdate(pos, state)) return null;
        if (!(level.getBlockEntity(pos) instanceof MergingGlueBlockEntity glue)) {
            clear(level, pos);
            return null;
        }
        glue.setPartnerPos(partner);
        glue.notifyUpdate();
        return glue;
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(SimBlocks.MERGING_GLUE)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    public static boolean aimedAt(ServerPlayer player, WeldRecord record, BlockPos pos, Vec3 point) {
        if (player == null || record == null || pos == null || point == null) return false;
        ServerLevel level = player.serverLevel();
        SubLevel owner = Sable.HELPER.getContaining(level, pos);
        if (owner == null || !record.touches(owner.getUniqueId())) return false;
        WeldRecord aimed = CrossScaleWelds.weldNear(level, point);
        if (aimed == null || !record.weldId().equals(aimed.weldId())) return false;
        Vector3d world = owner.logicalPose().transformPosition(new Vector3d(point.x, point.y, point.z));
        Vec3 eye = player.getEyePosition();
        Vec3 delta = new Vec3(world.x - eye.x, world.y - eye.y, world.z - eye.z);
        double length = delta.length();
        double reach = player.blockInteractionRange() + 1.0D;
        if (!Double.isFinite(length) || length > reach || length < 0.0001D) return false;
        double forward = delta.dot(player.getLookAngle());
        double missSquared = Math.max(0.0D, delta.lengthSqr() - forward * forward);
        return forward > 0.0D && missSquared < 0.55D * 0.55D;
    }

    private SlimeWeldMerge() {}
}

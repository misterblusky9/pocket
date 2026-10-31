package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PocketKnifeSplit {
    private static final int MAX_CUT_PIECES = 256;

    public static void cut(ServerPlayer player, UUID id, BlockPos origin,
                           InteractionHand hand, BlockPos min, BlockPos max) {
        ServerLevel level = player.serverLevel();
        ItemStack knife = player.getItemInHand(hand);
        if (!knife.is(ModItems.POCKET_KNIFE.get()) || player.isSpectator()) return;
        if (!(Sable.HELPER.getContaining(level, origin) instanceof ServerSubLevel source)
                || source.isRemoved() || source.getPlot() == null || !source.getUniqueId().equals(id)) return;
        if (!Pym.scale().isSettled(source)) {
            refuse(player, "Cannot cut while a craft is resizing");
            return;
        }
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) return;
        AABB selection = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
        double reach = player.blockInteractionRange() + 1.0D;
        if (!inReach(player, source, selection, reach)
                || !aimsAtSelection(player, source, selection)) return;

        int maxBlocks = DeviceRanges.knifeMaxBlocks();
        List<BlockPos> blocks = gather(source, maxBlocks);
        if (blocks == null) return;
        if (maxBlocks > 0 && blocks.size() > maxBlocks) {
            refuse(player, "Block count is too large!");
            return;
        }
        List<BlockPos> selected = new ArrayList<>();
        List<BlockPos> retained = new ArrayList<>();
        for (BlockPos pos : blocks) {
            (inside(pos, min, max) ? selected : retained).add(pos);
        }
        if (selected.isEmpty()) {
            refuse(player, "Nothing selected");
            return;
        }
        if (retained.isEmpty()) {
            refuse(player, "The whole sublevel is selected");
            return;
        }
        Set<BlockPos> anchors = new HashSet<>();
        for (WeldRecord weld : WeldStore.get(level).touching(id)) {
            BlockPos anchor = id.equals(weld.smallSubLevel()) ? weld.smallPos() : weld.bigPos();
            if (anchor == null || level.getBlockState(anchor).isAir()
                    || Sable.HELPER.getContaining(level, anchor) != source
                    || inside(anchor, min, max)) {
                refuse(player, "Cut conflicting welds first");
                return;
            }
            anchors.add(anchor);
        }

        List<List<BlockPos>> pieces = components(selected);
        List<List<BlockPos>> remainder = components(retained);
        List<BlockPos> keep = null;
        if (anchors.isEmpty()) {
            for (List<BlockPos> part : remainder) {
                if (keep == null || part.size() > keep.size()) keep = part;
            }
        }
        for (List<BlockPos> part : remainder) {
            if (part != keep && part.stream().noneMatch(anchors::contains)) pieces.add(part);
        }
        if (pieces.size() > MAX_CUT_PIECES) {
            refuse(player, "Too many pieces would result");
            return;
        }
        int maxMoved = DeviceRanges.knifeMaxMovedBlocks();
        if (maxMoved > 0 && pieces.stream().mapToLong(List::size).sum() > maxMoved) {
            refuse(player, "Too many blocks on the cut side");
            return;
        }
        for (List<BlockPos> part : pieces) {
            for (BlockPos pos : part) {
                if (!ToolPermissions.mayAlter(player, level, pos)) {
                    refuse(player, "You cannot cut here");
                    return;
                }
            }
        }

        List<CutPiece> plan = new ArrayList<>(pieces.size());
        for (List<BlockPos> part : pieces) {
            BoundingBox3i bounds = BoundingBox3i.from(part);
            if (bounds == null || level.getBlockState(part.getFirst()).isAir()
                    || Sable.HELPER.getContaining(level, part.getFirst()) != source) {
                refuse(player, "Selection target changed; select again");
                return;
            }
            plan.add(new CutPiece(part, bounds.expand(1, 1, 1)));
        }

        Vector3d sound = source.logicalPose().transformPosition(
                new Vector3d(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D));
        boolean cut = false;
        boolean failed = false;
        for (CutPiece piece : plan) {
            try {
                ServerSubLevel child = SubLevelAssemblyHelper.assembleBlocks(
                        level, piece.blocks().getFirst(), piece.blocks(), piece.bounds());
                if (child == null || child.isRemoved()) {
                    failed = true;
                    break;
                }
                cut = true;
            } catch (RuntimeException exception) {
                Sable.LOGGER.error("Pocket knife failed to assemble a cut piece from sublevel {}", id, exception);
                failed = true;
                break;
            }
        }
        if (failed) refuse(player, "Cut incomplete, not all pieces could be assembled");
        if (!cut) return;
        knife.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        level.playSound(null, sound.x, sound.y, sound.z,
                SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.1F);
    }

    private static List<List<BlockPos>> components(List<BlockPos> blocks) {
        Set<BlockPos> remaining = new HashSet<>(blocks);
        List<List<BlockPos>> parts = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : blocks) {
            if (!remaining.remove(start)) continue;
            List<BlockPos> part = new ArrayList<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                part.add(pos);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (distance == 0 || distance == 3) continue;
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            if (remaining.remove(neighbor)) queue.addLast(neighbor);
                        }
                    }
                }
            }
            parts.add(part);
        }
        return parts;
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static boolean inReach(ServerPlayer player, ServerSubLevel source, AABB box, double reach) {
        var eye = player.getEyePosition();
        Vector3d local = source.logicalPose().transformPositionInverse(new Vector3d(eye.x, eye.y, eye.z));
        Vector3d nearest = new Vector3d(
                Math.clamp(local.x, box.minX, box.maxX),
                Math.clamp(local.y, box.minY, box.maxY),
                Math.clamp(local.z, box.minZ, box.maxZ));
        source.logicalPose().transformPosition(nearest);
        return eye.distanceToSqr(nearest.x, nearest.y, nearest.z) <= reach * reach;
    }

    private static boolean aimsAtSelection(ServerPlayer player, ServerSubLevel source, AABB box) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = eye.add(player.getLookAngle().scale(player.blockInteractionRange() * 5.0D));
        Vector3d localEye = source.logicalPose().transformPositionInverse(
                new Vector3d(eye.x, eye.y, eye.z));
        Vector3d localTarget = source.logicalPose().transformPositionInverse(
                new Vector3d(target.x, target.y, target.z));
        Vec3 from = new Vec3(localEye.x, localEye.y, localEye.z);
        Vec3 to = new Vec3(localTarget.x, localTarget.y, localTarget.z);
        AABB forgiving = box.inflate(0.125D);
        return forgiving.contains(from) || forgiving.clip(from, to).isPresent();
    }

    private record CutPiece(List<BlockPos> blocks, BoundingBox3i bounds) {}

    private static List<BlockPos> gather(ServerSubLevel source, int limit) {
        BoundingBox3ic bounds = source.getPlot().getBoundingBox();
        if (bounds == null) return null;
        List<BlockPos> blocks = new ArrayList<>();
        for (PlotChunkHolder holder : source.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            ChunkPos chunkPos = chunk.getPos();
            int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) continue;
                int sectionY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int y = Math.max(bounds.minY(), sectionY); y <= Math.min(bounds.maxY(), sectionY + 15); y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            if (section.getBlockState(x & 15, y & 15, z & 15).isAir()) continue;
                            blocks.add(new BlockPos(x, y, z));
                            if (limit > 0 && blocks.size() > limit) return blocks;
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static void refuse(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private PocketKnifeSplit() {}
}

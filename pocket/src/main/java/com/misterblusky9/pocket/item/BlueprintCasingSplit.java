package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BlueprintCasingSplit {
    static boolean isCasing(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.BLUEPRINT_CASING.get());
    }

    static void cut(ServerLevel level, ServerSubLevel source, BlockPos clicked, Direction face,
                    Player player, ItemStack knife, InteractionHand hand) {
        if (player == null || source.isRemoved() || source.getPlot() == null || face == null
                || !player.canInteractWithBlock(clicked, 1.0D)
                || Sable.HELPER.getContaining(level, clicked) != source) return;
        if (!Pym.scale().isSettled(source)) {
            refuse(player, "Cannot cut while resizing");
            return;
        }

        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int plane = clicked.get(axis) + (positive ? 1 : 0);
        List<BlockPos> moved = new ArrayList<>();
        boolean kept = gather(source, axis, plane, positive, moved);
        if (!kept || moved.isEmpty()) {
            refuse(player, "Nothing to cut here");
            return;
        }

        UUID id = source.getUniqueId();
        for (WeldRecord weld : WeldStore.get(level).touching(id)) {
            BlockPos anchor = id.equals(weld.smallSubLevel()) ? weld.smallPos() : weld.bigPos();
            if (anchor == null || level.getBlockState(anchor).isAir()
                    || Sable.HELPER.getContaining(level, anchor) != source
                    || (anchor.get(axis) >= plane) == positive) {
                refuse(player, "Cut conflicting welds first");
                return;
            }
        }

        BoundingBox3i bounds = BoundingBox3i.from(moved);
        if (bounds == null) return;
        Vector3d sound = source.logicalPose().transformPosition(
                new Vector3d(clicked.getX() + 0.5D, clicked.getY() + 0.5D, clicked.getZ() + 0.5D));

        SubLevelAssemblyHelper.assembleBlocks(level, moved.getFirst(), moved, bounds.expand(1, 1, 1));

        knife.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        level.playSound(null, sound.x, sound.y, sound.z,
                SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.1F);
    }

    private static boolean gather(ServerSubLevel source, Direction.Axis axis, int plane,
                                  boolean farIsUpper, List<BlockPos> moved) {
        BoundingBox3ic bounds = source.getPlot().getBoundingBox();
        if (bounds == null) return false;

        boolean kept = false;
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
                int minY = Math.max(bounds.minY(), sectionY);
                int maxY = Math.min(bounds.maxY(), sectionY + 15);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            if (section.getBlockState(x & 15, y & 15, z & 15).isAir()) continue;
                            BlockPos pos = new BlockPos(x, y, z);
                            if ((pos.get(axis) >= plane) == farIsUpper) moved.add(pos);
                            else kept = true;
                        }
                    }
                }
            }
        }
        return kept;
    }

    private static void refuse(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private BlueprintCasingSplit() {}
}

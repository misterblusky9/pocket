package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.network.HatNativeContraptionsPayload;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.contraption.PropellerBearingContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HatNativeContraptions {
    private static final String ENTRIES = "entries";
    private static final Map<UUID, Set<UUID>> KNOWN = new HashMap<>();
    private static final Map<UUID, Integer> RESUME = new HashMap<>();
    private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

    private record Found(ServerSubLevel part, AbstractContraptionEntity entity) {}

    public static void started(UUID token) {
        RESUME.put(token, 40);
        KNOWN.remove(token);
        LAST_TICK.remove(token);
    }

    public static void forget(UUID token) {
        RESUME.remove(token);
        KNOWN.remove(token);
        LAST_TICK.remove(token);
    }

    private static List<ServerSubLevel> parts(ServerSubLevel root) {
        List<ServerSubLevel> parts = new ArrayList<>();
        parts.add(root);
        if (Hosted.isRoot(root)) parts.addAll(Hosted.members(root));
        else parts.addAll(Detached.children(root));
        return parts;
    }

    private static List<Found> find(ServerSubLevel root) {
        List<Found> found = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        ServerLevel level = root.getLevel();
        for (ServerSubLevel part : parts(root)) {
            AABB area = null;
            for (PlotChunkHolder holder : part.getPlot().getLoadedChunks()) {
                ChunkPos at = holder.getChunk().getPos();
                AABB column = new AABB(at.getMinBlockX(), level.getMinBuildHeight(), at.getMinBlockZ(),
                        at.getMaxBlockX() + 1, level.getMaxBuildHeight(), at.getMaxBlockZ() + 1);
                area = area == null ? column : area.minmax(column);
            }
            if (area == null) continue;
            for (AbstractContraptionEntity entity : level.getEntities(
                    EntityTypeTest.forClass(AbstractContraptionEntity.class), area,
                    candidate -> candidate.isAlive() && candidate.isReadyForRender()
                            && candidate.getContraption() != null
                            && Sable.HELPER.getContaining(candidate) == part)) {
                if (seen.add(entity.getUUID())) found.add(new Found(part, entity));
            }
        }
        return found;
    }

    private static CompoundTag entry(Found found, boolean full) {
        AbstractContraptionEntity entity = found.entity();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("uuid", entity.getUUID());
        entry.putUUID("part", found.part().getUniqueId());
        entry.putDouble("x", entity.getX());
        entry.putDouble("y", entity.getY());
        entry.putDouble("z", entity.getZ());
        if (entity instanceof ControlledContraptionEntity controlled) {
            entry.putBoolean("controlled", true);
            entry.putFloat("angle", controlled.getAngle(1.0F));
        }
        if (entity instanceof PropellerBearingContraptionEntity propeller) {
            Direction facing = propeller.getBearingEntity() == null ? propeller.direction
                    : propeller.getBearingEntity().getBlockState().getValue(BearingBlock.FACING);
            entry.putInt("aero_facing", facing.get3DDataValue());
            entry.putFloat("aero_qx", propeller.tiltQuat.x());
            entry.putFloat("aero_qy", propeller.tiltQuat.y());
            entry.putFloat("aero_qz", propeller.tiltQuat.z());
            entry.putFloat("aero_qw", propeller.tiltQuat.w());
        }
        if (full) {
            CompoundTag data = entity.saveWithoutId(new CompoundTag());
            data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            entry.put("entity", data);
        }
        return entry;
    }

    public static CompoundTag snapshot(ServerSubLevel root) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (Found found : find(root)) entries.add(entry(found, true));
        tag.put(ENTRIES, entries);
        return tag;
    }

    public static boolean publish(UUID token, ServerSubLevel root) {
        if (!Hosted.isRoot(root)) return false;
        long tick = root.getLevel().getGameTime();
        if (LAST_TICK.getOrDefault(token, Long.MIN_VALUE) == tick) return false;
        LAST_TICK.put(token, tick);
        int resume = RESUME.getOrDefault(token, 0);
        if (resume > 0) {
            if ((resume % 5) == 0) resumeBearings(root);
            if (resume == 1) RESUME.remove(token);
            else RESUME.put(token, resume - 1);
        }

        Set<UUID> before = KNOWN.getOrDefault(token, Set.of());
        Set<UUID> after = new HashSet<>();
        ListTag entries = new ListTag();
        for (Found found : find(root)) {
            UUID id = found.entity().getUUID();
            after.add(id);
            entries.add(entry(found, !before.contains(id)));
        }
        boolean changed = !after.equals(before);
        KNOWN.put(token, after);
        if (root.getTrackingPlayers().isEmpty()) return changed;
        CompoundTag tag = new CompoundTag();
        tag.put(ENTRIES, entries);
        HatNativeContraptionsPayload payload = new HatNativeContraptionsPayload(token, tag);
        for (UUID id : root.getTrackingPlayers()) {
            if (root.getLevel().getPlayerByUUID(id) instanceof ServerPlayer viewer)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
        return changed;
    }

    private static void resumeBearings(ServerSubLevel root) {
        for (ServerSubLevel part : parts(root)) {
            for (PlotChunkHolder holder : part.getPlot().getLoadedChunks()) {
                for (BlockEntity blockEntity : List.copyOf(holder.getChunk().getBlockEntities().values())) {
                    if (blockEntity.isRemoved()) continue;
                    if (blockEntity instanceof MechanicalBearingBlockEntity bearing) {
                        if (bearing.getMovedContraption() != null) continue;
                        if (!(bearing.getBlockState().getBlock() instanceof BearingBlock)) continue;
                        Direction facing = bearing.getBlockState().getValue(BearingBlock.FACING);
                        BlockPos ahead = bearing.getBlockPos().relative(facing);
                        if (part.getLevel().getBlockState(ahead).isAir()) continue;
                        if (!(bearing instanceof WindmillBearingBlockEntity) && bearing.getSpeed() == 0) continue;
                        if (bearing.isRunning()) bearing.disassemble();
                        bearing.assemble();
                    } else if (blockEntity instanceof LinearActuatorBlockEntity actuator
                            && actuator.movedContraption == null && actuator.getSpeed() != 0) {
                        actuator.running = false;
                        actuator.assembleNextTick = true;
                    }
                }
            }
        }
    }

    private HatNativeContraptions() {}
}

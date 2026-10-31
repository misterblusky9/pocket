package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.debug.PocketTrace;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WeldMergeMigration {
    private static final long EXPIRY_TICKS = 100L;
    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<UUID>> MOVING = new WeakHashMap<>();

    private record Pending(UUID first, UUID second, long deadline) {
        boolean matches(UUID a, UUID b) {
            return first.equals(a) && second.equals(b) || first.equals(b) && second.equals(a);
        }
    }

    private record Remap(UUID id, WeldRecord record) {}

    public record Move(ServerLevel level, SubLevel sourceEntity, UUID source, UUID destination,
                       List<Remap> remaps, List<UUID> internal) {}

    public static void remember(ServerLevel level, UUID first, UUID second) {
        if (level == null || first == null || second == null) return;
        List<Pending> entries = PENDING.computeIfAbsent(level, ignored -> new ArrayList<>());
        entries.removeIf(p -> p.deadline() < level.getGameTime() || p.matches(first, second));
        entries.add(new Pending(first, second, level.getGameTime() + EXPIRY_TICKS));
    }

    public static boolean isQueued(ServerLevel level, UUID a, UUID b) {
        List<Pending> entries = PENDING.get(level);
        if (entries == null) return false;
        entries.removeIf(p -> p.deadline() < level.getGameTime());
        return entries.stream().anyMatch(p -> p.matches(a, b));
    }

    public static boolean isMoving(ServerLevel level, UUID id) {
        Set<UUID> moving = MOVING.get(level);
        return moving != null && moving.contains(id);
    }

    public static boolean isMoving(ServerLevel level) {
        Set<UUID> moving = MOVING.get(level);
        return moving != null && !moving.isEmpty();
    }

    public static Move begin(Level rawLevel, SubLevel source, BlockPos sourceAnchor,
                             BlockPos destinationAnchor, Rotation rotation) {
        if (!(rawLevel instanceof ServerLevel level) || source == null || source.isRemoved()
                || sourceAnchor == null || destinationAnchor == null || rotation == null) return null;
        SubLevel destination = Sable.HELPER.getContaining(level, destinationAnchor);
        if (destination == null || destination.isRemoved() || destination == source) return null;
        UUID removedId = source.getUniqueId();
        UUID survivorId = destination.getUniqueId();
        List<Pending> entries = PENDING.get(level);
        if (entries == null) return null;
        entries.removeIf(p -> p.deadline() < level.getGameTime());
        if (!entries.removeIf(p -> p.matches(removedId, survivorId))) return null;

        int turns = rotation == Rotation.NONE ? 0 : (4 - rotation.ordinal());
        SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                sourceAnchor, destinationAnchor, turns, rotation, level);
        Quaterniond rotate = switch (rotation) {
            case NONE -> new Quaterniond();
            case CLOCKWISE_90 -> new Quaterniond().rotationY(-Math.PI / 2.0D);
            case CLOCKWISE_180 -> new Quaterniond().rotationY(Math.PI);
            case COUNTERCLOCKWISE_90 -> new Quaterniond().rotationY(Math.PI / 2.0D);
        };
        List<Remap> remaps = new ArrayList<>();
        List<UUID> internal = new ArrayList<>();
        for (WeldRecord record : WeldStore.get(level).touching(removedId)) {
            if (record.connects(removedId, survivorId)) {
                internal.add(record.weldId());
                continue;
            }
            boolean movedSmall = removedId.equals(record.smallSubLevel());
            BlockPos pos = movedSmall ? record.smallPos() : record.bigPos();
            BlockPos mapped = transform.apply(pos);
            if (mapped == null) continue;
            Vector3d anchor = record.anchorFor(movedSmall);
            Vec3 mappedAnchor = transform.apply(new Vec3(anchor.x, anchor.y, anchor.z));
            Direction facing = rotation.rotate(record.facingFor(movedSmall));
            Quaterniond orientation = movedSmall
                    ? new Quaterniond(rotate).mul(record.orientation())
                    : new Quaterniond(record.orientation()).mul(new Quaterniond(rotate).invert());
            WeldRecord updated = new WeldRecord(
                    record.weldId(),
                    movedSmall ? survivorId : record.smallSubLevel(),
                    movedSmall ? record.bigSubLevel() : survivorId,
                    movedSmall ? mapped : record.smallPos(),
                    movedSmall ? record.bigPos() : mapped,
                    movedSmall ? facing : record.smallFacing(),
                    movedSmall ? record.bigFacing() : facing,
                    movedSmall ? new Vector3d(mappedAnchor.x, mappedAnchor.y, mappedAnchor.z)
                            : record.anchorFor(true),
                    movedSmall ? record.anchorFor(false)
                            : new Vector3d(mappedAnchor.x, mappedAnchor.y, mappedAnchor.z),
                    record.smallSpan(), record.bigSpan(), orientation.normalize());
            remaps.add(new Remap(record.weldId(), updated));
        }
        MOVING.computeIfAbsent(level, ignored -> new HashSet<>()).add(removedId);
        for (Remap remap : remaps) WeldRuntime.drop(remap.id());
        for (UUID id : internal) WeldRuntime.drop(id);
        return new Move(level, source, removedId, survivorId, List.copyOf(remaps), List.copyOf(internal));
    }

    public static void finish(Move move, boolean completed) {
        if (move == null) return;
        ServerLevel level = move.level();
        try {
            WeldStore store = WeldStore.get(level);
            if (!completed) {
                PocketTrace.logger().warn("[Pocket] Slime merge failed while transferring welds from {}", move.source());
                if (move.sourceEntity().isRemoved()) {
                    for (WeldRecord record : store.removeAllTouching(move.source())) {
                        WeldRuntime.drop(record.weldId());
                    }
                    CrossScaleWelds.invalidate(level);
                    CrossScaleWeldSync.broadcast(level);
                }
                return;
            }
            boolean changed = false;
            List<UUID> rebind = new ArrayList<>();
            for (UUID id : move.internal()) {
                if (store.remove(id) != null) changed = true;
            }
            for (Remap remap : move.remaps()) {
                WeldRecord old = store.get(remap.id());
                if (old == null) continue;
                WeldRecord next = remap.record();
                BlockPos movedPos = next.smallSubLevel().equals(move.destination())
                        && old.smallSubLevel().equals(move.source())
                        ? next.smallPos() : next.bigPos();
                SubLevel mappedOwner = Sable.HELPER.getContaining(level, movedPos);
                if (mappedOwner == null || mappedOwner.isRemoved()
                        || !move.destination().equals(mappedOwner.getUniqueId())
                        || level.getBlockState(movedPos).isAir()) {
                    PocketTrace.logger().warn("[Pocket] Weld {} could not follow slime merge to {}", remap.id(), movedPos);
                    store.remove(remap.id());
                } else {
                    store.add(next);
                    rebind.add(remap.id());
                }
                changed = true;
            }
            WeldRuntime.forgetMergedBody(level, move.source());
            if (changed) {
                CrossScaleWelds.invalidate(level);
                CrossScaleWeldSync.broadcast(level);
                WeldRuntime.rebindMigrated(level, rebind);
            }
        } finally {
            Set<UUID> moving = MOVING.get(level);
            if (moving != null) {
                moving.remove(move.source());
                if (moving.isEmpty()) MOVING.remove(level);
            }
        }
    }

    private WeldMergeMigration() {}
}

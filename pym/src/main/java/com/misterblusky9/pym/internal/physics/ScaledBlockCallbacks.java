package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.internal.debug.PymTrace;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.callback.ExplosiveBlockCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScaledBlockCallbacks implements BlockSubLevelCollisionCallback {
    public static final ScaledBlockCallbacks INSTANCE = new ScaledBlockCallbacks();

    private static final double CONTACT_SLOP_METRES = 0.02D;
    private static final double MIN_REACH_BLOCKS = 0.5D;
    private static final long[] NONE_FOUND = new long[0];

    private static final Map<CompiledCollider, Map<Long, long[]>> SITES = new WeakHashMap<>();

    private ScaledBlockCallbacks() {}

    @Override
    public CollisionResult sable$onCollision(
            BlockPos cell,
            @Nullable BlockPos otherHitBlockPos,
            Vector3d hit,
            double impactVelocity
    ) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) return CollisionResult.NONE;
        ServerLevel level = system.getLevel();
        SableColliderMirror.CallbackSite site = SableColliderMirror.callbackSite(
                level, cell.getX(), cell.getY(), cell.getZ());
        if (site == null) return CollisionResult.NONE;

        CompiledCollider collider = site.collider();
        double scale = collider.scale();
        if (!(scale > 0.0D) || !Double.isFinite(scale)) return CollisionResult.NONE;

        Vector3d source = new Vector3d(
                expand(collider.pivotX(), hit.x, scale),
                expand(collider.pivotY(), hit.y, scale),
                expand(collider.pivotZ(), hit.z, scale));

        long[] candidates = candidates(level, site, cell);
        double reach = Math.max(MIN_REACH_BLOCKS, CONTACT_SLOP_METRES / scale);
        BlockPos best = null;
        double bestDistance = reach * reach;
        for (long packed : candidates) {
            BlockPos pos = BlockPos.of(packed);
            double distance = distanceSquared(source, pos);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best == null) return CollisionResult.NONE;

        BlockState state = level.getBlockState(best);
        BlockSubLevelCollisionCallback callback = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        if (callback == null || callback == this) return CollisionResult.NONE;

        double velocity = callback instanceof ExplosiveBlockCallback && scale > 1.0D
                ? impactVelocity / scale
                : impactVelocity;
        CollisionResult result = callback.sable$onCollision(best, otherHitBlockPos, source, velocity);
        if (result == null) return CollisionResult.NONE;
        Vector3dc motion = result.tangentMotion();
        if (motion.x() == 0.0D && motion.y() == 0.0D && motion.z() == 0.0D) return result;
        return new CollisionResult(new Vector3d(motion).mul(scale), result.removeCollision());
    }

    private static long[] candidates(ServerLevel level, SableColliderMirror.CallbackSite site, BlockPos cell) {
        CompiledCollider collider = site.collider();
        long key = cell.asLong();
        synchronized (SITES) {
            Map<Long, long[]> cells = SITES.computeIfAbsent(collider, ignored -> new HashMap<>());
            long[] known = cells.get(key);
            if (known != null) return known;
            long[] found = scan(level, site, cell);
            cells.put(key, found);
            return found;
        }
    }

    private static long[] scan(ServerLevel level, SableColliderMirror.CallbackSite site, BlockPos cell) {
        CompiledCollider collider = site.collider();
        BoundingBox3ic plot = site.subLevel().getPlot() == null ? null : site.subLevel().getPlot().getBoundingBox();
        if (plot == null) return NONE_FOUND;
        double scale = collider.scale();
        int minX = Math.max(plot.minX(), (int) Math.floor(expand(collider.pivotX(), cell.getX(), scale)));
        int minY = Math.max(plot.minY(), (int) Math.floor(expand(collider.pivotY(), cell.getY(), scale)));
        int minZ = Math.max(plot.minZ(), (int) Math.floor(expand(collider.pivotZ(), cell.getZ(), scale)));
        int maxX = Math.min(plot.maxX(), (int) Math.ceil(expand(collider.pivotX(), cell.getX() + 1.0D, scale)) - 1);
        int maxY = Math.min(plot.maxY(), (int) Math.ceil(expand(collider.pivotY(), cell.getY() + 1.0D, scale)) - 1);
        int maxZ = Math.min(plot.maxZ(), (int) Math.ceil(expand(collider.pivotZ(), cell.getZ() + 1.0D, scale)) - 1);
        if (minX > maxX || minY > maxY || minZ > maxZ) return NONE_FOUND;

        long[] found = new long[8];
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !BlockWithSubLevelCollisionCallback.hasCallback(state)) continue;
                    if (count == found.length) found = java.util.Arrays.copyOf(found, count * 2);
                    found[count++] = pos.asLong();
                }
            }
        }
        if (count == 0) {
            PymTrace.scale("scaled callback cell [{},{},{}] has no callback block in its footprint",
                    cell.getX(), cell.getY(), cell.getZ());
            return NONE_FOUND;
        }
        return java.util.Arrays.copyOf(found, count);
    }

    private static double distanceSquared(Vector3dc point, BlockPos pos) {
        double dx = Math.max(0.0D, Math.max(pos.getX() - point.x(), point.x() - (pos.getX() + 1.0D)));
        double dy = Math.max(0.0D, Math.max(pos.getY() - point.y(), point.y() - (pos.getY() + 1.0D)));
        double dz = Math.max(0.0D, Math.max(pos.getZ() - point.z(), point.z() - (pos.getZ() + 1.0D)));
        return dx * dx + dy * dy + dz * dz;
    }

    private static double expand(double pivot, double value, double scale) {
        return pivot + (value - pivot) / scale;
    }
}

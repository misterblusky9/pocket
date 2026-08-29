package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonPhysicsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

// Turns a moon surface hit into a BlockHitResult expressed in the moon shim's plot
// coordinates - the shape Sable hands back for a real sub-level, and the shape
// Simulated's tools expect. Everything downstream (getContainingClient, atCenterOf,
// the uuid sent to the server) then works without Simulated knowing about the moon.
public final class MoonPickTarget {
    private static BlockPos lastPlotBlock;
    private static Vector3d lastBodyLocal;

    // The anchor Simulated derives from a block position, for the moon's synthetic hit.
    public static Vector3d lastBodyLocalHit(final Vec3i blockPos) {
        final BlockPos plotBlock = lastPlotBlock;
        final Vector3d local = lastBodyLocal;
        if (plotBlock == null || local == null || blockPos == null) return null;
        if (plotBlock.getX() != blockPos.getX()
                || plotBlock.getY() != blockPos.getY()
                || plotBlock.getZ() != blockPos.getZ()) {
            return null;
        }
        return new Vector3d(local);
    }

    public static BlockHitResult pick(final Entity viewer, final double range, final HitResult vanilla) {
        if (viewer == null) return null;

        final MoonClientSubLevel shim = MoonClientSubLevels.get();
        if (shim == null) return null;

        final MoonPhysicsState.State state = MoonPhysicsState.get(viewer.level());
        if (state == null) return null;

        final Vec3 from = viewer.getEyePosition();
        final Vec3 to = from.add(viewer.getLookAngle().scale(range));
        final MoonPhysicsState.RayHit hit = MoonPhysicsState.raycast(state, from, to);
        if (hit == null) return null;

        if (vanilla != null && vanilla.getType() != HitResult.Type.MISS
                && from.distanceToSqr(vanilla.getLocation()) + 1.0E-5D
                < from.distanceToSqr(hit.worldPoint())) {
            return null;
        }

        return toPlotHit(shim, hit);
    }

    public static BlockHitResult toPlotHit(
            final MoonClientSubLevel shim,
            final MoonPhysicsState.RayHit hit
    ) {
        final Vector3d local = hit.localPoint();
        final Vector3d anchor = shim.plotAnchor(local.x, local.y, local.z);
        if (!shim.acceptsPlotAnchor(anchor)) return null;

        // Simulated resolves the sub-level from getLocation(), so the location stays in
        // plot space. The body-local anchor reaches the joint through the atCenterOf
        // override in PhysicsStaffMoonAnchorMixin.
        final BlockPos plotPos = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        lastPlotBlock = plotPos;
        lastBodyLocal = new Vector3d(local);
        return new BlockHitResult(
                new Vec3(anchor.x, anchor.y, anchor.z),
                faceOf(local),
                plotPos,
                false
        );
    }

    // The moon is a box; the dominant local axis is the face that was struck.
    private static Direction faceOf(final Vector3d local) {
        final double ax = Math.abs(local.x);
        final double ay = Math.abs(local.y);
        final double az = Math.abs(local.z);
        if (ax >= ay && ax >= az) return local.x >= 0.0D ? Direction.EAST : Direction.WEST;
        if (ay >= az) return local.y >= 0.0D ? Direction.UP : Direction.DOWN;
        return local.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private MoonPickTarget() {}
}

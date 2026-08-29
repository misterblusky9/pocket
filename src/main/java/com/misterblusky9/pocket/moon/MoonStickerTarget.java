package com.misterblusky9.pocket.moon;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

// Server-side moon hit for the sticker, expressed in the moon shim's plot coordinates.
// Sable's sticker mixin then treats the moon exactly like any other sub-level: its own
// face check, 30 degree tolerance, orientation snapping, constraint and persistence.
public final class MoonStickerTarget {
    public static BlockHitResult clip(final ServerLevel level, final ClipContext context) {
        if (level == null || context == null) return null;

        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim == null) return null;
        final MoonPhysicsState.State state = MoonPhysicsTarget.state(level);
        if (state == null) return null;

        // The ray arrives in the sticker's own space, which is plot space when the
        // sticker rides a sub-level.
        final SubLevel from = Sable.HELPER.getContaining(level, context.getFrom());
        final Vec3 worldFrom = toWorld(from, context.getFrom());
        final Vec3 worldTo = toWorld(from, context.getTo());

        final MoonPhysicsState.RayHit hit = MoonPhysicsState.raycast(state, worldFrom, worldTo);
        if (hit == null) return null;

        final Vector3d local = hit.localPoint();
        final Vector3d address = shim.plotAnchor(local.x, local.y, local.z);
        if (!shim.acceptsPlotAnchor(address)) return null;

        // getBlockPos() is what Sable feeds to getContaining, so it must address the
        // plot. getLocation() becomes the constraint anchor, so it must be body-local.
        return new BlockHitResult(
                new Vec3(local.x, local.y, local.z),
                faceOf(local),
                BlockPos.containing(address.x, address.y, address.z),
                false
        );
    }

    // Is this position the moon? Used where Create asks whether a block face can be glued.
    public static boolean isMoonPosition(final ServerLevel level, final BlockPos pos) {
        if (level == null || pos == null) return false;
        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim == null) return false;
        return shim.getPlot().contains(new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
    }

    private static Vec3 toWorld(final SubLevel subLevel, final Vec3 local) {
        if (!(subLevel instanceof final ServerSubLevel server)) return local;
        return server.logicalPose().transformPosition(local);
    }

    private static Direction faceOf(final Vector3d local) {
        final double ax = Math.abs(local.x);
        final double ay = Math.abs(local.y);
        final double az = Math.abs(local.z);
        if (ax >= ay && ax >= az) return local.x >= 0.0D ? Direction.EAST : Direction.WEST;
        if (ay >= az) return local.y >= 0.0D ? Direction.UP : Direction.DOWN;
        return local.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private MoonStickerTarget() {}
}

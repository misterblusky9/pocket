package com.misterblusky9.pocket.moon;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public final class MoonPhysicsTarget {
    public static final UUID ID = UUID.fromString("5ca1ab1e-3d89-4c15-a3d9-2e3bb2e4dd4d");

    public static boolean isId(final UUID id) {
        return ID.equals(id);
    }

    public static BoxPhysicsObject body(final ServerLevel level) {
        return MoonPhysicsTest.body(level);
    }

    public static boolean isBody(final PhysicsPipelineBody body) {
        return MoonPhysicsTest.isBody(body);
    }

    public static MoonPhysicsState.State state(final Level level) {
        if (level instanceof final ServerLevel serverLevel) {
            final BoxPhysicsObject box = body(serverLevel);
            if (box == null) return null;
            final double half = box.getHalfExtents().x();
            final double radius = half * 1.7320508075688772D;
            final Vector3d position = new Vector3d(box.getPose().position());
            return new MoonPhysicsState.State(
                    position,
                    half,
                    new Quaterniond(box.getPose().orientation()),
                    new AABB(
                            position.x - radius,
                            position.y - radius,
                            position.z - radius,
                            position.x + radius,
                            position.y + radius,
                            position.z + radius
                    )
            );
        }
        return MoonPhysicsState.get(level);
    }

    public static Vector3d normalizedAnchor(final MoonPhysicsState.State state, final Vector3dc localPoint) {
        if (state == null || localPoint == null || !(state.halfExtent() > 0.0D)) return null;
        return new Vector3d(localPoint).div(state.halfExtent());
    }

    public static Vector3d localAnchor(final Level level, final Vector3dc normalizedAnchor) {
        final MoonPhysicsState.State state = state(level);
        if (state == null || normalizedAnchor == null) return null;
        return new Vector3d(normalizedAnchor).mul(state.halfExtent());
    }

    public static Vec3 worldAnchor(final Level level, final Vector3dc normalizedAnchor) {
        final MoonPhysicsState.State state = state(level);
        if (state == null || normalizedAnchor == null) return null;
        final Vector3d local = new Vector3d(normalizedAnchor).mul(state.halfExtent());
        state.orientation().transform(local);
        local.add(state.position());
        return new Vec3(local.x, local.y, local.z);
    }

    public static Vector3d localNormal(final MoonPhysicsState.State state, final Vector3dc localPoint) {
        if (state == null || localPoint == null) return null;
        final double ax = Math.abs(localPoint.x());
        final double ay = Math.abs(localPoint.y());
        final double az = Math.abs(localPoint.z());
        if (ax >= ay && ax >= az) return new Vector3d(Math.copySign(1.0D, localPoint.x()), 0.0D, 0.0D);
        if (ay >= az) return new Vector3d(0.0D, Math.copySign(1.0D, localPoint.y()), 0.0D);
        return new Vector3d(0.0D, 0.0D, Math.copySign(1.0D, localPoint.z()));
    }

    public static Vector3d worldNormal(final Level level, final Vector3dc localNormal) {
        final MoonPhysicsState.State state = state(level);
        if (state == null || localNormal == null) return null;
        return state.orientation().transform(new Vector3d(localNormal)).normalize();
    }

    public static Vector3d localVector(final Level level, final Vector3dc worldVector) {
        final MoonPhysicsState.State state = state(level);
        if (state == null || worldVector == null) return null;
        return new Quaterniond(state.orientation()).conjugate().transform(new Vector3d(worldVector));
    }

    private MoonPhysicsTarget() {}
}

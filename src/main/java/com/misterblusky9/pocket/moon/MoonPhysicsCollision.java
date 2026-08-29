package com.misterblusky9.pocket.moon;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MoonPhysicsCollision {
    private static final double EPSILON = 1.0E-7D;
    private static final double MAX_STEP = 0.25D;

    public static void apply(
            final Entity entity,
            final SubLevelEntityCollision.CollisionInfo info,
            final LevelReusedVectors sink
    ) {
        if (entity == null || info == null || info.motion == null || sink == null || entity.isSpectator()) return;
        final MoonPhysicsState.State state = MoonPhysicsState.get(entity.level());
        if (state == null) return;

        final AABB bounds = entity.getBoundingBox();
        final Vec3 requested = info.motion;
        final AABB swept = bounds.expandTowards(requested).inflate(0.05D);
        if (!swept.intersects(state.broadphase())) return;

        final Vector3d startCenter = new Vector3d(
                (bounds.minX + bounds.maxX) * 0.5D,
                (bounds.minY + bounds.maxY) * 0.5D,
                (bounds.minZ + bounds.maxZ) * 0.5D
        );
        final Vector3d dimensions = new Vector3d(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
        final OrientedBoundingBox3d entityBox = new OrientedBoundingBox3d(
                startCenter,
                dimensions,
                new Quaterniond(),
                sink
        );
        final double diameter = state.halfExtent() * 2.0D;
        final OrientedBoundingBox3d moonBox = new OrientedBoundingBox3d(
                state.position(),
                new Vector3d(diameter, diameter, diameter),
                state.orientation(),
                sink
        );

        final double length = requested.length();
        final int steps = Math.max(1, Math.min(32, (int) Math.ceil(length / MAX_STEP)));
        final Vector3d increment = new Vector3d(requested.x, requested.y, requested.z).div(steps);
        final Vector3d resolved = new Vector3d();
        final Vector3d trial = new Vector3d();
        final Vector3d mtv = new Vector3d();
        final Vector3d strongestNormal = new Vector3d();
        double strongestLength = 0.0D;

        for (int i = 0; i < steps; i++) {
            trial.set(resolved).add(increment);
            entityBox.setPosition(new Vector3d(startCenter).add(trial));
            OrientedBoundingBox3d.sat(entityBox, moonBox, mtv);
            if (validMtv(mtv)) {
                final double mtvLength = mtv.lengthSquared();
                if (mtvLength > strongestLength) {
                    strongestLength = mtvLength;
                    strongestNormal.set(mtv).normalize();
                }
                trial.add(mtv);
            }
            resolved.set(trial);
        }

        if (strongestLength <= 0.0D) return;

        info.motion = new Vec3(resolved.x, resolved.y, resolved.z);
        final double vertical = Math.abs(strongestNormal.y);
        if (vertical > 0.6D) {
            info.verticalCollision = true;
            if (strongestNormal.y > 0.0D) {
                info.verticalCollisionBelow = true;
                entity.setOnGround(true);
            }
        } else {
            info.subLevelHorizontalCollision = true;
            info.horizontalCollision = true;
        }

        final Vec3 velocity = entity.getDeltaMovement();
        final double intoSurface = velocity.x * strongestNormal.x
                + velocity.y * strongestNormal.y
                + velocity.z * strongestNormal.z;
        if (intoSurface < 0.0D) {
            entity.setDeltaMovement(
                    velocity.x - strongestNormal.x * intoSurface,
                    velocity.y - strongestNormal.y * intoSurface,
                    velocity.z - strongestNormal.z * intoSurface
            );
        }
    }

    private static boolean validMtv(final Vector3d mtv) {
        return Double.isFinite(mtv.x)
                && Double.isFinite(mtv.y)
                && Double.isFinite(mtv.z)
                && mtv.x != Double.MAX_VALUE
                && mtv.y != Double.MAX_VALUE
                && mtv.z != Double.MAX_VALUE
                && mtv.lengthSquared() > EPSILON * EPSILON;
    }

    private MoonPhysicsCollision() {}
}

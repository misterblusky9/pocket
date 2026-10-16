package com.misterblusky9.pym.internal.compat.create;

import com.simibubi.create.foundation.collision.CollisionList;
import com.simibubi.create.foundation.collision.CollisionList.Populate;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import com.simibubi.create.foundation.collision.OrientedBB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ScaledContraptionCollider {
    private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);
    private static final double AXIS_EPSILON = 1.0E-12D;

    private ScaledContraptionCollider() {
    }

    public static ContinuousOBBCollider.CollisionResponse collideMany(
            CollisionList colliders,
            CollisionList viableColliders,
            OrientedBB obb,
            Vec3 motion,
            float maxStep,
            boolean doHorizontalPass,
            double scale
    ) {
        Vec3 center = obb.getCenter();
        AABB base = obb.getAsAABB();
        Vec3 entityExtents = new Vec3(base.getXsize() * 0.5D, base.getYsize() * 0.5D, base.getZsize() * 0.5D);
        Vec3 b0 = obb.getRotation().transform(X).normalize();
        Vec3 b1 = obb.getRotation().transform(Y).normalize();
        Vec3 b2 = obb.getRotation().transform(Z).normalize();

        double boundX = Math.abs(b0.x) * entityExtents.x + Math.abs(b1.x) * entityExtents.y + Math.abs(b2.x) * entityExtents.z;
        double boundY = Math.abs(b0.y) * entityExtents.x + Math.abs(b1.y) * entityExtents.y + Math.abs(b2.y) * entityExtents.z;
        double boundZ = Math.abs(b0.z) * entityExtents.x + Math.abs(b1.z) * entityExtents.y + Math.abs(b2.z) * entityExtents.z;
        double minX = Math.min(center.x, center.x + motion.x) - boundX;
        double minY = Math.min(center.y, center.y + motion.y) - boundY;
        double minZ = Math.min(center.z, center.z + motion.z) - boundZ;
        double maxX = Math.max(center.x, center.x + motion.x) + boundX;
        double maxY = Math.max(center.y, center.y + motion.y) + boundY;
        double maxZ = Math.max(center.z, center.z + motion.z) + boundZ;

        viableColliders.size = 0;
        Populate populate = new Populate(viableColliders);
        for (int i = 0; i < colliders.size; i++) {
            if (maxX < colliders.centerX[i] - colliders.extentsX[i] || minX > colliders.centerX[i] + colliders.extentsX[i]) continue;
            if (maxY < colliders.centerY[i] - colliders.extentsY[i] || minY > colliders.centerY[i] + colliders.extentsY[i]) continue;
            if (maxZ < colliders.centerZ[i] - colliders.extentsZ[i] || minZ > colliders.centerZ[i] + colliders.extentsZ[i]) continue;
            populate.appendFrom(colliders, i);
        }

        if (viableColliders.size == 0) return emptyResponse();

        double responseX = 0.0D;
        double responseY = 0.0D;
        double responseZ = 0.0D;
        Vec3 normal = Vec3.ZERO;
        Vec3 location = Vec3.ZERO;
        boolean surfaceCollision = false;
        double temporalResponse = 1.0D;

        for (int pass = 0; pass < 2; pass++) {
            boolean horizontalPass = pass == 0;
            boolean verticalPass = !horizontalPass || !doHorizontalPass;
            if (horizontalPass && !doHorizontalPass) continue;

            for (int i = 0; i < viableColliders.size; i++) {
                Vec3 delta = new Vec3(
                        center.x + responseX - viableColliders.centerX[i],
                        center.y + responseY - viableColliders.centerY[i],
                        center.z + responseZ - viableColliders.centerZ[i]
                );
                Vec3 blockExtents = new Vec3(
                        viableColliders.extentsX[i],
                        viableColliders.extentsY[i],
                        viableColliders.extentsZ[i]
                );
                Manifold manifold = new Manifold(delta, blockExtents, entityExtents, motion, b0, b1, b2, scale);
                if (!manifold.collides()) continue;

                if (verticalPass) surfaceCollision = true;

                double timeOfImpact = manifold.timeOfImpact();
                boolean temporal = timeOfImpact > 0.0D && timeOfImpact < 1.0D;

                if (!temporal && manifold.discrete()) {
                    Vec3 response;
                    if (manifold.stepSeparation() <= maxStep) {
                        response = b1.scale(withEpsilon(manifold.stepSeparation(), scale));
                    } else {
                        response = manifold.separationAxis().scale(withEpsilon(manifold.separation(), scale));
                    }
                    responseX += response.x;
                    responseY += response.y;
                    responseZ += response.z;
                    timeOfImpact = 0.0D;
                }

                if (timeOfImpact >= 0.0D && temporalResponse > timeOfImpact) {
                    normal = manifold.normal();
                    location = manifold.location();
                }

                if (temporal && temporalResponse > timeOfImpact) temporalResponse = timeOfImpact;
            }

            if (verticalPass) break;
            if (temporalResponse == 1.0D && responseY == 0.0D) break;

            responseX *= 129.0D / 128.0D;
            responseZ *= 129.0D / 128.0D;
        }

        ContinuousOBBCollider.CollisionResponse out = new ContinuousOBBCollider.CollisionResponse();
        out.surfaceCollision = surfaceCollision;
        out.collisionResponse = new Vec3(responseX, responseY, responseZ);
        out.normal = normal;
        out.location = location;
        out.temporalResponse = temporalResponse;
        return out;
    }

    private static double withEpsilon(double value, double scale) {
        return value + Math.copySign(Math.max(1.0E-7D, 1.0E-4D * scale), value);
    }

    private static ContinuousOBBCollider.CollisionResponse emptyResponse() {
        ContinuousOBBCollider.CollisionResponse out = new ContinuousOBBCollider.CollisionResponse();
        out.surfaceCollision = false;
        out.collisionResponse = Vec3.ZERO;
        out.normal = Vec3.ZERO;
        out.location = Vec3.ZERO;
        out.temporalResponse = 1.0D;
        return out;
    }

    private static final class Manifold {
        private final Vec3 delta;
        private final Vec3 blockExtents;
        private final Vec3 entityExtents;
        private final Vec3 motion;
        private final Vec3 b0;
        private final Vec3 b1;
        private final Vec3 b2;
        private final double inset;
        private boolean discrete = true;
        private boolean collides = true;
        private double latestEntry = Double.NEGATIVE_INFINITY;
        private double earliestExit = Double.POSITIVE_INFINITY;
        private double separation = Double.POSITIVE_INFINITY;
        private Vec3 separationAxis = Vec3.ZERO;
        private double stepSeparation = Double.POSITIVE_INFINITY;
        private Vec3 normal = Vec3.ZERO;

        private Manifold(
                Vec3 delta,
                Vec3 blockExtents,
                Vec3 entityExtents,
                Vec3 motion,
                Vec3 b0,
                Vec3 b1,
                Vec3 b2,
                double scale
        ) {
            this.delta = delta;
            this.blockExtents = blockExtents;
            this.entityExtents = entityExtents;
            this.motion = motion;
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.inset = 0.125D * scale;
            test(X);
            test(Y);
            test(Z);
            test(b0);
            test(b1);
            test(b2);
            testCross(X, b0);
            testCross(X, b1);
            testCross(X, b2);
            testCross(Y, b0);
            testCross(Y, b1);
            testCross(Y, b2);
            testCross(Z, b0);
            testCross(Z, b1);
            testCross(Z, b2);
            if (collides && latestEntry > earliestExit) collides = false;
        }

        private void testCross(Vec3 a, Vec3 b) {
            if (!collides) return;
            Vec3 axis = a.cross(b);
            double lengthSqr = axis.lengthSqr();
            if (lengthSqr <= AXIS_EPSILON) return;
            test(axis.scale(1.0D / Math.sqrt(lengthSqr)));
        }

        private void test(Vec3 axis) {
            if (!collides) return;

            double projectedDelta = delta.dot(axis);
            double blockRadius = blockExtents.x * Math.abs(axis.x)
                    + blockExtents.y * Math.abs(axis.y)
                    + blockExtents.z * Math.abs(axis.z);
            double entityRadius = entityExtents.x * Math.abs(b0.dot(axis))
                    + entityExtents.y * Math.abs(b1.dot(axis))
                    + entityExtents.z * Math.abs(b2.dot(axis));
            double radius = blockRadius + entityRadius;
            double distance = Math.abs(projectedDelta);
            double projectedMotion = motion.dot(axis);
            boolean overlaps = distance <= radius;

            if (!overlaps) discrete = false;

            if (Math.abs(projectedMotion) <= AXIS_EPSILON) {
                if (!overlaps) collides = false;
            } else {
                double t0 = (-radius - projectedDelta) / projectedMotion;
                double t1 = (radius - projectedDelta) / projectedMotion;
                double entry = Math.min(t0, t1);
                double exit = Math.max(t0, t1);

                if (exit < 0.0D || entry > 1.0D) {
                    collides = false;
                    return;
                }

                if (entry > latestEntry) {
                    latestEntry = entry;
                    double atEntry = projectedDelta + projectedMotion * entry;
                    normal = signedAxis(axis, atEntry, projectedMotion);
                }
                earliestExit = Math.min(earliestExit, exit);
                if (latestEntry > earliestExit) {
                    collides = false;
                    return;
                }
            }

            if (!overlaps) return;

            double penetration = radius - distance;
            if (penetration < separation) {
                separation = penetration;
                separationAxis = signedAxis(axis, projectedDelta, projectedMotion);
                if (normal.equals(Vec3.ZERO)) normal = separationAxis;
            }

            double stepDot = b1.dot(axis);
            if (Math.abs(stepDot) > AXIS_EPSILON) {
                double positive = (radius - projectedDelta) / stepDot;
                double negative = (-radius - projectedDelta) / stepDot;
                if (positive >= 0.0D) stepSeparation = Math.min(stepSeparation, positive);
                if (negative >= 0.0D) stepSeparation = Math.min(stepSeparation, negative);
            }
        }

        private Vec3 signedAxis(Vec3 axis, double projectedDelta, double projectedMotion) {
            double sign = Math.signum(projectedDelta);
            if (sign == 0.0D) sign = -Math.signum(projectedMotion);
            if (sign == 0.0D) sign = 1.0D;
            return axis.scale(sign);
        }

        private boolean collides() {
            return collides && (discrete || latestEntry <= 1.0D && earliestExit >= 0.0D);
        }

        private boolean discrete() {
            return discrete;
        }

        private double timeOfImpact() {
            return discrete ? -1.0D : Math.max(0.0D, latestEntry);
        }

        private double separation() {
            return separation;
        }

        private Vec3 separationAxis() {
            return separationAxis;
        }

        private double stepSeparation() {
            return stepSeparation;
        }

        private Vec3 normal() {
            return normal;
        }

        private Vec3 location() {
            if (normal.equals(Vec3.ZERO)) return Vec3.ZERO;
            double s0 = Math.signum(normal.dot(b0));
            double s1 = Math.signum(normal.dot(b1));
            double s2 = Math.signum(normal.dot(b2));
            return b0.scale(-s0 * entityExtents.x)
                    .add(b1.scale(-s1 * entityExtents.y))
                    .add(b2.scale(-s2 * entityExtents.z))
                    .subtract(normal.scale(inset));
        }
    }
}

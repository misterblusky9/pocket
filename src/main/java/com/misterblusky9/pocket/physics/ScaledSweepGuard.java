package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaledSweepGuard {
    private static final double SAMPLE_FRACTION = 0.5D;

    private static final double MIN_SAMPLE = 0.01D;

    private static final int MAX_SAMPLES = 64;

    private static final double CONTACT_BACKOFF = 1.0E-3D;

    private static final double SAT_EPSILON = 1.0E-8D;

    private static final Map<UUID, double[]> LAST_POSITION = new ConcurrentHashMap<>();

    public static void forget(final UUID id) {
        if (id != null) LAST_POSITION.remove(id);
    }

    public static void afterStep(final PhysicsPipeline pipeline, final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null) return;

        final double scale = ScaleState.getServerScale(subLevel);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            LAST_POSITION.remove(id);
            return;
        }

        final Vector3d position = subLevel.logicalPose().position();
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            LAST_POSITION.remove(id);
            return;
        }

        final double[] previous = LAST_POSITION.get(id);
        if (previous == null) {
            store(id, position);
            return;
        }

        if (!ScaleState.isSettled(id)) {
            store(id, position);
            return;
        }

        final ServerLevel level = subLevel.getLevel();
        final BoundingBox3dc box = subLevel.boundingBox();
        if (level == null || box == null) {
            store(id, position);
            return;
        }

        final Vector3d from = new Vector3d(previous[0], previous[1], previous[2]);
        final Vector3d delta = new Vector3d(position).sub(from);
        final double distance = delta.length();
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            store(id, position);
            return;
        }

        final double thinnest = Math.min(
                box.maxX() - box.minX(),
                Math.min(box.maxY() - box.minY(), box.maxZ() - box.minZ()));
        final double spacing = Math.max(MIN_SAMPLE, thinnest * SAMPLE_FRACTION);
        if (distance <= spacing) {
            store(id, position);
            return;
        }

        final SweepShape shape = buildShape(subLevel, box, position);

        if (!isClear(level, shape, from)) {
            store(id, position);
            return;
        }

        final int samples = (int) Math.min(MAX_SAMPLES, Math.ceil(distance / spacing));
        final Vector3d candidate = new Vector3d();
        int lastClear = 0;

        for (int i = 1; i <= samples; i++) {
            final double t = (double) i / samples;
            candidate.set(delta).mul(t).add(from);
            if (!isClear(level, shape, candidate)) break;
            lastClear = i;
        }

        if (lastClear == samples) {
            store(id, position);
            return;
        }

        final Vector3d direction = new Vector3d(delta).div(distance);
        final Vector3d stopped = new Vector3d(delta)
                .mul((double) lastClear / samples)
                .add(from)
                .fma(-CONTACT_BACKOFF, direction);

        PocketTrace.scale(
                "swept collision {} mode={} parts={} distance={} spacing={} stoppedAfter={}/{}",
                PocketTrace.context(subLevel), shape.fallbackPrism() ? "fallback_prism" : "detailed",
                shape.parts().size(), distance, spacing, lastClear, samples);

        pipeline.teleport(subLevel, stopped, subLevel.logicalPose().orientation());
        subLevel.logicalPose().position().set(stopped);
        subLevel.updateBoundingBox();

        final Vector3d linear = pipeline.getLinearVelocity(subLevel, new Vector3d());
        if (linear != null) {
            final double into = linear.dot(direction);
            if (into > 0.0D) {
                pipeline.addLinearAndAngularVelocity(
                        subLevel, new Vector3d(direction).mul(-into), new Vector3d());
            }
        }

        store(id, stopped);
    }

    private static SweepShape buildShape(
            final ServerSubLevel subLevel,
            final BoundingBox3dc worldBounds,
            final Vector3dc currentPosition
    ) {
        final Pose3dc pose = subLevel.logicalPose();
        final CompiledCollider applied = ColliderCoordinator.current(subLevel.getUniqueId());

        final AABB broadPhase = new AABB(
                worldBounds.minX(), worldBounds.minY(), worldBounds.minZ(),
                worldBounds.maxX(), worldBounds.maxY(), worldBounds.maxZ())
                .move(-currentPosition.x(), -currentPosition.y(), -currentPosition.z());

        if (pose == null || applied == null || applied.cells().isEmpty()) {
            return new SweepShape(broadPhase, List.of(), true);
        }

        final Vector3d axisX = pose.orientation().transform(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
        final Vector3d axisY = pose.orientation().transform(new Vector3d(0.0D, 1.0D, 0.0D)).normalize();
        final Vector3d axisZ = pose.orientation().transform(new Vector3d(0.0D, 0.0D, 1.0D)).normalize();
        final List<OrientedPart> parts = new ArrayList<>();
        final Vector3d center = new Vector3d();

        for (final CompiledCollider.Cell cell : applied.cells()) {
            for (final ColliderShapeKey.Face face : cell.shape().faces()) {
                final double minX = cell.x() + face.minXd() - applied.pivotX();
                final double minY = cell.y() + face.minYd() - applied.pivotY();
                final double minZ = cell.z() + face.minZd() - applied.pivotZ();
                final double maxX = cell.x() + face.maxXd() - applied.pivotX();
                final double maxY = cell.y() + face.maxYd() - applied.pivotY();
                final double maxZ = cell.z() + face.maxZd() - applied.pivotZ();

                center.set((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
                pose.orientation().transform(center).add(pose.position()).sub(currentPosition);
                parts.add(new OrientedPart(
                        new Vector3d(center),
                        (maxX - minX) * 0.5D,
                        (maxY - minY) * 0.5D,
                        (maxZ - minZ) * 0.5D,
                        axisX, axisY, axisZ));
            }
        }

        return new SweepShape(broadPhase, List.copyOf(parts), applied.mode() == CompiledCollider.Mode.PRISM_FALLBACK);
    }

    private static boolean isClear(final ServerLevel level, final SweepShape shape, final Vector3dc at) {
        final AABB broadPhase = shape.broadPhaseRelative().move(at.x(), at.y(), at.z());
        final List<AABB> obstacles = new ArrayList<>();
        for (final VoxelShape collision : level.getBlockCollisions(null, broadPhase)) {
            obstacles.addAll(collision.toAabbs());
        }
        if (obstacles.isEmpty()) return true;

        if (shape.parts().isEmpty()) return false;

        for (final OrientedPart part : shape.parts()) {
            final double centerX = at.x() + part.relativeCenter().x;
            final double centerY = at.y() + part.relativeCenter().y;
            final double centerZ = at.z() + part.relativeCenter().z;

            final double radiusX = Math.abs(part.axisX().x()) * part.halfX()
                    + Math.abs(part.axisY().x()) * part.halfY()
                    + Math.abs(part.axisZ().x()) * part.halfZ();
            final double radiusY = Math.abs(part.axisX().y()) * part.halfX()
                    + Math.abs(part.axisY().y()) * part.halfY()
                    + Math.abs(part.axisZ().y()) * part.halfZ();
            final double radiusZ = Math.abs(part.axisX().z()) * part.halfX()
                    + Math.abs(part.axisY().z()) * part.halfY()
                    + Math.abs(part.axisZ().z()) * part.halfZ();
            final AABB partBounds = new AABB(
                    centerX - radiusX, centerY - radiusY, centerZ - radiusZ,
                    centerX + radiusX, centerY + radiusY, centerZ + radiusZ);

            for (final AABB obstacle : obstacles) {
                if (partBounds.intersects(obstacle)
                        && intersects(part, centerX, centerY, centerZ, obstacle)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean intersects(
            final OrientedPart part,
            final double centerX,
            final double centerY,
            final double centerZ,
            final AABB box
    ) {
        final double a0 = part.halfX(), a1 = part.halfY(), a2 = part.halfZ();
        final double b0 = box.getXsize() * 0.5D;
        final double b1 = box.getYsize() * 0.5D;
        final double b2 = box.getZsize() * 0.5D;
        final double dx = (box.minX + box.maxX) * 0.5D - centerX;
        final double dy = (box.minY + box.maxY) * 0.5D - centerY;
        final double dz = (box.minZ + box.maxZ) * 0.5D - centerZ;

        final double r00 = part.axisX().x(), r01 = part.axisX().y(), r02 = part.axisX().z();
        final double r10 = part.axisY().x(), r11 = part.axisY().y(), r12 = part.axisY().z();
        final double r20 = part.axisZ().x(), r21 = part.axisZ().y(), r22 = part.axisZ().z();
        final double ar00 = Math.abs(r00) + SAT_EPSILON;
        final double ar01 = Math.abs(r01) + SAT_EPSILON;
        final double ar02 = Math.abs(r02) + SAT_EPSILON;
        final double ar10 = Math.abs(r10) + SAT_EPSILON;
        final double ar11 = Math.abs(r11) + SAT_EPSILON;
        final double ar12 = Math.abs(r12) + SAT_EPSILON;
        final double ar20 = Math.abs(r20) + SAT_EPSILON;
        final double ar21 = Math.abs(r21) + SAT_EPSILON;
        final double ar22 = Math.abs(r22) + SAT_EPSILON;
        final double t0 = dx * r00 + dy * r01 + dz * r02;
        final double t1 = dx * r10 + dy * r11 + dz * r12;
        final double t2 = dx * r20 + dy * r21 + dz * r22;

        if (separated(t0, a0 + b0 * ar00 + b1 * ar01 + b2 * ar02)) return false;
        if (separated(t1, a1 + b0 * ar10 + b1 * ar11 + b2 * ar12)) return false;
        if (separated(t2, a2 + b0 * ar20 + b1 * ar21 + b2 * ar22)) return false;

        if (separated(dx, b0 + a0 * ar00 + a1 * ar10 + a2 * ar20)) return false;
        if (separated(dy, b1 + a0 * ar01 + a1 * ar11 + a2 * ar21)) return false;
        if (separated(dz, b2 + a0 * ar02 + a1 * ar12 + a2 * ar22)) return false;

        if (separated(t2 * r10 - t1 * r20, a1 * ar20 + a2 * ar10 + b1 * ar02 + b2 * ar01)) return false;
        if (separated(t2 * r11 - t1 * r21, a1 * ar21 + a2 * ar11 + b0 * ar02 + b2 * ar00)) return false;
        if (separated(t2 * r12 - t1 * r22, a1 * ar22 + a2 * ar12 + b0 * ar01 + b1 * ar00)) return false;
        if (separated(t0 * r20 - t2 * r00, a0 * ar20 + a2 * ar00 + b1 * ar12 + b2 * ar11)) return false;
        if (separated(t0 * r21 - t2 * r01, a0 * ar21 + a2 * ar01 + b0 * ar12 + b2 * ar10)) return false;
        if (separated(t0 * r22 - t2 * r02, a0 * ar22 + a2 * ar02 + b0 * ar11 + b1 * ar10)) return false;
        if (separated(t1 * r00 - t0 * r10, a0 * ar10 + a1 * ar00 + b1 * ar22 + b2 * ar21)) return false;
        if (separated(t1 * r01 - t0 * r11, a0 * ar11 + a1 * ar01 + b0 * ar22 + b2 * ar20)) return false;
        if (separated(t1 * r02 - t0 * r12, a0 * ar12 + a1 * ar02 + b0 * ar21 + b1 * ar20)) return false;
        return true;
    }

    private static boolean separated(final double distance, final double radius) {
        return Math.abs(distance) >= radius - SAT_EPSILON;
    }

    private static double uncontract(final double pivot, final double value, final double scale) {
        return pivot + (value - pivot) / scale;
    }

    private record SweepShape(AABB broadPhaseRelative, List<OrientedPart> parts, boolean fallbackPrism) {}

    private record OrientedPart(
            Vector3d relativeCenter,
            double halfX,
            double halfY,
            double halfZ,
            Vector3dc axisX,
            Vector3dc axisY,
            Vector3dc axisZ
    ) {}

    private static void store(final UUID id, final Vector3d position) {
        LAST_POSITION.put(id, new double[] {position.x, position.y, position.z});
    }

    private ScaledSweepGuard() {}
}

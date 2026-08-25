package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScaledFluidForces {
    private static final double BUOYANCY_ACCELERATION = 10.5D;
    private static final double LINEAR_DRAG = 1.7D;
    private static final double MIN_EXTENT = 1.0E-7D;
    private static final double BOUNDS_EPSILON = 1.0E-7D;
    private static final int DETAILED_BOX_LIMIT = 64;

    private static final long MAX_BUOYANCY_SCAN_VOLUME = 1_500_000L;
    private static final int CLUSTER_AXIS = 8;
    private static final int MAX_HYDRO_ELEMENTS = CLUSTER_AXIS * CLUSTER_AXIS * CLUSTER_AXIS;
    private static final int MAX_WORLD_FLUID_SCAN = 4096;
    private static final int FLUID_QUERY_CACHE_SIZE = 2048;
    private static final double DIAGNOSTIC_DELTA_V = 2.0D;
    private static final double DIAGNOSTIC_DELTA_OMEGA = 4.0D;
    private static final long DIAGNOSTIC_COOLDOWN_TICKS = 10L;

    private static final Map<ServerLevel, FluidPass> ACTIVE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<UUID, CachedHydroShape> SHAPES = new HashMap<>();
    private static final Set<UUID> ACTIVATION_LOGGED = new HashSet<>();
    private static final Set<UUID> DRAG_LIMIT_LOGGED = new HashSet<>();
    private static final Map<UUID, Long> WRENCH_LOG_TICKS = new HashMap<>();
    private static final ThreadLocal<ForceScratch> FORCE_SCRATCH =
            ThreadLocal.withInitial(ForceScratch::new);

    public static List<ServerSubLevel> suppressNativePass(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            ACTIVE.remove(level);
            return List.of();
        }

        final FluidPass pass = ACTIVE.computeIfAbsent(level, ignored -> new FluidPass());
        pass.suppressed.clear();
        pass.active.clear();

        try {
            for (final SubLevel candidate : container.getAllSubLevels()) {
                if (!(candidate instanceof final ServerSubLevel subLevel)
                        || subLevel.isRemoved()
                        || !ScaleState.isScaled(subLevel)) {
                    continue;
                }

                final HydroShape shape = hydroShape(subLevel);
                if (shape == null || !overlapsFluid(level, subLevel.logicalPose(), shape)) continue;
                if (!ScaledBoundsCollider.suppressNativeFluidProbe(subLevel)) continue;

                pass.suppressed.add(subLevel);
                if (subLevel.getUniqueId() != null) pass.active.add(subLevel.getUniqueId());
            }
        } catch (final RuntimeException failure) {
            try {
                restoreNativePass(pass.suppressed);
            } catch (final RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            pass.active.clear();
            throw failure;
        }

        return pass.suppressed;
    }

    public static void restoreNativePass(final List<ServerSubLevel> suppressed) {
        RuntimeException failure = null;
        for (final ServerSubLevel subLevel : suppressed) {
            if (subLevel.isRemoved()) continue;
            try {
                ScaledBoundsCollider.restoreAfterNativeFluidProbe(subLevel);
            } catch (final RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public static void apply(
            final ServerSubLevel subLevel,
            final RigidBodyHandle handle,
            final double timeStep
    ) {
        if (!(timeStep > 0.0D) || subLevel.isRemoved() || !ScaleState.isScaled(subLevel)) return;
        final FluidPass pass = ACTIVE.get(subLevel.getLevel());
        if (pass == null || !pass.active.contains(subLevel.getUniqueId())) return;

        final HydroShape shape = hydroShape(subLevel);
        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        if (shape == null || centerOfMass == null) return;

        final Pose3d pose = subLevel.logicalPose();
        final Quaterniondc orientation = pose.orientation();
        final double scale = ScaleState.getServerScale(subLevel);
        if (!PocketSized.isValidScale(scale)) return;

        final ForceScratch scratch = FORCE_SCRATCH.get();
        final Vector3d linearVelocity = handle.getLinearVelocity(scratch.linearVelocity);
        final Vector3d angularVelocity = handle.getAngularVelocity(scratch.angularVelocity);
        orientation.transformInverse(linearVelocity);
        orientation.transformInverse(angularVelocity);

        final Vector3d localUp = scratch.localUp.set(0.0D, 1.0D, 0.0D);
        orientation.transformInverse(localUp);
        final Vector3d axis = scratch.axis.set(scale, 0.0D, 0.0D);
        orientation.transform(axis);
        final double verticalX = Math.abs(axis.y);
        axis.set(0.0D, scale, 0.0D);
        orientation.transform(axis);
        final double verticalY = Math.abs(axis.y);
        axis.set(0.0D, 0.0D, scale);
        orientation.transform(axis);
        final double verticalZ = Math.abs(axis.y);

        final Vector3d linearImpulse = scratch.linearImpulse.zero();
        final Vector3d angularImpulse = scratch.angularImpulse.zero();
        final Vector3d dragLinearImpulse = scratch.dragLinearImpulse.zero();
        final Vector3d dragAngularImpulse = scratch.dragAngularImpulse.zero();
        final Vector3d worldCenter = scratch.worldCenter;
        final Vector3d lever = scratch.lever;
        final Vector3d pointVelocity = scratch.pointVelocity;
        final Vector3d localFlow = scratch.localFlow;
        final Vector3d force = scratch.force;
        final Vector3d torque = scratch.torque;
        final FluidSlice fluid = scratch.fluid;
        final BlockPos.MutableBlockPos fluidPos = scratch.fluidPos;
        double dragWork = 0.0D;
        double submergedVolumeTotal = 0.0D;
        double submergedMomentX = 0.0D;
        double submergedMomentY = 0.0D;
        double submergedMomentZ = 0.0D;
        scratch.fluidCache.begin();

        for (int i = 0; i < shape.size; i++) {
            worldCenter.set(shape.centerX[i], shape.centerY[i], shape.centerZ[i]);
            pose.transformPosition(worldCenter);

            final double halfWorldY = Math.max(MIN_EXTENT,
                    verticalX * shape.halfX[i]
                            + verticalY * shape.halfY[i]
                            + verticalZ * shape.halfZ[i]);
            sampleFluidColumn(
                    subLevel.getLevel(), worldCenter, halfWorldY,
                    fluidPos, scratch.fluidCache, fluid);
            if (!(fluid.fraction > 0.0D)) continue;

            final double submergedVolume = shape.volume[i] * fluid.fraction;
            submergedVolumeTotal += submergedVolume;
            submergedMomentX += shape.centerX[i] * submergedVolume;
            submergedMomentY += shape.centerY[i] * submergedVolume;
            submergedMomentZ += shape.centerZ[i] * submergedVolume;
            lever.set(
                    shape.centerX[i] - centerOfMass.x(),
                    shape.centerY[i] - centerOfMass.y(),
                    shape.centerZ[i] - centerOfMass.z());

            force.set(localUp).mul(BUOYANCY_ACCELERATION * submergedVolume * timeStep);
            linearImpulse.add(force);
            lever.cross(force, torque);
            angularImpulse.add(torque);

            localFlow.set(fluid.flowX, fluid.flowY, fluid.flowZ);
            orientation.transformInverse(localFlow);
            angularVelocity.cross(lever, pointVelocity).add(linearVelocity).sub(localFlow);
            force.set(pointVelocity).mul(-LINEAR_DRAG * submergedVolume * timeStep);
            dragWork += force.dot(pointVelocity);
            dragLinearImpulse.add(force);
            lever.cross(force, torque);
            dragAngularImpulse.add(torque);
        }

        final double dragFactor = stableDragFactor(
                subLevel, dragLinearImpulse, dragAngularImpulse, dragWork, scratch);
        linearImpulse.fma(dragFactor, dragLinearImpulse);
        angularImpulse.fma(dragFactor, dragAngularImpulse);

        if (linearImpulse.lengthSquared() <= PocketSized.EPSILON
                && angularImpulse.lengthSquared() <= PocketSized.EPSILON) {
            return;
        }

        final UUID id = subLevel.getUniqueId();
        logExtremeWrench(
                subLevel, id, scale, centerOfMass,
                submergedVolumeTotal, submergedMomentX, submergedMomentY, submergedMomentZ,
                linearImpulse, angularImpulse, dragLinearImpulse, dragAngularImpulse,
                linearVelocity, angularVelocity, scratch);
        if (PocketTrace.SCALE && id != null && dragFactor < 0.999D && DRAG_LIMIT_LOGGED.add(id)) {
            PocketTrace.scale(
                    "scaled fluid drag stabilised uuid={} scale={} factor={} rawMass={} dragWork={}",
                    id, scale, dragFactor, subLevel.getMassTracker().getMass(), dragWork);
        }
        if (PocketTrace.SCALE && id != null && ACTIVATION_LOGGED.add(id)) {
            PocketTrace.scale(
                    "scaled fluid hybrid active uuid={} scale={} elements={} linearImpulse={} angularImpulse={}",
                    id, scale, shape.size, linearImpulse, angularImpulse);
        }
        handle.applyLinearAndAngularImpulse(linearImpulse, angularImpulse, false);
    }

    public static void forget(final UUID id) {
        if (id != null) {
            SHAPES.remove(id);
            ACTIVATION_LOGGED.remove(id);
            DRAG_LIMIT_LOGGED.remove(id);
            WRENCH_LOG_TICKS.remove(id);
        }
    }

    private static final class FluidPass {
        private final List<ServerSubLevel> suppressed = new ArrayList<>();
        private final Set<UUID> active = new HashSet<>();
    }

    private static void logExtremeWrench(
            final ServerSubLevel subLevel,
            final UUID id,
            final double scale,
            final Vector3dc centerOfMass,
            final double submergedVolume,
            final double submergedMomentX,
            final double submergedMomentY,
            final double submergedMomentZ,
            final Vector3dc totalLinear,
            final Vector3dc totalAngular,
            final Vector3dc dragLinear,
            final Vector3dc dragAngular,
            final Vector3dc linearVelocity,
            final Vector3dc angularVelocity,
            final ForceScratch scratch
    ) {
        if (!PocketTrace.SCALE || id == null) return;

        final var mass = subLevel.getMassTracker();
        if (mass == null || !(mass.getMass() > PocketSized.EPSILON)) return;
        final var inverseInertia = mass.getInverseInertiaTensor();
        final var inertia = mass.getInertiaTensor();
        if (inverseInertia == null || inertia == null) return;

        final double predictedDeltaV = totalLinear.length() / mass.getMass();
        final Vector3d predictedAngular = inverseInertia.transform(
                totalAngular, scratch.predictedAngularResponse);
        final double predictedDeltaOmega = predictedAngular.length();
        if (predictedDeltaV < DIAGNOSTIC_DELTA_V
                && predictedDeltaOmega < DIAGNOSTIC_DELTA_OMEGA) {
            return;
        }

        final long tick = subLevel.getLevel().getGameTime();
        final long last = WRENCH_LOG_TICKS.getOrDefault(id, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && tick - last < DIAGNOSTIC_COOLDOWN_TICKS) return;
        WRENCH_LOG_TICKS.put(id, tick);

        final Vector3d centerOfBuoyancy = scratch.centerOfBuoyancy;
        if (submergedVolume > PocketSized.EPSILON) {
            centerOfBuoyancy.set(
                    submergedMomentX / submergedVolume,
                    submergedMomentY / submergedVolume,
                    submergedMomentZ / submergedVolume);
        } else {
            centerOfBuoyancy.set(Double.NaN);
        }

        final Vector3d buoyancyLinear = scratch.diagnosticBuoyancyLinear
                .set(totalLinear).sub(dragLinear);
        final Vector3d buoyancyAngular = scratch.diagnosticBuoyancyAngular
                .set(totalAngular).sub(dragAngular);
        PocketTrace.scale(
                "scaled fluid extreme wrench uuid={} tick={} scale={} mass={} inertiaDiag=({}, {}, {}) "
                        + "massCOM={} buoyancyCenter={} submergedVolume={} velocity=({}, {}) "
                        + "buoyancyImpulse=({}, {}) dragImpulse=({}, {}) predictedDelta=({}, {})",
                id, tick, scale, mass.getMass(), inertia.m00(), inertia.m11(), inertia.m22(),
                centerOfMass, centerOfBuoyancy, submergedVolume, linearVelocity, angularVelocity,
                buoyancyLinear, buoyancyAngular, dragLinear, dragAngular,
                predictedDeltaV, predictedDeltaOmega);
    }

    private static double stableDragFactor(
            final ServerSubLevel subLevel,
            final Vector3dc linearDrag,
            final Vector3dc angularDrag,
            final double dragWork,
            final ForceScratch scratch
    ) {
        if (!(dragWork < -PocketSized.EPSILON)) return 1.0D;

        final var mass = subLevel.getMassTracker();
        if (mass == null || !(mass.getMass() > PocketSized.EPSILON)) return 1.0D;
        final var inverseInertia = mass.getInverseInertiaTensor();
        if (inverseInertia == null) return 1.0D;

        final Vector3d angularResponse = inverseInertia.transform(
                angularDrag, scratch.angularDragResponse);
        final double response = linearDrag.lengthSquared() / mass.getMass()
                + angularDrag.dot(angularResponse);
        if (!(response > PocketSized.EPSILON) || !Double.isFinite(response)) return 1.0D;

        final double factor = -dragWork / response;
        if (!Double.isFinite(factor)) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, factor));
    }

    private static HydroShape hydroShape(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null) return null;

        final PlotShape plotShape = PlotShapeCache.get(subLevel);
        final int revision = PlotShapeCache.revision(id);
        final CachedHydroShape cached = SHAPES.get(id);
        if (cached != null && cached.revision == revision) return cached.shape;

        final HydroShape parityShape = buildSableParityShape(
                subLevel, subLevel.getPlot().getBoundingBox());
        final HydroShape built = parityShape != null
                ? parityShape
                : plotShape == null
                        ? fallbackShape(subLevel.getPlot().getBoundingBox())
                        : buildShape(plotShape);
        if (built == null) SHAPES.remove(id);
        else SHAPES.put(id, new CachedHydroShape(revision, built));
        return built;
    }

    private static HydroShape buildSableParityShape(
            final ServerSubLevel subLevel,
            final BoundingBox3ic bounds
    ) {
        if (bounds == null) return null;
        final int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        final int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        final long spanX = (long) maxX - minX + 1L;
        final long spanY = (long) maxY - minY + 1L;
        final long spanZ = (long) maxZ - minZ + 1L;
        final long scanVolume = spanX * spanY * spanZ;
        if (spanX <= 0L || spanY <= 0L || spanZ <= 0L
                || scanVolume <= 0L || scanVolume > MAX_BUOYANCY_SCAN_VOLUME) {
            return null;
        }

        final boolean detailed = (maxX - minX) + (maxY - minY) + (maxZ - minZ) < 10;
        final int samplesPerBlock = detailed ? 8 : 1;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sampleCount = 0;

        countLoop:
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    pos.set(x, y, z);
                    final var state = subLevel.getLevel().getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                    final double volume = PhysicsBlockPropertyHelper.getVolume(state);
                    if (!(volume > 0.0D) || !Double.isFinite(volume)) continue;
                    sampleCount += samplesPerBlock;
                    if (sampleCount > MAX_HYDRO_ELEMENTS) break countLoop;
                }
            }
        }
        if (sampleCount == 0) return null;

        if (sampleCount <= MAX_HYDRO_ELEMENTS) {
            final HydroShape result = new HydroShape(sampleCount);
            int index = 0;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        pos.set(x, y, z);
                        final var state = subLevel.getLevel().getBlockState(pos);
                        if (state.isAir()
                                || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                        final double volume = PhysicsBlockPropertyHelper.getVolume(state);
                        if (!(volume > 0.0D) || !Double.isFinite(volume)) continue;
                        index = appendNativeSamples(result, index, x, y, z, volume, detailed);
                    }
                }
            }
            result.size = index;
            result.finishBounds();
            return index == 0 ? null : result;
        }

        return buildClusteredSableParityShape(
                subLevel, bounds, detailed, pos, (double) spanX, (double) spanY, (double) spanZ);
    }

    private static int appendNativeSamples(
            final HydroShape result,
            int index,
            final int x,
            final int y,
            final int z,
            final double blockVolume,
            final boolean detailed
    ) {
        if (!detailed) {
            result.set(index++, x + 0.5D, y + 0.5D, z + 0.5D,
                    0.5D, 0.5D, 0.5D, blockVolume);
            return index;
        }

        final double sampleVolume = blockVolume / 8.0D;
        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                for (int sz = 0; sz < 2; sz++) {
                    result.set(index++,
                            x + 0.25D + sx * 0.5D,
                            y + 0.25D + sy * 0.5D,
                            z + 0.25D + sz * 0.5D,
                            0.25D, 0.25D, 0.25D, sampleVolume);
                }
            }
        }
        return index;
    }

    private static HydroShape buildClusteredSableParityShape(
            final ServerSubLevel subLevel,
            final BoundingBox3ic bounds,
            final boolean detailed,
            final BlockPos.MutableBlockPos pos,
            final double spanX,
            final double spanY,
            final double spanZ
    ) {
        final int cells = MAX_HYDRO_ELEMENTS;
        final double[] volume = new double[cells];
        final double[] momentX = new double[cells];
        final double[] momentY = new double[cells];
        final double[] momentZ = new double[cells];
        final double[] minX = filled(cells, Double.POSITIVE_INFINITY);
        final double[] minY = filled(cells, Double.POSITIVE_INFINITY);
        final double[] minZ = filled(cells, Double.POSITIVE_INFINITY);
        final double[] maxX = filled(cells, Double.NEGATIVE_INFINITY);
        final double[] maxY = filled(cells, Double.NEGATIVE_INFINITY);
        final double[] maxZ = filled(cells, Double.NEGATIVE_INFINITY);

        final int subdivisions = detailed ? 2 : 1;
        final double half = detailed ? 0.25D : 0.5D;
        final double offset = detailed ? 0.25D : 0.5D;
        final double stride = detailed ? 0.5D : 0.0D;
        final double divisor = detailed ? 8.0D : 1.0D;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    pos.set(x, y, z);
                    final var state = subLevel.getLevel().getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                    final double blockVolume = PhysicsBlockPropertyHelper.getVolume(state);
                    if (!(blockVolume > 0.0D) || !Double.isFinite(blockVolume)) continue;
                    final double sampleVolume = blockVolume / divisor;

                    for (int sx = 0; sx < subdivisions; sx++) {
                        for (int sy = 0; sy < subdivisions; sy++) {
                            for (int sz = 0; sz < subdivisions; sz++) {
                                final double cx = x + offset + sx * stride;
                                final double cy = y + offset + sy * stride;
                                final double cz = z + offset + sz * stride;
                                final int bx = bin(cx, bounds.minX(), spanX);
                                final int by = bin(cy, bounds.minY(), spanY);
                                final int bz = bin(cz, bounds.minZ(), spanZ);
                                final int cell = bx + CLUSTER_AXIS * (bz + CLUSTER_AXIS * by);
                                volume[cell] += sampleVolume;
                                momentX[cell] += cx * sampleVolume;
                                momentY[cell] += cy * sampleVolume;
                                momentZ[cell] += cz * sampleVolume;
                                minX[cell] = Math.min(minX[cell], cx - half);
                                minY[cell] = Math.min(minY[cell], cy - half);
                                minZ[cell] = Math.min(minZ[cell], cz - half);
                                maxX[cell] = Math.max(maxX[cell], cx + half);
                                maxY[cell] = Math.max(maxY[cell], cy + half);
                                maxZ[cell] = Math.max(maxZ[cell], cz + half);
                            }
                        }
                    }
                }
            }
        }

        int count = 0;
        for (final double value : volume) if (value > 0.0D) count++;
        if (count == 0) return null;
        final HydroShape result = new HydroShape(count);
        int output = 0;
        for (int i = 0; i < cells; i++) {
            if (!(volume[i] > 0.0D)) continue;
            final double cx = momentX[i] / volume[i];
            final double cy = momentY[i] / volume[i];
            final double cz = momentZ[i] / volume[i];
            result.set(output++, cx, cy, cz,
                    Math.max(maxX[i] - cx, cx - minX[i]),
                    Math.max(maxY[i] - cy, cy - minY[i]),
                    Math.max(maxZ[i] - cz, cz - minZ[i]),
                    volume[i]);
        }
        result.size = output;
        result.finishBounds();
        return result;
    }

    private static HydroShape buildShape(final PlotShape shape) {
        if (shape.boxes().isEmpty()) return null;
        if (shape.boxes().size() > DETAILED_BOX_LIMIT) return buildClusteredShape(shape);

        final int count = shape.boxes().size() * 8;
        final HydroShape result = new HydroShape(count);
        int index = 0;
        for (final PlotShape.Box box : shape.boxes()) {
            final double sizeX = box.maxX() - box.minX();
            final double sizeY = box.maxY() - box.minY();
            final double sizeZ = box.maxZ() - box.minZ();
            if (!(sizeX > 0.0D && sizeY > 0.0D && sizeZ > 0.0D)) continue;

            final double halfX = sizeX * 0.25D;
            final double halfY = sizeY * 0.25D;
            final double halfZ = sizeZ * 0.25D;
            final double elementVolume = sizeX * sizeY * sizeZ / 8.0D;
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        result.set(index++,
                                box.minX() + halfX * (x * 2.0D + 1.0D),
                                box.minY() + halfY * (y * 2.0D + 1.0D),
                                box.minZ() + halfZ * (z * 2.0D + 1.0D),
                                halfX, halfY, halfZ, elementVolume);
                    }
                }
            }
        }
        result.size = index;
        result.finishBounds();
        return index == 0 ? null : result;
    }

    private static HydroShape buildClusteredShape(final PlotShape shape) {
        final int cells = CLUSTER_AXIS * CLUSTER_AXIS * CLUSTER_AXIS;
        final double[] volume = new double[cells];
        final double[] momentX = new double[cells];
        final double[] momentY = new double[cells];
        final double[] momentZ = new double[cells];
        final double[] minX = filled(cells, Double.POSITIVE_INFINITY);
        final double[] minY = filled(cells, Double.POSITIVE_INFINITY);
        final double[] minZ = filled(cells, Double.POSITIVE_INFINITY);
        final double[] maxX = filled(cells, Double.NEGATIVE_INFINITY);
        final double[] maxY = filled(cells, Double.NEGATIVE_INFINITY);
        final double[] maxZ = filled(cells, Double.NEGATIVE_INFINITY);

        final double spanX = Math.max(1.0D, shape.maxX() + 1.0D - shape.minX());
        final double spanY = Math.max(1.0D, shape.maxY() + 1.0D - shape.minY());
        final double spanZ = Math.max(1.0D, shape.maxZ() + 1.0D - shape.minZ());

        for (final PlotShape.Box box : shape.boxes()) {
            final double boxVolume = (box.maxX() - box.minX())
                    * (box.maxY() - box.minY())
                    * (box.maxZ() - box.minZ());
            if (!(boxVolume > 0.0D)) continue;
            final double centerX = (box.minX() + box.maxX()) * 0.5D;
            final double centerY = (box.minY() + box.maxY()) * 0.5D;
            final double centerZ = (box.minZ() + box.maxZ()) * 0.5D;
            final int x = bin(centerX, shape.minX(), spanX);
            final int y = bin(centerY, shape.minY(), spanY);
            final int z = bin(centerZ, shape.minZ(), spanZ);
            final int index = x + CLUSTER_AXIS * (z + CLUSTER_AXIS * y);

            volume[index] += boxVolume;
            momentX[index] += centerX * boxVolume;
            momentY[index] += centerY * boxVolume;
            momentZ[index] += centerZ * boxVolume;
            minX[index] = Math.min(minX[index], box.minX());
            minY[index] = Math.min(minY[index], box.minY());
            minZ[index] = Math.min(minZ[index], box.minZ());
            maxX[index] = Math.max(maxX[index], box.maxX());
            maxY[index] = Math.max(maxY[index], box.maxY());
            maxZ[index] = Math.max(maxZ[index], box.maxZ());
        }

        int count = 0;
        for (final double value : volume) if (value > 0.0D) count++;
        if (count == 0) return null;

        final HydroShape result = new HydroShape(count);
        int output = 0;
        for (int i = 0; i < cells; i++) {
            if (!(volume[i] > 0.0D)) continue;
            result.set(output++,
                    momentX[i] / volume[i], momentY[i] / volume[i], momentZ[i] / volume[i],
                    Math.max(maxX[i] - momentX[i] / volume[i], momentX[i] / volume[i] - minX[i]),
                    Math.max(maxY[i] - momentY[i] / volume[i], momentY[i] / volume[i] - minY[i]),
                    Math.max(maxZ[i] - momentZ[i] / volume[i], momentZ[i] / volume[i] - minZ[i]),
                    volume[i]);
        }
        result.size = output;
        result.finishBounds();
        return result;
    }

    private static HydroShape fallbackShape(final BoundingBox3ic bounds) {
        if (bounds == null) return null;
        final double minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        final double maxX = bounds.maxX() + 1.0D;
        final double maxY = bounds.maxY() + 1.0D;
        final double maxZ = bounds.maxZ() + 1.0D;
        final HydroShape result = new HydroShape(1);
        result.set(0,
                (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D,
                (maxX - minX) * 0.5D, (maxY - minY) * 0.5D, (maxZ - minZ) * 0.5D,
                (maxX - minX) * (maxY - minY) * (maxZ - minZ));
        result.finishBounds();
        return result;
    }

    private static boolean overlapsFluid(final ServerLevel level, final Pose3d pose, final HydroShape shape) {
        final WorldBounds bounds = worldBounds(pose, shape);
        if (bounds == null) return false;

        final int minX = floor(bounds.minX);
        final int minY = floor(bounds.minY);
        final int minZ = floor(bounds.minZ);
        final int maxX = floor(bounds.maxX - BOUNDS_EPSILON);
        final int maxY = floor(bounds.maxY - BOUNDS_EPSILON);
        final int maxZ = floor(bounds.maxZ - BOUNDS_EPSILON);
        final long cells = (long) (maxX - minX + 1)
                * (maxY - minY + 1)
                * (maxZ - minZ + 1);
        if (cells <= 0L) return false;

        if (cells > MAX_WORLD_FLUID_SCAN) {
            final ForceScratch scratch = FORCE_SCRATCH.get();
            final BlockPos.MutableBlockPos pos = scratch.fluidPos;
            final Vector3d world = scratch.worldCenter;
            final FluidSlice slice = scratch.fluid;
            scratch.fluidCache.begin();
            for (int i = 0; i < shape.size; i++) {
                world.set(shape.centerX[i], shape.centerY[i], shape.centerZ[i]);
                pose.transformPosition(world);
                sampleFluidColumn(level, world, MIN_EXTENT, pos, scratch.fluidCache, slice);
                if (slice.fraction > 0.0D) return true;
            }
            return false;
        }

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    final FluidState fluid = level.getFluidState(pos);
                    if (fluid.isEmpty()) continue;
                    final double top = y + fluid.getHeight(level, pos);
                    if (top > bounds.minY && y < bounds.maxY) return true;
                }
            }
        }
        return false;
    }

    private static WorldBounds worldBounds(final Pose3d pose, final HydroShape shape) {
        if (shape.size == 0) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        final Vector3d corner = new Vector3d();
        for (int mask = 0; mask < 8; mask++) {
            corner.set(
                    (mask & 1) == 0 ? shape.minX : shape.maxX,
                    (mask & 2) == 0 ? shape.minY : shape.maxY,
                    (mask & 4) == 0 ? shape.minZ : shape.maxZ);
            pose.transformPosition(corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new WorldBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void sampleFluidColumn(
            final ServerLevel level,
            final Vector3dc worldCenter,
            final double halfWorldY,
            final BlockPos.MutableBlockPos pos,
            final FluidQueryCache cache,
            final FluidSlice output
    ) {
        output.clear();
        final double low = worldCenter.y() - halfWorldY;
        final double high = worldCenter.y() + halfWorldY;
        final double span = high - low;
        if (!(span > 0.0D)) return;

        final int x = floor(worldCenter.x());
        final int z = floor(worldCenter.z());
        final int minY = floor(low);
        final int maxY = floor(high - BOUNDS_EPSILON);
        double wetHeight = 0.0D;
        double flowX = 0.0D, flowY = 0.0D, flowZ = 0.0D;

        for (int y = minY; y <= maxY; y++) {
            final int cached = cache.query(level, pos, x, y, z);
            if (cached < 0) continue;
            final double fluidTop = cache.fluidTop[cached];
            final double overlap = Math.max(0.0D, Math.min(high, fluidTop) - Math.max(low, y));
            if (!(overlap > 0.0D)) continue;

            wetHeight += overlap;
            flowX += cache.flowX[cached] * overlap;
            flowY += cache.flowY[cached] * overlap;
            flowZ += cache.flowZ[cached] * overlap;
        }

        if (!(wetHeight > 0.0D)) return;
        output.fraction = Math.min(1.0D, wetHeight / span);
        output.flowX = flowX / wetHeight;
        output.flowY = flowY / wetHeight;
        output.flowZ = flowZ / wetHeight;
    }

    private static double[] filled(final int size, final double value) {
        final double[] result = new double[size];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static int bin(final double value, final double min, final double span) {
        return Math.max(0, Math.min(CLUSTER_AXIS - 1,
                (int) ((value - min) * CLUSTER_AXIS / span)));
    }

    private static int floor(final double value) {
        return (int) Math.floor(value);
    }

    private record CachedHydroShape(int revision, HydroShape shape) {}
    private record WorldBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private static final class FluidSlice {
        private double fraction;
        private double flowX;
        private double flowY;
        private double flowZ;

        private void clear() {
            this.fraction = 0.0D;
            this.flowX = 0.0D;
            this.flowY = 0.0D;
            this.flowZ = 0.0D;
        }
    }

    private static final class FluidQueryCache {
        private static final int MASK = FLUID_QUERY_CACHE_SIZE - 1;

        private final long[] keys = new long[FLUID_QUERY_CACHE_SIZE];
        private final int[] generation = new int[FLUID_QUERY_CACHE_SIZE];
        private final double[] fluidTop = new double[FLUID_QUERY_CACHE_SIZE];
        private final double[] flowX = new double[FLUID_QUERY_CACHE_SIZE];
        private final double[] flowY = new double[FLUID_QUERY_CACHE_SIZE];
        private final double[] flowZ = new double[FLUID_QUERY_CACHE_SIZE];
        private int currentGeneration;

        private void begin() {
            if (++this.currentGeneration == 0) {
                java.util.Arrays.fill(this.generation, 0);
                this.currentGeneration = 1;
            }
        }

        private int query(
                final ServerLevel level,
                final BlockPos.MutableBlockPos pos,
                final int x,
                final int y,
                final int z
        ) {
            pos.set(x, y, z);
            final long key = pos.asLong();
            int slot = mix(key) & MASK;
            for (int probes = 0; probes < FLUID_QUERY_CACHE_SIZE; probes++) {
                if (this.generation[slot] != this.currentGeneration) {
                    this.generation[slot] = this.currentGeneration;
                    this.keys[slot] = key;
                    this.flowX[slot] = this.flowY[slot] = this.flowZ[slot] = 0.0D;
                    this.fluidTop[slot] = Double.NEGATIVE_INFINITY;

                    if (level.hasChunkAt(pos)) {
                        final FluidState state = level.getFluidState(pos);
                        if (!state.isEmpty()) {
                            this.fluidTop[slot] = y + state.getHeight(level, pos);
                            final Vec3 flow = state.getFlow(level, pos);
                            this.flowX[slot] = flow.x;
                            this.flowY[slot] = flow.y;
                            this.flowZ[slot] = flow.z;
                        }
                    }
                    return this.fluidTop[slot] > Double.NEGATIVE_INFINITY ? slot : -1;
                }
                if (this.keys[slot] == key) {
                    return this.fluidTop[slot] > Double.NEGATIVE_INFINITY ? slot : -1;
                }
                slot = (slot + 1) & MASK;
            }

            this.generation[0] = this.currentGeneration;
            this.keys[0] = key;
            this.flowX[0] = this.flowY[0] = this.flowZ[0] = 0.0D;
            this.fluidTop[0] = Double.NEGATIVE_INFINITY;
            if (level.hasChunkAt(pos)) {
                final FluidState state = level.getFluidState(pos);
                if (!state.isEmpty()) {
                    this.fluidTop[0] = y + state.getHeight(level, pos);
                    final Vec3 flow = state.getFlow(level, pos);
                    this.flowX[0] = flow.x;
                    this.flowY[0] = flow.y;
                    this.flowZ[0] = flow.z;
                }
            }
            return this.fluidTop[0] > Double.NEGATIVE_INFINITY ? 0 : -1;
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            return (int) value;
        }
    }

    private static final class ForceScratch {
        private final Vector3d linearVelocity = new Vector3d();
        private final Vector3d angularVelocity = new Vector3d();
        private final Vector3d localUp = new Vector3d();
        private final Vector3d axis = new Vector3d();
        private final Vector3d linearImpulse = new Vector3d();
        private final Vector3d angularImpulse = new Vector3d();
        private final Vector3d dragLinearImpulse = new Vector3d();
        private final Vector3d dragAngularImpulse = new Vector3d();
        private final Vector3d angularDragResponse = new Vector3d();
        private final Vector3d predictedAngularResponse = new Vector3d();
        private final Vector3d centerOfBuoyancy = new Vector3d();
        private final Vector3d diagnosticBuoyancyLinear = new Vector3d();
        private final Vector3d diagnosticBuoyancyAngular = new Vector3d();
        private final Vector3d worldCenter = new Vector3d();
        private final Vector3d lever = new Vector3d();
        private final Vector3d pointVelocity = new Vector3d();
        private final Vector3d localFlow = new Vector3d();
        private final Vector3d force = new Vector3d();
        private final Vector3d torque = new Vector3d();
        private final FluidSlice fluid = new FluidSlice();
        private final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        private final FluidQueryCache fluidCache = new FluidQueryCache();
    }

    private static final class HydroShape {
        private int size;
        private final double[] centerX;
        private final double[] centerY;
        private final double[] centerZ;
        private final double[] halfX;
        private final double[] halfY;
        private final double[] halfZ;
        private final double[] volume;
        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        private HydroShape(final int capacity) {
            this.size = capacity;
            this.centerX = new double[capacity];
            this.centerY = new double[capacity];
            this.centerZ = new double[capacity];
            this.halfX = new double[capacity];
            this.halfY = new double[capacity];
            this.halfZ = new double[capacity];
            this.volume = new double[capacity];
        }

        private void set(
                final int index,
                final double centerX, final double centerY, final double centerZ,
                final double halfX, final double halfY, final double halfZ,
                final double volume
        ) {
            this.centerX[index] = centerX;
            this.centerY[index] = centerY;
            this.centerZ[index] = centerZ;
            this.halfX[index] = Math.max(MIN_EXTENT, halfX);
            this.halfY[index] = Math.max(MIN_EXTENT, halfY);
            this.halfZ[index] = Math.max(MIN_EXTENT, halfZ);
            this.volume[index] = volume;
        }

        private void finishBounds() {
            this.minX = this.minY = this.minZ = Double.POSITIVE_INFINITY;
            this.maxX = this.maxY = this.maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < this.size; i++) {
                this.minX = Math.min(this.minX, this.centerX[i] - this.halfX[i]);
                this.minY = Math.min(this.minY, this.centerY[i] - this.halfY[i]);
                this.minZ = Math.min(this.minZ, this.centerZ[i] - this.halfZ[i]);
                this.maxX = Math.max(this.maxX, this.centerX[i] + this.halfX[i]);
                this.maxY = Math.max(this.maxY, this.centerY[i] + this.halfY[i]);
                this.maxZ = Math.max(this.maxZ, this.centerZ[i] + this.halfZ[i]);
            }
        }
    }

    private ScaledFluidForces() {}
}

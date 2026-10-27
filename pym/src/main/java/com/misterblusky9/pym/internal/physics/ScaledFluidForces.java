package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
import java.util.function.Predicate;

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
    private static final Predicate<BlockState> HAS_FLUID = state -> !state.getFluidState().isEmpty();

    private static final Map<ServerLevel, FluidPass> ACTIVE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<UUID, CachedHydroShape> SHAPES = new HashMap<>();
    private static final Set<UUID> ACTIVATION_LOGGED = new HashSet<>();
    private static final Set<UUID> DRAG_LIMIT_LOGGED = new HashSet<>();
    private static final Map<UUID, Long> WRENCH_LOG_TICKS = new HashMap<>();
    private static final ThreadLocal<ForceScratch> FORCE_SCRATCH =
            ThreadLocal.withInitial(ForceScratch::new);

    public static List<ServerSubLevel> suppressNativePass(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            ACTIVE.remove(level);
            return List.of();
        }

        FluidPass pass = ACTIVE.computeIfAbsent(level, ignored -> new FluidPass());
        pass.suppressed.clear();
        pass.active.clear();

        try {
            for (SubLevel candidate : container.getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel subLevel)
                        || subLevel.isRemoved()
                        || !ScaleState.isScaled(subLevel)) {
                    continue;
                }

                HydroShape shape = hydroShape(subLevel);
                if (shape == null || !nativeProbeMayTouchFluid(level, subLevel, shape)) continue;
                if (!ColliderCoordinator.suppressNativeFluidProbe(subLevel)) continue;

                pass.suppressed.add(subLevel);
                if (subLevel.getUniqueId() != null) pass.active.add(subLevel.getUniqueId());
            }
        } catch (RuntimeException failure) {
            try {
                restoreNativePass(pass.suppressed);
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            pass.active.clear();
            throw failure;
        }

        return pass.suppressed;
    }

    public static void restoreNativePass(List<ServerSubLevel> suppressed) {
        RuntimeException failure = null;
        for (ServerSubLevel subLevel : suppressed) {
            if (subLevel.isRemoved()) continue;
            try {
                ColliderCoordinator.restoreAfterNativeFluidProbe(subLevel);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public static void apply(
            ServerSubLevel subLevel,
            RigidBodyHandle handle,
            double timeStep
    ) {
        if (!(timeStep > 0.0D) || subLevel.isRemoved() || !ScaleState.isScaled(subLevel)) return;
        FluidPass pass = ACTIVE.get(subLevel.getLevel());
        if (pass == null || !pass.active.contains(subLevel.getUniqueId())) return;

        HydroShape shape = hydroShape(subLevel);
        Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        if (shape == null || centerOfMass == null) return;

        Pose3d pose = subLevel.logicalPose();
        Quaterniondc orientation = pose.orientation();
        double scale = ScaleState.getServerScale(subLevel);
        if (!ScaleBounds.isValid(scale)) return;

        ForceScratch scratch = FORCE_SCRATCH.get();
        Vector3d linearVelocity = handle.getLinearVelocity(scratch.linearVelocity);
        Vector3d angularVelocity = handle.getAngularVelocity(scratch.angularVelocity);
        orientation.transformInverse(linearVelocity);
        orientation.transformInverse(angularVelocity);

        Vector3d localUp = scratch.localUp.set(0.0D, 1.0D, 0.0D);
        orientation.transformInverse(localUp);
        Vector3d axis = scratch.axis.set(scale, 0.0D, 0.0D);
        orientation.transform(axis);
        double verticalX = Math.abs(axis.y);
        axis.set(0.0D, scale, 0.0D);
        orientation.transform(axis);
        double verticalY = Math.abs(axis.y);
        axis.set(0.0D, 0.0D, scale);
        orientation.transform(axis);
        double verticalZ = Math.abs(axis.y);

        Vector3d linearImpulse = scratch.linearImpulse.zero();
        Vector3d angularImpulse = scratch.angularImpulse.zero();
        Vector3d dragLinearImpulse = scratch.dragLinearImpulse.zero();
        Vector3d dragAngularImpulse = scratch.dragAngularImpulse.zero();
        Vector3d worldCenter = scratch.worldCenter;
        Vector3d lever = scratch.lever;
        Vector3d pointVelocity = scratch.pointVelocity;
        Vector3d localFlow = scratch.localFlow;
        Vector3d force = scratch.force;
        Vector3d torque = scratch.torque;
        FluidSlice fluid = scratch.fluid;
        BlockPos.MutableBlockPos fluidPos = scratch.fluidPos;
        double dragWork = 0.0D;
        double swayScale = Math.min(scale, 1.0D);
        double submergedVolumeTotal = 0.0D;
        double submergedMomentX = 0.0D;
        double submergedMomentY = 0.0D;
        double submergedMomentZ = 0.0D;
        scratch.fluidCache.begin();

        for (int i = 0; i < shape.size; i++) {
            worldCenter.set(shape.centerX[i], shape.centerY[i], shape.centerZ[i]);
            pose.transformPosition(worldCenter);

            double halfWorldY = Math.max(MIN_EXTENT,
                    verticalX * shape.halfX[i]
                            + verticalY * shape.halfY[i]
                            + verticalZ * shape.halfZ[i]);
            sampleFluidColumn(
                    subLevel.getLevel(), worldCenter, halfWorldY,
                    fluidPos, scratch.fluidCache, fluid);
            if (!(fluid.fraction > 0.0D)) continue;

            double submergedVolume = shape.volume[i] * fluid.fraction;
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
            localFlow.sub(linearVelocity).negate();
            angularVelocity.cross(lever, pointVelocity);
            double drag = -LINEAR_DRAG * submergedVolume * timeStep;
            force.set(pointVelocity).mul(swayScale).add(localFlow).mul(drag);
            dragWork += force.dot(localFlow);
            dragLinearImpulse.add(force);
            force.set(pointVelocity).add(localFlow).mul(drag);
            dragWork += force.dot(pointVelocity);
            lever.cross(force, torque);
            dragAngularImpulse.add(torque);
        }

        double dragFactor = stableDragFactor(
                subLevel, dragLinearImpulse, dragAngularImpulse, dragWork, scratch);
        linearImpulse.fma(dragFactor, dragLinearImpulse);
        angularImpulse.fma(dragFactor, dragAngularImpulse);

        if (linearImpulse.lengthSquared() <= ScaleBounds.EPSILON
                && angularImpulse.lengthSquared() <= ScaleBounds.EPSILON) {
            return;
        }

        UUID id = subLevel.getUniqueId();
        logExtremeWrench(
                subLevel, id, scale, centerOfMass,
                submergedVolumeTotal, submergedMomentX, submergedMomentY, submergedMomentZ,
                linearImpulse, angularImpulse, dragLinearImpulse, dragAngularImpulse,
                linearVelocity, angularVelocity, scratch);
        if (PymTrace.enabled() && id != null && dragFactor < 0.999D && DRAG_LIMIT_LOGGED.add(id)) {
            PymTrace.scale(
                    "scaled fluid drag stabilised uuid={} scale={} factor={} rawMass={} dragWork={}",
                    id, scale, dragFactor, subLevel.getMassTracker().getMass(), dragWork);
        }
        if (PymTrace.enabled() && id != null && ACTIVATION_LOGGED.add(id)) {
            PymTrace.scale(
                    "scaled fluid hybrid active uuid={} scale={} elements={} linearImpulse={} angularImpulse={}",
                    id, scale, shape.size, linearImpulse, angularImpulse);
        }
        heldMassCorrection(subLevel, scale, linearImpulse, angularImpulse);
        handle.applyLinearAndAngularImpulse(linearImpulse, angularImpulse, false);
    }

    private static void heldMassCorrection(
            ServerSubLevel subLevel,
            double scale,
            Vector3d linearImpulse,
            Vector3d angularImpulse
    ) {
        double held = RapierBridge.syncedMassScale(subLevel, scale);
        if (ScaleBounds.same(held, scale) || !ScaleBounds.isValid(held)) return;

        double raw = subLevel.getMassTracker().getMass();
        double floor = CoupledMass.solverFloor(subLevel);
        double[] current = ScaledMassData.forceFactors(raw, scale, floor);
        double[] synced = ScaledMassData.forceFactors(raw, held, floor);
        linearImpulse.mul(synced[0] / current[0]);
        angularImpulse.mul(synced[1] / current[1]);
    }

    public static void forget(UUID id) {
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
            ServerSubLevel subLevel,
            UUID id,
            double scale,
            Vector3dc centerOfMass,
            double submergedVolume,
            double submergedMomentX,
            double submergedMomentY,
            double submergedMomentZ,
            Vector3dc totalLinear,
            Vector3dc totalAngular,
            Vector3dc dragLinear,
            Vector3dc dragAngular,
            Vector3dc linearVelocity,
            Vector3dc angularVelocity,
            ForceScratch scratch
    ) {
        if (!PymTrace.enabled() || id == null) return;

        var mass = subLevel.getMassTracker();
        if (mass == null || !(mass.getMass() > ScaleBounds.EPSILON)) return;
        var inverseInertia = mass.getInverseInertiaTensor();
        var inertia = mass.getInertiaTensor();
        if (inverseInertia == null || inertia == null) return;

        double predictedDeltaV = totalLinear.length() / mass.getMass();
        Vector3d predictedAngular = inverseInertia.transform(
                totalAngular, scratch.predictedAngularResponse);
        double predictedDeltaOmega = predictedAngular.length();
        if (predictedDeltaV < DIAGNOSTIC_DELTA_V
                && predictedDeltaOmega < DIAGNOSTIC_DELTA_OMEGA) {
            return;
        }

        long tick = subLevel.getLevel().getGameTime();
        long last = WRENCH_LOG_TICKS.getOrDefault(id, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && tick - last < DIAGNOSTIC_COOLDOWN_TICKS) return;
        WRENCH_LOG_TICKS.put(id, tick);

        Vector3d centerOfBuoyancy = scratch.centerOfBuoyancy;
        if (submergedVolume > ScaleBounds.EPSILON) {
            centerOfBuoyancy.set(
                    submergedMomentX / submergedVolume,
                    submergedMomentY / submergedVolume,
                    submergedMomentZ / submergedVolume);
        } else {
            centerOfBuoyancy.set(Double.NaN);
        }

        Vector3d buoyancyLinear = scratch.diagnosticBuoyancyLinear
                .set(totalLinear).sub(dragLinear);
        Vector3d buoyancyAngular = scratch.diagnosticBuoyancyAngular
                .set(totalAngular).sub(dragAngular);
        PymTrace.scale(
                "scaled fluid extreme wrench uuid={} tick={} scale={} mass={} inertiaDiag=({}, {}, {}) "
                        + "massCOM={} buoyancyCenter={} submergedVolume={} velocity=({}, {}) "
                        + "buoyancyImpulse=({}, {}) dragImpulse=({}, {}) predictedDelta=({}, {})",
                id, tick, scale, mass.getMass(), inertia.m00(), inertia.m11(), inertia.m22(),
                centerOfMass, centerOfBuoyancy, submergedVolume, linearVelocity, angularVelocity,
                buoyancyLinear, buoyancyAngular, dragLinear, dragAngular,
                predictedDeltaV, predictedDeltaOmega);
    }

    private static double stableDragFactor(
            ServerSubLevel subLevel,
            Vector3dc linearDrag,
            Vector3dc angularDrag,
            double dragWork,
            ForceScratch scratch
    ) {
        if (!(dragWork < -ScaleBounds.EPSILON)) return 1.0D;

        var mass = subLevel.getMassTracker();
        if (mass == null || !(mass.getMass() > ScaleBounds.EPSILON)) return 1.0D;
        var inverseInertia = mass.getInverseInertiaTensor();
        if (inverseInertia == null) return 1.0D;

        Vector3d angularResponse = inverseInertia.transform(
                angularDrag, scratch.angularDragResponse);
        double response = linearDrag.lengthSquared() / mass.getMass()
                + angularDrag.dot(angularResponse);
        if (!(response > ScaleBounds.EPSILON) || !Double.isFinite(response)) return 1.0D;

        double factor = -dragWork / response;
        if (!Double.isFinite(factor)) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, factor));
    }

    private static HydroShape hydroShape(ServerSubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        if (id == null) return null;

        PlotShape plotShape = PlotShapeCache.get(subLevel);
        int revision = PlotShapeCache.revision(id);
        CachedHydroShape cached = SHAPES.get(id);
        if (cached != null && cached.revision == revision) return cached.shape;

        HydroShape parityShape = buildSableParityShape(
                subLevel, subLevel.getPlot().getBoundingBox());
        HydroShape built = parityShape != null
                ? parityShape
                : plotShape == null
                        ? fallbackShape(subLevel.getPlot().getBoundingBox())
                        : buildShape(plotShape);
        if (built == null) SHAPES.remove(id);
        else SHAPES.put(id, new CachedHydroShape(revision, built));
        return built;
    }

    private static HydroShape buildSableParityShape(
            ServerSubLevel subLevel,
            BoundingBox3ic bounds
    ) {
        if (bounds == null) return null;
        int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        long spanX = (long) maxX - minX + 1L;
        long spanY = (long) maxY - minY + 1L;
        long spanZ = (long) maxZ - minZ + 1L;
        long scanVolume = spanX * spanY * spanZ;
        if (spanX <= 0L || spanY <= 0L || spanZ <= 0L
                || scanVolume <= 0L || scanVolume > MAX_BUOYANCY_SCAN_VOLUME) {
            return null;
        }

        boolean detailed = (maxX - minX) + (maxY - minY) + (maxZ - minZ) < 10;
        int samplesPerBlock = detailed ? 8 : 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sampleCount = 0;

        countLoop:
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    pos.set(x, y, z);
                    var state = subLevel.getLevel().getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                    double volume = PhysicsBlockPropertyHelper.getVolume(state);
                    if (!(volume > 0.0D) || !Double.isFinite(volume)) continue;
                    sampleCount += samplesPerBlock;
                    if (sampleCount > MAX_HYDRO_ELEMENTS) break countLoop;
                }
            }
        }
        if (sampleCount == 0) return null;

        if (sampleCount <= MAX_HYDRO_ELEMENTS) {
            HydroShape result = new HydroShape(sampleCount);
            int index = 0;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        pos.set(x, y, z);
                        var state = subLevel.getLevel().getBlockState(pos);
                        if (state.isAir()
                                || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                        double volume = PhysicsBlockPropertyHelper.getVolume(state);
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
            HydroShape result,
            int index,
            int x,
            int y,
            int z,
            double blockVolume,
            boolean detailed
    ) {
        if (!detailed) {
            result.set(index++, x + 0.5D, y + 0.5D, z + 0.5D,
                    0.5D, 0.5D, 0.5D, blockVolume);
            return index;
        }

        double sampleVolume = blockVolume / 8.0D;
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
            ServerSubLevel subLevel,
            BoundingBox3ic bounds,
            boolean detailed,
            BlockPos.MutableBlockPos pos,
            double spanX,
            double spanY,
            double spanZ
    ) {
        int cells = MAX_HYDRO_ELEMENTS;
        double[] volume = new double[cells];
        double[] momentX = new double[cells];
        double[] momentY = new double[cells];
        double[] momentZ = new double[cells];
        double[] minX = filled(cells, Double.POSITIVE_INFINITY);
        double[] minY = filled(cells, Double.POSITIVE_INFINITY);
        double[] minZ = filled(cells, Double.POSITIVE_INFINITY);
        double[] maxX = filled(cells, Double.NEGATIVE_INFINITY);
        double[] maxY = filled(cells, Double.NEGATIVE_INFINITY);
        double[] maxZ = filled(cells, Double.NEGATIVE_INFINITY);

        int subdivisions = detailed ? 2 : 1;
        double half = detailed ? 0.25D : 0.5D;
        double offset = detailed ? 0.25D : 0.5D;
        double stride = detailed ? 0.5D : 0.0D;
        double divisor = detailed ? 8.0D : 1.0D;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    pos.set(x, y, z);
                    var state = subLevel.getLevel().getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(subLevel.getLevel(), pos).isEmpty()) continue;
                    double blockVolume = PhysicsBlockPropertyHelper.getVolume(state);
                    if (!(blockVolume > 0.0D) || !Double.isFinite(blockVolume)) continue;
                    double sampleVolume = blockVolume / divisor;

                    for (int sx = 0; sx < subdivisions; sx++) {
                        for (int sy = 0; sy < subdivisions; sy++) {
                            for (int sz = 0; sz < subdivisions; sz++) {
                                double cx = x + offset + sx * stride;
                                double cy = y + offset + sy * stride;
                                double cz = z + offset + sz * stride;
                                int bx = bin(cx, bounds.minX(), spanX);
                                int by = bin(cy, bounds.minY(), spanY);
                                int bz = bin(cz, bounds.minZ(), spanZ);
                                int cell = bx + CLUSTER_AXIS * (bz + CLUSTER_AXIS * by);
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
        for (double value : volume) if (value > 0.0D) count++;
        if (count == 0) return null;
        HydroShape result = new HydroShape(count);
        int output = 0;
        for (int i = 0; i < cells; i++) {
            if (!(volume[i] > 0.0D)) continue;
            double cx = momentX[i] / volume[i];
            double cy = momentY[i] / volume[i];
            double cz = momentZ[i] / volume[i];
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

    private static HydroShape buildShape(PlotShape shape) {
        if (shape.boxes().isEmpty()) return null;
        if (shape.boxes().size() > DETAILED_BOX_LIMIT) return buildClusteredShape(shape);

        int count = shape.boxes().size() * 8;
        HydroShape result = new HydroShape(count);
        int index = 0;
        for (PlotShape.Box box : shape.boxes()) {
            double sizeX = box.maxX() - box.minX();
            double sizeY = box.maxY() - box.minY();
            double sizeZ = box.maxZ() - box.minZ();
            if (!(sizeX > 0.0D && sizeY > 0.0D && sizeZ > 0.0D)) continue;

            double halfX = sizeX * 0.25D;
            double halfY = sizeY * 0.25D;
            double halfZ = sizeZ * 0.25D;
            double elementVolume = sizeX * sizeY * sizeZ / 8.0D;
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

    private static HydroShape buildClusteredShape(PlotShape shape) {
        int cells = CLUSTER_AXIS * CLUSTER_AXIS * CLUSTER_AXIS;
        double[] volume = new double[cells];
        double[] momentX = new double[cells];
        double[] momentY = new double[cells];
        double[] momentZ = new double[cells];
        double[] minX = filled(cells, Double.POSITIVE_INFINITY);
        double[] minY = filled(cells, Double.POSITIVE_INFINITY);
        double[] minZ = filled(cells, Double.POSITIVE_INFINITY);
        double[] maxX = filled(cells, Double.NEGATIVE_INFINITY);
        double[] maxY = filled(cells, Double.NEGATIVE_INFINITY);
        double[] maxZ = filled(cells, Double.NEGATIVE_INFINITY);

        double spanX = Math.max(1.0D, shape.maxX() + 1.0D - shape.minX());
        double spanY = Math.max(1.0D, shape.maxY() + 1.0D - shape.minY());
        double spanZ = Math.max(1.0D, shape.maxZ() + 1.0D - shape.minZ());

        for (PlotShape.Box box : shape.boxes()) {
            double boxVolume = (box.maxX() - box.minX())
                    * (box.maxY() - box.minY())
                    * (box.maxZ() - box.minZ());
            if (!(boxVolume > 0.0D)) continue;
            double centerX = (box.minX() + box.maxX()) * 0.5D;
            double centerY = (box.minY() + box.maxY()) * 0.5D;
            double centerZ = (box.minZ() + box.maxZ()) * 0.5D;
            int x = bin(centerX, shape.minX(), spanX);
            int y = bin(centerY, shape.minY(), spanY);
            int z = bin(centerZ, shape.minZ(), spanZ);
            int index = x + CLUSTER_AXIS * (z + CLUSTER_AXIS * y);

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
        for (double value : volume) if (value > 0.0D) count++;
        if (count == 0) return null;

        HydroShape result = new HydroShape(count);
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

    private static HydroShape fallbackShape(BoundingBox3ic bounds) {
        if (bounds == null) return null;
        double minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        double maxX = bounds.maxX() + 1.0D;
        double maxY = bounds.maxY() + 1.0D;
        double maxZ = bounds.maxZ() + 1.0D;
        HydroShape result = new HydroShape(1);
        result.set(0,
                (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D,
                (maxX - minX) * 0.5D, (maxY - minY) * 0.5D, (maxZ - minZ) * 0.5D,
                (maxX - minX) * (maxY - minY) * (maxZ - minZ));
        result.finishBounds();
        return result;
    }

    private static boolean nativeProbeMayTouchFluid(
            ServerLevel level,
            ServerSubLevel subLevel,
            HydroShape shape
    ) {
        Pose3d pose = subLevel.logicalPose();
        WorldBounds scaled = worldBounds(pose, shape);
        WorldBounds probe = nativeProbeBounds(subLevel, pose);
        if (scaled == null) return probe != null && anyFluidCell(level, probe);
        if (probe == null) return anyFluidCell(level, scaled);
        return anyFluidCell(level, new WorldBounds(
                Math.min(scaled.minX, probe.minX), Math.min(scaled.minY, probe.minY),
                Math.min(scaled.minZ, probe.minZ), Math.max(scaled.maxX, probe.maxX),
                Math.max(scaled.maxY, probe.maxY), Math.max(scaled.maxZ, probe.maxZ)));
    }

    private static WorldBounds nativeProbeBounds(ServerSubLevel subLevel, Pose3d pose) {
        CompiledCollider.Bounds local = ColliderCoordinator.nativeLocalBounds(subLevel);
        var tracker = subLevel.getMassTracker();
        Vector3dc com = tracker == null ? null : tracker.getCenterOfMass();
        if (local == null || com == null) return null;

        Vector3d worldCom = pose.transformPosition(new Vector3d(com));
        Quaterniondc orientation = pose.orientation();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        Vector3d corner = new Vector3d();
        for (int mask = 0; mask < 8; mask++) {
            corner.set(
                    ((mask & 1) == 0 ? local.minX() : local.maxX() + 1.0D) - com.x(),
                    ((mask & 2) == 0 ? local.minY() : local.maxY() + 1.0D) - com.y(),
                    ((mask & 4) == 0 ? local.minZ() : local.maxZ() + 1.0D) - com.z());
            orientation.transform(corner).add(worldCom);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new WorldBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean anyFluidCell(ServerLevel level, WorldBounds bounds) {
        int minX = floor(bounds.minX);
        int minY = Math.max(floor(bounds.minY), level.getMinBuildHeight());
        int minZ = floor(bounds.minZ);
        int maxX = floor(bounds.maxX - BOUNDS_EPSILON);
        int maxY = Math.min(floor(bounds.maxY - BOUNDS_EPSILON), level.getMaxBuildHeight() - 1);
        int maxZ = floor(bounds.maxZ - BOUNDS_EPSILON);
        if (maxX < minX || maxY < minY || maxZ < minZ) return false;

        long budget = MAX_WORLD_FLUID_SCAN;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
                    if (section.hasOnlyAir() || !section.maybeHas(HAS_FLUID)) continue;

                    int x0 = Math.max(minX, chunkX << 4), x1 = Math.min(maxX, (chunkX << 4) + 15);
                    int y0 = Math.max(minY, sectionY << 4), y1 = Math.min(maxY, (sectionY << 4) + 15);
                    int z0 = Math.max(minZ, chunkZ << 4), z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                    budget -= (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
                    if (budget < 0L) return true;
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                if (!section.getFluidState(x & 15, y & 15, z & 15).isEmpty()) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static WorldBounds worldBounds(Pose3d pose, HydroShape shape) {
        if (shape.size == 0) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        Vector3d corner = new Vector3d();
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
            ServerLevel level,
            Vector3dc worldCenter,
            double halfWorldY,
            BlockPos.MutableBlockPos pos,
            FluidQueryCache cache,
            FluidSlice output
    ) {
        output.clear();
        double low = worldCenter.y() - halfWorldY;
        double high = worldCenter.y() + halfWorldY;
        double span = high - low;
        if (!(span > 0.0D)) return;

        int x = floor(worldCenter.x());
        int z = floor(worldCenter.z());
        int minY = floor(low);
        int maxY = floor(high - BOUNDS_EPSILON);
        double wetHeight = 0.0D;
        double flowX = 0.0D, flowY = 0.0D, flowZ = 0.0D;

        for (int y = minY; y <= maxY; y++) {
            int cached = cache.query(level, pos, x, y, z);
            if (cached < 0) continue;
            double fluidTop = cache.fluidTop[cached];
            double overlap = Math.max(0.0D, Math.min(high, fluidTop) - Math.max(low, y));
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

    private static double[] filled(int size, double value) {
        double[] result = new double[size];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static int bin(double value, double min, double span) {
        return Math.max(0, Math.min(CLUSTER_AXIS - 1,
                (int) ((value - min) * CLUSTER_AXIS / span)));
    }

    private static int floor(double value) {
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
                ServerLevel level,
                BlockPos.MutableBlockPos pos,
                int x,
                int y,
                int z
        ) {
            pos.set(x, y, z);
            long key = pos.asLong();
            int slot = mix(key) & MASK;
            for (int probes = 0; probes < FLUID_QUERY_CACHE_SIZE; probes++) {
                if (this.generation[slot] != this.currentGeneration) {
                    this.generation[slot] = this.currentGeneration;
                    this.keys[slot] = key;
                    this.flowX[slot] = this.flowY[slot] = this.flowZ[slot] = 0.0D;
                    this.fluidTop[slot] = Double.NEGATIVE_INFINITY;

                    if (level.hasChunkAt(pos)) {
                        FluidState state = level.getFluidState(pos);
                        if (!state.isEmpty()) {
                            this.fluidTop[slot] = y + state.getHeight(level, pos);
                            Vec3 flow = state.getFlow(level, pos);
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
                FluidState state = level.getFluidState(pos);
                if (!state.isEmpty()) {
                    this.fluidTop[0] = y + state.getHeight(level, pos);
                    Vec3 flow = state.getFlow(level, pos);
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

        private HydroShape(int capacity) {
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
                int index,
                double centerX, double centerY, double centerZ,
                double halfX, double halfY, double halfZ,
                double volume
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

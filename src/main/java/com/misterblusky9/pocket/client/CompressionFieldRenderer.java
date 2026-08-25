package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompressionFieldRenderer {
    // Feel
    private static final float INITIAL_SPEED_FRACTION = 0.35F;
    private static final float CLIMB_TICKS = 9.0F;
    private static final int SHAPE_SAMPLES = 64;

    private static final float MAX_STALL_DEPTH = 0.72F;
    private static final float GRIND_FAST = 2.30F;
    private static final float GRIND_SLOW = 0.83F;

    private static final float RETRACT_SPEED_MULTIPLIER = 4.2F;
    private static final float FRONT_WIDTH = 2.6F;

    public static final int PULSE_TRAVEL_TICKS = 5;
    private static final float PULSE_WIDTH = 1.15F;
    private static final float PULSE_CELL = 1.0F;
    private static final double AIM_RANGE = 160.0D;

    private static final float[] SHRINK_SHEEN = { 0.05F, 0.42F, 0.62F, 0.22F };
    private static final float[] SHRINK_FRONT = { 0.55F, 0.93F, 1.00F, 0.95F };
    private static final float[] GROW_SHEEN = { 0.60F, 0.44F, 0.04F, 0.22F };
    private static final float[] GROW_FRONT = { 1.00F, 0.90F, 0.45F, 0.95F };

    private static final float STRAIN_BOOST = 0.55F;

    private static final float FACE_WORLD_EPSILON = 0.004F;
    private static final float MAX_LOCAL_EPSILON = 0.03F;

    private static final float POCKET_ENCLOSURE_RATIO = 0.8F;
    private static final int POCKET_MIN_NEIGHBOURS = 4;
    private static final float POCKET_SNAP = 0.35F;
    private static final int POCKET_PASSES = 8;

    private static final float JITTER_AMOUNT = 0.45F;
    private static final long MAX_SCAN_VOLUME = 4_000_000L;

    private static final float RESEED_INTERVAL_TICKS = 3.0F;

    private static final AABB FULL_CUBE = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final int SURFACE_NEIGHBOUR_COUNT = 26;
    private static final int[] NEIGHBOUR_DX = new int[SURFACE_NEIGHBOUR_COUNT];
    private static final int[] NEIGHBOUR_DY = new int[SURFACE_NEIGHBOUR_COUNT];
    private static final int[] NEIGHBOUR_DZ = new int[SURFACE_NEIGHBOUR_COUNT];
    private static final float[] NEIGHBOUR_COST = new float[SURFACE_NEIGHBOUR_COUNT];

    private static final Map<UUID, Field> ACTIVE = new ConcurrentHashMap<>();

    static {
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    NEIGHBOUR_DX[i] = dx;
                    NEIGHBOUR_DY[i] = dy;
                    NEIGHBOUR_DZ[i] = dz;
                    NEIGHBOUR_COST[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    i++;
                }
            }
        }
    }

    private enum Phase { ACQUIRING, SEALED, RELEASING }

    // API

    public static void begin(
            final UUID subLevelId,
            final BlockPos hitLocalPos,
            final int acquireTicks,
            final boolean growing,
            final int cellLimit,
            final boolean acquired
    ) {
        if (subLevelId == null || hitLocalPos == null) return;

        final Level level = Minecraft.getInstance().level;
        if (level == null) return;

        final Field existing = ACTIVE.get(subLevelId);
        if (existing != null && existing.phase != Phase.RELEASING) {
            if (!existing.seed.equals(hitLocalPos)) existing.pendingSeed = hitLocalPos.immutable();

            existing.growing = growing;
            existing.cellLimit = cellLimit;
            return;
        }
        if (existing != null) existing.dispose();

        final float now = AnimationTickHolder.getRenderTime(level);
        final Field field = new Field(hitLocalPos.immutable(), now, Math.max(1, acquireTicks));
        field.growing = growing;
        field.cellLimit = cellLimit;
        field.acquired = acquired;
        ACTIVE.put(subLevelId, field);
    }

    public static void pulse(final UUID subLevelId, final UUID sourcePlayerId) {
        final Field field = ACTIVE.get(subLevelId);
        if (field == null) return;

        final Level level = Minecraft.getInstance().level;
        if (level == null) return;

        field.lastPulseTick = AnimationTickHolder.getRenderTime(level);
        field.pulsePlotOrigin = aimedPointOn(level, sourcePlayerId, subLevelId);
    }

    private static Vec3 aimedPointOn(final Level level, final UUID playerId, final UUID subLevelId) {
        if (playerId == null) return null;

        final var player = level.getPlayerByUUID(playerId);
        if (player == null) return null;

        final CompressionAim.Aim aim = CompressionAim.of(player, AIM_RANGE);
        if (aim != null && subLevelId.equals(aim.subLevelId())) return aim.plotHit();

        final Vec3 held = CompressionBeamRenderer.landingOn(playerId, subLevelId);
        if (held == null) return null;

        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return null;
        final var found = container.getSubLevel(subLevelId);
        if (!(found instanceof final dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel)) return null;

        final org.joml.Vector3d plot = subLevel.renderPose()
                .transformPositionInverse(new org.joml.Vector3d(held.x, held.y, held.z));
        return new Vec3(plot.x, plot.y, plot.z);
    }

    public static void release(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        if (field != null && field.phase != Phase.RELEASING) field.phase = Phase.RELEASING;
    }

    public static void cancel(final UUID subLevelId) {
        final Field field = ACTIVE.remove(subLevelId);
        if (field != null) field.dispose();
    }

    public static void clear() {
        for (final Field field : ACTIVE.values()) field.dispose();
        ACTIVE.clear();
    }

    public static boolean isSealed(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        return field != null && field.phase == Phase.SEALED;
    }

    public static float sealedTicks(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        return field == null ? 0.0F : field.sealedTicks;
    }

    public static boolean isGrowing(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        return field != null && field.growing;
    }

    public static float progress(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        return field == null ? 0.0F : field.progress;
    }

    public static boolean isActive(final UUID subLevelId) {
        return ACTIVE.containsKey(subLevelId);
    }

    public static boolean isGripped(final UUID subLevelId) {
        final Field field = ACTIVE.get(subLevelId);
        return field != null && field.phase != Phase.RELEASING;
    }

    // Render
    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            PocketClientFrame.captureFrustum(event.getFrustum());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE.isEmpty()) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final float renderTicks = AnimationTickHolder.getRenderTime(level);
        final Vec3 camera = event.getCamera().getPosition();

        for (final SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final UUID id = subLevel.getUniqueId();
            if (id == null) continue;

            final Field field = ACTIVE.get(id);
            if (field == null) continue;

            if (field.pendingSeed != null && field.surface != null
                    && renderTicks - field.lastReseedTick >= RESEED_INTERVAL_TICKS) {
                reseed(field, subLevel, renderTicks);
            }

            if (field.surface == null) {
                field.surface = buildSurface(subLevel, field.seed, null, field);
                if (field.surface == null || field.surface.faces().isEmpty()) {
                    ACTIVE.remove(id);
                    continue;
                }
                field.mesh = CompressionFieldMesh.upload(field.surface.faces());
                if (field.mesh == null) {
                    ACTIVE.remove(id);
                    continue;
                }

                field.sealedMesh = CompressionFieldMesh.upload(field.surface.sealedFaces());
            }

            final float resistance = resistanceOf(subLevel);
            advance(field, renderTicks, resistance);

            if (field.phase == Phase.RELEASING && field.front <= 0.0F) {
                field.dispose();
                ACTIVE.remove(id);
                continue;
            }

            if (!PocketClientFrame.isPotentiallyVisible(subLevel)) continue;

            draw(event, camera, subLevel, field, renderTicks, resistance);
        }
    }

    private static void draw(
            final RenderLevelStageEvent event,
            final Vec3 camera,
            final ClientSubLevel subLevel,
            final Field field,
            final float renderTicks,
            final float resistance
    ) {
        final var shader = PocketShaders.compressionField();
        if (shader == null) return;

        final Pose3dc pose = subLevel.renderPose();
        final Vector3dc position = pose.position();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Vector3dc scale = pose.scale();
        final Quaterniondc orientation = pose.orientation();

        final Vector3dc baked = field.surface.bakedPivot();

        final Matrix4f modelView = new Matrix4f(event.getModelViewMatrix());
        modelView.translate(
                (float) (position.x() - camera.x),
                (float) (position.y() - camera.y),
                (float) (position.z() - camera.z));
        modelView.rotate(new Quaternionf(
                (float) orientation.x(), (float) orientation.y(),
                (float) orientation.z(), (float) orientation.w()));
        modelView.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        modelView.translate(
                (float) (baked.x() - rotationPoint.x()),
                (float) (baked.y() - rotationPoint.y()),
                (float) (baked.z() - rotationPoint.z()));

        final float uniformScale = (float) Math.max(1.0E-4D, scale.x());
        final float epsilon = Math.min(MAX_LOCAL_EPSILON, FACE_WORLD_EPSILON / uniformScale);

        float strain = 0.0F;
        if (field.phase == Phase.SEALED) {
            final float grind = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_FAST * 1.3F);
            final float shudder = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_SLOW * 2.7F + 0.9F);
            strain = STRAIN_BOOST * (0.25F + 0.75F * grind * grind * shudder)
                    * (0.35F + 0.65F * resistance);
        }

        final Vector3f pulseOrigin = pulseOrigin(field, baked);

        final Vector3d plotCamera = pose.transformPositionInverse(
                new Vector3d(camera.x, camera.y, camera.z));
        final Vector3f localCamera = new Vector3f(
                (float) (plotCamera.x - baked.x()),
                (float) (plotCamera.y - baked.y()),
                (float) (plotCamera.z - baked.z()));

        final CompressionFieldMesh activeMesh =
                field.phase == Phase.SEALED && field.sealedMesh != null
                        ? field.sealedMesh
                        : field.mesh;

        activeMesh.draw(
                shader,
                modelView,
                event.getProjectionMatrix(),
                localCamera,
                epsilon,
                field.front,
                FRONT_WIDTH,
                pulseOrigin,
                pulseRadius(field, renderTicks, pulseOrigin),
                PULSE_WIDTH,
                PULSE_CELL,
                strain,
                field.growing ? GROW_SHEEN : SHRINK_SHEEN,
                field.growing ? GROW_FRONT : SHRINK_FRONT
        );
    }

    private static void reseed(final Field field, final ClientSubLevel subLevel, final float renderTicks) {
        final BlockPos newSeed = field.pendingSeed;
        field.pendingSeed = null;
        field.lastReseedTick = renderTicks;
        if (newSeed == null) return;

        final LongSet covered = new LongOpenHashSet();
        for (final Long2FloatMap.Entry entry : field.surface.distances().long2FloatEntrySet()) {
            if (entry.getFloatValue() <= field.front) covered.add(entry.getLongKey());
        }

        final SurfaceCache rebuilt = buildSurface(subLevel, newSeed, covered, field);
        if (rebuilt == null || rebuilt.faces().isEmpty()) return;

        final CompressionFieldMesh mesh = CompressionFieldMesh.upload(rebuilt.faces());
        if (mesh == null) return;
        final CompressionFieldMesh sealedMesh = CompressionFieldMesh.upload(rebuilt.sealedFaces());

        field.dispose();
        field.seed = newSeed;
        field.surface = rebuilt;
        field.mesh = mesh;
        field.sealedMesh = sealedMesh;
        field.shape = null;

        field.front = 0.0F;
        if (field.phase == Phase.SEALED) field.phase = Phase.ACQUIRING;
    }

    private static Vector3f pulseOrigin(final Field field, final Vector3dc baked) {
        final Vec3 plot = field.pulsePlotOrigin;
        if (plot != null) {
            return new Vector3f(
                    (float) (plot.x - baked.x()),
                    (float) (plot.y - baked.y()),
                    (float) (plot.z - baked.z()));
        }
        return new Vector3f(
                (float) (field.seed.getX() + 0.5D - baked.x()),
                (float) (field.seed.getY() + 0.5D - baked.y()),
                (float) (field.seed.getZ() + 0.5D - baked.z()));
    }

    private static float pulseRadius(
            final Field field,
            final float renderTicks,
            final Vector3f origin
    ) {
        if (field.lastPulseTick < 0.0F) return -1000.0F;

        final float age = renderTicks - field.lastPulseTick;
        if (age < 0.0F || age > PULSE_TRAVEL_TICKS + PULSE_WIDTH) return -1000.0F;

        final float span = field.surface.maxRadius() + origin.length() + PULSE_WIDTH;
        return (age / PULSE_TRAVEL_TICKS) * span;
    }

    private static void advance(final Field field, final float renderTicks, final float resistance) {
        final float deltaTicks = field.lastRenderTick < 0.0F
                ? 0.0F
                : Mth.clamp(renderTicks - field.lastRenderTick, 0.0F, 4.0F);
        field.lastRenderTick = renderTicks;

        final float sealDistance = field.sealDistance();

        if (field.phase == Phase.RELEASING) {
            final float retractPerTick = sealDistance / Math.max(1.0F, field.acquireTicks)
                    * RETRACT_SPEED_MULTIPLIER;
            field.front = Math.max(0.0F, field.front - retractPerTick * deltaTicks);
            field.progress = Mth.clamp(
                    field.front / Math.max(1.0E-4F, field.surface.maxDistance()), 0.0F, 1.0F);
            return;
        }

        final float elapsed = Math.max(0.0F, renderTicks - field.startRenderTick);
        final float u = Mth.clamp(elapsed / field.acquireTicks, 0.0F, 1.0F);

        if (field.acquired) {
            field.front = Math.max(field.front, u * sealDistance);
        } else {
            if (field.shape == null) field.shape = buildShape(field, resistance);
            field.front = Math.max(field.front, sampleShape(field.shape, u) * sealDistance);
        }

        if (u >= 1.0F) {
            field.front = sealDistance;
            if (field.cellLimit > 0) return;
            if (field.phase == Phase.ACQUIRING) {
                field.phase = Phase.SEALED;
                field.sealedTicks = 0.0F;
            } else {
                field.sealedTicks += deltaTicks;
            }
        }

        field.progress = field.surface.maxDistance() <= 1.0E-4F
                ? 1.0F
                : Mth.clamp(field.front / field.surface.maxDistance(), 0.0F, 1.0F);
    }

    private static float[] buildShape(final Field field, final float resistance) {
        final float[] cumulative = new float[SHAPE_SAMPLES];
        final float step = field.acquireTicks / (SHAPE_SAMPLES - 1.0F);

        float total = 0.0F;
        for (int i = 1; i < SHAPE_SAMPLES; i++) {
            final float t = i * step;
            final float climb = INITIAL_SPEED_FRACTION
                    + (1.0F - INITIAL_SPEED_FRACTION) * Mth.clamp(t / CLIMB_TICKS, 0.0F, 1.0F);
            final float fast = 0.5F + 0.5F * Mth.sin(t * GRIND_FAST);
            final float slow = 0.5F + 0.5F * Mth.sin(t * GRIND_SLOW + 1.7F);
            final float stall = MAX_STALL_DEPTH * resistance * (0.35F + 0.65F * fast * slow);
            total += Math.max(0.08F, climb * (1.0F - stall));
            cumulative[i] = total;
        }

        if (total <= 1.0E-5F) {
            for (int i = 0; i < SHAPE_SAMPLES; i++) cumulative[i] = i / (SHAPE_SAMPLES - 1.0F);
            return cumulative;
        }
        for (int i = 0; i < SHAPE_SAMPLES; i++) cumulative[i] /= total;
        return cumulative;
    }

    private static float sampleShape(final float[] shape, final float u) {
        final float scaled = Mth.clamp(u, 0.0F, 1.0F) * (shape.length - 1);
        final int index = (int) scaled;
        if (index >= shape.length - 1) return shape[shape.length - 1];
        return Mth.lerp(scaled - index, shape[index], shape[index + 1]);
    }

    private static float resistanceOf(final ClientSubLevel subLevel) {
        final double scale = ScaleState.getClientScale(subLevel);
        if (!(scale > 0.0D) || scale >= 1.0D) return 0.0F;
        final double depth = -Math.log(scale) / Math.log(2.0D);
        return Mth.clamp((float) (depth / 4.0D), 0.0F, 1.0F);
    }

    // Surface

    private static SurfaceCache buildSurface(
            final ClientSubLevel subLevel,
            final BlockPos requestedHit,
            final LongSet covered,
            final Field field
    ) {
        final VoxelScan scan = scanFor(subLevel, field);
        if (scan == null) return null;

        final MutableCell seed = nearestCell(scan, requestedHit);
        if (seed == null) return null;

        solveSurfaceDistances(scan, seed);
        sealEnclosedPockets(scan);

        float maxDistance = 0.0F;
        for (final MutableCell cell : scan.cells()) {
            float distance = cell.distance;
            if (!Float.isFinite(distance)) {
                final double dx = cell.x - requestedHit.getX();
                final double dy = cell.y - requestedHit.getY();
                final double dz = cell.z - requestedHit.getZ();
                distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            if (covered != null && covered.contains(cell.key)) {
                distance = 0.0F;
            } else {
                distance = Math.max(0.0F, distance + jitter(cell.key) * JITTER_AMOUNT);
            }
            cell.distance = distance;
            maxDistance = Math.max(maxDistance, cell.distance);
        }

        final Vector3dc pivot = new Vector3d(subLevel.renderPose().rotationPoint());
        final List<CompressionFieldMesh.Face> faces = new ArrayList<>();
        final Long2FloatOpenHashMap distances = new Long2FloatOpenHashMap(scan.cells().length * 2);
        distances.defaultReturnValue(Float.POSITIVE_INFINITY);

        float maxRadius = 0.0F;
        for (final MutableCell cell : scan.cells()) {
            emitFaces(faces, cell, pivot, cell.distance);
            distances.put(cell.key, cell.distance);

            final double rx = cell.x + 0.5D - pivot.x();
            final double ry = cell.y + 0.5D - pivot.y();
            final double rz = cell.z + 0.5D - pivot.z();
            maxRadius = Math.max(maxRadius, (float) Math.sqrt(rx * rx + ry * ry + rz * rz));
        }

        if (faces.isEmpty()) return null;

        final List<CompressionFieldMesh.Face> sealedFaces = buildSealedFaces(scan.cells(), pivot);

        return new SurfaceCache(
                faces,
                sealedFaces.isEmpty() ? faces : sealedFaces,
                maxDistance,
                maxRadius,
                scan.cells().length,
                pivot,
                distances
        );
    }

    private static void emitFaces(
            final List<CompressionFieldMesh.Face> output,
            final MutableCell cell,
            final Vector3dc pivot,
            final float distance
    ) {
        final int mask = cell.exposedMask;
        for (final Direction direction : DIRECTIONS) {
            if ((mask & (1 << direction.ordinal())) == 0) continue;
            emitFace(output, cell, pivot, direction, distance);
        }
    }

    private static void emitFace(
            final List<CompressionFieldMesh.Face> output,
            final MutableCell cell,
            final Vector3dc pivot,
            final Direction direction,
            final float distance
    ) {
        final double x = cell.x - pivot.x();
        final double y = cell.y - pivot.y();
        final double z = cell.z - pivot.z();
        final AABB shape = cell.shape;

        switch (direction) {
            case DOWN -> output.add(new CompressionFieldMesh.Face(
                    Direction.DOWN, y + shape.minY,
                    x + shape.minX, x + shape.maxX,
                    z + shape.minZ, z + shape.maxZ,
                    distance));
            case UP -> output.add(new CompressionFieldMesh.Face(
                    Direction.UP, y + shape.maxY,
                    x + shape.minX, x + shape.maxX,
                    z + shape.minZ, z + shape.maxZ,
                    distance));
            case NORTH -> output.add(new CompressionFieldMesh.Face(
                    Direction.NORTH, z + shape.minZ,
                    x + shape.minX, x + shape.maxX,
                    y + shape.minY, y + shape.maxY,
                    distance));
            case SOUTH -> output.add(new CompressionFieldMesh.Face(
                    Direction.SOUTH, z + shape.maxZ,
                    x + shape.minX, x + shape.maxX,
                    y + shape.minY, y + shape.maxY,
                    distance));
            case WEST -> output.add(new CompressionFieldMesh.Face(
                    Direction.WEST, x + shape.minX,
                    z + shape.minZ, z + shape.maxZ,
                    y + shape.minY, y + shape.maxY,
                    distance));
            case EAST -> output.add(new CompressionFieldMesh.Face(
                    Direction.EAST, x + shape.maxX,
                    z + shape.minZ, z + shape.maxZ,
                    y + shape.minY, y + shape.maxY,
                    distance));
        }
    }

    private static List<CompressionFieldMesh.Face> buildSealedFaces(
            final MutableCell[] cells,
            final Vector3dc pivot
    ) {
        final List<CompressionFieldMesh.Face> output = new ArrayList<>();
        final Map<PlaneKey, LongSet> mergeable = new HashMap<>();

        for (final MutableCell cell : cells) {
            final int mask = cell.exposedMask;
            for (final Direction direction : DIRECTIONS) {
                if ((mask & (1 << direction.ordinal())) == 0) continue;

                if (!hasUnitProjection(cell.shape, direction)) {
                    emitFace(output, cell, pivot, direction, 0.0F);
                    continue;
                }

                final double plane = localPlane(cell, pivot, direction);
                final int u = tileU(cell, direction);
                final int v = tileV(cell, direction);
                final PlaneKey key = new PlaneKey(direction, Double.doubleToLongBits(plane));
                mergeable.computeIfAbsent(key, ignored -> new LongOpenHashSet())
                        .add(packUv(u, v));
            }
        }

        for (final Map.Entry<PlaneKey, LongSet> entry : mergeable.entrySet()) {
            emitGreedyPlane(output, entry.getKey(), entry.getValue(), pivot);
        }
        return output;
    }

    private static boolean hasUnitProjection(final AABB shape, final Direction direction) {
        return switch (direction) {
            case DOWN, UP -> isUnit(shape.minX, shape.maxX) && isUnit(shape.minZ, shape.maxZ);
            case NORTH, SOUTH -> isUnit(shape.minX, shape.maxX) && isUnit(shape.minY, shape.maxY);
            case WEST, EAST -> isUnit(shape.minZ, shape.maxZ) && isUnit(shape.minY, shape.maxY);
        };
    }

    private static boolean isUnit(final double min, final double max) {
        return Math.abs(min) <= 1.0E-6D && Math.abs(max - 1.0D) <= 1.0E-6D;
    }

    private static double localPlane(
            final MutableCell cell,
            final Vector3dc pivot,
            final Direction direction
    ) {
        final AABB shape = cell.shape;
        return switch (direction) {
            case DOWN -> cell.y + shape.minY - pivot.y();
            case UP -> cell.y + shape.maxY - pivot.y();
            case NORTH -> cell.z + shape.minZ - pivot.z();
            case SOUTH -> cell.z + shape.maxZ - pivot.z();
            case WEST -> cell.x + shape.minX - pivot.x();
            case EAST -> cell.x + shape.maxX - pivot.x();
        };
    }

    private static int tileU(final MutableCell cell, final Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> cell.x;
            case WEST, EAST -> cell.z;
        };
    }

    private static int tileV(final MutableCell cell, final Direction direction) {
        return switch (direction) {
            case DOWN, UP -> cell.z;
            case NORTH, SOUTH, WEST, EAST -> cell.y;
        };
    }

    private static double pivotU(final Vector3dc pivot, final Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> pivot.x();
            case WEST, EAST -> pivot.z();
        };
    }

    private static double pivotV(final Vector3dc pivot, final Direction direction) {
        return switch (direction) {
            case DOWN, UP -> pivot.z();
            case NORTH, SOUTH, WEST, EAST -> pivot.y();
        };
    }

    private static void emitGreedyPlane(
            final List<CompressionFieldMesh.Face> output,
            final PlaneKey key,
            final LongSet tiles,
            final Vector3dc pivot
    ) {
        if (tiles == null || tiles.isEmpty()) return;

        final LongOpenHashSet remaining = new LongOpenHashSet(tiles);
        final double uPivot = pivotU(pivot, key.direction());
        final double vPivot = pivotV(pivot, key.direction());
        final double plane = Double.longBitsToDouble(key.planeBits());

        while (!remaining.isEmpty()) {
            final long seed = remaining.iterator().nextLong();
            int u0 = unpackU(seed);
            int v0 = unpackV(seed);

            while (remaining.contains(packUv(u0 - 1, v0))) u0--;

            int width = 1;
            while (remaining.contains(packUv(u0 + width, v0))) width++;

            int height = 1;
            heightLoop:
            while (true) {
                final int nextV = v0 + height;
                for (int du = 0; du < width; du++) {
                    if (!remaining.contains(packUv(u0 + du, nextV))) break heightLoop;
                }
                height++;
            }

            for (int dv = 0; dv < height; dv++) {
                for (int du = 0; du < width; du++) {
                    remaining.remove(packUv(u0 + du, v0 + dv));
                }
            }

            output.add(new CompressionFieldMesh.Face(
                    key.direction(),
                    plane,
                    u0 - uPivot,
                    u0 + width - uPivot,
                    v0 - vPivot,
                    v0 + height - vPivot,
                    0.0F
            ));
        }
    }

    private static long packUv(final int u, final int v) {
        return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
    }

    private static int unpackU(final long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackV(final long packed) {
        return (int) packed;
    }

    private static VoxelScan scanFor(final ClientSubLevel subLevel, final Field field) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;

        final VoxelScan cached = field.scan;
        if (cached != null && cached.covers(bounds)) return cached;

        final Level level = subLevel.getLevel();
        if (level == null) return null;

        final int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        final int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();

        final long sizeX = (long) maxX - minX + 1L;
        final long sizeY = (long) maxY - minY + 1L;
        final long sizeZ = (long) maxZ - minZ + 1L;
        final long volume = sizeX * sizeY * sizeZ;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || volume <= 0 || volume > MAX_SCAN_VOLUME) {
            return null;
        }

        final Long2ObjectOpenHashMap<BlockState> occupied = new Long2ObjectOpenHashMap<>();

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;

            final ChunkPos chunkPos = chunk.getPos();
            final int chunkMinX = Math.max(minX, chunkPos.getMinBlockX());
            final int chunkMaxX = Math.min(maxX, chunkPos.getMaxBlockX());
            final int chunkMinZ = Math.max(minZ, chunkPos.getMinBlockZ());
            final int chunkMaxZ = Math.min(maxZ, chunkPos.getMaxBlockZ());
            if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                final LevelChunkSection section = sections[index];
                if (section == null || section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int sectionY0 = Math.max(minY, sectionMinY);
                final int sectionY1 = Math.min(maxY, sectionMinY + 15);
                if (sectionY0 > sectionY1) continue;

                for (int y = sectionY0; y <= sectionY1; y++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            occupied.put(BlockPos.asLong(x, y, z), state);
                        }
                    }
                }
            }
        }

        if (occupied.isEmpty()) return null;

        final List<MutableCell> surface = new ArrayList<>();
        final Long2ObjectOpenHashMap<MutableCell> byKey = new Long2ObjectOpenHashMap<>();
        final BlockPos.MutableBlockPos shapePos = new BlockPos.MutableBlockPos();

        for (final Long2ObjectMap.Entry<BlockState> entry : occupied.long2ObjectEntrySet()) {
            final long packed = entry.getLongKey();
            final int x = BlockPos.getX(packed);
            final int y = BlockPos.getY(packed);
            final int z = BlockPos.getZ(packed);
            final BlockState state = entry.getValue();

            int exposed = 0;
            for (final Direction direction : DIRECTIONS) {
                final long neighbourKey = BlockPos.asLong(
                        x + direction.getStepX(),
                        y + direction.getStepY(),
                        z + direction.getStepZ());
                final BlockState other = occupied.get(neighbourKey);
                if (other == null || !other.canOcclude()) {
                    exposed |= 1 << direction.ordinal();
                }
            }
            if (exposed == 0) continue;

            shapePos.set(x, y, z);
            final BlockPos immutable = shapePos.immutable();
            final MutableCell cell = new MutableCell(
                    surface.size(),
                    packed,
                    x, y, z,
                    exposed,
                    shapeOf(level, immutable, state)
            );
            surface.add(cell);
            byKey.put(packed, cell);
        }

        if (surface.isEmpty()) return null;

        final MutableCell[] cells = surface.toArray(MutableCell[]::new);
        final int[] neighbours = new int[cells.length * SURFACE_NEIGHBOUR_COUNT];
        Arrays.fill(neighbours, -1);

        for (final MutableCell cell : cells) {
            final int base = cell.index * SURFACE_NEIGHBOUR_COUNT;
            for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                final MutableCell next = byKey.get(BlockPos.asLong(
                        cell.x + NEIGHBOUR_DX[n],
                        cell.y + NEIGHBOUR_DY[n],
                        cell.z + NEIGHBOUR_DZ[n]));
                if (next != null) neighbours[base + n] = next.index;
            }
        }

        final VoxelScan scan = new VoxelScan(
                cells, byKey, neighbours,
                minX, minY, minZ, maxX, maxY, maxZ
        );
        field.scan = scan;
        return scan;
    }

    private record VoxelScan(
            MutableCell[] cells,
            Long2ObjectOpenHashMap<MutableCell> byKey,
            int[] neighbours,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        private boolean covers(final BoundingBox3ic bounds) {
            return bounds.minX() == this.minX && bounds.minY() == this.minY && bounds.minZ() == this.minZ
                    && bounds.maxX() == this.maxX && bounds.maxY() == this.maxY && bounds.maxZ() == this.maxZ;
        }
    }

    private static AABB shapeOf(final Level level, final BlockPos pos, final BlockState state) {
        try {
            final VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) return FULL_CUBE;

            final AABB b = shape.bounds();
            if (b.getXsize() <= 1.0E-4D || b.getYsize() <= 1.0E-4D || b.getZsize() <= 1.0E-4D) {
                return FULL_CUBE;
            }
            return b;
        } catch (final RuntimeException ignored) {
            return FULL_CUBE;
        }
    }

    private static void solveSurfaceDistances(final VoxelScan scan, final MutableCell seed) {
        for (final MutableCell cell : scan.cells()) cell.distance = Float.POSITIVE_INFINITY;

        final IndexedMinHeap queue = new IndexedMinHeap(scan.cells());
        seed.distance = 0.0F;
        queue.offerOrDecrease(seed.index);

        while (!queue.isEmpty()) {
            final int currentIndex = queue.poll();
            final MutableCell current = scan.cells()[currentIndex];
            final int base = currentIndex * SURFACE_NEIGHBOUR_COUNT;

            for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                final int nextIndex = scan.neighbours()[base + n];
                if (nextIndex < 0) continue;

                final MutableCell next = scan.cells()[nextIndex];
                final float candidate = current.distance + NEIGHBOUR_COST[n];
                if (candidate < next.distance) {
                    next.distance = candidate;
                    queue.offerOrDecrease(nextIndex);
                }
            }
        }
    }

    private static void sealEnclosedPockets(final VoxelScan scan) {
        for (int pass = 0; pass < POCKET_PASSES; pass++) {
            boolean changed = false;

            for (final MutableCell cell : scan.cells()) {
                if (!Float.isFinite(cell.distance)) continue;

                int neighbours = 0;
                int covered = 0;
                float lastCovered = 0.0F;

                final int base = cell.index * SURFACE_NEIGHBOUR_COUNT;
                for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                    final int nextIndex = scan.neighbours()[base + n];
                    if (nextIndex < 0) continue;
                    final MutableCell next = scan.cells()[nextIndex];
                    if (!Float.isFinite(next.distance)) continue;

                    neighbours++;
                    if (next.distance < cell.distance) {
                        covered++;
                        lastCovered = Math.max(lastCovered, next.distance);
                    }
                }

                if (neighbours < POCKET_MIN_NEIGHBOURS) continue;
                if (covered < Math.ceil(neighbours * POCKET_ENCLOSURE_RATIO)) continue;

                final float snapped = lastCovered + POCKET_SNAP;
                if (snapped < cell.distance - 1.0E-3F) {
                    cell.distance = snapped;
                    changed = true;
                }
            }

            if (!changed) break;
        }
    }

    private static MutableCell nearestCell(final VoxelScan scan, final BlockPos hit) {
        final MutableCell exact = scan.byKey().get(hit.asLong());
        if (exact != null) return exact;

        MutableCell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (final MutableCell cell : scan.cells()) {
            final double dx = cell.x - hit.getX();
            final double dy = cell.y - hit.getY();
            final double dz = cell.z - hit.getZ();
            final double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDistance) {
                bestDistance = d2;
                best = cell;
            }
        }
        return best;
    }

    private static float jitter(final long packedPos) {
        long h = packedPos;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return ((h & 0xFFFFL) / 65535.0F) * 2.0F - 1.0F;
    }

    // State
    private static final class Field {
        private BlockPos seed;
        private volatile BlockPos pendingSeed;
        private float lastReseedTick = -1000.0F;
        private final float startRenderTick;
        private final int acquireTicks;

        private SurfaceCache surface;

        private VoxelScan scan;
        private CompressionFieldMesh mesh;
        private CompressionFieldMesh sealedMesh;
        private float[] shape;
        private volatile Phase phase = Phase.ACQUIRING;
        private float front;
        private float lastRenderTick = -1.0F;
        private volatile float sealedTicks;
        private volatile float progress;
        private volatile float lastPulseTick = -1.0F;

        private volatile Vec3 pulsePlotOrigin;
        private volatile boolean growing;
        private volatile int cellLimit;

        private volatile boolean acquired;

        private Field(final BlockPos hitPos, final float startRenderTick, final int acquireTicks) {
            this.seed = hitPos;
            this.startRenderTick = startRenderTick;
            this.acquireTicks = acquireTicks;
        }

        private float sealDistance() {
            final float full = this.surface.maxDistance() + FRONT_WIDTH;
            if (this.cellLimit <= 0 || this.cellLimit >= this.surface.cellCount()) return full;
            return full * (this.cellLimit / (float) this.surface.cellCount());
        }

        private void dispose() {
            if (this.mesh != null) {
                this.mesh.close();
                this.mesh = null;
            }
            if (this.sealedMesh != null) {
                this.sealedMesh.close();
                this.sealedMesh = null;
            }
        }
    }

    private record SurfaceCache(
            List<CompressionFieldMesh.Face> faces,
            List<CompressionFieldMesh.Face> sealedFaces,
            float maxDistance,
            float maxRadius,
            int cellCount,
            Vector3dc bakedPivot,
            Long2FloatOpenHashMap distances
    ) {}

    private record PlaneKey(Direction direction, long planeBits) {}

    private static final class MutableCell {
        private final int index;
        private final long key;
        private final int x;
        private final int y;
        private final int z;
        private final int exposedMask;
        private final AABB shape;
        private float distance = Float.POSITIVE_INFINITY;

        private MutableCell(
                final int index,
                final long key,
                final int x,
                final int y,
                final int z,
                final int exposedMask,
                final AABB shape
        ) {
            this.index = index;
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.exposedMask = exposedMask;
            this.shape = shape;
        }
    }

    private static final class IndexedMinHeap {
        private final MutableCell[] cells;
        private final int[] heap;
        private final int[] positions;
        private int size;

        private IndexedMinHeap(final MutableCell[] cells) {
            this.cells = cells;
            this.heap = new int[cells.length];
            this.positions = new int[cells.length];
            Arrays.fill(this.positions, -1);
        }

        private boolean isEmpty() {
            return this.size == 0;
        }

        private void offerOrDecrease(final int index) {
            final int position = this.positions[index];
            if (position >= 0) {
                siftUp(position);
                return;
            }

            final int inserted = this.size++;
            this.heap[inserted] = index;
            this.positions[index] = inserted;
            siftUp(inserted);
        }

        private int poll() {
            final int result = this.heap[0];
            this.positions[result] = -1;

            final int last = --this.size;
            if (last > 0) {
                final int replacement = this.heap[last];
                this.heap[0] = replacement;
                this.positions[replacement] = 0;
                siftDown(0);
            }
            return result;
        }

        private void siftUp(int position) {
            final int value = this.heap[position];
            while (position > 0) {
                final int parent = (position - 1) >>> 1;
                final int parentValue = this.heap[parent];
                if (!less(value, parentValue)) break;

                this.heap[position] = parentValue;
                this.positions[parentValue] = position;
                position = parent;
            }
            this.heap[position] = value;
            this.positions[value] = position;
        }

        private void siftDown(int position) {
            final int value = this.heap[position];
            final int half = this.size >>> 1;
            while (position < half) {
                int child = (position << 1) + 1;
                int childValue = this.heap[child];
                final int right = child + 1;
                if (right < this.size && less(this.heap[right], childValue)) {
                    child = right;
                    childValue = this.heap[right];
                }
                if (!less(childValue, value)) break;

                this.heap[position] = childValue;
                this.positions[childValue] = position;
                position = child;
            }
            this.heap[position] = value;
            this.positions[value] = position;
        }

        private boolean less(final int a, final int b) {
            final int compared = Float.compare(this.cells[a].distance, this.cells[b].distance);
            return compared < 0 || (compared == 0 && a < b);
        }
    }

    private CompressionFieldRenderer() {}
}

package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.misterblusky9.pym.api.client.RenderFrame;
import com.misterblusky9.pym.api.Pym;

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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

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

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class CompressionFieldRenderer {
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

    private static final float FACE_LOCAL_OFFSET = 0.004F;
    private static final double PLANE_EPSILON = 1.0E-6D;

    private static final float POCKET_ENCLOSURE_RATIO = 0.8F;
    private static final int POCKET_MIN_NEIGHBOURS = 4;
    private static final float POCKET_SNAP = 0.35F;
    private static final int POCKET_PASSES = 8;

    private static final float JITTER_AMOUNT = 0.45F;
    private static final float COAST_VELOCITY_RETAIN_PER_TICK = 0.92F;
    private static final float COAST_MAX_LEAD = 6.0F;
    private static final float RESUME_VISUAL_SPEED_FRACTION = 0.45F;
    private static final long MAX_SCAN_VOLUME = 4_000_000L;

    private static final float NUCLEUS_COMMIT_INTERVAL_TICKS = 1.0F;
    private static final double NUCLEUS_SAMPLE_SPACING = 0.22D;
    private static final int NUCLEUS_MAX_SAMPLES = 384;
    private static final int NUCLEUS_MAX_CONSECUTIVE_MISSES = 3;
    private static final double NUCLEUS_LOCAL_RADIUS_SQR = 2.25D;
    private static final float NUCLEUS_REVEAL_EPSILON = 0.03F;
    private static final int NUCLEUS_RELAXATION_NODE_BUDGET = 768;
    private static final float EDGE_OFF_RETAIN_PER_TICK = 0.72F;
    private static final float EDGE_ON_RETAIN_PER_TICK = 0.30F;
    private static final float DORMANT_EDGE_ACTIVITY = 0.58F;

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

    public static void begin(
            UUID subLevelId,
            BlockPos hitLocalPos,
            int acquireTicks,
            boolean growing,
            int cellLimit,
            boolean acquired
    ) {
        if (subLevelId == null || hitLocalPos == null) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        Field existing = ACTIVE.get(subLevelId);
        if (existing != null && existing.phase != Phase.RELEASING) {
            if (!existing.seed.equals(hitLocalPos)) existing.pendingSeed = hitLocalPos.immutable();

            if (existing.phase == Phase.ACQUIRING) existing.growing = growing;
            existing.cellLimit = cellLimit;
            return;
        }
        if (existing != null) existing.dispose();

        float now = AnimationTickHolder.getRenderTime(level);
        Field field = new Field(hitLocalPos.immutable(), now, Math.max(1, acquireTicks));
        field.growing = growing;
        field.cellLimit = cellLimit;
        field.acquired = acquired;
        ACTIVE.put(subLevelId, field);
    }

    public static void pulse(UUID subLevelId, UUID sourcePlayerId) {
        Field field = ACTIVE.get(subLevelId);
        if (field == null) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        field.lastPulseTick = AnimationTickHolder.getRenderTime(level);
        field.pulsePlotOrigin = aimedPointOn(level, sourcePlayerId, subLevelId);
    }

    public static void nucleate(UUID subLevelId, Vec3 worldPoint) {
        if (subLevelId == null || worldPoint == null) return;

        Field field = ACTIVE.get(subLevelId);
        if (field == null || field.phase != Phase.ACQUIRING || field.cellLimit > 0) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        SubLevel found = container.getSubLevel(subLevelId);
        if (!(found instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) return;

        Vector3d plot = subLevel.renderPose().transformPositionInverse(
                new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z));
        queueBeamNuclei(field, new Vec3(plot.x, plot.y, plot.z));
    }

    private static Vec3 aimedPointOn(Level level, UUID playerId, UUID subLevelId) {
        if (playerId == null) return null;

        var player = level.getPlayerByUUID(playerId);
        if (player == null) return null;

        CompressionAim.Aim aim = CompressionAim.of(player, AIM_RANGE);
        if (aim != null && subLevelId.equals(aim.subLevelId())) return aim.plotHit();

        Vec3 held = CompressionBeamRenderer.landingOn(playerId, subLevelId);
        if (held == null) return null;

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return null;
        var found = container.getSubLevel(subLevelId);
        if (!(found instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel)) return null;

        org.joml.Vector3d plot = subLevel.renderPose()
                .transformPositionInverse(new org.joml.Vector3d(held.x, held.y, held.z));
        return new Vec3(plot.x, plot.y, plot.z);
    }

    public static void release(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        if (field != null && field.phase != Phase.RELEASING) field.phase = Phase.RELEASING;
    }

    public static void cancel(UUID subLevelId) {
        Field field = ACTIVE.remove(subLevelId);
        if (field != null) field.dispose();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    public static void clear() {
        for (Field field : ACTIVE.values()) field.dispose();
        ACTIVE.clear();
    }

    public static boolean isSealed(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        return field != null && field.phase == Phase.SEALED;
    }

    public static float sealedTicks(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        return field == null ? 0.0F : field.sealedTicks;
    }

    public static boolean isGrowing(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        return field != null && field.growing;
    }

    public static float progress(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        return field == null ? 0.0F : field.progress;
    }

    public static boolean isActive(UUID subLevelId) {
        return ACTIVE.containsKey(subLevelId);
    }

    public static boolean isGripped(UUID subLevelId) {
        Field field = ACTIVE.get(subLevelId);
        return field != null && field.phase != Phase.RELEASING;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        float renderTicks = AnimationTickHolder.getRenderTime(level);
        Vec3 camera = event.getCamera().getPosition();

        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            UUID id = subLevel.getUniqueId();
            if (id == null) continue;

            Field field = ACTIVE.get(id);
            if (field == null) continue;

            if (field.surface == null) {
                field.surface = buildSurface(subLevel, field.seed, field);
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

            if (field.pendingBeamPlotPoint != null) {
                Vec3 pending = field.pendingBeamPlotPoint;
                field.pendingBeamPlotPoint = null;
                queueBeamNuclei(field, pending);
            }
            if (field.pendingSeed != null) {
                MutableCell pending = nearestCell(field.scan, field.pendingSeed);
                field.pendingSeed = null;
                if (pending != null) queueNucleus(field, pending, field.front);
            }
            float resistance = resistanceOf(subLevel);
            boolean acquisitionActive = field.acquired
                    || !CompressionBeamRenderer.hasLockedTarget(id)
                    || CompressionBeamRenderer.isAimingAt(id);
            advance(field, renderTicks, resistance, acquisitionActive);

            if (field.phase == Phase.RELEASING && field.front <= 0.0F) {
                field.dispose();
                ACTIVE.remove(id);
                continue;
            }

            if (!RenderFrame.mayBeVisible(subLevel)) continue;

            if (acquisitionActive) commitNuclei(field, renderTicks);
            draw(event, camera, subLevel, field, renderTicks, resistance,
                    field.phase == Phase.ACQUIRING && !acquisitionActive);
        }
    }

    private static void draw(
            RenderLevelStageEvent event,
            Vec3 camera,
            ClientSubLevel subLevel,
            Field field,
            float renderTicks,
            float resistance,
            boolean halted
    ) {
        var shader = PocketShaders.compressionField();
        if (shader == null) return;

        Pose3dc pose = subLevel.renderPose();
        Vector3dc position = pose.position();
        Vector3dc rotationPoint = pose.rotationPoint();
        Vector3dc scale = pose.scale();
        Quaterniondc orientation = pose.orientation();

        Vector3dc baked = field.surface.bakedPivot();

        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix());
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

        float epsilon = FACE_LOCAL_OFFSET;

        float strain = 0.0F;
        if (field.phase == Phase.SEALED) {
            float grind = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_FAST * 1.3F);
            float shudder = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_SLOW * 2.7F + 0.9F);
            strain = STRAIN_BOOST * (0.25F + 0.75F * grind * grind * shudder)
                    * (0.35F + 0.65F * resistance);
        } else if (field.phase == Phase.ACQUIRING && field.edgeActivity < 0.999F) {
            strain = -(1.0F - field.edgeActivity);
        }

        Vector3f pulseOrigin = pulseOrigin(field, baked);

        Vector3d plotCamera = pose.transformPositionInverse(
                new Vector3d(camera.x, camera.y, camera.z));
        Vector3f localCamera = new Vector3f(
                (float) (plotCamera.x - baked.x()),
                (float) (plotCamera.y - baked.y()),
                (float) (plotCamera.z - baked.z()));

        CompressionFieldMesh activeMesh =
                field.phase == Phase.SEALED && field.sealedMesh != null
                        ? field.sealedMesh
                        : field.mesh;

        activeMesh.draw(
                shader,
                modelView,
                event.getProjectionMatrix(),
                localCamera,
                new Vector3f((float) baked.x(), (float) baked.y(), (float) baked.z()),
                epsilon,
                field.visualFront,
                FRONT_WIDTH,
                pulseOrigin,
                pulseRadius(field, renderTicks, pulseOrigin),
                PULSE_WIDTH,
                PULSE_CELL,
                strain,
                renderTicks,
                halted ? 1.0F : 0.0F,
                field.growing ? GROW_SHEEN : SHRINK_SHEEN,
                field.growing ? GROW_FRONT : SHRINK_FRONT
        );
    }

    private static void queueBeamNuclei(Field field, Vec3 plotPoint) {
        if (field == null || plotPoint == null) return;

        VoxelScan scan = field.scan;
        if (scan == null || field.surface == null) {
            field.pendingBeamPlotPoint = plotPoint;
            return;
        }

        Vec3 previous = field.lastBeamPlotPoint;
        field.lastBeamPlotPoint = plotPoint;

        if (previous != null) {
            double distance = previous.distanceTo(plotPoint);
            if (distance > 1.0E-4D) {
                int steps = Math.min(
                        NUCLEUS_MAX_SAMPLES,
                        Math.max(1, Mth.ceil(distance / NUCLEUS_SAMPLE_SPACING)));
                int misses = 0;
                long lastKey = Long.MIN_VALUE;
                for (int i = 1; i < steps; i++) {
                    Vec3 sample = previous.lerp(plotPoint, i / (double) steps);
                    MutableCell cell = nearestSurfaceCell(scan, sample);
                    if (cell == null) {
                        if (++misses >= NUCLEUS_MAX_CONSECUTIVE_MISSES) break;
                        continue;
                    }
                    misses = 0;
                    if (cell.key == lastKey) continue;
                    lastKey = cell.key;
                    queueNucleus(field, cell, field.front);
                }
            }
        }

        MutableCell cell = nearestSurfaceCell(scan, plotPoint);
        if (cell != null) queueNucleus(field, cell, field.front);
    }

    private static void queueNucleus(Field field, MutableCell cell, float front) {
        if (field == null || cell == null) return;
        if (field.nuclei.contains(cell.key)) return;

        float queued = field.pendingNuclei.get(cell.key);
        if (!Float.isFinite(queued) || front < queued) {
            field.pendingNuclei.put(cell.key, Math.max(0.0F, front));
        }
    }

    private static void commitNuclei(Field field, float renderTicks) {
        if (field == null || field.phase != Phase.ACQUIRING) return;
        if (field.scan == null || field.surface == null) return;
        if (field.pendingNuclei.isEmpty()
                && (field.relaxationQueue == null || field.relaxationQueue.isEmpty())) return;
        if (renderTicks - field.lastNucleusCommitTick < NUCLEUS_COMMIT_INTERVAL_TICKS) return;

        field.lastNucleusCommitTick = renderTicks;

        MutableCell[] cells = field.scan.cells();
        IndexedMinHeap queue = field.relaxationQueue;
        if (queue == null || queue.capacity() != cells.length) {
            queue = new IndexedMinHeap(cells);
            field.relaxationQueue = queue;
        }

        for (it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry entry
                : field.pendingNuclei.long2FloatEntrySet()) {
            long key = entry.getLongKey();
            float sourceFront = entry.getFloatValue();
            field.nuclei.add(key);

            MutableCell cell = field.scan.byKey().get(key);
            if (cell == null) continue;

            float sourceRoute = Math.max(
                    0.0F,
                    sourceFront - jitter(cell.key) * JITTER_AMOUNT - NUCLEUS_REVEAL_EPSILON);
            if (sourceRoute >= cell.routeDistance - 1.0E-4F) continue;

            cell.routeDistance = sourceRoute;
            lowerVisualDistance(field, cell, sourceRoute);
            queue.offerOrDecrease(cell.index);
        }
        field.pendingNuclei.clear();

        int processed = 0;
        while (processed < NUCLEUS_RELAXATION_NODE_BUDGET && !queue.isEmpty()) {
            int currentIndex = queue.poll();
            MutableCell current = cells[currentIndex];
            int base = currentIndex * SURFACE_NEIGHBOUR_COUNT;

            for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                int nextIndex = field.scan.neighbours()[base + n];
                if (nextIndex < 0) continue;

                MutableCell next = cells[nextIndex];
                float candidate = current.routeDistance + NEIGHBOUR_COST[n];
                if (candidate >= next.routeDistance - 1.0E-4F) continue;

                next.routeDistance = candidate;
                lowerVisualDistance(field, next, candidate);
                queue.offerOrDecrease(nextIndex);
            }
            processed++;
        }

        refreshActiveMesh(field);
    }

    private static void lowerVisualDistance(
            Field field,
            MutableCell cell,
            float distance
    ) {
        if (distance >= cell.distance - 1.0E-4F) return;

        cell.distance = distance;
        SurfaceCache surface = field.surface;
        if (surface == null || cell.faceCount <= 0) return;

        float rendered = renderDistance(cell);
        List<CompressionFieldMesh.Face> faces = surface.faces();
        int end = Math.min(faces.size(), cell.faceStart + cell.faceCount);
        for (int i = cell.faceStart; i < end; i++) {
            faces.get(i).setDistance(rendered);
        }

        field.dirtyDirectionMask |= cell.exposedMask;
        field.meshDirty = true;
    }

    private static void refreshActiveMesh(Field field) {
        if (!field.meshDirty || field.mesh == null) return;

        int directions = field.dirtyDirectionMask;
        field.meshDirty = false;
        field.dirtyDirectionMask = 0;
        if (directions != 0) field.mesh.refresh(directions);
    }

    private static Vector3f pulseOrigin(Field field, Vector3dc baked) {
        Vec3 plot = field.pulsePlotOrigin;
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
            Field field,
            float renderTicks,
            Vector3f origin
    ) {
        if (field.lastPulseTick < 0.0F) return -1000.0F;

        float age = renderTicks - field.lastPulseTick;
        if (age < 0.0F || age > PULSE_TRAVEL_TICKS + PULSE_WIDTH) return -1000.0F;

        float span = field.surface.maxRadius() + origin.length() + PULSE_WIDTH;
        return (age / PULSE_TRAVEL_TICKS) * span;
    }

    private static void advance(
            Field field,
            float renderTicks,
            float resistance,
            boolean acquiring
    ) {
        float deltaTicks = field.lastRenderTick < 0.0F
                ? 0.0F
                : Mth.clamp(renderTicks - field.lastRenderTick, 0.0F, 4.0F);
        field.lastRenderTick = renderTicks;

        float sealDistance = field.sealDistance();

        if (field.phase == Phase.RELEASING) {
            float retractPerTick = sealDistance / Math.max(1.0F, field.acquireTicks)
                    * RETRACT_SPEED_MULTIPLIER;
            field.front = Math.max(0.0F, field.front - retractPerTick * deltaTicks);
            field.visualFront = Math.max(field.front, field.visualFront - retractPerTick * deltaTicks);
            field.edgeActivity = 1.0F;
            field.progress = Mth.clamp(
                    field.front / Math.max(1.0E-4F, field.surface.maxDistance()), 0.0F, 1.0F);
            return;
        }

        float previousFront = field.front;
        if (field.phase == Phase.ACQUIRING && acquiring) {
            field.acquireElapsedTicks = Math.min(field.acquireTicks, field.acquireElapsedTicks + deltaTicks);
        }
        float u = Mth.clamp(field.acquireElapsedTicks / field.acquireTicks, 0.0F, 1.0F);

        if (field.acquired) {
            field.front = Math.max(field.front, u * sealDistance);
        } else {
            if (field.shape == null) field.shape = buildShape(field, resistance);
            field.front = Math.max(field.front, sampleShape(field.shape, u) * sealDistance);
        }

        if (field.visualFront < field.front) field.visualFront = field.front;

        if (field.phase == Phase.ACQUIRING) {
            if (acquiring) {
                float logicalVelocity = deltaTicks <= 1.0E-4F
                        ? 0.0F
                        : Math.max(0.0F, (field.front - previousFront) / deltaTicks);
                field.drivenVelocity = Math.max(logicalVelocity, field.drivenVelocity * 0.65F);
                field.coastVelocity = field.drivenVelocity;
                field.edgeActivity = easeRetained(
                        field.edgeActivity, 1.0F, EDGE_ON_RETAIN_PER_TICK, deltaTicks);

                if (field.visualFront > field.front + 1.0E-4F && deltaTicks > 0.0F) {
                    field.visualFront = Math.min(
                            sealDistance,
                            field.visualFront + field.drivenVelocity
                                    * RESUME_VISUAL_SPEED_FRACTION * deltaTicks);
                    if (field.visualFront < field.front) field.visualFront = field.front;
                } else {
                    field.visualFront = field.front;
                }
            } else {
                field.edgeActivity = easeRetained(
                        field.edgeActivity, DORMANT_EDGE_ACTIVITY, EDGE_OFF_RETAIN_PER_TICK, deltaTicks);
                if (deltaTicks > 0.0F && field.coastVelocity > 1.0E-4F) {
                    float lead = Math.max(0.0F, field.visualFront - field.front);
                    float room = Math.max(0.0F, COAST_MAX_LEAD - lead);
                    float move = Math.min(room, field.coastVelocity * deltaTicks);
                    field.visualFront = Math.min(sealDistance, field.visualFront + move);
                    field.coastVelocity *= (float) Math.pow(
                            COAST_VELOCITY_RETAIN_PER_TICK, deltaTicks);
                    if (field.coastVelocity < 1.0E-4F) field.coastVelocity = 0.0F;
                }
            }
        }

        if (u >= 1.0F) {
            field.front = sealDistance;
            field.visualFront = sealDistance;
            if (field.cellLimit > 0) return;
            if (field.phase == Phase.ACQUIRING) {
                field.phase = Phase.SEALED;
                field.sealedTicks = 0.0F;
                field.edgeActivity = 1.0F;
            } else {
                field.sealedTicks += deltaTicks;
            }
        }

        field.progress = field.surface.maxDistance() <= 1.0E-4F
                ? 1.0F
                : Mth.clamp(field.front / field.surface.maxDistance(), 0.0F, 1.0F);
    }

    private static float easeRetained(
            float value,
            float target,
            float retainPerTick,
            float deltaTicks
    ) {
        if (deltaTicks <= 0.0F) return value;
        float retain = (float) Math.pow(retainPerTick, deltaTicks);
        return target + (value - target) * retain;
    }

    private static float[] buildShape(Field field, float resistance) {
        float[] cumulative = new float[SHAPE_SAMPLES];
        float step = field.acquireTicks / (SHAPE_SAMPLES - 1.0F);

        float total = 0.0F;
        for (int i = 1; i < SHAPE_SAMPLES; i++) {
            float t = i * step;
            float climb = INITIAL_SPEED_FRACTION
                    + (1.0F - INITIAL_SPEED_FRACTION) * Mth.clamp(t / CLIMB_TICKS, 0.0F, 1.0F);
            float fast = 0.5F + 0.5F * Mth.sin(t * GRIND_FAST);
            float slow = 0.5F + 0.5F * Mth.sin(t * GRIND_SLOW + 1.7F);
            float stall = MAX_STALL_DEPTH * resistance * (0.35F + 0.65F * fast * slow);
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

    private static float sampleShape(float[] shape, float u) {
        float scaled = Mth.clamp(u, 0.0F, 1.0F) * (shape.length - 1);
        int index = (int) scaled;
        if (index >= shape.length - 1) return shape[shape.length - 1];
        return Mth.lerp(scaled - index, shape[index], shape[index + 1]);
    }

    private static float resistanceOf(ClientSubLevel subLevel) {
        double scale = Pym.scale().of(subLevel);
        if (!(scale > 0.0D) || scale >= 1.0D) return 0.0F;
        double depth = -Math.log(scale) / Math.log(2.0D);
        return Mth.clamp((float) (depth / 4.0D), 0.0F, 1.0F);
    }

    private static SurfaceCache buildSurface(
            ClientSubLevel subLevel,
            BlockPos requestedHit,
            Field field
    ) {
        VoxelScan scan = scanFor(subLevel, field);
        if (scan == null) return null;

        MutableCell seed = nearestCell(scan, requestedHit);
        if (seed == null) return null;

        solveSurfaceDistances(scan, seed);
        sealEnclosedPockets(scan);

        float maxDistance = 0.0F;
        for (MutableCell cell : scan.cells()) {
            if (!Float.isFinite(cell.routeDistance)) {
                double dx = cell.x - requestedHit.getX();
                double dy = cell.y - requestedHit.getY();
                double dz = cell.z - requestedHit.getZ();
                cell.routeDistance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            if (!Float.isFinite(cell.distance)) cell.distance = cell.routeDistance;
            maxDistance = Math.max(maxDistance, renderDistance(cell));
        }

        field.nuclei.add(seed.key);
        field.relaxationQueue = new IndexedMinHeap(scan.cells());

        Vector3dc pivot = new Vector3d(subLevel.renderPose().rotationPoint());
        List<CompressionFieldMesh.Face> faces = new ArrayList<>();

        float maxRadius = 0.0F;
        for (MutableCell cell : scan.cells()) {
            float distance = renderDistance(cell);
            emitFaces(faces, cell, pivot, distance);

            double rx = cell.x + 0.5D - pivot.x();
            double ry = cell.y + 0.5D - pivot.y();
            double rz = cell.z + 0.5D - pivot.z();
            maxRadius = Math.max(maxRadius, (float) Math.sqrt(rx * rx + ry * ry + rz * rz));
        }

        if (faces.isEmpty()) return null;

        List<CompressionFieldMesh.Face> sealedFaces = buildSealedFaces(scan.cells(), pivot);

        return new SurfaceCache(
                faces,
                sealedFaces.isEmpty() ? faces : sealedFaces,
                maxDistance,
                maxRadius,
                scan.cells().length,
                pivot
        );
    }

    private static void emitFaces(
            List<CompressionFieldMesh.Face> output,
            MutableCell cell,
            Vector3dc pivot,
            float distance
    ) {
        int start = output.size();
        for (int i = 0; i < cell.boxes.length; i++) {
            int mask = cell.boxMasks[i];
            for (Direction direction : DIRECTIONS) {
                if ((mask & (1 << direction.ordinal())) == 0) continue;
                emitFace(output, cell, cell.boxes[i], pivot, direction, distance);
            }
        }
        cell.faceStart = start;
        cell.faceCount = output.size() - start;
    }

    private static void emitFace(
            List<CompressionFieldMesh.Face> output,
            MutableCell cell,
            AABB shape,
            Vector3dc pivot,
            Direction direction,
            float distance
    ) {
        double x = cell.x - pivot.x();
        double y = cell.y - pivot.y();
        double z = cell.z - pivot.z();

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
            MutableCell[] cells,
            Vector3dc pivot
    ) {
        List<CompressionFieldMesh.Face> output = new ArrayList<>();
        Map<PlaneKey, LongSet> mergeable = new HashMap<>();

        for (MutableCell cell : cells) {
            for (int i = 0; i < cell.boxes.length; i++) {
                AABB box = cell.boxes[i];
                int mask = cell.boxMasks[i];
                for (Direction direction : DIRECTIONS) {
                    if ((mask & (1 << direction.ordinal())) == 0) continue;

                    if (!hasUnitProjection(box, direction)) {
                        emitFace(output, cell, box, pivot, direction, 0.0F);
                        continue;
                    }

                    double plane = localPlane(cell, box, pivot, direction);
                    int u = tileU(cell, direction);
                    int v = tileV(cell, direction);
                    PlaneKey key = new PlaneKey(direction, Double.doubleToLongBits(plane));
                    mergeable.computeIfAbsent(key, ignored -> new LongOpenHashSet())
                            .add(packUv(u, v));
                }
            }
        }

        for (Map.Entry<PlaneKey, LongSet> entry : mergeable.entrySet()) {
            emitGreedyPlane(output, entry.getKey(), entry.getValue(), pivot);
        }
        return output;
    }

    private static boolean hasUnitProjection(AABB shape, Direction direction) {
        return switch (direction) {
            case DOWN, UP -> isUnit(shape.minX, shape.maxX) && isUnit(shape.minZ, shape.maxZ);
            case NORTH, SOUTH -> isUnit(shape.minX, shape.maxX) && isUnit(shape.minY, shape.maxY);
            case WEST, EAST -> isUnit(shape.minZ, shape.maxZ) && isUnit(shape.minY, shape.maxY);
        };
    }

    private static boolean isUnit(double min, double max) {
        return Math.abs(min) <= 1.0E-6D && Math.abs(max - 1.0D) <= 1.0E-6D;
    }

    private static double localPlane(
            MutableCell cell,
            AABB shape,
            Vector3dc pivot,
            Direction direction
    ) {
        return switch (direction) {
            case DOWN -> cell.y + shape.minY - pivot.y();
            case UP -> cell.y + shape.maxY - pivot.y();
            case NORTH -> cell.z + shape.minZ - pivot.z();
            case SOUTH -> cell.z + shape.maxZ - pivot.z();
            case WEST -> cell.x + shape.minX - pivot.x();
            case EAST -> cell.x + shape.maxX - pivot.x();
        };
    }

    private static int tileU(MutableCell cell, Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> cell.x;
            case WEST, EAST -> cell.z;
        };
    }

    private static int tileV(MutableCell cell, Direction direction) {
        return switch (direction) {
            case DOWN, UP -> cell.z;
            case NORTH, SOUTH, WEST, EAST -> cell.y;
        };
    }

    private static double pivotU(Vector3dc pivot, Direction direction) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> pivot.x();
            case WEST, EAST -> pivot.z();
        };
    }

    private static double pivotV(Vector3dc pivot, Direction direction) {
        return switch (direction) {
            case DOWN, UP -> pivot.z();
            case NORTH, SOUTH, WEST, EAST -> pivot.y();
        };
    }

    private static void emitGreedyPlane(
            List<CompressionFieldMesh.Face> output,
            PlaneKey key,
            LongSet tiles,
            Vector3dc pivot
    ) {
        if (tiles == null || tiles.isEmpty()) return;

        LongOpenHashSet remaining = new LongOpenHashSet(tiles);
        double uPivot = pivotU(pivot, key.direction());
        double vPivot = pivotV(pivot, key.direction());
        double plane = Double.longBitsToDouble(key.planeBits());

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().nextLong();
            int u0 = unpackU(seed);
            int v0 = unpackV(seed);

            while (remaining.contains(packUv(u0 - 1, v0))) u0--;

            int width = 1;
            while (remaining.contains(packUv(u0 + width, v0))) width++;

            int height = 1;
            heightLoop:
            while (true) {
                int nextV = v0 + height;
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

    private static long packUv(int u, int v) {
        return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
    }

    private static int unpackU(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackV(long packed) {
        return (int) packed;
    }

    private static VoxelScan scanFor(ClientSubLevel subLevel, Field field) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;

        VoxelScan cached = field.scan;
        if (cached != null && cached.covers(bounds)) return cached;

        Level level = subLevel.getLevel();
        if (level == null) return null;

        int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();

        long sizeX = (long) maxX - minX + 1L;
        long sizeY = (long) maxY - minY + 1L;
        long sizeZ = (long) maxZ - minZ + 1L;
        long volume = sizeX * sizeY * sizeZ;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || volume <= 0 || volume > MAX_SCAN_VOLUME) {
            return null;
        }

        Long2ObjectOpenHashMap<BlockState> occupied = new Long2ObjectOpenHashMap<>();

        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;

            ChunkPos chunkPos = chunk.getPos();
            int chunkMinX = Math.max(minX, chunkPos.getMinBlockX());
            int chunkMaxX = Math.min(maxX, chunkPos.getMaxBlockX());
            int chunkMinZ = Math.max(minZ, chunkPos.getMinBlockZ());
            int chunkMaxZ = Math.min(maxZ, chunkPos.getMaxBlockZ());
            if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ) continue;

            LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                LevelChunkSection section = sections[index];
                if (section == null || section.hasOnlyAir()) continue;

                int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                int sectionY0 = Math.max(minY, sectionMinY);
                int sectionY1 = Math.min(maxY, sectionMinY + 15);
                if (sectionY0 > sectionY1) continue;

                for (int y = sectionY0; y <= sectionY1; y++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            occupied.put(BlockPos.asLong(x, y, z), state);
                        }
                    }
                }
            }
        }

        if (occupied.isEmpty()) return null;

        List<MutableCell> surface = new ArrayList<>();
        Long2ObjectOpenHashMap<MutableCell> byKey = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos shapePos = new BlockPos.MutableBlockPos();
        Map<BlockState, AABB[]> shapes = new HashMap<>();

        for (Long2ObjectMap.Entry<BlockState> entry : occupied.long2ObjectEntrySet()) {
            long packed = entry.getLongKey();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            AABB[] boxes = boxesOf(level, shapePos.set(x, y, z), entry.getValue(), shapes);

            int[] masks = new int[boxes.length];
            int exposed = 0;
            for (int i = 0; i < boxes.length; i++) {
                for (Direction direction : DIRECTIONS) {
                    if (faceCovered(level, occupied, shapes, shapePos, x, y, z, boxes, i, direction)) continue;
                    masks[i] |= 1 << direction.ordinal();
                }
                exposed |= masks[i];
            }
            if (exposed == 0) continue;

            MutableCell cell = new MutableCell(
                    surface.size(),
                    packed,
                    x, y, z,
                    boxes,
                    masks
            );
            surface.add(cell);
            byKey.put(packed, cell);
        }

        if (surface.isEmpty()) return null;

        MutableCell[] cells = surface.toArray(MutableCell[]::new);
        int[] neighbours = new int[cells.length * SURFACE_NEIGHBOUR_COUNT];
        Arrays.fill(neighbours, -1);

        for (MutableCell cell : cells) {
            int base = cell.index * SURFACE_NEIGHBOUR_COUNT;
            for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                MutableCell next = byKey.get(BlockPos.asLong(
                        cell.x + NEIGHBOUR_DX[n],
                        cell.y + NEIGHBOUR_DY[n],
                        cell.z + NEIGHBOUR_DZ[n]));
                if (next != null) neighbours[base + n] = next.index;
            }
        }

        VoxelScan scan = new VoxelScan(
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
        private boolean covers(BoundingBox3ic bounds) {
            return bounds.minX() == this.minX && bounds.minY() == this.minY && bounds.minZ() == this.minZ
                    && bounds.maxX() == this.maxX && bounds.maxY() == this.maxY && bounds.maxZ() == this.maxZ;
        }
    }

    private static AABB[] boxesOf(
            Level level,
            BlockPos pos,
            BlockState state,
            Map<BlockState, AABB[]> cache
    ) {
        boolean shared = !state.hasOffsetFunction();
        if (shared) {
            AABB[] cached = cache.get(state);
            if (cached != null) return cached;
        }

        AABB[] boxes;
        try {
            boxes = state.getShape(level, pos).toAabbs().stream()
                    .filter(b -> b.getXsize() > 1.0E-4D && b.getYsize() > 1.0E-4D && b.getZsize() > 1.0E-4D)
                    .toArray(AABB[]::new);
        } catch (RuntimeException ignored) {
            boxes = new AABB[0];
        }
        if (boxes.length == 0) boxes = new AABB[] { FULL_CUBE };

        if (shared) cache.put(state, boxes);
        return boxes;
    }

    private static boolean faceCovered(
            Level level,
            Long2ObjectOpenHashMap<BlockState> occupied,
            Map<BlockState, AABB[]> shapes,
            BlockPos.MutableBlockPos scratch,
            int x,
            int y,
            int z,
            AABB[] boxes,
            int index,
            Direction direction
    ) {
        AABB box = boxes[index];
        for (int j = 0; j < boxes.length; j++) {
            if (j != index && covers(boxes[j], 0, box, direction)) return true;
        }

        Direction.Axis axis = direction.getAxis();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = positive ? box.max(axis) : box.min(axis);
        if (Math.abs(plane - (positive ? 1.0D : 0.0D)) > PLANE_EPSILON) return false;

        int nx = x + direction.getStepX();
        int ny = y + direction.getStepY();
        int nz = z + direction.getStepZ();
        BlockState neighbour = occupied.get(BlockPos.asLong(nx, ny, nz));
        if (neighbour == null) return false;

        AABB[] neighbourBoxes = boxesOf(level, scratch.set(nx, ny, nz), neighbour, shapes);
        scratch.set(x, y, z);
        int shift = positive ? 1 : -1;
        for (AABB other : neighbourBoxes) {
            if (covers(other, shift, box, direction)) return true;
        }
        return false;
    }

    private static boolean covers(AABB other, int shift, AABB box, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = positive ? box.max(axis) : box.min(axis);
        double touching = (positive ? other.min(axis) : other.max(axis)) + shift;
        if (Math.abs(touching - plane) > PLANE_EPSILON) return false;

        for (Direction.Axis across : Direction.Axis.values()) {
            if (across == axis) continue;
            if (other.min(across) > box.min(across) + PLANE_EPSILON) return false;
            if (other.max(across) < box.max(across) - PLANE_EPSILON) return false;
        }
        return true;
    }

    private static void solveSurfaceDistances(VoxelScan scan, MutableCell seed) {
        for (MutableCell cell : scan.cells()) {
            cell.routeDistance = Float.POSITIVE_INFINITY;
            cell.distance = Float.POSITIVE_INFINITY;
        }

        IndexedMinHeap queue = new IndexedMinHeap(scan.cells());
        seed.routeDistance = 0.0F;
        queue.offerOrDecrease(seed.index);

        while (!queue.isEmpty()) {
            int currentIndex = queue.poll();
            MutableCell current = scan.cells()[currentIndex];
            int base = currentIndex * SURFACE_NEIGHBOUR_COUNT;

            for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                int nextIndex = scan.neighbours()[base + n];
                if (nextIndex < 0) continue;

                MutableCell next = scan.cells()[nextIndex];
                float candidate = current.routeDistance + NEIGHBOUR_COST[n];
                if (candidate < next.routeDistance) {
                    next.routeDistance = candidate;
                    queue.offerOrDecrease(nextIndex);
                }
            }
        }

        for (MutableCell cell : scan.cells()) cell.distance = cell.routeDistance;
    }

    private static void sealEnclosedPockets(VoxelScan scan) {
        for (int pass = 0; pass < POCKET_PASSES; pass++) {
            boolean changed = false;

            for (MutableCell cell : scan.cells()) {
                if (!Float.isFinite(cell.distance)) continue;

                int neighbours = 0;
                int covered = 0;
                float lastCovered = 0.0F;

                int base = cell.index * SURFACE_NEIGHBOUR_COUNT;
                for (int n = 0; n < SURFACE_NEIGHBOUR_COUNT; n++) {
                    int nextIndex = scan.neighbours()[base + n];
                    if (nextIndex < 0) continue;
                    MutableCell next = scan.cells()[nextIndex];
                    if (!Float.isFinite(next.distance)) continue;

                    neighbours++;
                    if (next.distance < cell.distance) {
                        covered++;
                        lastCovered = Math.max(lastCovered, next.distance);
                    }
                }

                if (neighbours < POCKET_MIN_NEIGHBOURS) continue;
                if (covered < Math.ceil(neighbours * POCKET_ENCLOSURE_RATIO)) continue;

                float snapped = lastCovered + POCKET_SNAP;
                if (snapped < cell.distance - 1.0E-3F) {
                    cell.distance = snapped;
                    changed = true;
                }
            }

            if (!changed) break;
        }
    }

    private static MutableCell nearestSurfaceCell(VoxelScan scan, Vec3 point) {
        if (scan == null || point == null) return null;

        int bx = Mth.floor(point.x);
        int by = Mth.floor(point.y);
        int bz = Mth.floor(point.z);

        MutableCell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    MutableCell cell = scan.byKey().get(BlockPos.asLong(bx + dx, by + dy, bz + dz));
                    if (cell == null) continue;

                    double minX = cell.x + cell.bounds.minX;
                    double minY = cell.y + cell.bounds.minY;
                    double minZ = cell.z + cell.bounds.minZ;
                    double maxX = cell.x + cell.bounds.maxX;
                    double maxY = cell.y + cell.bounds.maxY;
                    double maxZ = cell.z + cell.bounds.maxZ;

                    double px = Mth.clamp(point.x, minX, maxX);
                    double py = Mth.clamp(point.y, minY, maxY);
                    double pz = Mth.clamp(point.z, minZ, maxZ);
                    double ddx = point.x - px;
                    double ddy = point.y - py;
                    double ddz = point.z - pz;
                    double d2 = ddx * ddx + ddy * ddy + ddz * ddz;

                    if (d2 < bestDistance) {
                        bestDistance = d2;
                        best = cell;
                    }
                }
            }
        }

        return bestDistance <= NUCLEUS_LOCAL_RADIUS_SQR ? best : null;
    }

    private static float renderDistance(MutableCell cell) {
        return Math.max(0.0F, cell.distance + jitter(cell.key) * JITTER_AMOUNT);
    }

    private static MutableCell nearestCell(VoxelScan scan, BlockPos hit) {
        MutableCell exact = scan.byKey().get(hit.asLong());
        if (exact != null) return exact;

        MutableCell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (MutableCell cell : scan.cells()) {
            double dx = cell.x - hit.getX();
            double dy = cell.y - hit.getY();
            double dz = cell.z - hit.getZ();
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDistance) {
                bestDistance = d2;
                best = cell;
            }
        }
        return best;
    }

    private static float jitter(long packedPos) {
        long h = packedPos;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return ((h & 0xFFFFL) / 65535.0F) * 2.0F - 1.0F;
    }

    private static final class Field {
        private final BlockPos seed;
        private volatile BlockPos pendingSeed;
        private final LongSet nuclei = new LongOpenHashSet();
        private final Long2FloatOpenHashMap pendingNuclei = new Long2FloatOpenHashMap();
        private Vec3 lastBeamPlotPoint;
        private Vec3 pendingBeamPlotPoint;
        private IndexedMinHeap relaxationQueue;
        private int dirtyDirectionMask;
        private boolean meshDirty;
        private float lastNucleusCommitTick = -1000.0F;
        private final float startRenderTick;
        private final int acquireTicks;
        private float acquireElapsedTicks;

        private SurfaceCache surface;

        private VoxelScan scan;
        private CompressionFieldMesh mesh;
        private CompressionFieldMesh sealedMesh;
        private float[] shape;
        private volatile Phase phase = Phase.ACQUIRING;
        private float front;
        private float visualFront;
        private float drivenVelocity;
        private float coastVelocity;
        private float edgeActivity = 1.0F;
        private float lastRenderTick = -1.0F;
        private volatile float sealedTicks;
        private volatile float progress;
        private volatile float lastPulseTick = -1.0F;

        private volatile Vec3 pulsePlotOrigin;
        private volatile boolean growing;
        private volatile int cellLimit;

        private volatile boolean acquired;

        private Field(BlockPos hitPos, float startRenderTick, int acquireTicks) {
            this.seed = hitPos;
            this.startRenderTick = startRenderTick;
            this.acquireTicks = acquireTicks;
            this.pendingNuclei.defaultReturnValue(Float.POSITIVE_INFINITY);
        }

        private float sealDistance() {
            float full = this.surface.maxDistance() + FRONT_WIDTH;
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
            Vector3dc bakedPivot
    ) {}

    private record PlaneKey(Direction direction, long planeBits) {}

    private static final class MutableCell {
        private final int index;
        private final long key;
        private final int x;
        private final int y;
        private final int z;
        private final int exposedMask;
        private final AABB[] boxes;
        private final int[] boxMasks;
        private final AABB bounds;
        private int faceStart;
        private int faceCount;
        private float routeDistance = Float.POSITIVE_INFINITY;
        private float distance = Float.POSITIVE_INFINITY;

        private MutableCell(
                int index,
                long key,
                int x,
                int y,
                int z,
                AABB[] boxes,
                int[] boxMasks
        ) {
            this.index = index;
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.boxes = boxes;
            this.boxMasks = boxMasks;
            int exposed = 0;
            AABB bounds = boxes[0];
            for (int i = 0; i < boxes.length; i++) {
                exposed |= boxMasks[i];
                bounds = bounds.minmax(boxes[i]);
            }
            this.exposedMask = exposed;
            this.bounds = bounds;
        }
    }

    private static final class IndexedMinHeap {
        private final MutableCell[] cells;
        private final int[] heap;
        private final int[] positions;
        private int size;

        private IndexedMinHeap(MutableCell[] cells) {
            this.cells = cells;
            this.heap = new int[cells.length];
            this.positions = new int[cells.length];
            Arrays.fill(this.positions, -1);
        }

        private boolean isEmpty() {
            return this.size == 0;
        }

        private int capacity() {
            return this.heap.length;
        }

        private void offerOrDecrease(int index) {
            int position = this.positions[index];
            if (position >= 0) {
                siftUp(position);
                return;
            }

            int inserted = this.size++;
            this.heap[inserted] = index;
            this.positions[index] = inserted;
            siftUp(inserted);
        }

        private int poll() {
            int result = this.heap[0];
            this.positions[result] = -1;

            int last = --this.size;
            if (last > 0) {
                int replacement = this.heap[last];
                this.heap[0] = replacement;
                this.positions[replacement] = 0;
                siftDown(0);
            }
            return result;
        }

        private void siftUp(int position) {
            int value = this.heap[position];
            while (position > 0) {
                int parent = (position - 1) >>> 1;
                int parentValue = this.heap[parent];
                if (!less(value, parentValue)) break;

                this.heap[position] = parentValue;
                this.positions[parentValue] = position;
                position = parent;
            }
            this.heap[position] = value;
            this.positions[value] = position;
        }

        private void siftDown(int position) {
            int value = this.heap[position];
            int half = this.size >>> 1;
            while (position < half) {
                int child = (position << 1) + 1;
                int childValue = this.heap[child];
                int right = child + 1;
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

        private boolean less(int a, int b) {
            int compared = Float.compare(this.cells[a].routeDistance, this.cells[b].routeDistance);
            return compared < 0 || (compared == 0 && a < b);
        }
    }

    private CompressionFieldRenderer() {}
}

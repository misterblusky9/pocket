package com.misterblusky9.pocket.pocket;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PocketRenderSnapshot {
    public static final String SIZE_KEY = "preview_size";

    public static final String BLOCKS_KEY = "preview_shell";

    public static final String BLOCKS_V2_KEY = "preview_shell_v2";
    public static final String VIEW_YAW_KEY = "preview_view_yaw";
    public static final String VIEW_PITCH_KEY = "preview_view_pitch";

    public static final float FIXED_PREVIEW_YAW = 45.0F;
    public static final float FIXED_PREVIEW_PITCH = -35.264389F;
    public static final String UPRIGHT_YAW_KEY = "placement_upright_yaw";
    public static final String LOCAL_BOUNDS_KEY = "placement_local_bounds";
    public static final String ROTATION_POINT_X_KEY = "placement_rp_x";
    public static final String ROTATION_POINT_Y_KEY = "placement_rp_y";
    public static final String ROTATION_POINT_Z_KEY = "placement_rp_z";

    public static final int MAX_PREVIEW_BLOCKS = 12_000;

    public static final int DISPLAY_PLATE_PREVIEW_BLOCKS = 24_000;

    private static final int MAX_STORED_PREVIEW_BLOCKS = DISPLAY_PLATE_PREVIEW_BLOCKS;

    public static final int STRIDE = 3;

    public static final int NO_STATE = -1;

    private static final int COARSE_RESOLUTION_MAX = 44;
    private static final int COARSE_RESOLUTION_MIN = 6;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] blocks;
    private final float previewYaw;
    private final float previewPitch;
    private final double uprightYaw;
    private final int[] localBounds;
    private final Vector3d rotationPoint;
    private final int[] wheels;
    private final ListTag copycats;

    public PocketRenderSnapshot(
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final int[] blocks,
            final float previewYaw,
            final float previewPitch,
            final double uprightYaw,
            final int[] localBounds,
            final Vector3dc rotationPoint
    ) {
        this(sizeX, sizeY, sizeZ, blocks, previewYaw, previewPitch, uprightYaw,
                localBounds, rotationPoint, null, null);
    }

    public PocketRenderSnapshot(
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final int[] blocks,
            final float previewYaw,
            final float previewPitch,
            final double uprightYaw,
            final int[] localBounds,
            final Vector3dc rotationPoint,
            final int[] wheels
    ) {
        this(sizeX, sizeY, sizeZ, blocks, previewYaw, previewPitch, uprightYaw,
                localBounds, rotationPoint, wheels, null);
    }

    public PocketRenderSnapshot(
            final int sizeX,
            final int sizeY,
            final int sizeZ,
            final int[] blocks,
            final float previewYaw,
            final float previewPitch,
            final double uprightYaw,
            final int[] localBounds,
            final Vector3dc rotationPoint,
            final int[] wheels,
            final ListTag copycats
    ) {
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        if (blocks == null) {
            this.blocks = new int[0];
        } else {
            final int safeLength =
                    Math.min(blocks.length - (blocks.length % STRIDE), MAX_STORED_PREVIEW_BLOCKS * STRIDE);
            this.blocks = safeLength == blocks.length ? blocks : Arrays.copyOf(blocks, safeLength);
        }
        this.previewYaw = previewYaw;
        this.previewPitch = previewPitch;
        this.uprightYaw = uprightYaw;
        this.localBounds = localBounds == null ? new int[0] : localBounds.clone();
        this.rotationPoint = rotationPoint == null ? new Vector3d() : new Vector3d(rotationPoint);
        this.wheels = wheels == null
                ? new int[0]
                : Arrays.copyOf(wheels, wheels.length - (wheels.length % PocketWheelPreview.STRIDE));
        this.copycats = copycats == null ? new ListTag() : copycats.copy();
    }

    public int sizeX() { return this.sizeX; }
    public int sizeY() { return this.sizeY; }
    public int sizeZ() { return this.sizeZ; }
    public int[] blocks() { return this.blocks; }
    public int blockCount() { return this.blocks.length / STRIDE; }
    public float previewYaw() { return this.previewYaw; }
    public float previewPitch() { return this.previewPitch; }
    public double uprightYaw() { return this.uprightYaw; }
    public int[] localBounds() { return this.localBounds.clone(); }
    public Vector3d rotationPoint() { return new Vector3d(this.rotationPoint); }
    public int[] wheels() { return this.wheels.clone(); }
    public int wheelCount() { return this.wheels.length / PocketWheelPreview.STRIDE; }
    public ListTag copycats() { return this.copycats.copy(); }

    public PocketRenderSnapshot withUprightYaw(final double yaw) {
        return new PocketRenderSnapshot(
                this.sizeX, this.sizeY, this.sizeZ, this.blocks.clone(),
                this.previewYaw, this.previewPitch, yaw, this.localBounds, this.rotationPoint,
                this.wheels, this.copycats
        );
    }

    public boolean hasPlacementGeometry() {
        return this.localBounds.length == 6;
    }

    public boolean hasExactBlockGrid() {
        if (this.localBounds.length != 6) return false;

        final int sourceSizeX = this.localBounds[3] - this.localBounds[0] + 1;
        final int sourceSizeY = this.localBounds[4] - this.localBounds[1] + 1;
        final int sourceSizeZ = this.localBounds[5] - this.localBounds[2] + 1;
        return sourceSizeX == this.sizeX
                && sourceSizeY == this.sizeY
                && sourceSizeZ == this.sizeZ;
    }

    public static PocketRenderSnapshot capture(final ServerSubLevel subLevel) {
        return capture(subLevel, null);
    }

    public static PocketRenderSnapshot capture(final ServerSubLevel subLevel, final Player viewer) {
        return capture(subLevel, viewer, MAX_PREVIEW_BLOCKS);
    }

    public static PocketRenderSnapshot capture(
            final ServerSubLevel subLevel,
            final Player viewer,
            final int requestedPreviewBudget
    ) {
        final int previewBudget = Math.max(1, Math.min(requestedPreviewBudget, MAX_STORED_PREVIEW_BLOCKS));
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final Level level = subLevel.getLevel();
        final Pose3dc pose = subLevel.logicalPose();

        final int sizeX = bounds.maxX() - bounds.minX() + 1;
        final int sizeY = bounds.maxY() - bounds.minY() + 1;
        final int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        final float previewYaw = FIXED_PREVIEW_YAW;
        final float previewPitch = FIXED_PREVIEW_PITCH;
        final double uprightYaw = captureFrontAxis(pose, viewer);
        final int[] placementBounds = {
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()
        };

        final List<Entry> shell = new ArrayList<>();
        final List<PocketWheelPreview.Wheel> wheels = new ArrayList<>();
        final List<PocketCopycatPreview.Entry> copycats = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos chunkPos = chunk.getPos();

            final int chunkMinX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int chunkMaxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int chunkMinZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int chunkMaxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                final LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int minY = Math.max(bounds.minY(), sectionMinY);
                final int maxY = Math.min(bounds.maxY(), sectionMinY + 15);
                if (minY > maxY) continue;

                for (int y = minY; y <= maxY; y++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        for (int x = chunkMinX; x <= chunkMaxX; x++) {
                            final BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            if (!ShellVoxels.isExposed(level, neighbour, x, y, z)) continue;

                            pos.set(x, y, z);

                            if (state.hasBlockEntity()) {
                                final PocketWheelPreview.Wheel wheel = PocketWheelPreview.capture(
                                        level, pos, state,
                                        bounds.minX(), bounds.minY(), bounds.minZ()
                                );
                                if (wheel != null) wheels.add(wheel);

                                final PocketCopycatPreview.Entry copycat = PocketCopycatPreview.capture(
                                        level, pos, state,
                                        bounds.minX(), bounds.minY(), bounds.minZ()
                                );
                                if (copycat != null) copycats.add(copycat);
                            }

                            shell.add(new Entry(
                                    x - bounds.minX(), y - bounds.minY(), z - bounds.minZ(),
                                    ShellVoxels.averageColour(level, pos, state),
                                    Block.getId(state)
                            ));
                        }
                    }
                }
            }
        }

        final boolean fitsExactly = sizeX <= 255 && sizeY <= 255 && sizeZ <= 255
                && shell.size() <= previewBudget;
        if (fitsExactly) {
            return new PocketRenderSnapshot(
                    sizeX, sizeY, sizeZ, encode(shell), previewYaw, previewPitch, uprightYaw,
                    placementBounds, pose.rotationPoint(),
                    PocketWheelPreview.encode(wheels),
                    PocketCopycatPreview.encode(copycats)
            );
        }

        final int maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));

        final int coarseResolutionMax = Math.max(
                COARSE_RESOLUTION_MAX,
                (int) Math.round(COARSE_RESOLUTION_MAX
                        * Math.sqrt(previewBudget / (double) MAX_PREVIEW_BLOCKS))
        );
        for (int resolution = coarseResolutionMax; ; resolution = resolution * 3 / 4) {
            final int gridX = axisCells(sizeX, maxDim, resolution);
            final int gridY = axisCells(sizeY, maxDim, resolution);
            final int gridZ = axisCells(sizeZ, maxDim, resolution);

            final Map<Integer, Entry> cells = new LinkedHashMap<>();
            for (final Entry entry : shell) {
                final int cx = Math.min(gridX - 1, (int) (((long) entry.x() * gridX) / Math.max(1, sizeX)));
                final int cy = Math.min(gridY - 1, (int) (((long) entry.y() * gridY) / Math.max(1, sizeY)));
                final int cz = Math.min(gridZ - 1, (int) (((long) entry.z() * gridZ) / Math.max(1, sizeZ)));
                cells.putIfAbsent(
                        cx + gridX * (cz + gridZ * cy),
                        new Entry(cx, cy, cz, entry.colour(), entry.stateId()));
            }

            if (cells.size() <= previewBudget || resolution <= COARSE_RESOLUTION_MIN) {
                return new PocketRenderSnapshot(
                        gridX, gridY, gridZ, encode(new ArrayList<>(cells.values())),
                        previewYaw, previewPitch, uprightYaw, placementBounds, pose.rotationPoint(),
                        PocketWheelPreview.encode(
                                coarsenWheels(wheels, sizeX, sizeY, sizeZ, gridX, gridY, gridZ))
                );
            }
        }
    }

    private static List<PocketWheelPreview.Wheel> coarsenWheels(
            final List<PocketWheelPreview.Wheel> wheels,
            final int sizeX, final int sizeY, final int sizeZ,
            final int gridX, final int gridY, final int gridZ
    ) {
        if (wheels.isEmpty()) return wheels;

        final double ratio = Math.min(
                gridX / (double) Math.max(1, sizeX),
                Math.min(gridY / (double) Math.max(1, sizeY), gridZ / (double) Math.max(1, sizeZ)));

        final float scaleX = gridX / (float) Math.max(1, sizeX);
        final float scaleY = gridY / (float) Math.max(1, sizeY);
        final float scaleZ = gridZ / (float) Math.max(1, sizeZ);

        final List<PocketWheelPreview.Wheel> coarse = new ArrayList<>(wheels.size());
        for (final PocketWheelPreview.Wheel wheel : wheels) {
            coarse.add(new PocketWheelPreview.Wheel(
                    wheel.x() * scaleX,
                    wheel.y() * scaleY,
                    wheel.z() * scaleZ,
                    wheel.facing(),
                    (float) Math.max(0.05D, wheel.radius() * ratio),
                    wheel.itemId()
            ));
        }
        return coarse;
    }

    private static int axisCells(final int size, final int maxDim, final int resolution) {
        return Math.max(1, (int) Math.ceil(size * (double) resolution / maxDim));
    }

    public void writeTo(final CompoundTag tag) {
        tag.putIntArray(SIZE_KEY, new int[]{this.sizeX, this.sizeY, this.sizeZ});
        tag.putIntArray(BLOCKS_V2_KEY, this.blocks);
        tag.putFloat(VIEW_YAW_KEY, this.previewYaw);
        tag.putFloat(VIEW_PITCH_KEY, this.previewPitch);
        tag.putDouble(UPRIGHT_YAW_KEY, this.uprightYaw);

        if (this.wheels.length > 0) tag.putIntArray(PocketWheelPreview.WHEELS_KEY, this.wheels);
        if (!this.copycats.isEmpty()) tag.put(PocketCopycatPreview.COPYCATS_KEY, this.copycats.copy());

        if (this.localBounds.length == 6) {
            tag.putIntArray(LOCAL_BOUNDS_KEY, this.localBounds);
            tag.putDouble(ROTATION_POINT_X_KEY, this.rotationPoint.x);
            tag.putDouble(ROTATION_POINT_Y_KEY, this.rotationPoint.y);
            tag.putDouble(ROTATION_POINT_Z_KEY, this.rotationPoint.z);
        }
    }

    public static PocketRenderSnapshot readFrom(final CompoundTag tag) {
        if (tag == null || !tag.contains(SIZE_KEY)) return null;

        final int[] size = tag.getIntArray(SIZE_KEY);
        if (size.length != 3) return null;

        final int[] blocks;
        if (tag.contains(BLOCKS_V2_KEY)) {
            blocks = tag.getIntArray(BLOCKS_V2_KEY);
            if ((blocks.length % STRIDE) != 0) return null;
        } else if (tag.contains(BLOCKS_KEY)) {
            final int[] legacy = tag.getIntArray(BLOCKS_KEY);
            if ((legacy.length % 4) != 0) return null;
            blocks = widenLegacy(legacy);
        } else {
            return null;
        }

        final float previewYaw = tag.contains(VIEW_YAW_KEY) ? tag.getFloat(VIEW_YAW_KEY) : 138.0F;
        final float previewPitch = tag.contains(VIEW_PITCH_KEY) ? tag.getFloat(VIEW_PITCH_KEY) : 24.0F;
        final double uprightYaw = tag.contains(UPRIGHT_YAW_KEY) ? tag.getDouble(UPRIGHT_YAW_KEY) : 0.0D;

        final int[] localBounds = tag.contains(LOCAL_BOUNDS_KEY)
                ? tag.getIntArray(LOCAL_BOUNDS_KEY)
                : new int[0];

        final Vector3d rotationPoint = new Vector3d(
                tag.contains(ROTATION_POINT_X_KEY) ? tag.getDouble(ROTATION_POINT_X_KEY) : 0.0D,
                tag.contains(ROTATION_POINT_Y_KEY) ? tag.getDouble(ROTATION_POINT_Y_KEY) : 0.0D,
                tag.contains(ROTATION_POINT_Z_KEY) ? tag.getDouble(ROTATION_POINT_Z_KEY) : 0.0D
        );

        final int[] wheels = tag.contains(PocketWheelPreview.WHEELS_KEY)
                ? tag.getIntArray(PocketWheelPreview.WHEELS_KEY)
                : new int[0];
        final ListTag copycats = tag.contains(PocketCopycatPreview.COPYCATS_KEY, Tag.TAG_LIST)
                ? tag.getList(PocketCopycatPreview.COPYCATS_KEY, Tag.TAG_COMPOUND)
                : new ListTag();

        return new PocketRenderSnapshot(
                size[0], size[1], size[2], blocks,
                previewYaw, previewPitch, uprightYaw,
                localBounds, rotationPoint, wheels, copycats
        );
    }

    private static double captureFrontAxis(final Pose3dc pose, final Player viewer) {
        final double craftHeading = captureHorizontalHeading(pose);
        if (viewer == null) return craftHeading;

        final Vec3 look = viewer.getLookAngle();
        if (look.x * look.x + look.z * look.z < 1.0E-10D) return craftHeading;

        final double lookHeading = Math.atan2(look.x, look.z);
        return lookHeading - craftHeading;
    }

    private static double captureHorizontalHeading(final Pose3dc pose) {
        final Vector3d forward = new Vector3d(0.0D, 0.0D, 1.0D);
        pose.orientation().transform(forward);

        if (forward.x * forward.x + forward.z * forward.z < 1.0E-10D) {
            return 0.0D;
        }

        return Math.atan2(forward.x, forward.z);
    }

    private static int[] encode(final List<Entry> entries) {
        final int[] data = new int[entries.size() * STRIDE];
        int i = 0;
        for (final Entry entry : entries) {
            data[i++] = ShellVoxels.packPosition(entry.x(), entry.y(), entry.z());
            data[i++] = entry.colour();
            data[i++] = entry.stateId();
        }
        return data;
    }

    private static int[] widenLegacy(final int[] legacy) {
        final int[] data = new int[(legacy.length / 4) * STRIDE];
        for (int i = 0, o = 0; i + 3 < legacy.length; i += 4) {
            data[o++] = ShellVoxels.packPosition(legacy[i], legacy[i + 1], legacy[i + 2]);
            data[o++] = legacy[i + 3];
            data[o++] = NO_STATE;
        }
        return data;
    }

    private record Entry(int x, int y, int z, int colour, int stateId) {}
}

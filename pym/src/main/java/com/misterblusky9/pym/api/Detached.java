package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.PlacedSubLevels;
import com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pym.internal.scale.ScaleState;
import com.misterblusky9.pym.internal.mixin.sable.ServerChunkCacheAccessor;
import com.misterblusky9.pym.internal.mixin.sable.SubLevelContainerAccessor;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeBoundsSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Detached {
    private static final Set<ServerSubLevel> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerSubLevel, Rules> RULES = new IdentityHashMap<>();
    private static final Map<ServerSubLevel, BlockPos> ANCHORS = new IdentityHashMap<>();
    private static final Set<ServerSubLevel> BROKEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerSubLevel, Link> LINKS = new IdentityHashMap<>();
    private static final Set<UUID> ADOPTING = new HashSet<>();

    private record Link(ServerSubLevel parent, Pose3d relative) {}

    public static @Nullable ServerSubLevel open(ServerLevel level, UUID id, CompoundTag plot) {
        return open(level, id, plot, level);
    }

    public static @Nullable ServerSubLevel open(ServerLevel level, UUID id, CompoundTag plot, ServerLevel from) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || plot.getInt("log_size") != container.getLogPlotSize()) return null;
        int x = plot.getInt("plot_x");
        int z = plot.getInt("plot_z");
        if (from != level || !free(container, x, z)) {
            if (Plots.move(plot, from, level) == null) return null;
            unreserve(from, x, z);
            x = plot.getInt("plot_x");
            z = plot.getInt("plot_z");
        }

        ServerSubLevel detached = attach(level, container, id, x, z);
        detached.getPlot().load(plot);
        detached.updateLastPose();
        detached.lastNetworkedBoundingBox().set(detached.getPlot().getBoundingBox());
        return detached;
    }

    public static @Nullable ServerSubLevel create(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        int side = 1 << container.getLogSideLength();
        for (int index = container.getOccupancy().nextClearBit(0); index < side * side;
             index = container.getOccupancy().nextClearBit(index + 1)) {
            int x = index & (side - 1);
            int z = index >> container.getLogSideLength();
            if (!free(container, x, z)) continue;
            ServerSubLevel detached = attach(level, container, id, x, z);
            detached.getPlot().newEmptyChunk(detached.getPlot().getCenterChunk());
            detached.updateLastPose();
            detached.lastNetworkedBoundingBox().set(detached.getPlot().getBoundingBox());
            return detached;
        }
        return null;
    }

    private static void unreserve(ServerLevel level, int x, int z) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !Plots.inBounds(container, x, z) || container.getSubLevel(x, z) != null) return;
        container.getOccupancy().clear(container.getIndex(x, z));
        SubLevelOccupancySavedData.getOrLoad(level).setDirty();
    }

    private static boolean free(ServerSubLevelContainer container, int x, int z) {
        int side = 1 << container.getLogSideLength();
        return x >= 0 && z >= 0 && x < side && z < side && container.getSubLevel(x, z) == null;
    }

    private static ServerSubLevel attach(ServerLevel level, ServerSubLevelContainer container, UUID id, int x, int z) {
        ServerSubLevel detached = new ServerSubLevel(level, x + container.getOrigin().x, z + container.getOrigin().y,
                new Pose3d());
        detached.setUniqueId(id);
        BlockPos centre = detached.getPlot().getCenterBlock();
        detached.logicalPose().rotationPoint().set(centre.getX(), centre.getY(), centre.getZ());
        int index = container.getIndex(x, z);
        ((SubLevelContainerAccessor) container).pym$getSubLevels()[index] = detached;
        if (!container.getOccupancy().get(index)) {
            container.getOccupancy().set(index);
            SubLevelOccupancySavedData.getOrLoad(level).setDirty();
        }
        OPEN.add(detached);
        detached.buildMassTracker();
        ((ServerChunkCacheAccessor) level.getChunkSource()).pym$clearCache();
        return detached;
    }

    public static @Nullable ServerSubLevel find(UUID id) {
        for (ServerSubLevel detached : OPEN) {
            if (id.equals(detached.getUniqueId())) return detached;
        }
        return null;
    }

    public static boolean isDetached(@Nullable SubLevel subLevel) {
        return subLevel instanceof ServerSubLevel server && OPEN.contains(server)
                || subLevel != null && PlacedSubLevels.isPlaced(subLevel);
    }

    public static void rules(ServerSubLevel detached, Rules rules) {
        if (!OPEN.contains(detached)) return;
        RULES.put(detached, rules);
        if (rules.interact()) PlacedSubLevels.add(detached);
        else PlacedSubLevels.remove(detached);
    }

    public static Rules rules(@Nullable SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel server) || !OPEN.contains(server)) return Rules.EDITABLE;
        return RULES.getOrDefault(server, Rules.COSMETIC);
    }

    public static void anchor(ServerSubLevel detached, BlockPos anchor) {
        if (OPEN.contains(detached)) ANCHORS.put(detached, anchor.immutable());
    }

    public static @Nullable BlockPos anchor(@Nullable SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel server) || !OPEN.contains(server)) return null;
        return ANCHORS.get(server);
    }

    public static void broke(ServerSubLevel detached) {
        if (OPEN.contains(detached)) BROKEN.add(detached);
    }

    public static void attach(ServerSubLevel child, ServerSubLevel parent) {
        attach(child, parent, relative(parent.logicalPose(), child.logicalPose()));
    }

    public static void attach(ServerSubLevel child, ServerSubLevel parent, Pose3dc relative) {
        if (OPEN.contains(child) && OPEN.contains(parent) && child != parent)
            LINKS.put(child, new Link(parent, new Pose3d(relative)));
    }

    public static void detach(ServerSubLevel child) {
        LINKS.remove(child);
    }

    public static List<ServerSubLevel> children(ServerSubLevel parent) {
        List<ServerSubLevel> children = new ArrayList<>();
        LINKS.forEach((child, link) -> {
            if (link.parent() == parent) children.add(child);
        });
        return children;
    }

    public static @Nullable ServerSubLevel parent(ServerSubLevel child) {
        Link link = LINKS.get(child);
        return link == null ? null : link.parent();
    }

    public static @Nullable Pose3d relative(ServerSubLevel child) {
        Link link = LINKS.get(child);
        return link == null ? null : new Pose3d(link.relative());
    }

    public static Pose3d relative(Pose3dc parent, Pose3dc child) {
        Pose3d relative = new Pose3d();
        relative.position().set(child.position());
        parent.transformPositionInverse(relative.position());
        new Quaterniond(parent.orientation()).conjugate().mul(child.orientation(), relative.orientation());
        relative.scale().set(child.scale()).div(parent.scale().x(), parent.scale().y(), parent.scale().z());
        relative.rotationPoint().set(child.rotationPoint());
        return relative;
    }

    public static Pose3d compose(Pose3dc parent, Pose3dc relative) {
        Pose3d pose = new Pose3d();
        pose.position().set(relative.position());
        parent.transformPosition(pose.position());
        new Quaterniond(parent.orientation()).mul(relative.orientation(), pose.orientation());
        pose.scale().set(parent.scale()).mul(relative.scale());
        pose.rotationPoint().set(relative.rotationPoint());
        return pose;
    }

    public static CompoundTag payload(ServerSubLevel detached) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", detached.getUniqueId());
        tag.put("plot", detached.getPlot().save());
        tag.put("pose", SableNBTUtils.writePose3d(new Pose3d(detached.logicalPose())));
        tag.put("world_bounds", SableNBTUtils.writeBoundingBox(detached.boundingBox()));
        if (detached.getUserDataTag() != null) tag.put("user_data", detached.getUserDataTag().copy());
        return tag;
    }

    public static @Nullable ServerSubLevel adopt(ServerSubLevel subLevel) {
        ServerLevel level = subLevel.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || OPEN.contains(subLevel) || subLevel.isRemoved()) return null;
        SubLevelData backup = SubLevelSerializer.toData(subLevel, List.of());
        Pose3d pose = new Pose3d(subLevel.logicalPose());
        double scale = ScaleState.getServerScale(subLevel);
        pose.scale().set(scale, scale, scale);
        CompoundTag plot = subLevel.getPlot().save();
        subLevel.getPlot().kickAllEntities();
        ADOPTING.add(subLevel.getUniqueId());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } finally {
            ADOPTING.remove(subLevel.getUniqueId());
        }
        ServerSubLevel detached = open(level, subLevel.getUniqueId(), plot);
        if (detached == null) {
            SubLevelSerializer.fullyLoad(level, backup);
            return null;
        }
        detached.logicalPose().set(pose);
        detached.updateLastPose();
        return detached;
    }

    public static boolean adopting(@Nullable SubLevel subLevel) {
        return subLevel != null && ADOPTING.contains(subLevel.getUniqueId());
    }

    public static @Nullable ServerSubLevel release(ServerSubLevel detached) {
        if (!OPEN.contains(detached)) return null;
        ServerLevel level = detached.getLevel();
        Link link = LINKS.get(detached);
        CompoundTag payload = payload(detached);
        close(detached);
        SubLevelData data = SubLevelSerializer.fromData(payload);
        ServerSubLevel released = data == null ? null : SubLevelSerializer.fullyLoad(level, data);
        if (released != null) {
            double scale = SableNBTUtils.readPose3d(payload.getCompound("pose")).scale().x();
            if (!ScaleBounds.same(scale, 1.0D)
                    && Pym.resize().request(released).scaleTo(scale).immediate().submit().accepted()) {
                released.updateMergedMassData(1.0F);
                SubLevelContainer.getContainer(level).physicsSystem().getPipeline().onStatsChanged(released);
                ScaledColliderRebuildQueue.mark(released);
            }
            return released;
        }
        ServerSubLevel back = open(level, payload.getUUID("uuid"), payload.getCompound("plot"));
        if (back != null && link != null) attach(back, link.parent(), link.relative());
        return null;
    }

    public static @Nullable CompoundTag close(ServerSubLevel detached) {
        if (!OPEN.remove(detached)) return null;
        PlacedSubLevels.remove(detached);
        RULES.remove(detached);
        ANCHORS.remove(detached);
        BROKEN.remove(detached);
        LINKS.remove(detached);
        LINKS.values().removeIf(link -> link.parent() == detached);
        CompoundTag plot = detached.getPlot().save();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(detached.getLevel());
        detached.getTrackingPlayers().clear();
        detached.onRemove();
        ((ServerChunkCacheAccessor) detached.getLevel().getChunkSource()).pym$clearCache();
        if (container != null) {
            SubLevel[] slots = ((SubLevelContainerAccessor) container).pym$getSubLevels();
            int index = index(container, detached);
            if (slots[index] == detached) slots[index] = null;
        }
        return plot;
    }

    public static void discard(ServerSubLevel detached) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(detached.getLevel());
        if (close(detached) == null || container == null) return;
        container.getOccupancy().clear(index(container, detached));
        SubLevelOccupancySavedData.getOrLoad(detached.getLevel()).setDirty();
    }

    private static int index(SubLevelContainer container, SubLevel subLevel) {
        return container.getIndex(subLevel.getPlot().plotPos.x - container.getOrigin().x,
                subLevel.getPlot().plotPos.z - container.getOrigin().y);
    }

    public static void tick(ServerTickEvent.Post event) {
        LINKS.forEach((child, link) -> child.logicalPose().set(compose(link.parent().logicalPose(), link.relative())));
        for (ServerSubLevel detached : List.copyOf(OPEN)) {
            detached.getPlot().tick();
            detached.updateLastPose();
            detached.updateBoundingBox();
            syncBounds(detached);
            if (BROKEN.remove(detached) && rules(detached).split()) split(detached);
        }
    }

    private static void split(ServerSubLevel detached) {
        BoundingBox3ic bounds = detached.getPlot().getBoundingBox();
        BlockPos anchor = anchor(detached);
        if (bounds == null || anchor == null) return;
        ServerLevel level = detached.getLevel();
        List<BlockPos> solid = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (!level.getBlockState(pos).isAir()) solid.add(pos.immutable());
        }
        List<List<BlockPos>> parts = Plots.components(solid);
        if (parts.stream().noneMatch(part -> part.contains(anchor))) return;
        for (List<BlockPos> part : parts) {
            if (part.contains(anchor)) continue;
            BoundingBox3i box = BoundingBox3i.from(part);
            if (box == null) continue;
            try {
                SubLevelAssemblyHelper.assembleBlocks(level, part.getFirst(), part, box.expand(1, 1, 1));
            } catch (RuntimeException exception) {
                LogUtils.getLogger().error("Could not split a piece off detached sub-level {}", detached, exception);
            }
        }
    }

    private static void syncBounds(ServerSubLevel detached) {
        BoundingBox3ic bounds = detached.getPlot().getBoundingBox();
        BoundingBox3i sent = detached.lastNetworkedBoundingBox();
        if (bounds.equals(sent)) return;
        sent.set(bounds);
        SubLevelContainer container = SubLevelContainer.getContainer(detached.getLevel());
        if (container == null) return;
        long plot = ChunkPos.asLong(detached.getPlot().plotPos.x - container.getOrigin().x,
                detached.getPlot().plotPos.z - container.getOrigin().y);
        ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(
                new ClientboundChangeBoundsSubLevelPacket(plot, new BoundingBox3i(bounds)));
        for (UUID id : detached.getTrackingPlayers()) {
            if (detached.getLevel().getPlayerByUUID(id) instanceof ServerPlayer player) player.connection.send(packet);
        }
    }

    private Detached() {}
}

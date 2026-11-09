package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pocket.network.BuildRiderPosesPayload;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.Rules;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BuildSites {
    private static final double GRID_WIDTH = 16.0 * 8.0;
    private static final double SKULL_CAP_SCALE = 8.0 / GRID_WIDTH;
    private static final double DRAGON_SCALE = 14.25 / GRID_WIDTH;
    private static final double DRAGON_LIFT_PIXELS = 3.113125;
    private static final double DRAGON_DEPTH_PIXELS = -1.78125;
    private static final double GOGGLES_SCALE = 8.2 / GRID_WIDTH;
    private static final double SKULL_SCALE = 9.5 / GRID_WIDTH;
    private static final double HELMET_SCALE = 10.0 / GRID_WIDTH;
    private static final double GOGGLES_LIFT_PIXELS = -0.40625;
    private static final double SKULL_LIFT_PIXELS = 0.75;
    private static final String OFFSET = "pocket_hat_offset";
    private static final String KEY = "pocket:hat_stand_child";
    private static final String OLD_KEY = "pocket:hatters_stand_child";
    private static final String CHILDREN = "children";

    private record Site(ServerSubLevel hat, BlockPos host) {}

    private static final Map<UUID, Site> SITES = new HashMap<>();
    private static final Set<UUID> UNLOADING = new HashSet<>();
    private static final Map<UUID, Set<UUID>> PUBLISHED = new HashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>();
    private static final int RETRY_TICKS = 100;

    static void replaced(ServerSubLevel before, ServerSubLevel after) {
        SITES.replaceAll((token, site) -> site.hat() == before ? new Site(after, site.host()) : site);
    }

    public static void settleHosting(UUID token) {
        ServerSubLevel hat = live(token);
        if (hat == null) return;
        boolean build = hasBuild(hat);
        if (Hosted.isRoot(hat)) {
            if (!build) {
                Hosted.unhost(hat);
                return;
            }
            List<UUID> split = new ArrayList<>();
            for (ServerSubLevel part : Hosted.takeBroken(hat)) {
                if (Detached.fragmented(part)) split.add(part.getUniqueId());
            }
            if (split.isEmpty() || fold(hat) == null) return;
            for (UUID id : split) {
                ServerSubLevel part = Detached.find(id);
                if (part != null) Detached.broke(part);
            }
            return;
        }
        if (build) tryHost(token, hat);
    }

    public static @Nullable ServerSubLevel unfold(ServerSubLevel part) {
        ServerSubLevel root = Hosted.rootOf(part);
        if (root == null) return part;
        UUID id = part.getUniqueId();
        return fold(root) == null ? null : Detached.find(id);
    }

    private static @Nullable ServerSubLevel fold(ServerSubLevel root) {
        UUID token = token(root);
        BuildHostBlockEntity host = hostOf(root);
        if (host == null) return null;
        ServerSubLevel folded = Hosted.unhost(root);
        if (folded == null) return null;
        track(token, folded, host.getBlockPos());
        follow(token, folded, folded.getLevel(), host.getBlockPos(), host.getOffset());
        for (ServerSubLevel child : Detached.children(folded)) {
            child.logicalPose().set(Detached.compose(folded.logicalPose(), Detached.relative(child)));
            child.updateLastPose();
        }
        return folded;
    }

    static void tryHost(UUID token, ServerSubLevel hat) {
        long now = hat.getLevel().getGameTime();
        if (RETRY.getOrDefault(token, 0L) > now) return;
        if (Hosted.host(hat) == null) RETRY.put(token, now + RETRY_TICKS);
        else HatNativeContraptions.started(token);
    }

    public static @Nullable ServerSubLevel live(UUID token) {
        Site site = SITES.get(token);
        return site == null || site.hat().isRemoved() ? null : site.hat();
    }

    public static @Nullable ServerSubLevel open(ServerLevel level, UUID token) {
        ServerSubLevel live = live(token);
        if (live != null) return live.getLevel() == level ? live : null;
        CarriedBuilds.close(token);
        return load(level, token, true);
    }

    public static @Nullable ServerSubLevel load(ServerLevel level, UUID token) {
        return load(level, token, false);
    }

    private static @Nullable ServerSubLevel load(ServerLevel level, UUID token, boolean riders) {
        ServerLevel from = storedIn(level, token);
        if (from == null) return null;
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(from);
        CompoundTag saved = storage.wasDeployed(token) ? null : storage.getCopy(token);
        if (saved == null || !saved.hasUUID("uuid")) return null;
        ServerSubLevel hat = Detached.open(level, saved.getUUID("uuid"), saved.getCompound("plot"), from);
        if (hat != null && riders) {
            for (Tag entry : saved.getList(CHILDREN, Tag.TAG_COMPOUND)) {
                CompoundTag rider = (CompoundTag) entry;
                ServerSubLevel child = Detached.open(level, rider.getUUID("uuid"), rider.getCompound("plot"), from);
                if (child == null) {
                    for (ServerSubLevel opened : Detached.children(hat)) Detached.close(opened);
                    Detached.close(hat);
                    return null;
                }
                Detached.attach(child, hat, Detached.loadPose(rider.getCompound("relative")));
                Detached.rules(child, Rules.EDITABLE);
            }
        }
        if (hat != null && from != level) {
            storage.remove(token);
            PocketedSubLevelSavedData.getOrLoad(level).put(token, saved);
        }
        return hat;
    }

    public static boolean pending(ServerLevel level, UUID token) {
        ServerLevel from = storedIn(level, token);
        return from != null && !PocketedSubLevelSavedData.getOrLoad(from).wasDeployed(token);
    }

    public static boolean ownedElsewhere(UUID token, ServerLevel level, BlockPos host) {
        Site site = SITES.get(token);
        if (site == null || site.hat().isRemoved()) return false;
        ServerLevel hatLevel = site.hat().getLevel();
        if (hatLevel == level && site.host().equals(host)) return false;
        return hatLevel.isLoaded(site.host()) && hatLevel.getBlockEntity(site.host()) instanceof BuildHostBlockEntity owner
                && token.equals(owner.getHat());
    }

    public static boolean holds(UUID token, ServerLevel level, BlockPos host) {
        if (!(Sable.HELPER.getContaining(level, host) instanceof ServerSubLevel inside)) return false;
        ServerSubLevel live = live(token);
        UUID id = live != null ? live.getUniqueId() : storedId(level, token);
        for (ServerSubLevel above = inside; above != null; above = Detached.parent(above)) {
            if (above.getUniqueId().equals(id)) return true;
        }
        ServerSubLevel root = Hosted.rootOf(inside);
        return root != null && root.getUniqueId().equals(id);
    }

    private static @Nullable UUID storedId(ServerLevel level, UUID token) {
        ServerLevel from = storedIn(level, token);
        CompoundTag saved = from == null ? null : PocketedSubLevelSavedData.getOrLoad(from).getCopy(token);
        return saved != null && saved.hasUUID("uuid") ? saved.getUUID("uuid") : null;
    }

    public static @Nullable ServerLevel storedIn(ServerLevel level, UUID token) {
        if (PocketedSubLevelSavedData.getOrLoad(level).contains(token)) return level;
        for (ServerLevel other : level.getServer().getAllLevels()) {
            if (PocketedSubLevelSavedData.getOrLoad(other).contains(token)) return other;
        }
        return null;
    }

    public static @Nullable ServerSubLevel create(ServerLevel level) {
        return Detached.create(level, UUID.randomUUID());
    }

    public static void track(UUID token, ServerSubLevel hat, BlockPos host) {
        UNLOADING.remove(token);
        SITES.put(token, new Site(hat, host));
        Detached.rules(hat, Rules.EDITABLE);
    }

    public static void save(UUID token) {
        ServerSubLevel hat = live(token);
        if (hat == null) return;
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(hat.getLevel());
        CompoundTag saved = storage.getCopy(token);
        if (saved == null) saved = payload(hat);
        else saved.put("plot", hat.getPlot().save());
        saved.put(CHILDREN, riders(hat));
        storage.put(token, saved);
    }

    public static CompoundTag payload(ServerSubLevel hat) {
        CompoundTag tag = Detached.payload(hat);
        PocketCaseItem.markDetachedPayload(tag);
        return tag;
    }

    private static ListTag riders(ServerSubLevel hat) {
        ListTag list = new ListTag();
        for (ServerSubLevel child : Detached.children(hat)) {
            Pose3d relative = Detached.relative(child);
            if (relative == null) continue;
            CompoundTag rider = new CompoundTag();
            rider.putUUID("uuid", child.getUniqueId());
            rider.put("plot", child.getPlot().save());
            rider.put("relative", Detached.savePose(relative));
            list.add(rider);
        }
        for (ServerSubLevel member : Hosted.members(hat)) {
            CompoundTag rider = new CompoundTag();
            rider.putUUID("uuid", member.getUniqueId());
            rider.put("plot", member.getPlot().save());
            rider.put("relative", Detached.savePose(Hosted.relative(hat, member)));
            list.add(rider);
        }
        return list;
    }

    static boolean openRiders(ServerLevel level, UUID token, ServerSubLevel hat) {
        ServerLevel from = storedIn(level, token);
        CompoundTag saved = from == null ? null : PocketedSubLevelSavedData.getOrLoad(from).getCopy(token);
        if (saved == null) return true;
        for (Tag entry : saved.getList(CHILDREN, Tag.TAG_COMPOUND)) {
            CompoundTag rider = (CompoundTag) entry;
            ServerSubLevel child = Detached.open(level, rider.getUUID("uuid"), rider.getCompound("plot"), from);
            if (child == null) {
                for (ServerSubLevel opened : Detached.children(hat)) {
                    Detached.detach(opened);
                    Detached.close(opened);
                }
                return false;
            }
            Detached.attach(child, hat, Detached.loadPose(rider.getCompound("relative")));
            Detached.rules(child, Rules.EDITABLE);
        }
        return true;
    }

    static void storeRiders(CompoundTag saved, ServerSubLevel hat) {
        if (!Detached.children(hat).isEmpty()) saved.put(CHILDREN, riders(hat));
    }

    static void publishHosted(UUID token, ServerSubLevel hat, Set<UUID> known) {
        List<ServerSubLevel> members = Hosted.members(hat);
        Set<UUID> ids = new HashSet<>();
        CompoundTag poses = new CompoundTag();
        for (ServerSubLevel member : members) {
            ids.add(member.getUniqueId());
            poses.put(member.getUniqueId().toString(), Detached.savePose(Hosted.relative(hat, member)));
        }
        boolean changed = !ids.equals(known);
        if (changed) {
            known.clear();
            known.addAll(ids);
        }
        changed |= HatNativeContraptions.publish(token, hat);
        if (changed) announce(token, hat);
        if (members.isEmpty()) return;
        BuildRiderPosesPayload payload = new BuildRiderPosesPayload(token, hat.getLevel().getGameTime(), poses);
        for (UUID id : hat.getTrackingPlayers()) {
            if (hat.getLevel().getPlayerByUUID(id) instanceof ServerPlayer viewer)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public static void close(UUID token) {
        HatNativeContraptions.forget(token);
        UNLOADING.remove(token);
        PUBLISHED.remove(token);
        RETRY.remove(token);
        ServerSubLevel live = live(token);
        if (live != null && Hosted.isRoot(live)) live = Hosted.unhost(live);
        if (live != null) {
            HatContraptions.disassemble(live);
            for (ServerSubLevel child : Detached.children(live)) HatContraptions.disassemble(child);
        }
        save(token);
        Site site = SITES.remove(token);
        if (site == null) return;
        for (ServerSubLevel child : Detached.children(site.hat())) Detached.close(child);
        Detached.close(site.hat());
    }

    public static void mount(ServerLevel level, ServerSubLevel target, Set<UUID> riders, WeldRecord weld) {
        ServerSubLevel hosted = Hosted.rootOf(target);
        if (hosted != null) {
            UUID targetId = target.getUniqueId();
            if (Hosted.unhost(hosted) == null) return;
            target = Detached.find(targetId);
            if (target == null) return;
        }
        ServerSubLevel hat = Detached.parent(target) == null ? target : Detached.parent(target);
        UUID token = token(hat);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (token == null || container == null) return;
        List<ServerSubLevel> adopted = new ArrayList<>();
        for (UUID id : riders) {
            if (!(container.getSubLevel(id) instanceof ServerSubLevel rider) || rider.isRemoved()) continue;
            ServerSubLevel child = Detached.adopt(rider);
            if (child != null) adopted.add(child);
        }
        ServerSubLevel small = Detached.find(weld.smallSubLevel());
        if (small != null && adopted.contains(small)) {
            Pose3d before = new Pose3d(small.logicalPose());
            Pose3d snapped = new Pose3d(before);
            snapped.orientation().set(new Quaterniond(target.logicalPose().orientation())
                    .mul(new Quaterniond(weld.orientation()).invert()));
            snapped.position().set(0.0D, 0.0D, 0.0D);
            Vector3d reach = snapped.transformPosition(new Vector3d(weld.anchorFor(true)));
            snapped.position().set(target.logicalPose().transformPosition(new Vector3d(weld.anchorFor(false))))
                    .sub(reach);
            for (ServerSubLevel child : adopted) {
                child.logicalPose().set(Detached.compose(snapped, Detached.relative(before, child.logicalPose())));
                child.updateLastPose();
            }
        }
        for (ServerSubLevel child : adopted) {
            Detached.attach(child, hat);
            Detached.rules(child, Rules.EDITABLE);
        }
        WeldRuntime.finishEffect(level, weld);
        announce(token, hat);
    }

    public static void prune(ServerLevel level, UUID token, ServerSubLevel hat) {
        List<ServerSubLevel> children = Detached.children(hat);
        if (children.isEmpty()) return;
        WeldStore store = WeldStore.get(level);
        boolean changed = false;
        Set<UUID> members = new HashSet<>();
        members.add(hat.getUniqueId());
        for (ServerSubLevel child : children) {
            members.add(child.getUniqueId());
            for (WeldRecord record : store.touching(child.getUniqueId())) {
                if (!WeldRuntime.glueLost(level, record)) continue;
                store.remove(record.weldId());
                changed = true;
            }
        }
        Set<UUID> reached = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        reached.add(hat.getUniqueId());
        queue.add(hat.getUniqueId());
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            for (WeldRecord record : store.touching(id)) {
                UUID other = record.other(id);
                if (other != null && members.contains(other) && reached.add(other)) queue.add(other);
            }
        }
        for (ServerSubLevel child : children) {
            if (reached.contains(child.getUniqueId())) continue;
            Detached.release(child);
            changed = true;
        }
        if (!changed) return;
        CrossScaleWeldSync.broadcast(level);
        announce(token, hat);
    }

    public static void forget(UUID token) {
        PUBLISHED.remove(token);
        RETRY.remove(token);
        UNLOADING.remove(token);
        SITES.remove(token);
    }

    public static void unload(UUID token) {
        if (SITES.containsKey(token)) UNLOADING.add(token);
    }

    public static void onServerTick(ServerTickEvent.Pre event) {
        if (UNLOADING.isEmpty()) return;
        for (UUID token : List.copyOf(UNLOADING)) close(token);
        UNLOADING.clear();
    }

    public static boolean isAttached(@Nullable SubLevel subLevel) {
        return token(subLevel) != null;
    }

    public static @Nullable UUID token(@Nullable SubLevel subLevel) {
        if (subLevel == null) return null;
        for (Map.Entry<UUID, Site> entry : SITES.entrySet()) {
            if (entry.getValue().hat() == subLevel) return entry.getKey();
        }
        return null;
    }

    public static @Nullable BuildHostBlockEntity hostOf(ServerSubLevel hat) {
        UUID token = token(hat);
        if (token == null) return null;
        BlockPos pos = SITES.get(token).host();
        ServerLevel level = hat.getLevel();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BuildHostBlockEntity host
                && token.equals(host.getHat()) ? host : null;
    }

    public static @Nullable BuildMirrorPayload mirror(UUID token, ServerPlayer viewer) {
        ServerSubLevel hat = live(token);
        if (hat == null || hat.getLevel() != viewer.serverLevel()) return null;
        hat.getTrackingPlayers().add(viewer.getUUID());
        for (ServerSubLevel child : Detached.children(hat)) child.getTrackingPlayers().add(viewer.getUUID());
        return mirror(token, hat);
    }

    static BuildMirrorPayload mirror(UUID token, ServerSubLevel hat) {
        CompoundTag children;
        if (SITES.containsKey(token) || !Hosted.members(hat).isEmpty()) {
            children = new CompoundTag();
            children.put(CHILDREN, riders(hat));
        } else {
            children = mirrorChildren(hat.getLevel(), token);
        }
        children.putUUID("uuid", hat.getUniqueId());
        children.put("native_contraptions", HatNativeContraptions.snapshot(hat));
        return new BuildMirrorPayload(token, hat.getPlot().save(), children);
    }

    public static CompoundTag mirrorChildren(ServerLevel level, UUID token) {
        CompoundTag children = new CompoundTag();
        ServerLevel stored = storedIn(level, token);
        if (stored == null) return children;
        CompoundTag saved = PocketedSubLevelSavedData.getOrLoad(stored).getCopy(token);
        if (saved == null) return children;
        if (saved.hasUUID("uuid")) children.putUUID("uuid", saved.getUUID("uuid"));
        children.put(CHILDREN, saved.getList(CHILDREN, Tag.TAG_COMPOUND).copy());
        return children;
    }

    private static void announce(UUID token, ServerSubLevel hat) {
        BuildMirrorPayload payload = mirror(token, hat);
        for (UUID id : hat.getTrackingPlayers()) {
            if (hat.getLevel().getPlayerByUUID(id) instanceof ServerPlayer viewer)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public static boolean hasBuild(ServerSubLevel build) {
        if (!Detached.children(build).isEmpty()) return true;
        for (var holder : build.getPlot().getLoadedChunks()) {
            var chunk = holder.getChunk();
            for (var section : chunk.getSections()) {
                if (section.hasOnlyAir()) continue;
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (!state.isAir() && !state.is(com.misterblusky9.pocket.block.ModBlocks.POCKET_DUMMY.get()))
                                return true;
                        }
            }
        }
        return false;
    }

    public static @Nullable ServerSubLevel releaseToPhysics(UUID token, ServerSubLevel build, double scale) {
        if (Hosted.isRoot(build)) {
            build = Hosted.unhost(build);
            if (build == null) return null;
        }
        ServerLevel level = build.getLevel();
        for (ServerSubLevel rider : Detached.children(build)) {
            if (Detached.release(rider) == null) return null;
        }
        CompoundTag data = payload(build);
        if (Detached.close(build) == null) return null;
        var serialized = dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.fromData(data);
        ServerSubLevel physical = serialized == null ? null
                : dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.fullyLoad(level, serialized);
        if (physical == null) {
            ServerSubLevel restored = Detached.open(level, data.getUUID("uuid"), data.getCompound("plot"));
            Site site = SITES.get(token);
            if (restored != null && site != null) track(token, restored, site.host());
            return null;
        }
        forget(token);
        PocketedSubLevelSavedData.getOrLoad(level).remove(token);
        if (!Pym.resize().request(physical).scaleTo(scale).immediate().submit().accepted()) {
            Sable.LOGGER.error("Could not set released build {} to scale {}", physical.getUniqueId(), scale);
        }
        physical.updateMergedMassData(1.0F);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) container.physicsSystem().getPipeline().onStatsChanged(physical);
        com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue.mark(physical);
        return physical;
    }

    public static void follow(UUID token, ServerSubLevel hat, ServerLevel level, BlockPos host, int[] offset) {
        Vector3d shift = new Vector3d(offset[0], offset[1], offset[2]);
        boolean hosted = Hosted.isRoot(hat);
        if (hosted) {
            Pose3d shown = new Pose3d();
            if (stand(shown, hat, level, host, shift)) Hosted.present(hat, shown);
        } else {
            stand(hat.logicalPose(), hat, level, host, shift);
        }
        Vector3d head = head(hat, shift);
        Detached.anchor(hat, BlockPos.containing(head.x, head.y, head.z));
        watch(token, hat, level, host);
        if (hosted) publishHosted(token, hat, PUBLISHED.computeIfAbsent(token, ignored -> new HashSet<>()));
    }

    public static void watch(UUID token, ServerSubLevel hat, ServerLevel level, BlockPos near) {
        Collection<UUID> tracking = hat.getTrackingPlayers();
        Set<UUID> before = new HashSet<>(tracking);
        tracking.clear();
        BuildMirrorPayload payload = null;
        List<ServerPlayer> viewers = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(near), false);
        boolean fresh = false;
        for (ServerPlayer viewer : viewers) {
            tracking.add(viewer.getUUID());
            if (before.contains(viewer.getUUID())) continue;
            fresh = true;
            if (payload == null) payload = mirror(token, hat);
            PacketDistributor.sendToPlayer(viewer, payload);
        }
        List<ServerSubLevel> parts = new ArrayList<>(Detached.children(hat));
        parts.addAll(Hosted.members(hat));
        for (ServerSubLevel part : parts) {
            fresh |= !part.getTrackingPlayers().containsAll(tracking);
            part.getTrackingPlayers().clear();
            part.getTrackingPlayers().addAll(tracking);
        }
        if (fresh) for (ServerPlayer viewer : viewers) level.getChunkSource().chunkMap.move(viewer);
    }

    public static boolean stand(Pose3d pose, SubLevel hat, Level level, BlockPos host, Vector3dc offset) {
        BlockState state = level.getBlockState(host);
        if (!(state.getBlock() instanceof BuildHostBlock block)) return false;
        BuildHostBlockEntity entity = level.getBlockEntity(host) instanceof BuildHostBlockEntity found ? found : null;
        Vec3 anchor = entity != null ? entity.headCentre() : block.anchor(state);
        Vector3d centre = new Vector3d(host.getX() + anchor.x, host.getY() + anchor.y, host.getZ() + anchor.z);
        Quaterniond orientation = new Quaterniond(entity != null ? entity.orientation() : block.orientation(state));
        ItemStack headwear = entity != null ? entity.getActivator() : ItemStack.EMPTY;
        double scale = scale(headwear);
        SubLevel parent = Sable.HELPER.getContaining(level, host);
        if (parent != null && parent != hat) {
            Pose3dc parentPose = parent.logicalPose();
            parentPose.transformPosition(centre);
            orientation = new Quaterniond(parentPose.orientation()).mul(orientation);
            scale *= parentPose.scale().x();
        }
        pose.rotationPoint().set(head(hat, offset, headwear));
        pose.position().set(centre);
        pose.orientation().set(orientation);
        pose.scale().set(scale);
        return true;
    }

    public static void wear(Pose3d pose, SubLevel hat, LivingEntity wearer, Vector3dc offset, ItemStack headwear) {
        pose.rotationPoint().set(head(hat, offset, headwear));
        pose.position().set(wearer.getX(), wearer.getEyeY(), wearer.getZ());
        pose.orientation().set(new Quaterniond()
                .rotateY(Math.toRadians(180.0 - wearer.getYHeadRot()))
                .rotateX(Math.toRadians(-wearer.getXRot())));
        pose.scale().set(scale(headwear));
    }

    public static void hold(SubLevel hat, Vector3dc position, Quaterniond orientation, ItemStack headwear) {
        Pose3d pose = hat.logicalPose();
        pose.rotationPoint().set(head(hat, new Vector3d(), headwear));
        pose.position().set(position);
        pose.orientation().set(orientation);
        pose.scale().set(scale(headwear));
    }

    public static double scale(ItemStack headwear) {
        if (headwear.is(ModItems.SKULL_CAP.get())) return SKULL_CAP_SCALE;
        if (headwear.getItem() instanceof GogglesItem) return GOGGLES_SCALE;
        if (headwear.is(Items.DRAGON_HEAD)) return DRAGON_SCALE;
        if (isSkull(headwear)) return SKULL_SCALE;
        return HELMET_SCALE;
    }

    private static boolean isSkull(ItemStack headwear) {
        return headwear.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractSkullBlock;
    }

    private static double gridLift(ItemStack headwear) {
        double pixels = headwear.getItem() instanceof GogglesItem ? GOGGLES_LIFT_PIXELS
                : headwear.is(Items.DRAGON_HEAD) ? DRAGON_LIFT_PIXELS
                : isSkull(headwear) ? SKULL_LIFT_PIXELS : 0.0;
        return pixels / 16.0 / scale(headwear);
    }

    public static Vector3d head(SubLevel hat, Vector3dc offset, ItemStack headwear) {
        double depth = headwear.is(Items.DRAGON_HEAD) ? DRAGON_DEPTH_PIXELS / 16.0 / scale(headwear) : 0.0;
        return head(hat, offset).sub(0.0, gridLift(headwear), depth);
    }

    public static BlockPos scaffoldOrigin(SubLevel child, int size, int[] offset) {
        return child.getPlot().getCenterBlock().offset(-size / 2 - offset[0], -size / 2 - offset[1], -size / 2 - offset[2]);
    }

    public static Vector3d head(SubLevel hat, Vector3dc offset) {
        BlockPos centre = hat.getPlot().getCenterBlock();
        return new Vector3d(centre.getX(), centre.getY(), centre.getZ()).sub(offset);
    }

    public static int[] offset(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int[] offset = data == null ? new int[0] : data.copyTag().getIntArray(OFFSET);
        return offset.length == 3 ? offset : new int[3];
    }

    public static Vector3d offsetVector(ItemStack stack) {
        int[] offset = offset(stack);
        return new Vector3d(offset[0], offset[1], offset[2]);
    }

    public static void writeOffset(CompoundTag data, int[] offset) {
        if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) data.remove(OFFSET);
        else data.putIntArray(OFFSET, offset.clone());
    }

    public static int[] readOffset(CompoundTag data) {
        int[] offset = data.getIntArray(OFFSET);
        data.remove(OFFSET);
        return offset.length == 3 ? offset : new int[3];
    }

    public static boolean serialized(CompoundTag data) {
        return data.contains(KEY, Tag.TAG_LONG) || data.contains(OLD_KEY, Tag.TAG_LONG);
    }

    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (Map.Entry<UUID, Site> entry : List.copyOf(SITES.entrySet())) {
            if (entry.getValue().hat().getLevel() == level) save(entry.getKey());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID token : List.copyOf(SITES.keySet())) close(token);
    }

    private BuildSites() {}
}

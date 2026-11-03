package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Rules;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
    public static final double SCALE = 5.0 / 64.0;
    public static final int REACH = 16;
    private static final String OFFSET = "pocket_hat_offset";
    private static final String KEY = "pocket:hat_stand_child";
    private static final String OLD_KEY = "pocket:hatters_stand_child";
    private static final String CHILDREN = "children";

    private record Site(ServerSubLevel hat, BlockPos host) {}

    private static final Map<UUID, Site> SITES = new HashMap<>();
    private static final Set<UUID> UNLOADING = new HashSet<>();

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
                if (child == null) continue;
                Detached.attach(child, hat, SableNBTUtils.readPose3d(rider.getCompound("relative")));
                Detached.rules(child, Rules.EDITABLE);
            }
        }
        if (hat != null && from != level) {
            storage.remove(token);
            PocketedSubLevelSavedData.getOrLoad(level).put(token, saved);
        }
        return hat;
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
            rider.put("relative", SableNBTUtils.writePose3d(relative));
            list.add(rider);
        }
        return list;
    }

    public static void close(UUID token) {
        UNLOADING.remove(token);
        save(token);
        Site site = SITES.remove(token);
        if (site == null) return;
        for (ServerSubLevel child : Detached.children(site.hat())) Detached.close(child);
        Detached.close(site.hat());
    }

    public static void mount(ServerLevel level, ServerSubLevel target, Set<UUID> riders, WeldRecord weld) {
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

    private static BuildMirrorPayload mirror(UUID token, ServerSubLevel hat) {
        CompoundTag children = new CompoundTag();
        children.put(CHILDREN, riders(hat));
        return new BuildMirrorPayload(token, hat.getPlot().save(), children);
    }

    private static void announce(UUID token, ServerSubLevel hat) {
        BuildMirrorPayload payload = mirror(token, hat);
        for (UUID id : hat.getTrackingPlayers()) {
            if (hat.getLevel().getPlayerByUUID(id) instanceof ServerPlayer viewer)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public static void follow(UUID token, ServerSubLevel hat, ServerLevel level, BlockPos host, int[] offset) {
        Vector3d shift = new Vector3d(offset[0], offset[1], offset[2]);
        stand(hat.logicalPose(), hat, level, host, shift);
        Vector3d head = head(hat, shift);
        Detached.anchor(hat, BlockPos.containing(head.x, head.y, head.z));
        watch(token, hat, level, host);
    }

    public static void watch(UUID token, ServerSubLevel hat, ServerLevel level, BlockPos near) {
        Collection<UUID> tracking = hat.getTrackingPlayers();
        Set<UUID> before = new HashSet<>(tracking);
        tracking.clear();
        BuildMirrorPayload payload = null;
        for (ServerPlayer viewer : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(near), false)) {
            tracking.add(viewer.getUUID());
            if (before.contains(viewer.getUUID())) continue;
            if (payload == null) payload = mirror(token, hat);
            PacketDistributor.sendToPlayer(viewer, payload);
        }
        for (ServerSubLevel child : Detached.children(hat)) {
            child.getTrackingPlayers().clear();
            child.getTrackingPlayers().addAll(tracking);
        }
    }

    public static boolean stand(Pose3d pose, SubLevel hat, Level level, BlockPos host, Vector3dc offset) {
        BlockState state = level.getBlockState(host);
        if (!(state.getBlock() instanceof BuildHostBlock block)) return false;
        Vec3 anchor = block.anchor(state);
        Vector3d centre = new Vector3d(host.getX() + anchor.x, host.getY() + anchor.y, host.getZ() + anchor.z);
        Quaterniond orientation = new Quaterniond(block.orientation(state));
        double scale = SCALE;
        SubLevel parent = Sable.HELPER.getContaining(level, host);
        if (parent != null && parent != hat) {
            Pose3dc parentPose = parent.logicalPose();
            parentPose.transformPosition(centre);
            orientation = new Quaterniond(parentPose.orientation()).mul(orientation);
            scale *= parentPose.scale().x();
        }
        pose.rotationPoint().set(head(hat, offset));
        pose.position().set(centre);
        pose.orientation().set(orientation);
        pose.scale().set(scale);
        return true;
    }

    public static void wear(Pose3d pose, SubLevel hat, LivingEntity wearer, Vector3dc offset) {
        pose.rotationPoint().set(head(hat, offset));
        pose.position().set(wearer.getX(), wearer.getEyeY(), wearer.getZ());
        pose.orientation().set(new Quaterniond()
                .rotateY(Math.toRadians(180.0 - wearer.getYHeadRot()))
                .rotateX(Math.toRadians(-wearer.getXRot())));
        pose.scale().set(SCALE);
    }

    public static void hold(SubLevel hat, Vector3dc position, Quaterniond orientation) {
        Pose3d pose = hat.logicalPose();
        pose.rotationPoint().set(head(hat, new Vector3d()));
        pose.position().set(position);
        pose.orientation().set(orientation);
        pose.scale().set(SCALE);
    }

    public static BlockPos scaffoldOrigin(ServerSubLevel child, int size, int[] offset) {
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

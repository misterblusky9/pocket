package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.network.BuildMirrorRequestPayload;
import com.misterblusky9.pocket.network.BuildRiderPosesPayload;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Rules;
import com.misterblusky9.pym.api.client.Mirrors;
import com.misterblusky9.pym.api.client.Portrait;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BuildMirrors {
    private static final int IDLE_TICKS = 100;
    private static final int RETRY_TICKS = 100;
    private static final Map<UUID, ClientSubLevel> MIRRORS = new HashMap<>();
    private static final Map<UUID, Long> USED = new HashMap<>();
    private static final Map<UUID, Long> REQUESTED = new HashMap<>();
    private static final Set<UUID> MISSING = new HashSet<>();
    private static final Set<UUID> STANDING = new HashSet<>();
    private static final Set<UUID> SEEN = new HashSet<>();
    private static final Set<UUID> WORN = new HashSet<>();
    private static final Map<UUID, double[]> EASED = new HashMap<>();
    private static final Map<UUID, List<Rider>> RIDERS = new HashMap<>();
    private static final Map<UUID, BlockPos> HOSTS = new HashMap<>();

    public record Rider(ClientSubLevel mirror, Pose3d relative, RiderPlayback playback) {
        public Pose3d at() {
            return playback.at(clock(), relative);
        }
    }

    private static double clock() {
        return System.nanoTime() / 50_000_000.0D;
    }

    public static void acceptPoses(BuildRiderPosesPayload payload) {
        for (Rider rider : riders(payload.token())) {
            String key = rider.mirror().getUniqueId().toString();
            if (!payload.poses().contains(key)) continue;
            Pose3d pose = Detached.loadPose(payload.poses().getCompound(key));
            rider.relative().set(pose);
            rider.playback().push(payload.tick(), clock(), pose);
        }
    }

    public static @Nullable ClientSubLevel get(ItemStack stack) {
        return get(token(stack));
    }

    public static @Nullable ClientSubLevel get(@Nullable UUID token) {
        ClientLevel level = Minecraft.getInstance().level;
        if (token == null || level == null) return null;
        ClientSubLevel mirror = MIRRORS.get(token);
        if (mirror == null) {
            request(token, level.getGameTime());
            return null;
        }
        if (!Portrait.canDraw(mirror)) return null;
        USED.put(token, level.getGameTime());
        return mirror;
    }

    public static boolean pending(ItemStack stack) {
        UUID token = token(stack);
        return token != null && !MISSING.contains(token);
    }

    public static @Nullable ClientSubLevel standing(UUID token) {
        ClientSubLevel mirror = STANDING.contains(token) ? MIRRORS.get(token) : null;
        return mirror != null && Portrait.canDraw(mirror) ? mirror : null;
    }

    public static List<Rider> riders(UUID token) {
        return RIDERS.getOrDefault(token, List.of());
    }

    public static void stand(UUID token, Level level, BlockPos pos, int[] offset) {
        long now = level.getGameTime();
        ClientSubLevel mirror = MIRRORS.get(token);
        if (mirror == null) {
            request(token, now);
            return;
        }
        double[] eased = EASED.computeIfAbsent(token,
                ignored -> new double[] {offset[0], offset[1], offset[2], offset[0], offset[1], offset[2]});
        for (int i = 0; i < 3; i++) {
            eased[i + 3] = eased[i];
            double gap = offset[i] - eased[i];
            eased[i] = Math.abs(gap) < 0.01 ? offset[i] : eased[i] + gap * 0.5;
        }
        Pose3d pose = new Pose3d();
        if (!BuildSites.stand(pose, mirror, level, pos, new Vector3d(eased[0], eased[1], eased[2]))) return;
        boolean standing = STANDING.contains(token);
        if (standing) Mirrors.moveTo(mirror, pose);
        else Mirrors.snapTo(mirror, pose);
        Mirrors.rules(mirror, Rules.EDITABLE);
        for (Rider rider : riders(token)) {
            place(rider, pose, standing);
            Mirrors.rules(rider.mirror(), Rules.EDITABLE);
        }
        USED.put(token, now);
        HOSTS.put(token, pos.immutable());
        STANDING.add(token);
        SEEN.add(token);
    }

    private static void place(Rider rider, Pose3d hat, boolean smooth) {
        Pose3d riding = Detached.compose(hat, rider.playback().at(clock() + 1.0D, rider.relative()));
        if (smooth) Mirrors.moveTo(rider.mirror(), riding);
        else Mirrors.snapTo(rider.mirror(), riding);
    }

    public static Vector3d easedOffset(UUID token, int[] offset, float partialTick) {
        double[] eased = EASED.get(token);
        if (eased == null) return new Vector3d(offset[0], offset[1], offset[2]);
        return new Vector3d(eased[3], eased[4], eased[5]).lerp(new Vector3d(eased[0], eased[1], eased[2]), partialTick);
    }

    public static @Nullable BlockPos host(SubLevel subLevel) {
        for (Map.Entry<UUID, ClientSubLevel> entry : MIRRORS.entrySet())
            if (entry.getValue() == subLevel && STANDING.contains(entry.getKey())) return HOSTS.get(entry.getKey());
        return null;
    }

    public static List<ClientSubLevel> standingMirrors() {
        List<ClientSubLevel> standing = new ArrayList<>();
        for (UUID token : STANDING) {
            ClientSubLevel mirror = MIRRORS.get(token);
            if (mirror != null && !mirror.isRemoved()) standing.add(mirror);
        }
        return standing;
    }

    public static Collection<ClientSubLevel> open() {
        return MIRRORS.values();
    }

    public static boolean wornAt(BlockPos position) {
        if (position == null) return false;
        Vector3d inPlot = new Vector3d(position.getX() + 0.5D,
                position.getY() + 0.5D, position.getZ() + 0.5D);
        for (UUID token : WORN) {
            ClientSubLevel root = MIRRORS.get(token);
            if (inPlot(root, inPlot)) return true;
            for (Rider rider : riders(token)) {
                if (inPlot(rider.mirror(), inPlot)) return true;
            }
        }
        return false;
    }

    private static boolean inPlot(ClientSubLevel mirror, Vector3d position) {
        return mirror != null && !mirror.isRemoved() && mirror.getPlot().contains(position);
    }

    public static boolean sameBuild(SubLevel first, SubLevel second) {
        if (first == null || second == null || first == second) return false;
        for (Map.Entry<UUID, ClientSubLevel> entry : MIRRORS.entrySet()) {
            if (!containsPart(entry.getKey(), entry.getValue(), first)) continue;
            if (containsPart(entry.getKey(), entry.getValue(), second)) return true;
        }
        return false;
    }

    private static boolean containsPart(UUID token, ClientSubLevel root, SubLevel candidate) {
        if (root == candidate) return true;
        for (Rider rider : riders(token)) if (rider.mirror() == candidate) return true;
        return false;
    }

    private static void request(UUID token, long now) {
        Long asked = REQUESTED.get(token);
        if (asked != null && now - asked < RETRY_TICKS) return;
        REQUESTED.put(token, now);
        PacketDistributor.sendToServer(new BuildMirrorRequestPayload(token));
    }

    public static void accept(BuildMirrorPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (payload.plot().isEmpty()) {
            MISSING.add(payload.token());
            return;
        }
        ClientSubLevel old = MIRRORS.remove(payload.token());
        if (old != null) {
            Mirrors.close(old);
            forget(payload.token());
            WORN.remove(payload.token());
        }
        dismount(payload.token());
        HatNativeContraptionRender.forget(payload.token());
        UUID id = payload.children().hasUUID("uuid") ? payload.children().getUUID("uuid") : payload.token();
        ClientSubLevel mirror = Mirrors.open(level, id, payload.plot());
        if (mirror == null) {
            MISSING.add(payload.token());
            return;
        }
        MISSING.remove(payload.token());
        MIRRORS.put(payload.token(), mirror);
        List<Rider> riders = new ArrayList<>();
        for (Tag entry : payload.children().getList("children", Tag.TAG_COMPOUND)) {
            CompoundTag rider = (CompoundTag) entry;
            ClientSubLevel child = Mirrors.open(level, rider.getUUID("uuid"), rider.getCompound("plot"));
            if (child != null) {
                Pose3d relative = Detached.loadPose(rider.getCompound("relative"));
                riders.add(new Rider(child, relative, new RiderPlayback()));
            }
        }
        if (!riders.isEmpty()) RIDERS.put(payload.token(), riders);
        HatNativeContraptionRender.accept(payload.token(), payload.children().getCompound("native_contraptions"));
        USED.put(payload.token(), level.getGameTime());
    }

    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused() || !level.tickRateManager().runsNormally()) return;
        long now = level.getGameTime();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) keep(stack, now);
            for (ItemStack stack : player.getInventory().armor) keep(stack, now);
            for (ItemStack stack : player.getInventory().offhand) keep(stack, now);
            keep(HatRendering.headwear(player), now);
        }
        Set<UUID> worn = new HashSet<>();
        for (Player wearer : level.players()) {
            UUID token = BuildHostBlockEntity.token(HatRendering.headwear(wearer));
            ClientSubLevel mirror = token == null ? null : MIRRORS.get(token);
            if (mirror == null) continue;
            Pose3d pose = new Pose3d();
            ItemStack headwear = HatRendering.headwear(wearer);
            BuildSites.wear(pose, mirror, wearer, BuildSites.offsetVector(headwear), headwear);
            if (WORN.contains(token)) Mirrors.moveTo(mirror, pose);
            else Mirrors.snapTo(mirror, pose);
            for (Rider rider : riders(token)) place(rider, pose, WORN.contains(token));
            worn.add(token);
        }
        WORN.clear();
        WORN.addAll(worn);
        Iterator<Map.Entry<UUID, ClientSubLevel>> iterator = MIRRORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ClientSubLevel> entry = iterator.next();
            UUID token = entry.getKey();
            ClientSubLevel mirror = entry.getValue();
            if (STANDING.contains(token) && !SEEN.contains(token)) {
                STANDING.remove(token);
                EASED.remove(token);
                HOSTS.remove(token);
                Mirrors.rules(mirror, Rules.COSMETIC);
                riders(token).forEach(rider -> Mirrors.rules(rider.mirror(), Rules.COSMETIC));
            }
            if (mirror.isRemoved()) {
                iterator.remove();
                forget(token);
                dismount(token);
            } else if (now - USED.getOrDefault(token, now) > IDLE_TICKS) {
                Mirrors.close(mirror);
                iterator.remove();
                forget(token);
                dismount(token);
            }
        }
        SEEN.clear();
    }

    private static void dismount(UUID token) {
        HatNativeContraptionRender.forget(token);
        List<Rider> riders = RIDERS.remove(token);
        if (riders != null) riders.forEach(rider -> Mirrors.close(rider.mirror()));
    }

    private static void forget(UUID token) {
        USED.remove(token);
        REQUESTED.remove(token);
        STANDING.remove(token);
        EASED.remove(token);
        HOSTS.remove(token);
    }

    private static @Nullable UUID token(ItemStack stack) {
        return stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
    }

    private static void keep(ItemStack stack, long now) {
        UUID token = token(stack);
        if (token == null) return;
        if (MIRRORS.containsKey(token)) USED.put(token, now);
        else request(token, now);
    }

    public static void clear(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        MIRRORS.clear();
        HatNativeContraptionRender.clear();
        USED.clear();
        REQUESTED.clear();
        MISSING.clear();
        STANDING.clear();
        SEEN.clear();
        WORN.clear();
        EASED.clear();
        RIDERS.clear();
        HOSTS.clear();
    }

    private BuildMirrors() {}
}

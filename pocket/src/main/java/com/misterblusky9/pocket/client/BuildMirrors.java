package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.network.BuildMirrorRequestPayload;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Rules;
import com.misterblusky9.pym.api.client.Mirrors;
import com.misterblusky9.pym.api.client.Portrait;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableNBTUtils;
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
    private static final Map<UUID, BlockPos> STANDS = new HashMap<>();
    private static final Map<UUID, List<Rider>> RIDERS = new HashMap<>();

    public record Rider(ClientSubLevel mirror, Pose3d relative) {}

    public static @Nullable ClientSubLevel get(ItemStack stack) {
        UUID token = token(stack);
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
            Pose3d riding = Detached.compose(pose, rider.relative());
            if (standing) Mirrors.moveTo(rider.mirror(), riding);
            else Mirrors.snapTo(rider.mirror(), riding);
            Mirrors.rules(rider.mirror(), Rules.EDITABLE);
        }
        USED.put(token, now);
        STANDING.add(token);
        SEEN.add(token);
        STANDS.put(token, pos.immutable());
    }

    public static @Nullable BlockPos standOf(SubLevel subLevel) {
        for (UUID token : STANDING) {
            if (MIRRORS.get(token) == subLevel) return STANDS.get(token);
        }
        return null;
    }

    public static Vector3d easedOffset(UUID token, float partialTick) {
        double[] eased = EASED.get(token);
        if (eased == null) return new Vector3d();
        return new Vector3d(eased[3], eased[4], eased[5]).lerp(new Vector3d(eased[0], eased[1], eased[2]), partialTick);
    }

    public static Collection<ClientSubLevel> open() {
        return MIRRORS.values();
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
        ClientSubLevel mirror = Mirrors.open(level, payload.token(), payload.plot());
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
            if (child != null) riders.add(new Rider(child, SableNBTUtils.readPose3d(rider.getCompound("relative"))));
        }
        if (!riders.isEmpty()) RIDERS.put(payload.token(), riders);
        USED.put(payload.token(), level.getGameTime());
    }

    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
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
            BuildSites.wear(pose, mirror, wearer, BuildSites.offsetVector(HatRendering.headwear(wearer)));
            if (WORN.contains(token)) Mirrors.moveTo(mirror, pose);
            else Mirrors.snapTo(mirror, pose);
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
                STANDS.remove(token);
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
        List<Rider> riders = RIDERS.remove(token);
        if (riders != null) riders.forEach(rider -> Mirrors.close(rider.mirror()));
    }

    private static void forget(UUID token) {
        USED.remove(token);
        REQUESTED.remove(token);
        STANDING.remove(token);
        EASED.remove(token);
        STANDS.remove(token);
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
        USED.clear();
        REQUESTED.clear();
        MISSING.clear();
        STANDING.clear();
        SEEN.clear();
        WORN.clear();
        EASED.clear();
        STANDS.clear();
        RIDERS.clear();
    }

    private BuildMirrors() {}
}

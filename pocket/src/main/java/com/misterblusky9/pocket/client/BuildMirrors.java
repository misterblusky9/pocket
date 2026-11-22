package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeAlignment;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.HatSeats;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.network.BuildMirrorRequestPayload;
import com.misterblusky9.pocket.network.BuildRiderPosesPayload;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Rules;
import com.misterblusky9.pym.api.client.Mirrors;
import com.misterblusky9.pym.api.client.Portrait;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
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
    private static final int BUSY_RETRY_TICKS = 5;
    private static final Map<UUID, ClientSubLevel> MIRRORS = new HashMap<>();
    private static final Map<UUID, BuildMirrorPayload> CONTENT = new HashMap<>();
    private static final Map<UUID, Long> USED = new HashMap<>();
    private static final Map<UUID, Long> REQUESTED = new HashMap<>();
    private static final Map<UUID, Integer> ASKED_EPOCH = new HashMap<>();
    private static final Set<UUID> MISSING = new HashSet<>();
    private static final Set<UUID> STANDING = new HashSet<>();
    private static final Set<UUID> SEEN = new HashSet<>();
    private static final Set<UUID> WORN = new HashSet<>();
    private static final Map<UUID, double[]> EASED = new HashMap<>();
    private static final Map<UUID, List<Rider>> RIDERS = new HashMap<>();
    private static final Map<UUID, BlockPos> HOSTS = new HashMap<>();
    private static final Map<UUID, Player> WEARERS = new HashMap<>();
    private static final Map<UUID, Integer> EPOCHS = new HashMap<>();
    private static final Map<UUID, Long> ACCEPTED = new HashMap<>();
    private static final int SETTLE_TICKS = 20;
    private static final Rules WORN_RULES = Rules.COSMETIC.collide(true);

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
        UUID token = token(stack);
        return token != null && current(token, stack) ? get(token) : null;
    }

    public static @Nullable ClientSubLevel preview(ItemStack stack) {
        UUID token = token(stack);
        if (token == null) return null;
        boolean upToDate = current(token, stack);
        int held = BuildHostBlockEntity.epoch(stack);
        int shown = EPOCHS.getOrDefault(token, 0);
        return upToDate || (held > shown && MIRRORS.containsKey(token)) ? get(token) : null;
    }

    public static boolean current(UUID token, ItemStack stack) {
        int held = BuildHostBlockEntity.epoch(stack);
        int shown = EPOCHS.getOrDefault(token, 0);
        ClientLevel level = Minecraft.getInstance().level;
        if (held > shown && level != null) requestEpoch(token, held, level.getGameTime());
        return held == shown;
    }

    private static void requestEpoch(UUID token, int epoch, long now) {
        if (ASKED_EPOCH.getOrDefault(token, -1) >= epoch) {
            request(token, now);
            return;
        }
        ASKED_EPOCH.put(token, epoch);
        REQUESTED.put(token, now);
        PacketDistributor.sendToServer(new BuildMirrorRequestPayload(token));
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

    public static @Nullable ClientSubLevel peek(@Nullable UUID token) {
        ClientLevel level = Minecraft.getInstance().level;
        if (token == null || level == null) return null;
        ClientSubLevel mirror = MIRRORS.get(token);
        if (mirror == null) request(token, level.getGameTime());
        else USED.put(token, level.getGameTime());
        return mirror;
    }

    public static @Nullable ClientSubLevel interim(ItemStack stack) {
        UUID token = token(stack);
        if (token == null) return null;
        int held = BuildHostBlockEntity.epoch(stack);
        int shown = EPOCHS.getOrDefault(token, 0);
        if (held == shown || held < shown && !settling(token)) return null;
        return get(token);
    }

    public static boolean pending(ItemStack stack) {
        UUID token = token(stack);
        return token != null && !MISSING.contains(token)
                && (BuildHostBlockEntity.epoch(stack) >= EPOCHS.getOrDefault(token, 0) || settling(token));
    }

    private static boolean settling(UUID token) {
        Long at = ACCEPTED.get(token);
        ClientLevel level = Minecraft.getInstance().level;
        return at != null && level != null && level.getGameTime() - at < SETTLE_TICKS;
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
        standAt(token, mirror, level, pos, eased);
    }

    private static void standAt(UUID token, ClientSubLevel mirror, Level level, BlockPos pos, double[] eased) {
        long now = level.getGameTime();
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
        // Vanilla's client riding tick already positions seat passengers. Repeating
        // it here after moving the stand mirror can conflict with the regular
        // rider position update and introduce camera jitter.
        USED.put(token, now);
        HOSTS.put(token, pos.immutable());
        STANDING.add(token);
        SEEN.add(token);
    }

    private static void seat(UUID token, ClientSubLevel mirror) {
        HatSeats.follow(mirror);
        for (Rider rider : riders(token)) HatSeats.follow(rider.mirror());
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

    public static @Nullable BlockPos standHost(SubLevel part) {
        if (part == null) return null;
        UUID id = part.getUniqueId();
        for (UUID token : STANDING)
            if (matchesHatPart(token, MIRRORS.get(token), id)) return HOSTS.get(token);
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

    public static boolean isKnownHatPart(SubLevel candidate) {
        if (candidate == null) return false;
        UUID id = candidate.getUniqueId();
        for (Map.Entry<UUID, ClientSubLevel> entry : MIRRORS.entrySet()) {
            if (matchesHatPart(entry.getKey(), entry.getValue(), id)) return true;
        }
        return false;
    }

    public static boolean isWornHatPart(SubLevel candidate) {
        if (candidate == null) return false;
        UUID id = candidate.getUniqueId();
        for (UUID token : WORN) {
            if (matchesHatPart(token, MIRRORS.get(token), id)) return true;
        }
        return false;
    }

    private static boolean matchesHatPart(UUID token, ClientSubLevel root, UUID id) {
        if (root != null && !root.isRemoved() && id.equals(root.getUniqueId())) return true;
        for (Rider rider : riders(token)) {
            ClientSubLevel part = rider.mirror();
            if (!part.isRemoved() && id.equals(part.getUniqueId())) return true;
        }
        return false;
    }

    public static @Nullable Player wearerOf(SubLevel part) {
        if (part == null) return null;
        for (UUID token : WORN) {
            ClientSubLevel root = MIRRORS.get(token);
            if (root == null) continue;
            if (root == part) return WEARERS.get(token);
            for (Rider rider : riders(token)) if (rider.mirror() == part) return WEARERS.get(token);
        }
        return null;
    }

    public static boolean seatable(SubLevel part, BlockState state) {
        if (!(state.getBlock() instanceof SeatBlock)) return false;
        Player wearer = wearerOf(part);
        Player player = Minecraft.getInstance().player;
        return wearer != null && player != null && wearer != player && !player.hasIndirectPassenger(wearer);
    }

    public static boolean wornBy(SubLevel part, Entity entity) {
        if (!(part instanceof ClientSubLevel)) return false;
        Player wearer = wearerOf(part);
        return wearer != null && entity.getRootVehicle() == wearer.getRootVehicle();
    }

    public static @Nullable ClientSubLevel wornRootOf(SubLevel part) {
        if (part == null) return null;
        for (UUID token : WORN) {
            ClientSubLevel root = MIRRORS.get(token);
            if (root == null) continue;
            if (root == part) return root;
            for (Rider rider : riders(token)) if (rider.mirror() == part) return root;
        }
        return null;
    }

    public static @Nullable Pose3d wornFrame(SubLevel part, float partialTick) {
        if (part == null) return null;
        for (UUID token : WORN) {
            ClientSubLevel root = MIRRORS.get(token);
            Player wearer = WEARERS.get(token);
            if (root == null || root.isRemoved() || wearer == null || wearer.isRemoved()) continue;
            boolean isRoot = root == part;
            Rider owner = null;
            if (!isRoot) for (Rider rider : riders(token)) if (rider.mirror() == part) owner = rider;
            if (!isRoot && owner == null) continue;
            Pose3d hat = WornHatFrame.at(root, wearer, partialTick);
            if (hat == null) return null;
            return isRoot ? hat : Detached.compose(hat, owner.at());
        }
        return null;
    }

    public static boolean isStandingHatPart(SubLevel candidate) {
        if (candidate == null) return false;
        UUID id = candidate.getUniqueId();
        for (UUID token : STANDING) {
            if (matchesHatPart(token, MIRRORS.get(token), id)) return true;
        }
        return false;
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
        UUID token = payload.token();
        int incomingEpoch = payload.children().getInt(BuildSites.MIRROR_EPOCH);
        if (incomingEpoch < EPOCHS.getOrDefault(token, 0)) return;
        if (payload.plot().isEmpty()) {
            MISSING.add(token);
            return;
        }
        if (payload.children().contains(FacadeAlignment.MIRROR_KEY, Tag.TAG_INT_ARRAY))
            CopycatFacadeFrames.acceptAlignment(
                    payload.children().hasUUID("uuid") ? payload.children().getUUID("uuid") : token,
                    payload.children().getIntArray(FacadeAlignment.MIRROR_KEY));
        ClientSubLevel existing = MIRRORS.get(token);
        BuildMirrorPayload previous = CONTENT.get(token);
        List<Rider> existingRiders = RIDERS.getOrDefault(token, List.of());
        boolean ridersComplete = existingRiders.size() == payload.children()
                .getList("children", Tag.TAG_COMPOUND).size()
                && existingRiders.stream().noneMatch(rider -> rider.mirror().isRemoved());
        if (existing != null && !existing.isRemoved() && existing.getLevel() == level
                && ridersComplete && previous != null && sameContent(previous, payload)) {
            EPOCHS.put(token, incomingEpoch);
            USED.put(token, level.getGameTime());
            ACCEPTED.put(token, level.getGameTime());
            MISSING.remove(token);
            REQUESTED.remove(token);
            ASKED_EPOCH.remove(token);
            return;
        }
        ClientSubLevel old = MIRRORS.remove(token);
        BlockPos host = old != null && STANDING.contains(payload.token()) ? HOSTS.get(payload.token()) : null;
        double[] eased = EASED.get(payload.token());
        if (old != null) {
            Mirrors.close(old);
            forget(payload.token());
            WORN.remove(payload.token());
        }
        dismount(payload.token());
        HatNativeContraptionRender.forget(payload.token());
        UUID id = payload.children().hasUUID("uuid") ? payload.children().getUUID("uuid") : payload.token();
        ClientSubLevel mirror = Mirrors.open(level, id, payload.plot());
        if (mirror == null && evict(level, payload.plot())) mirror = Mirrors.open(level, id, payload.plot());
        if (mirror == null) {
            MISSING.add(payload.token());
            if (occupied(level, payload.plot()))
                REQUESTED.put(payload.token(), level.getGameTime() - RETRY_TICKS + BUSY_RETRY_TICKS);
            return;
        }
        MISSING.remove(payload.token());
        MIRRORS.put(token, mirror);
        CONTENT.put(token, payload);
        EPOCHS.put(token, incomingEpoch);
        ACCEPTED.put(token, level.getGameTime());
        REQUESTED.remove(token);
        ASKED_EPOCH.remove(token);
        List<Rider> riders = new ArrayList<>();
        for (Tag entry : payload.children().getList("children", Tag.TAG_COMPOUND)) {
            CompoundTag rider = (CompoundTag) entry;
            ClientSubLevel child = Mirrors.open(level, rider.getUUID("uuid"), rider.getCompound("plot"));
            if (child == null && evict(level, rider.getCompound("plot")))
                child = Mirrors.open(level, rider.getUUID("uuid"), rider.getCompound("plot"));
            if (child != null) {
                Pose3d relative = Detached.loadPose(rider.getCompound("relative"));
                riders.add(new Rider(child, relative, new RiderPlayback()));
            }
        }
        if (!riders.isEmpty()) RIDERS.put(payload.token(), riders);
        HatNativeContraptionRender.accept(payload.token(), payload.children().getCompound("native_contraptions"));
        USED.put(payload.token(), level.getGameTime());
        if (host != null && eased != null) {
            EASED.put(payload.token(), eased);
            standAt(payload.token(), mirror, level, host, eased);
        }
    }

    private static boolean sameContent(BuildMirrorPayload previous, BuildMirrorPayload incoming) {
        if (!previous.plot().equals(incoming.plot())) return false;
        CompoundTag before = previous.children();
        CompoundTag after = incoming.children();
        if (before.size() != after.size()) return false;
        for (String key : before.getAllKeys()) {
            if (key.equals(BuildSites.MIRROR_EPOCH)) continue;
            if (!java.util.Objects.equals(before.get(key), after.get(key))) return false;
        }
        return true;
    }

    private static boolean evict(ClientLevel level, CompoundTag plot) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        int x = plot.getInt("plot_x");
        int z = plot.getInt("plot_z");
        int side = 1 << container.getLogSideLength();
        if (x < 0 || z < 0 || x >= side || z >= side) return false;
        SubLevel there = container.getSubLevel(x, z);
        if (there == null) return false;
        for (Map.Entry<UUID, ClientSubLevel> entry : MIRRORS.entrySet()) {
            UUID token = entry.getKey();
            if (!containsPart(token, entry.getValue(), there)) continue;
            Mirrors.close(entry.getValue());
            MIRRORS.remove(token);
            EPOCHS.remove(token);
            WORN.remove(token);
            forget(token);
            dismount(token);
            return container.getSubLevel(x, z) == null;
        }
        return false;
    }

    private static boolean occupied(ClientLevel level, CompoundTag plot) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        int x = plot.getInt("plot_x");
        int z = plot.getInt("plot_z");
        int side = container == null ? 0 : 1 << container.getLogSideLength();
        return x >= 0 && z >= 0 && x < side && z < side && container.getSubLevel(x, z) != null;
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
        Map<UUID, Player> wearers = new HashMap<>();
        for (Player wearer : level.players()) {
            ItemStack headwear = HatRendering.headwear(wearer);
            UUID token = BuildHostBlockEntity.token(headwear);
            ClientSubLevel mirror = token == null ? null : MIRRORS.get(token);
            if (mirror == null || !current(token, headwear)) continue;
            Pose3d pose = new Pose3d();
            BuildSites.wear(pose, mirror, wearer, BuildSites.offsetVector(headwear), headwear);
            if (WORN.contains(token)) Mirrors.moveTo(mirror, pose);
            else Mirrors.snapTo(mirror, pose);
            for (Rider rider : riders(token)) place(rider, pose, WORN.contains(token));
            seat(token, mirror);
            Mirrors.rules(mirror, WORN_RULES);
            riders(token).forEach(rider -> Mirrors.rules(rider.mirror(), WORN_RULES));
            worn.add(token);
            wearers.put(token, wearer);
        }
        for (UUID token : WORN) {
            ClientSubLevel mirror = MIRRORS.get(token);
            if (worn.contains(token) || mirror == null || STANDING.contains(token)) continue;
            Mirrors.rules(mirror, Rules.COSMETIC);
            riders(token).forEach(rider -> Mirrors.rules(rider.mirror(), Rules.COSMETIC));
        }
        WORN.clear();
        WORN.addAll(worn);
        WEARERS.clear();
        WEARERS.putAll(wearers);
        Iterator<Map.Entry<UUID, ClientSubLevel>> iterator = MIRRORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ClientSubLevel> entry = iterator.next();
            UUID token = entry.getKey();
            ClientSubLevel mirror = entry.getValue();
            if (STANDING.contains(token) && !SEEN.contains(token)) {
                STANDING.remove(token);
                EASED.remove(token);
                HOSTS.remove(token);
                Rules rules = worn.contains(token) ? WORN_RULES : Rules.COSMETIC;
                Mirrors.rules(mirror, rules);
                riders(token).forEach(rider -> Mirrors.rules(rider.mirror(), rules));
            }
            if (mirror.isRemoved()) {
                iterator.remove();
                EPOCHS.remove(token);
                forget(token);
                dismount(token);
            } else if (now - USED.getOrDefault(token, now) > IDLE_TICKS) {
                Mirrors.close(mirror);
                iterator.remove();
                EPOCHS.remove(token);
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
        CONTENT.remove(token);
        ACCEPTED.remove(token);
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
        if (!MIRRORS.containsKey(token)) request(token, now);
        else if (current(token, stack)) USED.put(token, now);
    }

    public static void clear(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        MIRRORS.clear();
        CONTENT.clear();
        WEARERS.clear();
        EPOCHS.clear();
        ACCEPTED.clear();
        HatNativeContraptionRender.clear();
        USED.clear();
        REQUESTED.clear();
        ASKED_EPOCH.clear();
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

package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.compat.curios.CuriosHats;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class CarriedBuilds {
    private static final boolean CURIOS = ModList.get().isLoaded("curios");
    private static final int HOTBAR = 9;

    private static final class Live {
        ServerSubLevel hat;
        final Set<UUID> riders = new HashSet<>();
        int kept;

        Live(ServerSubLevel hat) {
            this.hat = hat;
        }
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();
    private static int now;

    public static void keep(ServerLevel level, UUID token, BlockPos near, boolean hat, Consumer<ServerSubLevel> pose) {
        if (BuildSites.live(token) != null) return;
        Live live = LIVE.get(token);
        if (live != null && live.hat.isRemoved()) {
            LIVE.remove(token);
            live = null;
        }
        if (live != null && live.hat.getLevel() != level) {
            close(token);
            live = null;
        }
        if (live == null) {
            ServerSubLevel opened = BuildSites.load(level, token);
            if (opened == null) return;
            live = new Live(opened);
            LIVE.put(token, live);
        }
        live.kept = now;
        if (Hosted.isRoot(live.hat)) {
            Pose3d physical = new Pose3d(live.hat.logicalPose());
            pose.accept(live.hat);
            Hosted.present(live.hat, live.hat.logicalPose());
            live.hat.logicalPose().set(physical);
        } else {
            pose.accept(live.hat);
            if (hat && BuildSites.hasBuild(live.hat)) BuildSites.tryHost(token, live.hat);
        }
        BuildSites.watch(token, live.hat, level, near);
        if (Hosted.isRoot(live.hat)) BuildSites.publishHosted(token, live.hat, live.riders);
    }

    public static boolean openRiders(ServerSubLevel hat) {
        if (!Detached.children(hat).isEmpty()) return true;
        for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
            if (entry.getValue().hat == hat) return BuildSites.openRiders(hat.getLevel(), entry.getKey(), hat);
        }
        return true;
    }

    public static void replaced(ServerSubLevel before, ServerSubLevel after) {
        BuildSites.replaced(before, after);
        for (Live live : LIVE.values()) {
            if (live.hat == before) live.hat = after;
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BuildHostBlockEntity.token(head) == null && CURIOS) {
            ItemStack cosmetic = CuriosHats.find(player);
            if (cosmetic != null) head = cosmetic;
        }
        UUID worn = BuildHostBlockEntity.token(head);
        if (worn != null) {
            Vector3d offset = BuildSites.offsetVector(head);
            ItemStack headwear = head;
            keep(player.serverLevel(), worn, player.blockPosition(), true,
                    hat -> BuildSites.wear(hat.logicalPose(), hat, player, offset, headwear));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) carry(player, player.getItemBySlot(slot), worn);
        for (int i = 0; i < HOTBAR; i++) carry(player, player.getInventory().getItem(i), worn);
        if (CURIOS) CuriosHats.forEach(player, stack -> carry(player, stack, worn));
    }

    private static void carry(ServerPlayer player, ItemStack stack, @Nullable UUID worn) {
        UUID token = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token == null || token.equals(worn)) return;
        keep(player.serverLevel(), token, player.blockPosition(), !(stack.getItem() instanceof PocketCaseItem), hat -> BuildSites.hold(hat,
                new Vector3d(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ()),
                new Quaterniond().rotateY(Math.toRadians(180.0 - player.getYRot())), stack));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, Live> entry : List.copyOf(LIVE.entrySet())) {
            if (entry.getValue().kept != now) close(entry.getKey());
        }
        now++;
    }

    public static @Nullable ServerSubLevel worn(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BuildHostBlockEntity.token(head) == null && CURIOS) {
            ItemStack cosmetic = CuriosHats.find(player);
            if (cosmetic != null) head = cosmetic;
        }
        UUID token = BuildHostBlockEntity.token(head);
        Live live = token == null ? null : LIVE.get(token);
        return live != null && live.hat.getLevel() == player.serverLevel() ? live.hat : null;
    }

    public static void close(UUID token) {
        HatNativeContraptions.forget(token);
        Live live = LIVE.remove(token);
        if (live == null) return;
        ServerSubLevel hat = Hosted.isRoot(live.hat) ? Hosted.unhost(live.hat) : live.hat;
        if (hat == null) return;
        HatContraptions.disassemble(hat);
        save(token, hat);
        for (ServerSubLevel child : Detached.children(hat)) Detached.close(child);
        Detached.close(hat);
    }

    private static void save(UUID token, ServerSubLevel hat) {
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(hat.getLevel());
        CompoundTag saved = storage.getCopy(token);
        if (saved == null) return;
        saved.put("plot", hat.getPlot().save());
        BuildSites.storeRiders(saved, hat);
        storage.put(token, saved);
    }

    public static @Nullable BuildMirrorPayload mirror(UUID token, ServerPlayer viewer) {
        Live live = LIVE.get(token);
        if (live == null || live.hat.getLevel() != viewer.serverLevel()) return null;
        live.hat.getTrackingPlayers().add(viewer.getUUID());
        if (Hosted.isRoot(live.hat)) return BuildSites.mirror(token, live.hat);
        CompoundTag children = BuildSites.mirrorChildren(live.hat.getLevel(), token);
        children.put("native_contraptions", HatNativeContraptions.snapshot(live.hat));
        return new BuildMirrorPayload(token, live.hat.getPlot().save(), children);
    }

    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
            if (entry.getValue().hat.getLevel() == level) save(entry.getKey(), entry.getValue().hat);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID token : List.copyOf(LIVE.keySet())) close(token);
    }

    private CarriedBuilds() {}
}

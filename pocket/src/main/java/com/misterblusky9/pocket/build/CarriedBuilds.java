package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.advancement.HatAdvancements;
import com.misterblusky9.pocket.block.FacadeAlignment;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.compat.WornHats;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.misterblusky9.pocket.network.BuildMirrorPayload;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pocket.config.DeviceRanges;
import net.minecraft.network.chat.Component;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
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
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class CarriedBuilds {
    private static final int HOTBAR = 9;
    private static final double POP_SPEED = 6.0D;
    private static final double POP_TUMBLE = 4.0D;

    private static final class Live {
        ServerSubLevel hat;
        final Set<UUID> riders = new HashSet<>();
        int kept;
        ItemStack stack = ItemStack.EMPTY;
        @Nullable ServerPlayer wearer;

        Live(ServerSubLevel hat) {
            this.hat = hat;
        }
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();
    private static final Map<AbstractContainerMenu, Boolean> LISTENED = new WeakHashMap<>();
    private static int now;

    public static void keep(ServerLevel level, UUID token, BlockPos near, ItemStack stack, Consumer<ServerSubLevel> pose) {
        keep(level, token, near, stack, pose, () -> {}, null);
    }

    public static void keep(ServerLevel level, UUID token, BlockPos near, ItemStack stack, Consumer<ServerSubLevel> pose,
                            Runnable stamped) {
        keep(level, token, near, stack, pose, stamped, null);
    }

    private static void keep(ServerPlayer player, UUID token, ItemStack stack, Consumer<ServerSubLevel> pose,
                             @Nullable ServerPlayer wearer) {
        keep(player.serverLevel(), token, player.blockPosition(), stack, pose,
                () -> player.containerMenu.broadcastChanges(), wearer);
    }

    private static void keep(ServerLevel level, UUID token, BlockPos near, ItemStack stack, Consumer<ServerSubLevel> pose,
                             Runnable stamped, @Nullable ServerPlayer wearer) {
        if (stack.getItem() instanceof PocketCaseItem
                && PocketContainer.of(stack) == PocketContainer.CREATIVE_POCKET_CASE) return;
        boolean hat = !(stack.getItem() instanceof PocketCaseItem);
        if (BuildSites.live(token) != null) {
            if (hat) BuildHostBlockEntity.strip(stack);
            return;
        }
        if (!BuildSites.settle(level, token, stack)) {
            if (hat) BuildHostBlockEntity.strip(stack);
            return;
        }
        Live live = LIVE.get(token);
        if (live != null && live.hat.isRemoved()) {
            LIVE.remove(token);
            live = null;
        }
        if (live != null && live.hat.getLevel() != level) {
            close(token);
            live = null;
        }
        if (live != null && live.kept == now && live.stack != stack) {
            if (hat) BuildHostBlockEntity.strip(stack);
            return;
        }
        if (live == null) {
            ServerSubLevel opened = BuildSites.load(level, token);
            if (opened == null) return;
            BuildHostBlockEntity.stampEpoch(stack, BuildSites.claim(level, token));
            stamped.run();
            live = new Live(opened);
            LIVE.put(token, live);
        }
        live.kept = now;
        live.stack = stack;
        if (live.wearer != null && wearer == null) unseat(live.hat);
        live.wearer = wearer;
        if (Hosted.isRoot(live.hat)) {
            Pose3d current = new Pose3d(live.hat.logicalPose());
            pose.accept(live.hat);
            Pose3d shown = new Pose3d(live.hat.logicalPose());
            live.hat.logicalPose().set(current);
            Hosted.present(live.hat, shown);
        } else {
            pose.accept(live.hat);
            if (hat && BuildSites.hasBuild(live.hat)) BuildSites.tryHost(token, live.hat);
        }
        if (wearer != null) {
            for (ServerSubLevel part : parts(live.hat)) {
                HatSeats.unseat(part, rider -> tooBig(rider, wearer));
                HatSeats.follow(part);
            }
        }
        BuildSites.watch(token, live.hat, level, near);
        if (Hosted.isRoot(live.hat)) BuildSites.publishHosted(token, live.hat, live.riders);
    }

    public static boolean holds(UUID token) {
        Live live = LIVE.get(token);
        return live != null && !live.hat.isRemoved();
    }

    public static boolean isHat(ServerSubLevel subLevel) {
        for (Live live : LIVE.values())
            if (live.hat == subLevel && !(live.stack.getItem() instanceof PocketCaseItem)) return true;
        return false;
    }

    public static boolean belongsToHat(@Nullable dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel at)) return false;
        Set<ServerSubLevel> seen = new HashSet<>();
        while (at != null && seen.add(at)) {
            if (isHat(at) || BuildSites.isAttached(at)) return true;
            ServerSubLevel root = Hosted.rootOf(at);
            if (root != null && root != at && (isHat(root) || BuildSites.isAttached(root))) return true;
            at = Detached.parent(at);
        }
        return false;
    }

    public static boolean isWorn(ServerSubLevel hat) {
        Live live = live(hat);
        return live != null && live.wearer != null;
    }

    public static boolean wornBy(dev.ryanhcode.sable.sublevel.SubLevel subLevel, Entity entity) {
        if (!(subLevel instanceof ServerSubLevel server)) return false;
        ServerSubLevel root = Hosted.rootOf(server);
        Live live = live(root == null ? server : root);
        return live != null && live.wearer != null && entity.getRootVehicle() == live.wearer.getRootVehicle();
    }

    public static void flush(UUID token) {
        Live live = LIVE.get(token);
        if (live != null && !live.hat.isRemoved()) save(token, live.hat, live.stack);
    }

    public static boolean openRiders(ServerSubLevel hat) {
        if (!Detached.children(hat).isEmpty()) return true;
        for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
            if (entry.getValue().hat == hat) return BuildSites.openRiders(hat.getLevel(), entry.getKey(), hat);
        }
        return true;
    }

    public static void replaced(ServerSubLevel before, ServerSubLevel after) {
        FacadeAlignment.carry(before, after);
        BuildSites.replaced(before, after);
        for (Live live : LIVE.values()) {
            if (live.hat == before) live.hat = after;
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BuildHostBlockEntity.token(head) == null) {
            ItemStack cosmetic = WornHats.find(player);
            if (cosmetic != null) head = cosmetic;
        }
        UUID worn = BuildHostBlockEntity.token(head);
        if (worn != null) {
            Vector3d offset = BuildSites.offsetVector(head);
            ItemStack headwear = head;
            adoptCreator(player, headwear);
            keep(player, worn, headwear,
                    hat -> BuildSites.wear(hat.logicalPose(), hat, player, offset, headwear), player);
            if (BuildHostBlockEntity.token(head) == null) worn = null;
        }
        Live wornLive = worn == null ? null : LIVE.get(worn);
        HatAdvancements.worn(player, worn,
                wornLive != null && wornLive.hat.getLevel() == player.serverLevel() ? wornLive.hat : null, head);
        ItemStack wornStack = worn == null ? ItemStack.EMPTY : head;
        for (EquipmentSlot slot : EquipmentSlot.values()) carry(player, player.getItemBySlot(slot), wornStack);
        for (int i = 0; i < HOTBAR; i++) carry(player, player.getInventory().getItem(i), wornStack);
        WornHats.forEach(player, stack -> carry(player, stack, wornStack));
        if (LISTENED.putIfAbsent(player.inventoryMenu, Boolean.TRUE) == null) listen(player);
    }

    private static void listen(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) arrived(player, stack);
        player.inventoryMenu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
                arrived(player, stack);
            }

            @Override
            public void dataChanged(AbstractContainerMenu menu, int index, int value) {}
        });
    }

    private static void arrived(ServerPlayer player, ItemStack stack) {
        UUID token = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token == null) return;
        BuildSites.settle(player.serverLevel(), token, stack);
        adoptCreator(player, stack);
    }

    private static void adoptCreator(ServerPlayer player, ItemStack stack) {
        if (BuildHostBlockEntity.token(stack) != null && BuildHostBlockEntity.creator(stack) == null)
            BuildHostBlockEntity.stampCreator(stack, player.getGameProfile().getName());
    }

    private static void carry(ServerPlayer player, ItemStack stack, ItemStack worn) {
        UUID token = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token == null || stack == worn) return;
        adoptCreator(player, stack);
        keep(player, token, stack, hat -> BuildSites.hold(hat,
                new Vector3d(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ()),
                new Quaterniond().rotateY(Math.toRadians(180.0 - player.getYRot())), stack), null);
    }

    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !(entity.level() instanceof ServerLevel level)) return;
        ItemStack stack = event.getTo();
        UUID token = BuildHostBlockEntity.token(stack);
        if (token == null) return;
        Live live = LIVE.get(token);
        boolean carried = live != null && live.kept >= now - 1 && live.stack != stack
                && token.equals(BuildHostBlockEntity.token(live.stack));
        if (carried || BuildSites.live(token) != null || !BuildSites.current(level, token, stack))
            BuildHostBlockEntity.strip(stack);
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BuildHostBlockEntity.token(head) == null) {
            ItemStack cosmetic = WornHats.find(player);
            if (cosmetic != null) head = cosmetic;
        }
        if (NameEgg.of(head) != NameEgg.RHYGUY) return;
        UUID token = BuildHostBlockEntity.token(head);
        Live live = LIVE.get(token);
        if (live == null || live.hat.isRemoved() || live.hat.getLevel() != player.serverLevel()
                || !BuildSites.hasBuild(live.hat)) return;
        LIVE.remove(token);
        HatNativeContraptions.forget(token);
        unseat(live.hat);
        ServerSubLevel hat = Hosted.isRoot(live.hat) ? Hosted.unhost(live.hat) : live.hat;
        if (hat == null) return;
        HatContraptions.disassemble(hat);
        for (ServerSubLevel child : Detached.children(hat)) HatContraptions.disassemble(child);
        PocketCaseItem.rememberHatAnchor(hat, BuildSites.scaffoldOrigin(hat,
                ModBlocks.HAT_STAND.get().scaffoldSize(), BuildSites.offset(head)));
        UUID id = hat.getUniqueId();
        ServerSubLevel popped = BuildSites.slice(token, hat, PocketCaseItem.POCKETABLE_SCALE);
        ServerSubLevel back = Detached.find(id);
        if (back != null) {
            live.hat = back;
            LIVE.put(token, live);
            if (popped != null) save(token, back, head);
        }
        if (popped == null) return;
        if (back == null) {
            BuildHostBlockEntity.strip(head);
            player.containerMenu.broadcastChanges();
        }
        RigidBodyHandle handle = RigidBodyHandle.of(popped);
        if (handle == null) return;
        double yaw = Math.toRadians(player.getYHeadRot());
        handle.addLinearAndAngularVelocity(new Vector3d(0.0D, POP_SPEED, 0.0D),
                new Vector3d(Math.cos(yaw), 0.0D, Math.sin(yaw)).mul(-POP_TUMBLE));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, Live> entry : List.copyOf(LIVE.entrySet())) {
            if (entry.getValue().kept != now) close(entry.getKey());
        }
        now++;
    }

    public static @Nullable ServerSubLevel worn(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BuildHostBlockEntity.token(head) == null) {
            ItemStack cosmetic = WornHats.find(player);
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
        unseat(live.hat);
        ServerSubLevel hat = Hosted.isRoot(live.hat) ? Hosted.unhost(live.hat) : live.hat;
        if (hat == null) return;
        HatContraptions.disassemble(hat);
        save(token, hat, live.stack);
        for (ServerSubLevel child : Detached.children(hat)) Detached.close(child);
        Detached.close(hat);
    }

    private static void save(UUID token, ServerSubLevel hat, ItemStack stack) {
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(hat.getLevel());
        if (!storage.contains(token)) return;
        storage.edit(token, saved -> {
            if (changed(hat)) {
                saved.put("plot", hat.getPlot().save());
                PocketCaseItem.refreshIntegrity(saved, hat);
            }
            BuildSites.storeRiders(saved, hat);
            FacadeAlignment.store(saved, hat);
        });
        UUID held = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token.equals(held)) storage.remember(token, stack, hat.getLevel().registryAccess());
    }

    static void unseat(ServerSubLevel hat) {
        for (ServerSubLevel part : parts(hat)) HatSeats.unseat(part);
    }

    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide()) return;
        if (!seatable(event.getEntityBeingMounted(), event.getEntityMounting())) event.setCanceled(true);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!wornAt(event.getLevel(), event.getPos())) return;
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SeatBlock
                && !event.getEntity().isSecondaryUseActive() && DyeColor.getColor(event.getItemStack()) == null) {
            event.setUseItem(TriState.FALSE);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (wornAt(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    private static boolean wornAt(Level level, BlockPos pos) {
        if (level.isClientSide()) return com.misterblusky9.pocket.client.BuildMirrors.wornAt(pos);
        ServerSubLevel root = pocketed(Sable.HELPER.getContaining(level, pos));
        return root != null && isWorn(root);
    }

    private static boolean seatable(Entity vehicle, Entity rider) {
        ServerSubLevel root = pocketed(Sable.HELPER.getContaining(vehicle));
        if (root == null || BuildSites.isAttached(root)) return true;
        Live live = live(root);
        return live != null && live.wearer != null && !carries(rider, live.wearer) && !tooBig(rider, live.wearer);
    }

    private static boolean tooBig(Entity rider, Entity wearer) {
        double ratio = DeviceRanges.hatSeatMinWearerRatio();
        if (ratio <= 0.0D || Pym.entities().scaleOf(wearer) >= ratio * Pym.entities().scaleOf(rider)) return false;
        if (rider instanceof Player player) player.displayClientMessage(Component.translatable("pocket.message.seat_too_small"), true);
        return true;
    }

    private static boolean carries(Entity rider, Entity wearer) {
        Set<Entity> seen = new HashSet<>();
        Entity at = wearer;
        while (at != null && seen.add(at)) {
            if (at == rider) return true;
            Entity vehicle = at.getVehicle();
            if (vehicle == null) return false;
            ServerSubLevel root = pocketed(Sable.HELPER.getContaining(vehicle));
            Live live = root == null ? null : live(root);
            at = live != null && live.wearer != null ? live.wearer : vehicle;
        }
        return false;
    }

    private static @Nullable ServerSubLevel pocketed(@Nullable dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel at)) return null;
        Set<ServerSubLevel> seen = new HashSet<>();
        while (at != null && seen.add(at)) {
            if (BuildSites.isAttached(at) || live(at) != null) return at;
            ServerSubLevel root = Hosted.rootOf(at);
            at = root != null && root != at ? root : Detached.parent(at);
        }
        return null;
    }

    private static @Nullable Live live(ServerSubLevel hat) {
        for (Live live : LIVE.values()) if (live.hat == hat) return live;
        return null;
    }

    static List<ServerSubLevel> parts(ServerSubLevel hat) {
        List<ServerSubLevel> parts = new java.util.ArrayList<>();
        parts.add(hat);
        parts.addAll(Detached.children(hat));
        parts.addAll(Hosted.members(hat));
        return parts;
    }

    private static boolean changed(ServerSubLevel hat) {
        for (ServerSubLevel part : parts(hat))
            for (var holder : part.getPlot().getLoadedChunks())
                if (holder.getChunk().isUnsaved()) return true;
        return false;
    }

    public static @Nullable BuildMirrorPayload mirror(UUID token, ServerPlayer viewer) {
        Live live = LIVE.get(token);
        if (live == null || live.hat.getLevel() != viewer.serverLevel()) return null;
        live.hat.getTrackingPlayers().add(viewer.getUUID());
        if (Hosted.isRoot(live.hat)) return BuildSites.mirror(token, live.hat);
        CompoundTag children = BuildSites.mirrorChildren(live.hat.getLevel(), token);
        children.put("native_contraptions", HatNativeContraptions.snapshot(live.hat));
        FacadeAlignment.stamp(children, live.hat.getUserDataTag());
        return new BuildMirrorPayload(token, Detached.mirrorPlot(live.hat), children);
    }

    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
            if (entry.getValue().hat.getLevel() == level) save(entry.getKey(), entry.getValue().hat, entry.getValue().stack);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID token : List.copyOf(LIVE.keySet())) close(token);
    }

    private CarriedBuilds() {}
}

package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.CarriedBuilds;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.network.FacadeAlignmentSyncPayload;
import com.misterblusky9.pocket.item.ToolPermissions;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FacadeAlignment {
    private static final String OFFSET_KEY = "pocket_facade_offset";
    public static final String MIRROR_KEY = "facade_offset";
    private static final Map<UUID, Set<UUID>> SENT = new HashMap<>();

    public static int[] of(ServerSubLevel craft) {
        CompoundTag data = craft == null ? null : craft.getUserDataTag();
        if (data == null || !data.contains(OFFSET_KEY, Tag.TAG_INT_ARRAY)) return null;
        int[] offset = data.getIntArray(OFFSET_KEY);
        return offset.length == 3 ? offset : null;
    }

    public static void stamp(CompoundTag children, @Nullable CompoundTag userData) {
        if (userData != null && userData.contains(OFFSET_KEY, Tag.TAG_INT_ARRAY)
                && userData.getIntArray(OFFSET_KEY).length == 3)
            children.putIntArray(MIRROR_KEY, userData.getIntArray(OFFSET_KEY));
        else children.remove(MIRROR_KEY);
    }

    public static void store(CompoundTag saved, ServerSubLevel craft) {
        int[] offset = of(craft);
        CompoundTag userData = saved.getCompound("user_data");
        if (offset == null) userData.remove(OFFSET_KEY);
        else userData.putIntArray(OFFSET_KEY, offset);
        if (!userData.isEmpty()) saved.put("user_data", userData);
        else saved.remove("user_data");
    }

    public static void restore(ServerSubLevel craft, @Nullable CompoundTag saved) {
        CompoundTag userData = saved == null ? null : saved.getCompound("user_data");
        if (craft == null || userData == null || !userData.contains(OFFSET_KEY, Tag.TAG_INT_ARRAY)) return;
        int[] offset = userData.getIntArray(OFFSET_KEY);
        if (offset.length == 3) put(craft, offset);
    }

    public static void carry(ServerSubLevel before, ServerSubLevel after) {
        if (before == null || after == null || before == after || of(after) != null) return;
        int[] offset = of(before);
        if (offset != null) put(after, offset);
    }

    private static void put(ServerSubLevel craft, int[] offset) {
        CompoundTag data = craft.getUserDataTag() == null ? new CompoundTag() : craft.getUserDataTag();
        data.putIntArray(OFFSET_KEY, offset.clone());
        craft.setUserDataTag(data);
    }

    public static double tilingScale(ServerSubLevel craft) {
        if (BuildSites.isAttached(craft) || CarriedBuilds.isHat(craft)
                || craft.getLevel().getBlockState(craft.getPlot().getCenterBlock()).is(ModBlocks.POCKET_DUMMY.get()))
            return CompressionStage.SIXTEENTH.scale();
        return Pym.scale().settled(craft);
    }

    public static boolean holds(ItemStack stack, Block material) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() == material;
    }

    public static boolean shrunk(double scale) {
        return scale < ScaleBounds.FULL && !ScaleBounds.same(scale, ScaleBounds.FULL);
    }

    public static void align(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return;

        ServerLevel level = player.serverLevel();
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel craft) || craft.isRemoved()) return;
        if (!withinReach(player, craft, pos)) return;
        if (!level.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())) return;
        if (!(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat) || !copycat.hasCustomMaterial()) return;

        Block material = copycat.getMaterial().getBlock();
        if (!holds(player.getMainHandItem(), material) && !holds(player.getOffhandItem(), material)) return;

        double scale = tilingScale(craft);
        if (!shrunk(scale)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        int plotSize = 1 << (container.getLogPlotSize() + 4);
        int[] block = {
                Math.floorMod(pos.getX(), plotSize),
                pos.getY() - level.getMinBuildHeight(),
                Math.floorMod(pos.getZ(), plotSize)
        };
        int[] offset = FacadeTiling.alignment(block, scale);
        if (offset == null) return;

        save(craft, offset);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.75F, 1.2F);
    }

    public static void nudge(ServerPlayer player, BlockPos pos, int faceId, int step) {
        if (player == null || pos == null || faceId < 0 || faceId >= 6 || Math.abs(step) != 1) return;
        if (!AllItems.WRENCH.isIn(player.getMainHandItem())
                && !AllItems.WRENCH.isIn(player.getOffhandItem())) return;

        ServerLevel level = player.serverLevel();
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel craft) || craft.isRemoved()) return;
        if (!withinReach(player, craft, pos)
                || !level.getBlockState(pos).is(ModBlocks.COPYCAT_FACADE.get())
                || !(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat)
                || !copycat.hasCustomMaterial() || !ToolPermissions.mayAlter(player, level, pos)) return;

        double scale = tilingScale(craft);
        if (!shrunk(scale)) return;

        Direction face = Direction.from3DDataValue(faceId);
        int[] offset = FacadeTiling.reduce(of(craft), scale);
        int axis = face.getAxis().ordinal();
        offset[axis] = Math.floorMod(offset[axis] + step * face.getAxisDirection().getStep(),
                FacadeTiling.blocksPerTile(scale));
        save(craft, offset);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.35F, 1.4F);
    }

    private static void save(ServerSubLevel craft, int[] offset) {
        CompoundTag data = craft.getUserDataTag() == null ? new CompoundTag() : craft.getUserDataTag();
        data.putIntArray(OFFSET_KEY, offset);
        craft.setUserDataTag(data);
        Set<UUID> sent = SENT.computeIfAbsent(craft.getUniqueId(), ignored -> new HashSet<>());
        sent.clear();
        FacadeAlignmentSyncPayload payload = new FacadeAlignmentSyncPayload(craft.getUniqueId(), offset);
        for (UUID viewer : craft.getTrackingPlayers()) {
            if (craft.getLevel().getServer().getPlayerList().getPlayer(viewer) instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, payload);
                sent.add(viewer);
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Set<UUID> live = new HashSet<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (ServerSubLevel craft : container.getAllSubLevels()) {
                if (craft.isRemoved()) continue;
                int[] offset = of(craft);
                if (offset == null) continue;

                UUID id = craft.getUniqueId();
                live.add(id);
                Set<UUID> sent = SENT.computeIfAbsent(id, ignored -> new HashSet<>());
                sent.retainAll(craft.getTrackingPlayers());
                for (UUID viewer : craft.getTrackingPlayers()) {
                    if (sent.contains(viewer)) continue;
                    ServerPlayer player = event.getServer().getPlayerList().getPlayer(viewer);
                    if (player == null) continue;
                    PacketDistributor.sendToPlayer(player, new FacadeAlignmentSyncPayload(id, offset));
                    sent.add(viewer);
                }
            }
        }
        SENT.keySet().retainAll(live);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    private static boolean withinReach(ServerPlayer player, ServerSubLevel craft, BlockPos pos) {
        Vector3d world = craft.logicalPose().transformPosition(
                new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
        double reach = player.blockInteractionRange() + 1.0D;
        return player.getEyePosition().distanceToSqr(world.x, world.y, world.z) <= reach * reach;
    }

    private FacadeAlignment() {}
}

package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.network.FacadeAlignmentSyncPayload;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
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
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FacadeAlignment {
    private static final String OFFSET_KEY = "pocket_facade_offset";
    private static final Map<UUID, Set<UUID>> SENT = new HashMap<>();

    public static int[] of(ServerSubLevel craft) {
        CompoundTag data = craft == null ? null : craft.getUserDataTag();
        if (data == null || !data.contains(OFFSET_KEY, Tag.TAG_INT_ARRAY)) return null;
        int[] offset = data.getIntArray(OFFSET_KEY);
        return offset.length == 3 ? offset : null;
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

        double scale = Pym.scale().settled(craft);
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

        CompoundTag data = craft.getUserDataTag() == null ? new CompoundTag() : craft.getUserDataTag();
        data.putIntArray(OFFSET_KEY, offset);
        craft.setUserDataTag(data);
        SENT.remove(craft.getUniqueId());
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.75F, 1.2F);
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

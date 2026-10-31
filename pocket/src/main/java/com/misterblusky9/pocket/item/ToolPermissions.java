package com.misterblusky9.pocket.item;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3d;

public final class ToolPermissions {
    public static boolean mayAlter(Player player, ServerLevel level, BlockPos pos) {
        if (!(player instanceof ServerPlayer server) || !server.mayBuild() || pos == null) return false;
        BlockPos world = worldPos(level, pos);
        if (!level.mayInteract(server, pos) || !level.mayInteract(server, world)) return false;
        return !NeoForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(level, world, level.getBlockState(pos), server)).isCanceled();
    }

    private static BlockPos worldPos(ServerLevel level, BlockPos pos) {
        SubLevel owner = Sable.HELPER.getContaining(level, pos);
        if (owner == null) return pos;
        Vector3d world = owner.logicalPose().transformPosition(
                new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
        return BlockPos.containing(world.x, world.y, world.z);
    }

    private ToolPermissions() {}
}

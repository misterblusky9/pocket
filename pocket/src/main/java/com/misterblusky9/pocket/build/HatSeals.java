package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.client.BuildMirrors;
import com.misterblusky9.pocket.item.CaseSeal;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

public final class HatSeals {
    public static boolean locks(@Nullable BuildHostBlockEntity stand, Player player) {
        CaseSeal seal = stand == null ? null : stand.getActivator().get(ModDataComponents.SEAL.get());
        return seal != null && !seal.canCut(player);
    }

    public static boolean locked(Level level, BlockPos pos, Player player) {
        return locks(standAt(level, pos), player) || buildLocked(level, pos, player);
    }

    public static boolean buildLocked(Level level, BlockPos pos, Player player) {
        return locks(standOf(level, pos), player);
    }

    public static @Nullable HatStandBlockEntity standAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HatStandBlock block)) return null;
        return level.getBlockEntity(block.standPos(state, pos)) instanceof HatStandBlockEntity stand ? stand : null;
    }

    public static @Nullable HatStandBlockEntity base(Level level, BlockHitResult hit) {
        HatStandBlockEntity stand = standAt(level, hit.getBlockPos());
        if (stand == null || BuildHostBlockEntity.token(stand.getActivator()) == null
                || stand.onHead(hit.getLocation())) return null;
        return stand;
    }

    private static @Nullable BuildHostBlockEntity standOf(Level level, BlockPos pos) {
        SubLevel part = Sable.HELPER.getContaining(level, pos);
        if (part == null) return null;
        if (level.isClientSide) {
            BlockPos host = BuildMirrors.standHost(part);
            return host != null && level.getBlockEntity(host) instanceof BuildHostBlockEntity stand ? stand : null;
        }
        if (!(part instanceof ServerSubLevel server)) return null;
        ServerSubLevel root = Hosted.rootOf(server);
        if (root == null) root = server;
        ServerSubLevel parent = Detached.parent(root);
        return BuildSites.hostOf(parent == null ? root : parent);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!buildLocked(event.getLevel(), event.getPos(), event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (buildLocked(event.getLevel(), event.getPos(), event.getEntity())) event.setCanceled(true);
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && buildLocked(level, event.getPos(), event.getPlayer()))
            event.setCanceled(true);
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && event.getLevel() instanceof Level level
                && buildLocked(level, event.getPos(), player)) event.setCanceled(true);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!buildLocked(event.getLevel(), event.getTarget().blockPosition(), event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!buildLocked(event.getLevel(), event.getTarget().blockPosition(), event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (buildLocked(player.level(), event.getTarget().blockPosition(), player)) event.setCanceled(true);
    }

    private HatSeals() {}
}

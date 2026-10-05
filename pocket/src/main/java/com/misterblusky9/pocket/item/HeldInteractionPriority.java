package com.misterblusky9.pocket.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class HeldInteractionPriority {
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!claimsBlock(event.getEntity(), event.getItemStack(), event.getLevel(), event.getPos())) return;
        event.setUseBlock(TriState.FALSE);
    }

    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!claimsEntity(event.getEntity(), event.getItemStack(), event.getTarget())) return;
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }

    public static void onEntityInteractSpecific(final PlayerInteractEvent.EntityInteractSpecific event) {
        if (!claimsEntity(event.getEntity(), event.getItemStack(), event.getTarget())) return;
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }

    public static boolean claimsBlock(
            final Player player,
            final ItemStack used,
            final Level level,
            final BlockPos pos
    ) {
        if (player == null || level == null || pos == null) return false;
        if (held(used)) return claims(player, used, level, pos);
        return claims(player, player.getMainHandItem(), level, pos)
                || claims(player, player.getOffhandItem(), level, pos);
    }

    public static boolean claimsEntity(
            final Player player,
            final ItemStack used,
            final Entity target
    ) {
        if (player == null || target == null) return false;
        if (held(used)) return claims(player, used, target);
        return claims(player, player.getMainHandItem(), target)
                || claims(player, player.getOffhandItem(), target);
    }

    public static boolean claimsInput(final Player player, final ItemStack used) {
        if (player == null) return false;
        if (held(used)) return claims(player, used);
        return claims(player, player.getMainHandItem()) || claims(player, player.getOffhandItem());
    }

    private static boolean claims(
            final Player player,
            final ItemStack stack,
            final Level level,
            final BlockPos pos
    ) {
        if (!held(stack)) return false;
        return stack.getItem() instanceof final PriorityInteractionItem item
                && item.claimsBlock(player, stack, level, pos);
    }

    private static boolean claims(final Player player, final ItemStack stack, final Entity target) {
        return held(stack)
                && stack.getItem() instanceof final PriorityInteractionItem item
                && item.claimsEntity(player, stack, target);
    }

    private static boolean claims(final Player player, final ItemStack stack) {
        return held(stack)
                && stack.getItem() instanceof final PriorityInteractionItem item
                && item.claimsInput(player, stack);
    }

    private static boolean held(final ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private HeldInteractionPriority() {}
}

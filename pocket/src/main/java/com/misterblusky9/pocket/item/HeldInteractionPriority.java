package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class HeldInteractionPriority {
    public static final TagKey<Block> TAKES_HELD_ITEMS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "takes_held_items"));
    public static final TagKey<EntityType<?>> TAKES_HELD_ITEMS_ENTITY = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "takes_held_items"));

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!claimsBlock(event.getEntity(), event.getItemStack(), event.getLevel(), event.getPos())) return;
        event.setUseBlock(TriState.FALSE);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!claimsEntity(event.getEntity(), event.getItemStack(), event.getTarget())) return;
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!claimsEntity(event.getEntity(), event.getItemStack(), event.getTarget())) return;
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }

    public static boolean claimsBlock(
            Player player,
            ItemStack used,
            Level level,
            BlockPos pos
    ) {
        if (player == null || level == null || pos == null) return false;
        if (level.getBlockState(pos).is(TAKES_HELD_ITEMS)) return false;
        if (held(used)) return claims(player, used, level, pos);
        return claims(player, player.getMainHandItem(), level, pos)
                || claims(player, player.getOffhandItem(), level, pos);
    }

    public static boolean claimsEntity(
            Player player,
            ItemStack used,
            Entity target
    ) {
        if (player == null || target == null) return false;
        if (target.getType().is(TAKES_HELD_ITEMS_ENTITY)) return false;
        if (held(used)) return claims(player, used, target);
        return claims(player, player.getMainHandItem(), target)
                || claims(player, player.getOffhandItem(), target);
    }

    public static boolean claimsInput(Player player, ItemStack used) {
        if (player == null) return false;
        if (held(used)) return claims(player, used);
        return claims(player, player.getMainHandItem()) || claims(player, player.getOffhandItem());
    }

    private static boolean claims(
            Player player,
            ItemStack stack,
            Level level,
            BlockPos pos
    ) {
        if (!held(stack)) return false;
        return stack.getItem() instanceof PriorityInteractionItem item
                && item.claimsBlock(player, stack, level, pos);
    }

    private static boolean claims(Player player, ItemStack stack, Entity target) {
        return held(stack)
                && stack.getItem() instanceof PriorityInteractionItem item
                && item.claimsEntity(player, stack, target);
    }

    private static boolean claims(Player player, ItemStack stack) {
        return held(stack)
                && stack.getItem() instanceof PriorityInteractionItem item
                && item.claimsInput(player, stack);
    }

    private static boolean held(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private HeldInteractionPriority() {}
}

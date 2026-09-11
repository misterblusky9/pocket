package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.simulated.SimulatedRopeScaleBoundary;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.simulated_team.simulated.content.items.rope.RopeItem.ClientRopeItemHandler", remap = false)
public abstract class ClientRopeItemScaleBoundaryMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/items/rope/RopeItem/RopeItem;isValidRopeAttachment(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = false
    )
    private static boolean pocket$validateScaleBoundary(
            final Level level,
            final BlockPos hitBlock,
            final Operation<Boolean> original
    ) {
        if (!original.call(level, hitBlock)) return false;

        final Player player = Minecraft.getInstance().player;
        if (player == null) return true;

        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack held = player.getItemInHand(hand);
            if (!SimItems.ROPE_COUPLING.isIn(held)) continue;
            if (!held.has(SimDataComponents.ROPE_FIRST_CONNECTION)) continue;

            final BlockPos firstBlock = held.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            if (firstBlock == null) return true;

            final RopeStrandHolderBehavior first = RopeItem.getRopeHolder(level, firstBlock);
            final RopeStrandHolderBehavior target = RopeItem.getRopeHolder(level, hitBlock);
            if (first == null || target == null) return true;

            return SimulatedRopeScaleBoundary.canConnect(level, first, target);
        }

        return true;
    }
}

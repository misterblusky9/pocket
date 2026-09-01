package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.item.CoasterCartItem", remap = false)
public abstract class CoasterCartItemPlacementScaleMixin {
    @Inject(
            method = "tryPlaceOnTrack(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            require = 1
    )
    private void pocket$beginTrackPlacement(
            final UseOnContext context,
            final Level level,
            final Player player,
            final ItemStack stack,
            final BlockPos clicked,
            final CallbackInfoReturnable<InteractionResult> cir
    ) {
        SimulatedCoastersPlacementScaleContext.remember(1.0D);
    }

    @Inject(
            method = "tryPlaceOnTrack(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"),
            require = 1
    )
    private void pocket$endTrackPlacement(
            final UseOnContext context,
            final Level level,
            final Player player,
            final ItemStack stack,
            final BlockPos clicked,
            final CallbackInfoReturnable<InteractionResult> cir
    ) {
        SimulatedCoastersPlacementScaleContext.remember(1.0D);
    }
}

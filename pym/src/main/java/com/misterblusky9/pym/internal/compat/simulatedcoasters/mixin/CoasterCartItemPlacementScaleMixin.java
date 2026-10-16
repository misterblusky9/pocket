package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
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
    private void pym$beginTrackPlacement(
            UseOnContext context,
            Level level,
            Player player,
            ItemStack stack,
            BlockPos clicked,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        CoasterPlacementScaleContext.reset();
    }

    @Inject(
            method = "tryPlaceOnTrack(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"),
            require = 1
    )
    private void pym$endTrackPlacement(
            UseOnContext context,
            Level level,
            Player player,
            ItemStack stack,
            BlockPos clicked,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        CoasterPlacementScaleContext.reset();
    }
}

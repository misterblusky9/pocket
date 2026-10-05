package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.simibubi.create.content.equipment.zapper.ZapperInteractionHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ZapperInteractionHandler.class, remap = false)
public abstract class ZapperSelectShrinkRayMixin {
    @Inject(method = "trySelect", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pocket$noBlockSelect(
            final ItemStack stack,
            final Player player,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (stack.getItem() instanceof CreativeShrinkRayItem) cir.setReturnValue(false);
    }
}

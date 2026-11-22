package com.misterblusky9.pocket.compat.accessories.mixin;

import com.misterblusky9.pocket.compat.WornHats;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AccessoriesEventHandler.class, remap = false)
public abstract class AccessoriesUseEquipMixin {
    @Inject(method = "attemptEquipFromUse", at = @At("HEAD"), cancellable = true)
    private static void pocket$customHatsUseArmorSlot(Player player, InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (WornHats.isCustomHat(stack)) cir.setReturnValue(InteractionResultHolder.pass(stack));
    }
}

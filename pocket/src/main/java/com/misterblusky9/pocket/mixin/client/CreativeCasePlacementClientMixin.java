package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class CreativeCasePlacementClientMixin {
    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult pocket$noteFilledCase(
            final ItemStack stack,
            final UseOnContext context,
            final Operation<InteractionResult> original,
            @Share("filledCase") final LocalBooleanRef filledCase
    ) {
        filledCase.set(PocketCaseItem.isUnique(stack));
        return original.call(stack, context);
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;setCount(I)V"))
    private void pocket$keepFilledCaseSpent(
            final ItemStack stack,
            final int count,
            final Operation<Void> original,
            @Share("filledCase") final LocalBooleanRef filledCase
    ) {
        if (!filledCase.get()) original.call(stack, count);
    }
}

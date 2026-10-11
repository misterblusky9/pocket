package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFrame.class)
public abstract class CreativeCaseItemFrameMixin {
    @WrapOperation(method = "interact", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void pocket$spendFilledCase(
            final ItemStack stack,
            final int amount,
            final LivingEntity entity,
            final Operation<Void> original
    ) {
        if (PocketCaseItem.isUnique(stack)) {
            stack.shrink(amount);
        } else {
            original.call(stack, amount, entity);
        }
    }
}

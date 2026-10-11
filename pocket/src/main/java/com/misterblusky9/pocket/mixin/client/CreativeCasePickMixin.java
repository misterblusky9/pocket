package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class CreativeCasePickMixin {
    @WrapOperation(method = "pickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getPickedResult(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pocket$pickEmptyCase(
            final Entity entity,
            final HitResult hit,
            final Operation<ItemStack> original
    ) {
        final ItemStack picked = original.call(entity, hit);
        if (picked == null || !PocketCaseItem.isUnique(picked)) return picked;
        return new ItemStack(PocketCaseItem.containerItem(picked));
    }
}

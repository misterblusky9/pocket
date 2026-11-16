package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.client.CaseCloning;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class CreativeCasePickMixin {
    @Shadow
    public net.minecraft.client.player.LocalPlayer player;

    @WrapOperation(method = "pickBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getPickedResult(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pocket$pickEmptyCase(
            Entity entity,
            HitResult hit,
            Operation<ItemStack> original
    ) {
        ItemStack picked = original.call(entity, hit);
        if (picked == null) return null;
        boolean hat = BuildHostBlockEntity.token(picked) != null;
        if (!hat && !PocketCaseItem.isUnique(picked)) return picked;
        if (player != null && player.hasInfiniteMaterials()) {
            CaseCloning.pick(picked);
            return null;
        }
        return hat ? BuildHostBlockEntity.plain(picked) : new ItemStack(PocketCaseItem.containerItem(picked));
    }
}

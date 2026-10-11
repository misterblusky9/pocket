package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmorStandItem.class)
public abstract class ArmorStandPlacementScaleMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityDimensions;makeBoundingBox(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB pocket$scaledSpace(
            final EntityDimensions dimensions,
            final double x,
            final double y,
            final double z,
            final Operation<AABB> original,
            @Local(argsOnly = true) final UseOnContext context
    ) {
        return PlayerSpawnScale.placementBox(context, original.call(dimensions, x, y, z));
    }
}

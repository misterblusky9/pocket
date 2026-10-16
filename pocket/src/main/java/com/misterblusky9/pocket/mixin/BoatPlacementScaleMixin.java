package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BoatItem.class)
public abstract class BoatPlacementScaleMixin {
    @WrapOperation(
            method = "use",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean pocket$scaledSpace(
            Level level,
            Entity boat,
            AABB box,
            Operation<Boolean> original,
            @Local(argsOnly = true) Player player,
            @Local HitResult hit
    ) {
        AABB scaled = hit instanceof BlockHitResult block
                ? PlayerSpawnScale.scaledBox(player, level, block.getBlockPos(), box)
                : box;
        return original.call(level, boat, scaled);
    }
}

package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerScaleMixin {
    @WrapOperation(
            method = "spawnMob",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean pocket$spawnAtSpawnerScale(
            ServerLevel level,
            Entity entity,
            Operation<Boolean> original,
            ServerLevel serverLevel,
            BlockPos pos
    ) {
        return PlayerSpawnScale.spawningOn(Sable.HELPER.getContaining(level, pos), () -> original.call(level, entity));
    }
}

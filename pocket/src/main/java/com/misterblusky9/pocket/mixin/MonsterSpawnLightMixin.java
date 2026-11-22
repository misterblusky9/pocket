package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.scale.SpawnLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Monster.class)
public abstract class MonsterSpawnLightMixin {
    @WrapOperation(
            method = "isDarkEnoughToSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private static int pocket$sublevelBrightness(
            ServerLevelAccessor level, LightLayer layer, BlockPos pos, Operation<Integer> original) {
        return SpawnLight.brightness(level, layer, pos, original.call(level, layer, pos));
    }

    @WrapOperation(
            method = "isDarkEnoughToSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"))
    private static int pocket$sublevelRaw(ServerLevelAccessor level, BlockPos pos, Operation<Integer> original) {
        return SpawnLight.raw(level, pos, original.call(level, pos), world -> level.getLevel().getMaxLocalRawBrightness(world));
    }

    @WrapOperation(
            method = "isDarkEnoughToSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I"))
    private static int pocket$sublevelRawThunder(
            ServerLevelAccessor level, BlockPos pos, int darken, Operation<Integer> original) {
        return SpawnLight.raw(level, pos, original.call(level, pos, darken),
                world -> level.getLevel().getMaxLocalRawBrightness(world, darken));
    }
}

package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleEmitterMixin {
    @WrapMethod(method = "tickNonPassenger")
    private void pym$particleSourceForEntityTick(Entity entity, Operation<Void> original) {
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromEntity(entity)) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void pym$particleSourceForPassengerTick(
            Entity vehicle, Entity passenger, Operation<Void> original
    ) {
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromEntity(passenger)) {
            original.call(vehicle, passenger);
        }
    }

    @WrapOperation(
            method = "doAnimateTick",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/block/Block;animateTick("
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/util/RandomSource;)V"),
            require = 0
    )
    private void pym$particleSourceForBlockAnimation(
            Block block, BlockState state, Level level,
            BlockPos pos, RandomSource random, Operation<Void> original
    ) {
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromBlock(level, pos)) {
            original.call(block, state, level, pos, random);
        }
    }

    @WrapOperation(
            method = "doAnimateTick",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/material/FluidState;animateTick("
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/util/RandomSource;)V"),
            require = 0
    )
    private void pym$particleSourceForFluidAnimation(
            FluidState state, Level level, BlockPos pos,
            RandomSource random, Operation<Void> original
    ) {
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromBlock(level, pos)) {
            original.call(state, level, pos, random);
        }
    }
}

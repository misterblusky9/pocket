package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleEngine.class)
public abstract class PlayerBlockHitParticleEmitterMixin {
    @WrapMethod(method = "crack")
    private void pym$particleSourceForBlockHit(
            BlockPos pos, Direction direction, Operation<Void> original
    ) {
        if (Double.isFinite(ParticleEmitterSource.currentScale())) {
            original.call(pos, direction);
            return;
        }
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromBlock(
                Minecraft.getInstance().level, pos)) {
            original.call(pos, direction);
        }
    }

    @WrapMethod(method = "destroy")
    private void pym$particleSourceForBlockDestruction(
            BlockPos pos, BlockState state, Operation<Void> original
    ) {
        if (Double.isFinite(ParticleEmitterSource.currentScale())) {
            original.call(pos, state);
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromBlock(level, pos)) {
            original.call(pos, state);
        }
    }
}

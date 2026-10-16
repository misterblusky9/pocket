package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityParticleEmitterMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @WrapMethod(method = "tick")
    private void pym$particleSourceForBlockEntity(Operation<Void> original) {
        if (!(this.blockEntity.getLevel() instanceof ClientLevel)) {
            original.call();
            return;
        }
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.fromBlock(
                this.blockEntity.getLevel(), this.blockEntity.getBlockPos())) {
            original.call();
        }
    }
}

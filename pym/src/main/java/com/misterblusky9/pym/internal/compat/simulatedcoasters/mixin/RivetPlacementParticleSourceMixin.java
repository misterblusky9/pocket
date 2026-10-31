package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Map;
import java.util.UUID;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.rivet.RivetPlacementAnimationClient", remap = false)
public abstract class RivetPlacementParticleSourceMixin {
    @WrapOperation(
            method = "clientTick",
            at = @At(value = "INVOKE", target =
                    "Ldev/silvergold/simulatedcoasters/client/rivet/RivetPlacementParticlesClient;"
                            + "onAnimationComplete(Lnet/minecraft/client/multiplayer/ClientLevel;"
                            + "Ldev/silvergold/simulatedcoasters/client/rivet/"
                            + "RivetPlacementAnimationClient$SeatFxSnapshot;)V"),
            require = 1
    )
    private static void pym$particleSource(
            ClientLevel level, @Coerce Object snapshot, Operation<Void> original,
            @Local Map.Entry<UUID, ?> entry
    ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        double scale = container != null
                && container.getSubLevel(entry.getKey()) instanceof ClientSubLevel rivet
                && !rivet.isRemoved() ? ParticleEmitterSource.scaleOf(rivet) : 1.0D;
        try (ParticleEmitterSource.Scope ignored = ParticleEmitterSource.push(scale)) {
            original.call(level, snapshot);
        }
    }
}

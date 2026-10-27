package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.MagnetLaw;
import com.misterblusky9.pym.internal.compat.simulated.ScaledMagnetForces;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MagnetPair.class, remap = false)
public abstract class MagnetPairScaleMixin {
    @Shadow @Final protected Level level;

    @Inject(method = "applyForces", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$scaleField(double timeStep, BlockEntity first, BlockEntity second, CallbackInfo ci) {
        if (!(first instanceof SimMagnet magnet1) || !(second instanceof SimMagnet magnet2)) return;
        if (!ScaledMagnetForces.scaled(magnet1, magnet2)) return;

        MagnetPair<?> self = (MagnetPair<?>) (Object) this;
        MagnetLaw law;
        if (self.getClass() == MagnetPair.class) {
            law = MagnetLaw.REDSTONE;
        } else if (self instanceof DockingConnectorPair docking && self.getClass() == DockingConnectorPair.class) {
            law = MagnetLaw.docking(docking, magnet1, magnet2, ScaledMagnetForces.unit(magnet1, magnet2));
        } else {
            return;
        }

        ScaledMagnetForces.apply(
                this.level, law, magnet1, magnet2, timeStep,
                self.getAccelerationLimit(), self.getAngularAccelerationLimit());
        ci.cancel();
    }
}

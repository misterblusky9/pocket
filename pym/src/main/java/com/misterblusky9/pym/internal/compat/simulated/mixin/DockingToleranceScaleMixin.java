package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulated.ScaledMagnetField;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = DockingConnectorPair.class, remap = false)
public abstract class DockingToleranceScaleMixin {
    @ModifyVariable(method = "dock", at = @At("STORE"), name = "distanceTolerance", remap = false)
    private double pym$scaleDistanceTolerance(
            double tolerance,
            @Local(name = "dock1") DockingConnectorBlockEntity dock1,
            @Local(name = "dock2") DockingConnectorBlockEntity dock2
    ) {
        return tolerance * Math.sqrt(
                ScaledMagnetField.scaleOf(dock1.getLatestSubLevel())
                        * ScaledMagnetField.scaleOf(dock2.getLatestSubLevel()));
    }
}

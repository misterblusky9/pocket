package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulated.ScaledMagnetField;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public abstract class DockingLockOffsetScaleMixin {
    @ModifyVariable(method = "setDock", at = @At("HEAD"), argsOnly = true, index = 4, remap = false)
    private Vector3dc pym$offsetInPartnerPlot(
            Vector3dc relativePos,
            @Local(argsOnly = true, index = 1) DockingConnectorBlockEntity other
    ) {
        if (relativePos == null || other == null) return relativePos;
        double scale = ScaledMagnetField.scaleOf(other.getLatestSubLevel());
        return scale == 1.0D ? relativePos : new Vector3d(relativePos).div(scale);
    }
}

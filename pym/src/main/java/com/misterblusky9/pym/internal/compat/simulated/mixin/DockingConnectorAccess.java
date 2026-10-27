package com.misterblusky9.pym.internal.compat.simulated.mixin;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public interface DockingConnectorAccess {
    @Accessor(value = "closestPairDistance", remap = false)
    double pym$closestPairDistance();
}

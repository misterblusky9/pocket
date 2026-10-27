package com.misterblusky9.pym.internal.compat.simulated.mixin;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DockingConnectorPair.class, remap = false)
public interface DockingConnectorPairAccess {
    @Invoker(value = "getRelativeOrientationDifference", remap = false)
    Vector3d pym$relativeOrientationDifference(SimMagnet magnet1, SimMagnet magnet2, Vector3d dest);
}

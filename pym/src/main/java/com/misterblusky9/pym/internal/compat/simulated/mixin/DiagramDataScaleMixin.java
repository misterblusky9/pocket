package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pym.internal.physics.InternalForceScaleContext;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup.PointForce;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

@Mixin(value = DiagramEntity.class, remap = false)
public abstract class DiagramDataScaleMixin {
    @ModifyReturnValue(method = "makeDiagramDataPacket", at = @At("RETURN"), remap = false)
    private static DiagramDataPacket pym$reportSolverValues(DiagramDataPacket packet, ServerSubLevel subLevel) {
        double factor = InternalForceScaleContext.queuedFactors(subLevel)[0];
        if (factor == 1.0D) return packet;

        Map<ForceGroup, List<PointForce>> forces = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<ForceGroup, List<PointForce>> entry : packet.forces().entrySet()) {
            List<PointForce> scaled = new ObjectArrayList<>(entry.getValue().size());
            for (PointForce force : entry.getValue()) {
                scaled.add(new PointForce(force.point(), new Vector3d(force.force()).mul(factor)));
            }
            forces.put(entry.getKey(), scaled);
        }
        return new DiagramDataPacket(forces, packet.mass() * factor);
    }
}

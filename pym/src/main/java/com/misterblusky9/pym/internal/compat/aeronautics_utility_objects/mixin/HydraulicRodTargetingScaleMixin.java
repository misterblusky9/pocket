package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRodCandidate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.client.HydraulicRodTargeting", remap = false)
public abstract class HydraulicRodTargetingScaleMixin {
    @WrapOperation(method = "pick", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting;giantHitDistance("
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting$Candidate;)D"), remap = false)
    private static double pym$scaledGiantHit(
            Vec3 origin, Vec3 direction, @Coerce Object candidate, Operation<Double> original
    ) {
        LinkageRodCandidate rod = (LinkageRodCandidate) candidate;
        double scale = rod.pym$scale();
        if (scale == 1.0D) return original.call(origin, direction, candidate);
        return original.call(pym$virtual(origin, rod.pym$start(), scale), direction, candidate) * scale;
    }

    @WrapOperation(method = "pick", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting;prismHitDistance("
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting$Candidate;DDDID)D"), remap = false)
    private static double pym$scaledPrismHit(
            Vec3 origin, Vec3 direction, @Coerce Object candidate,
            double startY, double endY, double radius, int sides, double phase,
            Operation<Double> original
    ) {
        LinkageRodCandidate rod = (LinkageRodCandidate) candidate;
        double scale = rod.pym$scale();
        if (scale == 1.0D) return original.call(origin, direction, candidate, startY, endY, radius, sides, phase);
        return original.call(pym$virtual(origin, rod.pym$start(), scale), direction, candidate,
                startY, endY, radius, sides, phase) * scale;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting;renderModelEdges("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting$Candidate;)V"), remap = false)
    private static void pym$scaledOutline(
            PoseStack pose, VertexConsumer consumer, @Coerce Object candidate, Operation<Void> original
    ) {
        LinkageRodCandidate rod = (LinkageRodCandidate) candidate;
        double scale = rod.pym$scale();
        if (scale == 1.0D) {
            original.call(pose, consumer, candidate);
            return;
        }
        Vec3 start = rod.pym$start();
        pose.pushPose();
        try {
            pose.translate(start.x, start.y, start.z);
            pose.scale((float) scale, (float) scale, (float) scale);
            pose.translate(-start.x, -start.y, -start.z);
            original.call(pose, consumer, candidate);
        } finally {
            pose.popPose();
        }
    }

    @Unique
    private static Vec3 pym$virtual(Vec3 point, Vec3 start, double scale) {
        return start.add(point.subtract(start).scale(1.0D / scale));
    }
}

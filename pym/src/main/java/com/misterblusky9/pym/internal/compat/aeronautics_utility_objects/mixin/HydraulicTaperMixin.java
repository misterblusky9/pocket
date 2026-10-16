package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.client.TaperedLinkVertices;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRenderScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.theme.Color;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.joml.Vector3f;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadRenderer", remap = false)
public abstract class HydraulicTaperMixin {
    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;renderCore("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3f;F"
                    + "Lnet/createmod/catnip/theme/Color;)V"), remap = false)
    private void pym$taperStandardRod(
            @Coerce Object renderer, @Coerce Object owner,
            PoseStack pose, VertexConsumer consumer, int light,
            Vec3 start, Vector3f direction, float stretch, Color color,
            Operation<Void> original
    ) {
        float farRadius = LinkageRenderScale.partnerRatio((BlockEntity) owner);
        Vec3 end = start.add(direction.x() * stretch * 1.5D,
                direction.y() * stretch * 1.5D, direction.z() * stretch * 1.5D);
        VertexConsumer tapered = TaperedLinkVertices.wrap(consumer, pose, start, end, 1.0D, farRadius);
        original.call(renderer, owner, pose, tapered, light, start, direction, stretch, color);
    }

    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;renderGiantVisual("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D"
                    + "Lnet/createmod/catnip/theme/Color;"
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer$RollReference;)V"), remap = false)
    private void pym$taperGiantRod(
            @Coerce Object owner, PoseStack pose, VertexConsumer consumer, int light,
            Vec3 start, Vec3 end, double length,
            Color color, @Coerce Object roll,
            Operation<Void> original
    ) {
        float farRadius = LinkageRenderScale.partnerRatio((BlockEntity) owner);
        VertexConsumer tapered = TaperedLinkVertices.wrap(consumer, pose, start, end, 1.0D, farRadius);
        original.call(owner, pose, tapered, light, start, end, length, color, roll);
    }
}

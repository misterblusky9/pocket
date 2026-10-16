package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRenderScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.theme.Color;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadRenderer", remap = false)
public abstract class HydraulicSleeveMixin {
    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;renderSleeve("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3f;"
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer$RollReference;"
                    + "Lnet/createmod/catnip/theme/Color;)V"), remap = false)
    private void pym$giantPartnerSleeve(
            @Coerce Object renderer, @Coerce Object owner,
            PoseStack pose, VertexConsumer consumer, int light,
            Vec3 anchor, Vector3f direction, @Coerce Object roll,
            Color color, Operation<Void> original
    ) {
        pym$drawPartner(renderer, owner, pose, consumer, light, anchor, direction, roll, color, original);
    }

    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE", ordinal = 3,
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;renderSleeve("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3f;"
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer$RollReference;"
                    + "Lnet/createmod/catnip/theme/Color;)V"), remap = false)
    private void pym$regularPartnerSleeve(
            @Coerce Object renderer, @Coerce Object owner,
            PoseStack pose, VertexConsumer consumer, int light,
            Vec3 anchor, Vector3f direction, @Coerce Object roll,
            Color color, Operation<Void> original
    ) {
        pym$drawPartner(renderer, owner, pose, consumer, light, anchor, direction, roll, color, original);
    }

    private static void pym$drawPartner(
            Object renderer, Object owner, PoseStack pose,
            VertexConsumer consumer, int light, Vec3 anchor,
            Vector3f direction, Object roll, Color color, Operation<Void> original
    ) {
        float ratio = LinkageRenderScale.partnerRatio((BlockEntity) owner);
        if (!(ratio > 0.0F) || !Float.isFinite(ratio) || ratio == 1.0F) {
            original.call(renderer, owner, pose, consumer, light, anchor, direction, roll, color);
            return;
        }
        pose.pushPose();
        try {
            pose.translate(anchor.x, anchor.y, anchor.z);
            pose.scale(ratio, ratio, ratio);
            pose.translate(-anchor.x, -anchor.y, -anchor.z);
            original.call(renderer, owner, pose, consumer, light, anchor, direction, roll, color);
        } finally {
            pose.popPose();
        }
    }
}

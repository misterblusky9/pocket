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
@Mixin(targets = "com.enxv.aerouniversaljoint.content.UniversalJointRenderer", remap = false)
public abstract class UniversalJointSleeveMixin {
    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/enxv/aerouniversaljoint/content/UniversalJointRenderer;renderSleeve("
                    + "Lcom/enxv/aerouniversaljoint/content/UniversalJointBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3f;F"
                    + "Lcom/enxv/aerouniversaljoint/content/UniversalJointRenderer$RollReference;"
                    + "Lnet/createmod/catnip/theme/Color;)V"), remap = false)
    private void pym$partnerSleeve(
            @Coerce Object renderer, @Coerce Object owner,
            PoseStack pose, VertexConsumer consumer, int light,
            Vec3 anchor, Vector3f direction, float angle,
            @Coerce Object roll, Color color, Operation<Void> original
    ) {
        float ratio = LinkageRenderScale.partnerRatio((BlockEntity) owner);
        if (!(ratio > 0.0F) || !Float.isFinite(ratio) || ratio == 1.0F) {
            original.call(renderer, owner, pose, consumer, light, anchor, direction, angle, roll, color);
            return;
        }
        pose.pushPose();
        try {
            pose.translate(anchor.x, anchor.y, anchor.z);
            pose.scale(ratio, ratio, ratio);
            pose.translate(-anchor.x, -anchor.y, -anchor.z);
            original.call(renderer, owner, pose, consumer, light, anchor, direction, angle, roll, color);
        } finally {
            pose.popPose();
        }
    }
}

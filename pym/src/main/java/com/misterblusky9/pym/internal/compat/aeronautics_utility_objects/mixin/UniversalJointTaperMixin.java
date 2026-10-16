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
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.UniversalJointRenderer", remap = false)
public abstract class UniversalJointTaperMixin {
    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/content/UniversalJointRenderer;renderStretchedCore("
                    + "Lcom/enxv/aerouniversaljoint/content/UniversalJointBlockEntity;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I"
                    + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3f;FF"
                    + "Lcom/enxv/aerouniversaljoint/content/JointVariant;"
                    + "Lnet/createmod/catnip/theme/Color;)V"), remap = false)
    private void pym$taperCore(
            @Coerce Object renderer, @Coerce Object owner,
            PoseStack pose, VertexConsumer consumer, int light,
            Vec3 start, Vector3f direction, float angle,
            float stretch, @Coerce Object variant, Color color,
            Operation<Void> original
    ) {
        float farRadius = LinkageRenderScale.partnerRatio((BlockEntity) owner);
        Vec3 end = start.add(direction.x() * stretch * 1.5D,
                direction.y() * stretch * 1.5D, direction.z() * stretch * 1.5D);
        VertexConsumer tapered = TaperedLinkVertices.wrap(consumer, pose, start, end, 1.0D, farRadius);
        original.call(renderer, owner, pose, tapered, light, start, direction, angle, stretch, variant, color);
    }
}

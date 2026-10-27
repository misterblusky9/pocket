package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRenderScale;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.ScaledLinkVertices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadRenderer", remap = false)
public abstract class HydraulicRodScaleMixin {
    @Unique private static final String PREVIEW = "renderPreview("
            + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ILnet/createmod/catnip/theme/Color;FZZ)V";

    @Unique private Vec3 pym$rodStart;
    @Unique private double pym$rodRatio = 1.0D;

    public boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
        return LinkageRenderScale.shouldRender(blockEntity, cameraPos);
    }

    public AABB getRenderBoundingBox(BlockEntity blockEntity) {
        return LinkageRenderScale.renderBox(blockEntity);
    }

    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;toRenderSpace("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 pym$rodStart(
            @Coerce Object renderer, @Coerce Object owner, Vec3 world, float partialTicks,
            Operation<Vec3> original
    ) {
        Vec3 start = original.call(renderer, owner, world, partialTicks);
        this.pym$rodStart = start;
        this.pym$rodRatio = LinkageRenderScale.rodRatio((BlockEntity) owner);
        return start;
    }

    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadRenderer;toRenderSpace("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 pym$rodEnd(
            @Coerce Object renderer, @Coerce Object owner, Vec3 world, float partialTicks,
            Operation<Vec3> original
    ) {
        Vec3 end = original.call(renderer, owner, world, partialTicks);
        return LinkageRenderScale.virtualEnd(this.pym$rodStart, end, this.pym$rodRatio);
    }

    @WrapOperation(method = "renderConnectingRod", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/HydraulicRodTargeting;register("
                    + "Lcom/enxv/aerouniversaljoint/content/HydraulicConnectionHeadBlockEntity;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;D)V"), remap = false)
    private void pym$scaledHitbox(
            @Coerce Object head, Vec3 start, Vec3 end, Vec3 rollReference, double radius,
            Operation<Void> original
    ) {
        double scale = LinkageRenderScale.rod((BlockEntity) head);
        LinkageRenderScale.pendingCandidate(scale);
        try {
            original.call(head, start, LinkageRenderScale.virtualEnd(start, end, scale), rollReference, radius);
        } finally {
            LinkageRenderScale.takePendingCandidate();
        }
    }

    @ModifyExpressionValue(method = "renderConnectingRod", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer("
                    + "Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            remap = false)
    private VertexConsumer pym$scaledRod(VertexConsumer buffer, @Local(argsOnly = true) PoseStack pose) {
        return ScaledLinkVertices.wrap(buffer, pose, this.pym$rodStart, this.pym$rodRatio);
    }

    @ModifyVariable(method = PREVIEW, at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static Vec3 pym$previewEnd(Vec3 end, @Local(argsOnly = true, ordinal = 0) Vec3 start) {
        return LinkageRenderScale.virtualEnd(start, end, LinkageRenderScale.previewRod());
    }

    @ModifyVariable(method = PREVIEW, at = @At("HEAD"), argsOnly = true, remap = false)
    private static VertexConsumer pym$previewBuffer(
            VertexConsumer buffer,
            @Local(argsOnly = true) PoseStack pose,
            @Local(argsOnly = true, ordinal = 0) Vec3 start
    ) {
        return ScaledLinkVertices.wrap(buffer, pose, start, LinkageRenderScale.previewRod());
    }
}

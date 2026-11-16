package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.create.SchematicGrid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DeployTool.class, remap = false)
public abstract class DeployToolGridMixin extends SchematicToolBase {
    @WrapOperation(
            method = {"updateSelection", "renderTool", "handleRightClick"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getCenter()Lnet/minecraft/world/phys/Vec3;"),
            remap = false,
            require = 3
    )
    private Vec3 pym$centreInOwnGrid(AABB bounds, Operation<Vec3> original) {
        double ratio = SchematicGrid.ratio(this.selectedPos);
        return ratio == 1.0D ? original.call(bounds) : original.call(bounds).scale(ratio);
    }

    @Inject(
            method = "renderTool",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", shift = At.Shift.AFTER),
            remap = false,
            require = 1
    )
    private void pym$previewInOwnGrid(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, CallbackInfo ci) {
        double ratio = SchematicGrid.ratio(this.selectedPos);
        if (ratio == 1.0D) return;
        Vec3 center = this.schematicHandler.getBounds().getCenter();
        Vec3 cells = pym$centreCells(ratio);
        ms.translate(Math.floor(center.x * ratio) - cells.x, 0.0D, Math.floor(center.z * ratio) - cells.z);
        ms.scale((float) ratio, (float) ratio, (float) ratio);
    }

    @WrapOperation(
            method = "handleRightClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/client/SchematicTransformation;moveTo(Lnet/minecraft/core/BlockPos;)V"
            ),
            remap = false,
            require = 1
    )
    private void pym$deployInOwnGrid(SchematicTransformation transformation, BlockPos target, Operation<Void> original) {
        original.call(transformation, target);
        if (this.selectedPos == null) return;
        double ratio = SchematicGrid.ratio(this.selectedPos);
        if (ratio == 1.0D) {
            ((SchematicGrid.Placement) transformation).pym$placeFull();
            return;
        }
        Vec3 center = pym$centreCells(ratio);
        Vec3 point = SchematicGrid.snapped(this.selectedPos);
        ((SchematicGrid.Placement) transformation).pym$placeAt(point.subtract(center.x, 0.0D, center.z), ratio);
    }

    @Unique
    private Vec3 pym$centreCells(double ratio) {
        Vec3 center = this.schematicHandler.getBounds().getCenter();
        return new Vec3(Math.floor(center.x) * ratio, 0.0D, Math.floor(center.z) * ratio);
    }
}

package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create.SchematicToolScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DeployTool.class, remap = false)
public abstract class DeployToolOutlineScaleMixin {
    @Unique
    private static final float PYM$CREATE_LINE_WIDTH = 1.0F / 16.0F;

    @Inject(
            method = "renderTool",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/AABBOutline;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"
            ),
            remap = false,
            require = 1
    )
    private void pym$scalePreviewLine(
            PoseStack ms,
            SuperRenderTypeBuffer buffer,
            Vec3 camera,
            CallbackInfo ci,
            @Local AABBOutline outline
    ) {
        outline.getParams().lineWidth(SchematicToolScale.lineWidth(PYM$CREATE_LINE_WIDTH));
    }
}

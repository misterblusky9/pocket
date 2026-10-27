package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.SchematicToolScale;
import com.simibubi.create.content.schematics.client.tools.FlipTool;
import com.simibubi.create.content.schematics.client.tools.RotateTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = {FlipTool.class, RotateTool.class}, remap = false)
public abstract class SchematicToolOutlineScaleMixin {
    @ModifyArg(
            method = "renderOnSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)"
                            + "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            remap = false,
            require = 1
    )
    private float pym$scaleToolLine(float width) {
        return SchematicToolScale.lineWidth(width);
    }
}

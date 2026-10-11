package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LineOutline.class, remap = false)
public abstract class LineOutlineScaleMixin {
    @Shadow @Final protected Vector3d start;
    @Shadow @Final protected Vector3d end;

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/LineOutline;renderInner(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/Vec3;FFLorg/joml/Vector4f;IZ)V"
            ),
            index = 4,
            remap = false,
            require = 1
    )
    private float pym$widthInPlotUnits(final float width, @Local(argsOnly = true) final float partialTick) {
        final Entity emitter = ((Outline) (Object) this).getParams() instanceof final OutlineEmitter tagged
                ? tagged.pym$emitter() : null;
        if (emitter != null) return (float) (width * pym$emitterScale(emitter, partialTick));
        return (float) (width * Math.min(pym$scaleAt(this.start), pym$scaleAt(this.end)));
    }

    @Unique
    private static double pym$emitterScale(final Entity emitter, final float partialTick) {
        final double scale = Pym.entities().renderScale(emitter, partialTick);
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    @Unique
    private static double pym$scaleAt(final Vector3dc point) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(point);
        if (subLevel == null) return ScaleBounds.FULL;
        final double scale = subLevel.renderPose().scale().x();
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }
}

package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
    private float pym$widthInPlotUnits(float width, @Local(argsOnly = true) float partialTick) {
        if (((Outline) (Object) this).getParams() instanceof OutlineEmitter tagged) {
            ClientSubLevel owner = tagged.pym$subLevel();
            if (tagged.pym$minimumPixels() > 0.0F) {
                double scale = owner != null && !owner.isRemoved()
                        ? owner.renderPose(partialTick).scale().x() : ScaleBounds.FULL;
                float physicalWidth = (float) (width * (ScaleBounds.isValid(scale)
                        ? scale : ScaleBounds.FULL));
                return pym$legibleWeldWidth(physicalWidth, tagged.pym$minimumPixels(), owner, partialTick);
            }
            if (owner != null && !owner.isRemoved()) {
                double scale = owner.renderPose(partialTick).scale().x();
                if (ScaleBounds.isValid(scale)) return (float) (width * scale);
            }
            Entity emitter = tagged.pym$emitter();
            if (emitter != null) return (float) (width * pym$emitterScale(emitter, partialTick));
        }
        return (float) (width * Math.min(pym$scaleAt(this.start), pym$scaleAt(this.end)));
    }

    @Unique
    private float pym$legibleWeldWidth(
            float physicalWidth,
            float minimumPixels,
            ClientSubLevel owner,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return physicalWidth;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        int height = minecraft.getWindow().getHeight();
        if (camera == null || !camera.isInitialized() || height <= 0) return physicalWidth;

        Vector3d a = new Vector3d(this.start);
        Vector3d b = new Vector3d(this.end);
        if (owner != null && !owner.isRemoved()) {
            owner.renderPose(partialTick).transformPosition(a);
            owner.renderPose(partialTick).transformPosition(b);
        }
        Vec3 eye = camera.getPosition();
        Vector3d delta = new Vector3d(b).sub(a);
        double lengthSquared = delta.lengthSquared();
        double along = lengthSquared < 1.0E-12D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, delta.dot(
                        eye.x - a.x, eye.y - a.y, eye.z - a.z) / lengthSquared));
        Vector3d closest = a.fma(along, delta);
        double distance = Math.sqrt(closest.distanceSquared(eye.x, eye.y, eye.z));
        double focal = RenderSystem.getProjectionMatrix().m11();
        if (!Double.isFinite(distance) || !(focal > 0.0D) || !Double.isFinite(focal)) return physicalWidth;

        double worldUnitsPerPixel = 2.0D * Math.max(0.05D, distance) / (focal * height);
        return (float) Math.max(physicalWidth, minimumPixels * worldUnitsPerPixel);
    }

    @Unique
    private static double pym$emitterScale(Entity emitter, float partialTick) {
        double scale = Pym.entities().renderScale(emitter, partialTick);
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    @Unique
    private static double pym$scaleAt(Vector3dc point) {
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(point);
        if (subLevel == null) return ScaleBounds.FULL;
        double scale = subLevel.renderPose().scale().x();
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }
}

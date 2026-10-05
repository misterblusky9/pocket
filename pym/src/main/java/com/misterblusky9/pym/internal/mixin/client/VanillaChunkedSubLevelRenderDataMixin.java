package com.misterblusky9.pym.internal.mixin.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class VanillaChunkedSubLevelRenderDataMixin {
    @Shadow @Final private ClientSubLevel subLevel;

    @Unique private boolean pym$normalMatrixAdjusted;
    @Unique private boolean pym$fogAdjusted;
    @Unique private float pym$baseFogStart;
    @Unique private float pym$baseFogEnd;
    @Unique private final Matrix3f pym$baseNormalMatrix = new Matrix3f();
    @Unique private final Matrix3f pym$scaledNormalMatrix = new Matrix3f();

    @Redirect(
            method = "renderChunkedSubLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniondc;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private Vector3d pym$scaleCenterOfRotationOffset(
            final Quaterniondc rotation,
            final Vector3d vector
    ) {
        final Vector3dc scale = pym$renderScale();
        vector.mul(scale.x(), scale.y(), scale.z());
        return rotation.transform(vector);
    }

    @Redirect(
            method = "renderChunkedSubLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;",
                    ordinal = 0
            ),
            remap = false
    )
    private Matrix4f pym$applyPoseScaleToTerrain(
            final Matrix4f matrix,
            final Quaternionfc rotation
    ) {
        matrix.rotate(rotation);
        final Vector3dc scale = pym$renderScale();
        matrix.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        return matrix;
    }

    @Redirect(
            method = "renderChunkedSubLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniondc;transformInverse(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private Vector3d pym$unscaleCameraCompensation(
            final Quaterniondc rotation,
            final Vector3dc vector,
            final Vector3d dest
    ) {
        final Vector3d transformed = rotation.transformInverse(vector, dest);
        final Vector3dc scale = pym$renderScale();

        transformed.mul(
                1.0D / scale.x(),
                1.0D / scale.y(),
                1.0D / scale.z()
        );
        return transformed;
    }

    @ModifyConstant(
            method = "compileSections",
            constant = @Constant(doubleValue = 768.0D),
            remap = false
    )
    private double pym$neverBlockOnScaledSectionMeshes(final double original) {
        return pym$isActuallyScaled() ? 0.0D : original;
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), remap = false)
    private void pym$prepareScaledShaderState(
            final RenderType layer,
            final ShaderInstance shader,
            final Matrix4f modelView,
            final double camX,
            final double camY,
            final double camZ,
            final CallbackInfo ci
    ) {
        this.pym$normalMatrixAdjusted = false;
        this.pym$fogAdjusted = false;

        final var traceBounds = this.subLevel.getPlot().getBoundingBox();
        PymTrace.render(
                "chunked:" + this.subLevel.getUniqueId() + ":" + layer,
                "chunkedTerrain uuid={} layer={} scale={} plotBounds={} blocksWide={}x{}x{}",
                this.subLevel.getUniqueId(), layer, pym$renderScale(), traceBounds,
                traceBounds.maxX() - traceBounds.minX() + 1,
                traceBounds.maxY() - traceBounds.minY() + 1,
                traceBounds.maxZ() - traceBounds.minZ() + 1);

        if (!pym$isActuallyScaled()) return;

        final double scale = pym$uniformScale();
        if (scale <= 0.0D) return;

        final Uniform normalLighting = shader.getUniform("SableEnableNormalLighting");
        if (normalLighting != null) {
            normalLighting.set(1.0F);
            normalLighting.upload();
        }

        final Uniform normalMatrix = shader.getUniform("NormalMat");
        if (normalMatrix != null) {
            modelView.normal(this.pym$baseNormalMatrix);
            this.pym$scaledNormalMatrix
                    .set(this.pym$baseNormalMatrix)
                    .scale((float) scale);

            normalMatrix.set(this.pym$scaledNormalMatrix);
            normalMatrix.upload();
            this.pym$normalMatrixAdjusted = true;
        }

        final Uniform fogStart = shader.getUniform("FogStart");
        final Uniform fogEnd = shader.getUniform("FogEnd");
        if (fogStart != null && fogEnd != null) {
            this.pym$baseFogStart = RenderSystem.getShaderFogStart();
            this.pym$baseFogEnd = RenderSystem.getShaderFogEnd();

            final float inverse = (float) (1.0D / scale);
            fogStart.set(this.pym$baseFogStart * inverse);
            fogStart.upload();
            fogEnd.set(this.pym$baseFogEnd * inverse);
            fogEnd.upload();
            this.pym$fogAdjusted = true;
        }
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("RETURN"), remap = false)
    private void pym$restoreScaledShaderState(
            final RenderType layer,
            final ShaderInstance shader,
            final Matrix4f modelView,
            final double camX,
            final double camY,
            final double camZ,
            final CallbackInfo ci
    ) {
        if (this.pym$normalMatrixAdjusted) {
            final Uniform normalMatrix = shader.getUniform("NormalMat");
            if (normalMatrix != null) {
                normalMatrix.set(this.pym$baseNormalMatrix);
                normalMatrix.upload();
            }
            this.pym$normalMatrixAdjusted = false;
        }

        if (this.pym$fogAdjusted) {
            final Uniform fogStart = shader.getUniform("FogStart");
            final Uniform fogEnd = shader.getUniform("FogEnd");
            if (fogStart != null && fogEnd != null) {
                fogStart.set(this.pym$baseFogStart);
                fogStart.upload();
                fogEnd.set(this.pym$baseFogEnd);
                fogEnd.upload();
            }
            this.pym$fogAdjusted = false;
        }
    }

    @Unique
    private Vector3dc pym$renderScale() {
        final Pose3dc pose = this.subLevel.renderPose();
        return pose.scale();
    }

    @Unique
    private double pym$uniformScale() {
        final Vector3dc scale = pym$renderScale();

        if (Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON) {
            return 1.0D;
        }
        return scale.x();
    }

    @Unique
    private boolean pym$isActuallyScaled() {
        final Vector3dc scale = pym$renderScale();
        return Math.abs(scale.x() - 1.0D) > ScaleBounds.EPSILON
                || Math.abs(scale.y() - 1.0D) > ScaleBounds.EPSILON
                || Math.abs(scale.z() - 1.0D) > ScaleBounds.EPSILON;
    }
}

package com.misterblusky9.pocket.mixin.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
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

    @Unique private boolean pocket$normalMatrixAdjusted;
    @Unique private boolean pocket$fogAdjusted;
    @Unique private float pocket$baseFogStart;
    @Unique private float pocket$baseFogEnd;
    @Unique private final Matrix3f pocket$baseNormalMatrix = new Matrix3f();
    @Unique private final Matrix3f pocket$scaledNormalMatrix = new Matrix3f();

    @Redirect(
            method = "renderChunkedSubLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniondc;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private Vector3d pocket$scaleCenterOfRotationOffset(
            final Quaterniondc rotation,
            final Vector3d vector
    ) {
        final Vector3dc scale = pocket$renderScale();
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
    private Matrix4f pocket$applyPoseScaleToTerrain(
            final Matrix4f matrix,
            final Quaternionfc rotation
    ) {
        matrix.rotate(rotation);
        final Vector3dc scale = pocket$renderScale();
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
    private Vector3d pocket$unscaleCameraCompensation(
            final Quaterniondc rotation,
            final Vector3dc vector,
            final Vector3d dest
    ) {
        final Vector3d transformed = rotation.transformInverse(vector, dest);
        final Vector3dc scale = pocket$renderScale();

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
    private double pocket$neverBlockOnScaledSectionMeshes(final double original) {
        return pocket$isActuallyScaled() ? 0.0D : original;
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), remap = false)
    private void pocket$prepareScaledShaderState(
            final RenderType layer,
            final ShaderInstance shader,
            final Matrix4f modelView,
            final double camX,
            final double camY,
            final double camZ,
            final CallbackInfo ci
    ) {
        this.pocket$normalMatrixAdjusted = false;
        this.pocket$fogAdjusted = false;

        final var traceBounds = this.subLevel.getPlot().getBoundingBox();
        PocketTrace.render(
                "chunked:" + this.subLevel.getUniqueId() + ":" + layer,
                "chunkedTerrain uuid={} layer={} scale={} plotBounds={} blocksWide={}x{}x{}",
                this.subLevel.getUniqueId(), layer, pocket$renderScale(), traceBounds,
                traceBounds.maxX() - traceBounds.minX() + 1,
                traceBounds.maxY() - traceBounds.minY() + 1,
                traceBounds.maxZ() - traceBounds.minZ() + 1);

        if (!pocket$isActuallyScaled()) return;

        final double scale = pocket$uniformScale();
        if (scale <= 0.0D) return;

        final Uniform normalLighting = shader.getUniform("SableEnableNormalLighting");
        if (normalLighting != null) {
            normalLighting.set(1.0F);
            normalLighting.upload();
        }

        final Uniform normalMatrix = shader.getUniform("NormalMat");
        if (normalMatrix != null) {
            modelView.normal(this.pocket$baseNormalMatrix);
            this.pocket$scaledNormalMatrix
                    .set(this.pocket$baseNormalMatrix)
                    .scale((float) scale);

            normalMatrix.set(this.pocket$scaledNormalMatrix);
            normalMatrix.upload();
            this.pocket$normalMatrixAdjusted = true;
        }

        final Uniform fogStart = shader.getUniform("FogStart");
        final Uniform fogEnd = shader.getUniform("FogEnd");
        if (fogStart != null && fogEnd != null) {
            this.pocket$baseFogStart = RenderSystem.getShaderFogStart();
            this.pocket$baseFogEnd = RenderSystem.getShaderFogEnd();

            final float inverse = (float) (1.0D / scale);
            fogStart.set(this.pocket$baseFogStart * inverse);
            fogStart.upload();
            fogEnd.set(this.pocket$baseFogEnd * inverse);
            fogEnd.upload();
            this.pocket$fogAdjusted = true;
        }
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("RETURN"), remap = false)
    private void pocket$restoreScaledShaderState(
            final RenderType layer,
            final ShaderInstance shader,
            final Matrix4f modelView,
            final double camX,
            final double camY,
            final double camZ,
            final CallbackInfo ci
    ) {
        if (this.pocket$normalMatrixAdjusted) {
            final Uniform normalMatrix = shader.getUniform("NormalMat");
            if (normalMatrix != null) {
                normalMatrix.set(this.pocket$baseNormalMatrix);
                normalMatrix.upload();
            }
            this.pocket$normalMatrixAdjusted = false;
        }

        if (this.pocket$fogAdjusted) {
            final Uniform fogStart = shader.getUniform("FogStart");
            final Uniform fogEnd = shader.getUniform("FogEnd");
            if (fogStart != null && fogEnd != null) {
                fogStart.set(this.pocket$baseFogStart);
                fogStart.upload();
                fogEnd.set(this.pocket$baseFogEnd);
                fogEnd.upload();
            }
            this.pocket$fogAdjusted = false;
        }
    }

    @Unique
    private Vector3dc pocket$renderScale() {
        final Pose3dc pose = this.subLevel.renderPose();
        return pose.scale();
    }

    @Unique
    private double pocket$uniformScale() {
        final Vector3dc scale = pocket$renderScale();

        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            return 1.0D;
        }
        return scale.x();
    }

    @Unique
    private boolean pocket$isActuallyScaled() {
        final Vector3dc scale = pocket$renderScale();
        return Math.abs(scale.x() - 1.0D) > PocketSized.EPSILON
                || Math.abs(scale.y() - 1.0D) > PocketSized.EPSILON
                || Math.abs(scale.z() - 1.0D) > PocketSized.EPSILON;
    }
}

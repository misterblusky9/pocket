package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContraptionVisual.class, remap = false)
public abstract class ContraptionVisualScaleMixin extends AbstractEntityVisual<AbstractContraptionEntity> {
    @Shadow @Final protected VisualEmbedding embedding;
    @Shadow @Final private PoseStack contraptionMatrix;

    private ContraptionVisualScaleMixin(
            final VisualizationContext context,
            final AbstractContraptionEntity entity,
            final float partialTick
    ) {
        super(context, entity, partialTick);
    }

    @Inject(
            method = "beginFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/render/ContraptionVisual;"
                            + "setEmbeddingMatrices(F)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void pocket$scaleContraptionEmbedding(
            final DynamicVisual.Context context,
            final CallbackInfo ci
    ) {
        final AbstractContraptionEntity host = this.entity;
        if (host == null) return;

        final SubLevel raw = Sable.HELPER.getContaining(host);
        if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return;

        final float partialTick = context.partialTick();
        final Pose3dc renderPose = subLevel.renderPose(partialTick);
        final Vector3dc poseScale = renderPose.scale();
        final double scale = poseScale.x();

        if (!PocketSized.isValidScale(scale)
                || Math.abs(poseScale.x() - poseScale.y()) > PocketSized.EPSILON
                || Math.abs(poseScale.x() - poseScale.z()) > PocketSized.EPSILON) {
            return;
        }
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final Vector3d anchor = renderPose.transformPosition(pocket$anchorPlotPosition(host, partialTick));
        final Vec3i origin = this.renderOrigin();
        anchor.sub(origin.getX(), origin.getY(), origin.getZ());

        final float s = (float) scale;
        this.contraptionMatrix.setIdentity();
        this.contraptionMatrix.translate(anchor.x, anchor.y, anchor.z);
        this.contraptionMatrix.mulPose(new Quaternionf(renderPose.orientation()));
        this.contraptionMatrix.scale(s, s, s);
        host.applyLocalTransforms(this.contraptionMatrix, partialTick);

        final PoseStack.Pose pose = this.contraptionMatrix.last();
        this.embedding.transforms(pose.pose(), pose.normal());

        PocketTrace.render(
                "contraption:" + host.getId(),
                "contraptionEmbedding entity={} id={} scale={} anchor={}",
                host.getClass().getSimpleName(), host.getId(), scale, anchor);
    }

    @Unique
    private static Vector3d pocket$anchorPlotPosition(
            final AbstractContraptionEntity host,
            final float partialTick
    ) {
        if (host.isPrevPosInvalid()) {
            return new Vector3d(host.getX(), host.getY(), host.getZ());
        }
        return new Vector3d(
                Mth.lerp(partialTick, host.xo, host.getX()),
                Mth.lerp(partialTick, host.yo, host.getY()),
                Mth.lerp(partialTick, host.zo, host.getZ()));
    }
}

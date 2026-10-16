package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
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
            VisualizationContext context,
            AbstractContraptionEntity entity,
            float partialTick
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
    private void pym$scaleContraptionEmbedding(
            DynamicVisual.Context context,
            CallbackInfo ci
    ) {
        AbstractContraptionEntity host = this.entity;
        if (host == null) return;

        ClientSubLevel subLevel = pym$getRenderSubLevel(host);
        if (subLevel == null) return;

        float partialTick = context.partialTick();
        Pose3dc renderPose = subLevel.renderPose(partialTick);
        Vector3dc poseScale = renderPose.scale();
        double scale = poseScale.x();

        if (!ScaleBounds.isValid(scale)
                || Math.abs(poseScale.x() - poseScale.y()) > ScaleBounds.EPSILON
                || Math.abs(poseScale.x() - poseScale.z()) > ScaleBounds.EPSILON) {
            return;
        }
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return;

        Vector3d anchor = renderPose.transformPosition(pym$anchorPlotPosition(host, partialTick));
        Vec3i origin = this.renderOrigin();
        anchor.sub(origin.getX(), origin.getY(), origin.getZ());

        float s = (float) scale;
        this.contraptionMatrix.setIdentity();
        this.contraptionMatrix.translate(anchor.x, anchor.y, anchor.z);
        this.contraptionMatrix.mulPose(new Quaternionf(renderPose.orientation()));
        this.contraptionMatrix.scale(s, s, s);
        host.applyLocalTransforms(this.contraptionMatrix, partialTick);

        PoseStack.Pose pose = this.contraptionMatrix.last();
        this.embedding.transforms(pose.pose(), pose.normal());

        PymTrace.render(
                "contraption:" + host.getId(),
                "contraptionEmbedding entity={} id={} scale={} anchor={}",
                host.getClass().getSimpleName(), host.getId(), scale, anchor);
    }

    @Unique
    private static ClientSubLevel pym$getRenderSubLevel(AbstractContraptionEntity host) {
        SubLevel containing = Sable.HELPER.getContaining(host);
        if (containing instanceof ClientSubLevel subLevel && !subLevel.isRemoved()) {
            return subLevel;
        }

        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(host);
        if (tracking instanceof ClientSubLevel subLevel && !subLevel.isRemoved()) {
            return subLevel;
        }

        return null;
    }

    @Unique
    private static Vector3d pym$anchorPlotPosition(
            AbstractContraptionEntity host,
            float partialTick
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

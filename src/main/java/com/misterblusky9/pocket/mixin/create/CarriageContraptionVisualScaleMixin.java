package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

@Mixin(value = CarriageContraptionVisual.class, remap = false)
public abstract class CarriageContraptionVisualScaleMixin extends ContraptionVisual<CarriageContraptionEntity> {
    @Shadow @Final private PoseStack poseStack;
    @Shadow @Final private BogeyVisual[] visuals;

    private CarriageContraptionVisualScaleMixin(
            final VisualizationContext context,
            final CarriageContraptionEntity entity,
            final float partialTick
    ) {
        super(context, entity, partialTick);
    }

    @Inject(
            method = "animate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntityRenderer;"
                            + "translateBogey(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lcom/simibubi/create/content/trains/entity/CarriageBogey;IFFF)V"
            ),
            require = 1
    )
    private void pocket$scaleBogeyPose(final float partialTick, final CallbackInfo ci) {
        final double scale = pocket$scaledSubLevelScale(partialTick);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return;

        final float s = (float) scale;
        this.poseStack.scale(s, s, s);
    }

    @Inject(method = "beginFrame", at = @At("TAIL"), require = 1)
    private void pocket$relightScaledBogeys(
            final DynamicVisual.Context context,
            final CallbackInfo ci
    ) {
        if (Math.abs(pocket$scaledSubLevelScale(context.partialTick()) - 1.0D)
                <= PocketSized.EPSILON) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        final int packedLight = minecraft.getEntityRenderDispatcher()
                .getRenderer(this.entity)
                .getPackedLightCoords(this.entity, context.partialTick());

        for (final BogeyVisual visual : this.visuals) {
            if (visual != null) visual.updateLight(packedLight);
        }
    }

    @Unique
    private double pocket$scaledSubLevelScale(final float partialTick) {
        final SubLevel raw = Sable.HELPER.getContaining(this.entity);
        if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            return 1.0D;
        }

        final Vector3dc scale = subLevel.renderPose(partialTick).scale();
        if (!PocketSized.isValidScale(scale.x())
                || Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            return 1.0D;
        }

        return scale.x();
    }
}

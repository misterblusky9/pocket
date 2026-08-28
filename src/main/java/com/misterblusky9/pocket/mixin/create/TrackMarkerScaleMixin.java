package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.TrackTargetingClient;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackTargetingClient.class, remap = false)
public abstract class TrackMarkerScaleMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/track/TrackTargetingBehaviour;"
                            + "render(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/core/Direction$AxisDirection;"
                            + "Lcom/simibubi/create/content/trains/track/BezierTrackPointLocation;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;II"
                            + "Lcom/simibubi/create/content/trains/track/TrackTargetingBehaviour$RenderedTrackOverlayType;F)V"
            ),
            remap = false,
            require = 1
    )
    private static void pocket$scaleTrackMarker(
            final PoseStack ms,
            final SuperRenderTypeBuffer buffer,
            final Vec3 camera,
            final CallbackInfo ci,
            @Local(ordinal = 0) final BlockPos pos
    ) {
        if (pos == null) return;

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        if (!(Sable.HELPER.getContaining(level, pos) instanceof final ClientSubLevel subLevel)) return;
        if (subLevel.isRemoved()) return;

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON) {
            return;
        }

        ms.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}

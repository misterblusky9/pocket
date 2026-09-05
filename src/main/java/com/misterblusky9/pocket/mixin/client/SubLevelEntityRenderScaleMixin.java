package com.misterblusky9.pocket.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.config.PocketServerConfig;
import com.misterblusky9.pocket.entity.EntityScaleTracker;
import com.misterblusky9.pocket.entity.PrimedTntScaleAccess;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(value = EntityRenderDispatcher.class, priority = 900)
public abstract class SubLevelEntityRenderScaleMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> pocket$SCALE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void pocket$pushInheritedScale(
            final Entity entity,
            final double x,
            final double y,
            final double z,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$SCALE_STACK.get();
        final double scale = entity instanceof final PrimedTntScaleAccess tnt
                ? tnt.pocket$renderScale(partialTick)
                : EntityScaleTracker.renderScale(entity, partialTick);
        final Vec3 seatOffset = pocket$seatModelOffset(entity, partialTick);

        final boolean applyScale = Double.isFinite(scale)
                && Math.abs(scale - 1.0D) > PocketSized.EPSILON;
        final boolean applyOffset = seatOffset.lengthSqr() > PocketSized.EPSILON * PocketSized.EPSILON;

        if (!applyScale && !applyOffset) {
            stack.push(Boolean.FALSE);
            return;
        }

        poseStack.pushPose();

        if (applyOffset) {
            poseStack.translate(seatOffset.x, seatOffset.y, seatOffset.z);
        }

        if (applyScale) {
            final double pivotX = x;
            final double pivotY = entity.isPassenger() ? y + entity.getEyeHeight() : y;
            final double pivotZ = z;

            poseStack.translate(pivotX, pivotY, pivotZ);
            poseStack.scale((float) scale, (float) scale, (float) scale);
            poseStack.translate(-pivotX, -pivotY, -pivotZ);
        }

        stack.push(Boolean.TRUE);
    }

    @Unique
    private static Vec3 pocket$seatModelOffset(final Entity entity, final float partialTick) {
        if (!(entity instanceof final Player player)) return Vec3.ZERO;
        if (PocketServerConfig.scalePlayerInShrunkenSeat()) return Vec3.ZERO;
        if (!(player.getVehicle() instanceof final SeatEntity seat)) return Vec3.ZERO;

        SubLevel subLevel = Sable.HELPER.getContaining(seat);
        if (subLevel == null || subLevel.isRemoved()) {
            subLevel = Sable.HELPER.getTrackingSubLevel(seat);
        }
        if (subLevel == null || subLevel.isRemoved()) return Vec3.ZERO;

        final Pose3dc pose = subLevel instanceof final ClientSubLevel client
                ? client.renderPose(partialTick)
                : subLevel.logicalPose();
        final Vector3dc poseScale = pose.scale();

        final double sx = Math.abs(poseScale.x());
        final double sy = Math.abs(poseScale.y());
        final double sz = Math.abs(poseScale.z());

        if (!PocketSized.isValidScale(sx)
                || !PocketSized.isValidScale(sy)
                || !PocketSized.isValidScale(sz)) {
            return Vec3.ZERO;
        }

        if (Math.abs(sx - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sy - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sz - 1.0D) <= PocketSized.EPSILON) {
            return Vec3.ZERO;
        }

        final Vec3 passengerPoint = seat.getPassengerRidingPosition(player);
        final Vec3 riderAttachment = player.getVehicleAttachmentPoint(seat);

        final Vector3d localOffset = new Vector3d(
                passengerPoint.x - seat.getX() - riderAttachment.x,
                passengerPoint.y - seat.getY() - riderAttachment.y
                        + 1.0D / 16.0D
                        + SeatEntity.getCustomEntitySeatOffset(player),
                passengerPoint.z - seat.getZ() - riderAttachment.z
        );

        final Vector3d delta = new Vector3d(localOffset)
                .mul(poseScale)
                .sub(localOffset);

        pose.orientation().transform(delta);

        if (!Double.isFinite(delta.x)
                || !Double.isFinite(delta.y)
                || !Double.isFinite(delta.z)) {
            return Vec3.ZERO;
        }

        return new Vec3(delta.x, delta.y, delta.z);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void pocket$popInheritedScale(
            final Entity entity,
            final double x,
            final double y,
            final double z,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$SCALE_STACK.get();
        if (!stack.isEmpty() && stack.pop()) poseStack.popPose();
        if (stack.isEmpty()) pocket$SCALE_STACK.remove();
    }
}

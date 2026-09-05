package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.config.PocketServerConfig;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SeatEntity.class, remap = false)
public abstract class ScaledSubLevelSeatPositionMixin {
    @Inject(
            method = "positionRider",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pocket$positionScaledSubLevelPlayer(
            final Entity passenger,
            final Entity.MoveFunction callback,
            final CallbackInfo ci
    ) {
        if (!PocketServerConfig.scalePlayerInShrunkenSeat()) return;
        if (!(passenger instanceof Player)) return;

        final SeatEntity seat = (SeatEntity) (Object) this;
        if (!seat.hasPassenger(passenger)) return;

        SubLevel subLevel = Sable.HELPER.getContaining(seat);
        if (subLevel == null || subLevel.isRemoved()) {
            subLevel = Sable.HELPER.getTrackingSubLevel(seat);
        }
        if (subLevel == null || subLevel.isRemoved()) return;

        final Vector3dc poseScale = subLevel.logicalPose().scale();
        final double sx = Math.abs(poseScale.x());
        final double sy = Math.abs(poseScale.y());
        final double sz = Math.abs(poseScale.z());

        if (!PocketSized.isValidScale(sx)
                || !PocketSized.isValidScale(sy)
                || !PocketSized.isValidScale(sz)) {
            return;
        }

        if (Math.abs(sx - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sy - 1.0D) <= PocketSized.EPSILON
                && Math.abs(sz - 1.0D) <= PocketSized.EPSILON) {
            return;
        }

        final Vec3 passengerPoint = seat.getPassengerRidingPosition(passenger);
        final Vec3 riderAttachment = passenger.getVehicleAttachmentPoint(seat);

        final Vector3d localOffset = new Vector3d(
                passengerPoint.x - seat.getX() - riderAttachment.x,
                passengerPoint.y - seat.getY() - riderAttachment.y
                        + 1.0D / 16.0D
                        + SeatEntity.getCustomEntitySeatOffset(passenger),
                passengerPoint.z - seat.getZ() - riderAttachment.z
        );

        localOffset.mul(poseScale);

        subLevel.logicalPose().orientation().transform(localOffset);

        callback.accept(
                passenger,
                seat.getX() + localOffset.x,
                seat.getY() + localOffset.y,
                seat.getZ() + localOffset.z
        );
        ci.cancel();
    }
}

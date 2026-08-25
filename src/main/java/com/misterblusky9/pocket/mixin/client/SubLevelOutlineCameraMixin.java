package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelCamera.class, remap = false)
public abstract class SubLevelOutlineCameraMixin {
    @Shadow private Vec3 pos;

    @Shadow @org.spongepowered.asm.mixin.Final private BlockPos.MutableBlockPos blockPosition;

    @Inject(method = "setPose", at = @At("RETURN"), remap = false)
    private void pocket$originAtRotationPoint(final Pose3dc pose, final CallbackInfo ci) {
        if (pose == null) return;

        final Vector3dc scale = pose.scale();
        if (Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON) {
            return;
        }

        final Vector3dc rotationPoint = pose.rotationPoint();
        this.pos = new Vec3(rotationPoint.x(), rotationPoint.y(), rotationPoint.z());
        this.blockPosition.set(this.pos.x, this.pos.y, this.pos.z);
    }
}

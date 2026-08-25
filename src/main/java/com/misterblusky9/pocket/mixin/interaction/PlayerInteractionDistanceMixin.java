package com.misterblusky9.pocket.mixin.interaction;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.EntityScaleTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerInteractionDistanceMixin extends LivingEntity {
    protected PlayerInteractionDistanceMixin(
            final EntityType<? extends LivingEntity> entityType,
            final Level level
    ) {
        super(entityType, level);
    }

    @Shadow
    public abstract double blockInteractionRange();

    @Shadow
    public abstract double entityInteractionRange();

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void pocket$scalePlayerBlockReach(
            final CallbackInfoReturnable<Double> cir
    ) {
        final double scale = EntityScaleTracker.dimensionScale((Player) (Object) this);
        if (Math.abs(scale - 1.0D) > PocketSized.EPSILON) {
            cir.setReturnValue(cir.getReturnValue() * scale);
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void pocket$scalePlayerEntityReach(
            final CallbackInfoReturnable<Double> cir
    ) {
        final double scale = EntityScaleTracker.dimensionScale((Player) (Object) this);
        if (Math.abs(scale - 1.0D) > PocketSized.EPSILON) {
            cir.setReturnValue(cir.getReturnValue() * scale);
        }
    }

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void pocket$worldSpaceBlockReach(
            final BlockPos pos,
            final double slop,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), pos);
        if (subLevel == null || !isActuallyScaled(subLevel)) return;

        final Pose3dc pose = subLevel.logicalPose();
        final Vec3 eyeWorld = this.getEyePosition();
        final Vec3 eyeLocal = pose.transformPositionInverse(eyeWorld);

        final Vec3 closestLocal = new Vec3(
                Mth.clamp(eyeLocal.x, pos.getX(), pos.getX() + 1.0D),
                Mth.clamp(eyeLocal.y, pos.getY(), pos.getY() + 1.0D),
                Mth.clamp(eyeLocal.z, pos.getZ(), pos.getZ() + 1.0D)
        );
        final Vec3 closestWorld = pose.transformPosition(closestLocal);

        final double worldReach = this.blockInteractionRange() + slop;
        cir.setReturnValue(eyeWorld.distanceToSqr(closestWorld) < worldReach * worldReach);
    }

    @Inject(
            method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void pocket$worldSpaceEntityReach(
            final AABB bounds,
            final double slop,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), bounds.getBottomCenter());
        if (subLevel == null || !isActuallyScaled(subLevel)) return;

        final Pose3dc pose = subLevel.logicalPose();
        final Vec3 eyeWorld = this.getEyePosition();
        final Vec3 eyeLocal = pose.transformPositionInverse(eyeWorld);

        final Vec3 closestLocal = new Vec3(
                Mth.clamp(eyeLocal.x, bounds.minX, bounds.maxX),
                Mth.clamp(eyeLocal.y, bounds.minY, bounds.maxY),
                Mth.clamp(eyeLocal.z, bounds.minZ, bounds.maxZ)
        );
        final Vec3 closestWorld = pose.transformPosition(closestLocal);

        final double worldReach = this.entityInteractionRange() + slop;
        cir.setReturnValue(eyeWorld.distanceToSqr(closestWorld) < worldReach * worldReach);
    }

    private static boolean isActuallyScaled(final SubLevel subLevel) {
        final double sx = subLevel.logicalPose().scale().x();
        final double sy = subLevel.logicalPose().scale().y();
        final double sz = subLevel.logicalPose().scale().z();
        return Math.abs(sx - 1.0D) > PocketSized.EPSILON
                || Math.abs(sy - 1.0D) > PocketSized.EPSILON
                || Math.abs(sz - 1.0D) > PocketSized.EPSILON;
    }
}

package com.misterblusky9.pym.internal.mixin.interaction;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pym.api.ScaleBounds;
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
            EntityType<? extends LivingEntity> entityType,
            Level level
    ) {
        super(entityType, level);
    }

    @Shadow
    public abstract double blockInteractionRange();

    @Shadow
    public abstract double entityInteractionRange();

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void pym$worldSpaceBlockReach(
            BlockPos pos,
            double slop,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SubLevel subLevel = Sable.HELPER.getContaining(this.level(), pos);
        if (subLevel == null || !isActuallyScaled(subLevel)) return;

        Pose3dc pose = subLevel.logicalPose();
        Vec3 eyeWorld = this.getEyePosition();
        Vec3 eyeLocal = pose.transformPositionInverse(eyeWorld);

        Vec3 closestLocal = new Vec3(
                Mth.clamp(eyeLocal.x, pos.getX(), pos.getX() + 1.0D),
                Mth.clamp(eyeLocal.y, pos.getY(), pos.getY() + 1.0D),
                Mth.clamp(eyeLocal.z, pos.getZ(), pos.getZ() + 1.0D)
        );
        Vec3 closestWorld = pose.transformPosition(closestLocal);

        double worldReach = this.blockInteractionRange() + slop;
        cir.setReturnValue(eyeWorld.distanceToSqr(closestWorld) < worldReach * worldReach);
    }

    @Inject(
            method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void pym$worldSpaceEntityReach(
            AABB bounds,
            double slop,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SubLevel subLevel = Sable.HELPER.getContaining(this.level(), bounds.getBottomCenter());
        if (subLevel == null || !isActuallyScaled(subLevel)) return;

        Pose3dc pose = subLevel.logicalPose();
        Vec3 eyeWorld = this.getEyePosition();
        Vec3 eyeLocal = pose.transformPositionInverse(eyeWorld);

        Vec3 closestLocal = new Vec3(
                Mth.clamp(eyeLocal.x, bounds.minX, bounds.maxX),
                Mth.clamp(eyeLocal.y, bounds.minY, bounds.maxY),
                Mth.clamp(eyeLocal.z, bounds.minZ, bounds.maxZ)
        );
        Vec3 closestWorld = pose.transformPosition(closestLocal);

        double worldReach = this.entityInteractionRange() + slop;
        cir.setReturnValue(eyeWorld.distanceToSqr(closestWorld) < worldReach * worldReach);
    }

    private static boolean isActuallyScaled(SubLevel subLevel) {
        double sx = subLevel.logicalPose().scale().x();
        double sy = subLevel.logicalPose().scale().y();
        double sz = subLevel.logicalPose().scale().z();
        return Math.abs(sx - 1.0D) > ScaleBounds.EPSILON
                || Math.abs(sy - 1.0D) > ScaleBounds.EPSILON
                || Math.abs(sz - 1.0D) > ScaleBounds.EPSILON;
    }
}

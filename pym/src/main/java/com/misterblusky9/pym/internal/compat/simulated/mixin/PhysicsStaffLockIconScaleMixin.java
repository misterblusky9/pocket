package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffRenderHandler", remap = false)
public abstract class PhysicsStaffLockIconScaleMixin {
    @Unique
    private static final String ADD_VERTEX =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

    @Unique
    private static final double REFERENCE_DISTANCE = 16.0;

    @Unique
    private static final double MAX_APPARENT = 8.0;

    @Unique
    private static final double MIN_APPARENT = 1.0 / 8.0;

    @ModifyArg(method = "renderAllLocks", at = @At(value = "INVOKE", target = ADD_VERTEX), index = 1, remap = false, require = 1)
    private static float pym$lockIconX(float x, @Local ClientSubLevel subLevel, @Local(argsOnly = true) Vec3 camera) {
        return x * pym$iconScale(subLevel, camera);
    }

    @ModifyArg(method = "renderAllLocks", at = @At(value = "INVOKE", target = ADD_VERTEX), index = 2, remap = false, require = 1)
    private static float pym$lockIconY(float y, @Local ClientSubLevel subLevel, @Local(argsOnly = true) Vec3 camera) {
        return y * pym$iconScale(subLevel, camera);
    }

    @Unique
    private static float pym$iconScale(ClientSubLevel subLevel, Vec3 camera) {
        if (subLevel == null) return 1.0F;
        double scale = subLevel.renderPose().scale().x();
        if (!ScaleBounds.isValid(scale)) return 1.0F;
        if (scale == 1.0 || camera == null) return (float) scale;
        Vector3dc position = subLevel.renderPose().position();
        double dx = position.x() - camera.x;
        double dy = position.y() - camera.y;
        double dz = position.z() - camera.z;
        double distance = Math.max(Math.sqrt(dx * dx + dy * dy + dz * dz), 1.0E-3);
        double perDistance = distance / REFERENCE_DISTANCE;
        return (float) Math.clamp(scale, MIN_APPARENT * perDistance, MAX_APPARENT * perDistance);
    }
}

package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.CameraSubLevelScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.CarriageCouplingRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CarriageCouplingRenderer.class, remap = false)
public abstract class CarriageCouplingScaleMixin {
    @WrapOperation(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
            ),
            remap = false,
            require = 1
    )
    private static boolean pocket$cullCouplingInWorldSpace(
            final Vec3 anchor,
            final Position camera,
            final double distance,
            final Operation<Boolean> original
    ) {
        final ClientSubLevel subLevel = pocket$subLevelAt(anchor);
        if (subLevel == null) return original.call(anchor, camera, distance);

        final Vec3 world = subLevel.renderPose(CameraSubLevelScale.partialTick()).transformPosition(anchor);
        return original.call(world, camera, distance);
    }

    @WrapOperation(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"
            ),
            remap = false,
            require = 2
    )
    private static void pocket$placeCouplingInWorldSpace(
            final PoseStack ms,
            final double x,
            final double y,
            final double z,
            final Operation<Void> original,
            @Local(argsOnly = true) final Vec3 camera
    ) {
        final Vec3 plotPoint = new Vec3(x + camera.x, y + camera.y, z + camera.z);
        final ClientSubLevel subLevel = pocket$subLevelAt(plotPoint);
        if (subLevel == null) {
            original.call(ms, x, y, z);
            return;
        }

        final Pose3dc renderPose = subLevel.renderPose(CameraSubLevelScale.partialTick());
        final Vec3 world = renderPose.transformPosition(plotPoint);
        original.call(ms, world.x - camera.x, world.y - camera.y, world.z - camera.z);

        ms.mulPose(new Quaternionf(renderPose.orientation()));

        final Vector3dc scale = renderPose.scale();
        ms.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }

    @Unique
    private static ClientSubLevel pocket$subLevelAt(final Vec3 point) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;

        final SubLevel subLevel = Sable.HELPER.getContaining(level, point);
        return subLevel instanceof final ClientSubLevel client && !client.isRemoved() ? client : null;
    }
}

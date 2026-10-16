package com.misterblusky9.pym.internal.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class SubLevelBlockEntityCameraPrecisionMixin {
    @ModifyArg(
            method = "renderBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/mixinterface/BlockEntityRenderDispatcherExtension;"
                            + "sable$setCameraPosition(Lnet/minecraft/world/phys/Vec3;)V",
                    ordinal = 0
            ),
            index = 0,
            remap = false
    )
    private Vec3 pym$preciseCameraPosition(
            Vec3 original,
            @Local ClientSubLevel subLevel,
            @Local(argsOnly = true, ordinal = 0) double cameraX,
            @Local(argsOnly = true, ordinal = 1) double cameraY,
            @Local(argsOnly = true, ordinal = 2) double cameraZ,
            @Local(argsOnly = true) float partialTick
    ) {
        Pose3dc pose = subLevel.renderPose(partialTick);
        Vector3d camera = pose.transformPositionInverse(new Vector3d(cameraX, cameraY, cameraZ));
        return new Vec3(camera.x, camera.y, camera.z);
    }
}

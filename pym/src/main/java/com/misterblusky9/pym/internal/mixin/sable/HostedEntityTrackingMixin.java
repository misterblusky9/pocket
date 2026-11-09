package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class HostedEntityTrackingMixin {
    @WrapOperation(
            method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 pym$presentedPosition(Entity entity, Operation<Vec3> original) {
        Vec3 position = original.call(entity);
        Pose3d shown = Hosted.presented(Sable.HELPER.getContaining(entity.level(), entity.position()));
        return shown != null ? shown.transformPosition(entity.position()) : position;
    }
}

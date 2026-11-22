package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.companion.math.Pose3d;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelSerializer.class, remap = false)
public abstract class HostedGhostMixin {
    @Inject(method = "fullyLoad", at = @At("RETURN"), remap = false)
    private static void pym$discardStaleCopy(ServerLevel level, SubLevelData data, CallbackInfoReturnable<ServerSubLevel> cir) {
        Hosted.loaded(cir.getReturnValue());
    }

    @WrapOperation(
            method = {"toData", "serialize"},
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"),
            remap = false,
            require = 3
    )
    private static Pose3d pym$savePhysicalPose(ServerSubLevel subLevel, Operation<Pose3d> original) {
        return Hosted.rootOf(subLevel) != null ? Hosted.physical(subLevel) : original.call(subLevel);
    }
}

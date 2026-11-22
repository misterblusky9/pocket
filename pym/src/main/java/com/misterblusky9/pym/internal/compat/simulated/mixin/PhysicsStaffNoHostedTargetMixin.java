package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.client.Mirrors;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffNoHostedTargetMixin {
    @WrapOperation(
            method = "onItemUsed",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getContainingClient(Lnet/minecraft/core/Position;)Ldev/ryanhcode/sable/sublevel/ClientSubLevel;"),
            remap = false
    )
    private ClientSubLevel pym$ignoreUnphysicalHatTargets(
            ActiveSableCompanion helper, Position hit, Operation<ClientSubLevel> original
    ) {
        ClientSubLevel found = original.call(helper, hit);
        if (found != null && (Mirrors.isMirror(found) || Hosted.isHosted(found))) return null;
        return found;
    }
}

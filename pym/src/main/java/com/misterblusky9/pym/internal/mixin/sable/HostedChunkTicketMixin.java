package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = PhysicsChunkTicketManager.class, remap = false)
public abstract class HostedChunkTicketMixin {
    @ModifyExpressionValue(
            method = "update",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;getAllSubLevels()Ljava/util/List;"),
            remap = false
    )
    private List<ServerSubLevel> pym$skipHosted(List<ServerSubLevel> original) {
        return Hosted.visible(original);
    }
}

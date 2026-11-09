package com.misterblusky9.pym.internal.compat.create_connected.mixin;

import com.misterblusky9.pym.internal.compat.create.HostedLinks;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.hlysine.create_connected.content.redstonelinkwildcard.LinkWildcardNetworkHandler", remap = false)
public abstract class HostedWildcardLinkRangeMixin {
    @Inject(method = "withinRange", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void pym$hostedRange(IRedstoneLinkable from, IRedstoneLinkable to, LevelAccessor level,
                                        CallbackInfoReturnable<Boolean> cir) {
        Boolean hosted = HostedLinks.inRange(from, to, level);
        if (hosted != null) cir.setReturnValue(hosted);
    }
}

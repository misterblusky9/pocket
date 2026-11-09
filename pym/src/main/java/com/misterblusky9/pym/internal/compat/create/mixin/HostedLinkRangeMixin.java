package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create.HostedLinks;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RedstoneLinkNetworkHandler.class, remap = false)
public abstract class HostedLinkRangeMixin {
    @WrapOperation(
            method = "updateNetworkOf",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"),
            remap = false,
            require = 1
    )
    private boolean pym$hostedRange(IRedstoneLinkable from, IRedstoneLinkable to, Operation<Boolean> original,
                                    @Local(argsOnly = true) LevelAccessor accessor) {
        Boolean hosted = HostedLinks.inRange(from, to, accessor);
        return hosted != null ? hosted : original.call(from, to);
    }
}

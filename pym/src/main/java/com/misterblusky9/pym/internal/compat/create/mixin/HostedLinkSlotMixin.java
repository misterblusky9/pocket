package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Hosted;
import com.simibubi.create.content.redstone.link.LinkHandler;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LinkHandler.class, remap = false)
public abstract class HostedLinkSlotMixin {
    @WrapOperation(
            method = "onBlockActivated",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceRange(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/phys/BlockHitResult;"),
            remap = false
    )
    private static BlockHitResult pym$clickedSlot(Level level, Player player, double range,
                                                  Operation<BlockHitResult> original,
                                                  @Local(argsOnly = true) PlayerInteractEvent.RightClickBlock event) {
        return Hosted.isHosted(Sable.HELPER.getContaining(level, event.getPos()))
                ? event.getHitVec() : original.call(level, player, range);
    }
}

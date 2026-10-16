package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScaling;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockHoldInteraction.class, remap = false)
public abstract class HoldInteractionReachMixin {
    @ModifyReturnValue(method = "getInteractionRange", at = @At("RETURN"), remap = false)
    private static double pym$scaleWithPlayer(double range, Player player) {
        return range * PehkuiEntityScaling.blockReachScale(player);
    }
}

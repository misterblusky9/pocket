package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Create claims contraption right-clicks off the raw input event, before the vanilla
// interaction pipeline runs, so held-tool priority has to be re-stated here.
@Mixin(value = ContraptionHandlerClient.class, remap = false)
public abstract class ContraptionInteractionPriorityMixin {
    @Inject(
            method = "rightClickingOnContraptionsGetsHandledLocally",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void pocket$heldToolsOutrankContraptions(
            final InputEvent.InteractionKeyMappingTriggered event,
            final CallbackInfo ci
    ) {
        if (!event.isUseItem()) return;

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (HeldInteractionPriority.claimsInput(player, player.getItemInHand(event.getHand()))) {
            ci.cancel();
        }
    }
}

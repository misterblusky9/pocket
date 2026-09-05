package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.SwitchControllerBlockEntity;
import com.misterblusky9.pocket.create.HelmBearingContraption;
import com.misterblusky9.pocket.create.SwitchContraption;
import com.misterblusky9.pocket.debug.SwitchBearingDebug;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class SwitchBearingContraptionInteractionMixin {
    @Shadow
    protected Contraption contraption;

    @Inject(
            method = "handlePlayerInteraction",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void pocket$routeWholeSwitchContraptionInteraction(
            final Player player,
            final BlockPos localPos,
            final Direction side,
            final InteractionHand hand,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (contraption instanceof HelmBearingContraption || !(contraption instanceof SwitchContraption)) {
            return;
        }

        final AbstractContraptionEntity self = (AbstractContraptionEntity) (Object) this;
        if (!(self instanceof ControlledContraptionEntity controlled)) {
            SwitchBearingDebug.warn(
                    "Switch contraption interaction reached unexpected entity type={} entityId={}",
                    self.getClass().getName(),
                    self.getId()
            );
            cir.setReturnValue(false);
            return;
        }

        if (self.level().isClientSide) {
            SwitchBearingDebug.info(
                    "Client accepted synthetic switch hit entityId={} localPos={} face={} hand={}",
                    self.getId(), localPos, side, hand
            );
            cir.setReturnValue(true);
            return;
        }

        final IControlContraption controller =
                ((ControlledContraptionEntityControllerInvoker) controlled).pocket$invokeGetController();

        if (!(controller instanceof SwitchControllerBlockEntity switchController)) {
            SwitchBearingDebug.warn(
                    "Server could not route switch hit entityId={} controllerType={}",
                    self.getId(),
                    controller == null ? "null" : controller.getClass().getName()
            );
            cir.setReturnValue(false);
            return;
        }

        final boolean handled = switchController.onContraptionInteraction(player, hand);
        SwitchBearingDebug.info(
                "Server routed switch hit entityId={} controller={} player={} hand={} handled={}",
                self.getId(),
                ((BlockEntity) switchController).getBlockPos(),
                player.getGameProfile().getName(),
                hand,
                handled
        );
        cir.setReturnValue(handled);
    }
}

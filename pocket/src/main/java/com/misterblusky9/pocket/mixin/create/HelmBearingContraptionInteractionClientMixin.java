package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import com.misterblusky9.pocket.client.HelmBearingHandler;
import com.misterblusky9.pocket.create.HelmBearingContraption;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContraptionEntity.class, priority = 1100, remap = false)
public abstract class HelmBearingContraptionInteractionClientMixin {
    @Shadow
    protected Contraption contraption;

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, require = 1)
    private void pocket$beginHelmControl(
            Player player,
            BlockPos localPos,
            Direction side,
            InteractionHand hand,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(contraption instanceof HelmBearingContraption) || !player.isLocalPlayer()) {
            return;
        }

        AbstractContraptionEntity self = (AbstractContraptionEntity) (Object) this;
        if (!(self instanceof ControlledContraptionEntity controlled)) {
            return;
        }

        BlockPos controllerPos = ((ControlledContraptionEntityControllerPosAccessor) controlled).pocket$getControllerPos();
        if (controllerPos == null
                || !(self.level().getBlockEntity(controllerPos) instanceof HelmBearingBlockEntity)) {
            return;
        }

        if (!HelmBearingHandler.INSTANCE.isActive()) {
            HelmBearingHandler.INSTANCE.startHold(self.level(), player, controllerPos);
        }
        cir.setReturnValue(true);
    }
}

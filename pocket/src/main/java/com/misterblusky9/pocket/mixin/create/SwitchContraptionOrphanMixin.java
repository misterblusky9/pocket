package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.SwitchControllerBlockEntity;
import com.misterblusky9.pocket.create.SwitchContraption;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ControlledContraptionEntity.class, remap = false)
public abstract class SwitchContraptionOrphanMixin {
    @Unique
    private static final int POCKET$ORPHAN_GRACE_TICKS = 200;

    @Unique
    private static final Logger POCKET$LOGGER = LogUtils.getLogger();

    @Shadow
    protected BlockPos controllerPos;

    @Shadow
    protected abstract IControlContraption getController();

    @Unique
    private int pocket$orphanTicks;

    @Inject(method = "tickContraption", at = @At("HEAD"), require = 1)
    private void pocket$recoverOrphanedSwitchContraption(final CallbackInfo ci) {
        final ControlledContraptionEntity self = (ControlledContraptionEntity) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (!(self.getContraption() instanceof SwitchContraption)) {
            return;
        }

        if (controllerPos != null && getController() instanceof SwitchControllerBlockEntity) {
            pocket$orphanTicks = 0;
            return;
        }

        if (++pocket$orphanTicks < POCKET$ORPHAN_GRACE_TICKS) {
            return;
        }

        POCKET$LOGGER.warn(
                "[PocketSwitch] Switch contraption entityId={} had no controller at {} for {} ticks;"
                        + " disassembling to recover its blocks",
                self.getId(),
                controllerPos,
                pocket$orphanTicks
        );
        self.disassemble();
    }
}

package com.misterblusky9.pocket.mixin.simulated;

import com.misterblusky9.pocket.compat.simulated.SimulatedRopeScaleBoundary;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public abstract class RopeScaleBoundaryMixin {
    @Inject(method = "createRope", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$rejectCrossScaleRope(
            final RopeStrandHolderBehavior target,
            final boolean dropItem,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        final RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (!(self.blockEntity.getLevel() instanceof final ServerLevel level)) {
            cir.setReturnValue(false);
            return;
        }

        if (!SimulatedRopeScaleBoundary.canConnect(level, self, target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void pocket$severInvalidCrossScaleRope(final CallbackInfo ci) {
        final RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (!(self.blockEntity.getLevel() instanceof final ServerLevel level)) return;

        final ServerRopeStrand strand = self.getOwnedStrand();
        if (strand == null || !strand.areAttachmentsLoaded(level)) return;
        if (!SimulatedRopeScaleBoundary.crossesBoundary(level, strand)) return;

        self.destroyRope(null, null, level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS));
    }
}

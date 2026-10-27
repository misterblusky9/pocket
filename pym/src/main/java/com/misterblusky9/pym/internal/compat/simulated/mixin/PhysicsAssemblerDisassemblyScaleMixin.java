package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.DisassemblyScaleAlignment;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(
        targets = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity",
        remap = false
)
public abstract class PhysicsAssemblerDisassemblyScaleMixin {
    @Shadow private boolean disassembling;
    @Shadow private int disassemblingTicks;
    @Shadow private int disassemblyReadyTicks;
    @Shadow private FreeConstraintHandle alignmentConstraint;

    @Unique private UUID pym$expanding;
    @Unique private int pym$expansionTicks;
    @Unique private boolean pym$slack;

    @Inject(method = "assembleOrDisassemble", at = @At("RETURN"), remap = false)
    private void pym$expandBeforeDisassembly(CallbackInfo ci) {
        if (!this.disassembling) return;
        BlockEntity self = (BlockEntity) (Object) this;
        boolean growing = DisassemblyScaleAlignment.growing(self);
        this.pym$expanding = DisassemblyScaleAlignment.begin(self);
        if (this.pym$expanding != null && growing) {
            DisassemblyScaleAlignment.slackMotors(this.alignmentConstraint);
            this.pym$slack = true;
        }
    }

    @Inject(method = "tickDisassembling", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$alignScale(CallbackInfo ci) {
        if (this.pym$expanding == null) return;
        boolean full = DisassemblyScaleAlignment.align((BlockEntity) (Object) this);
        if (!full && this.pym$expansionTicks++ < DisassemblyScaleAlignment.EXPANSION_BUDGET_TICKS) {
            this.disassemblingTicks = 0;
            this.disassemblyReadyTicks = 0;
            if (this.pym$slack) ci.cancel();
            return;
        }
        if (this.pym$slack) {
            DisassemblyScaleAlignment.restoreMotors(this.alignmentConstraint);
            this.pym$slack = false;
        }
    }

    @Inject(method = "placeIntoWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$requireFullScale(CallbackInfo ci) {
        if (this.pym$expanding == null) return;
        if (!DisassemblyScaleAlignment.align((BlockEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "stopDisassembling", at = @At("RETURN"), remap = false)
    private void pym$releaseExpansion(CallbackInfo ci) {
        DisassemblyScaleAlignment.end(this.pym$expanding);
        this.pym$expanding = null;
        this.pym$expansionTicks = 0;
        this.pym$slack = false;
    }
}

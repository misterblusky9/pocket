package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.DisassemblyScaleAlignment;
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

    @Unique private UUID pym$expanding;
    @Unique private int pym$expansionTicks;

    @Inject(method = "assembleOrDisassemble", at = @At("RETURN"), remap = false)
    private void pym$expandBeforeDisassembly(final CallbackInfo ci) {
        if (!this.disassembling) return;
        this.pym$expanding = DisassemblyScaleAlignment.begin((BlockEntity) (Object) this);
    }

    @Inject(method = "tickDisassembling", at = @At("HEAD"), remap = false)
    private void pym$alignScale(final CallbackInfo ci) {
        if (this.pym$expanding == null) return;
        if (DisassemblyScaleAlignment.align((BlockEntity) (Object) this)) return;
        if (this.pym$expansionTicks++ >= DisassemblyScaleAlignment.EXPANSION_BUDGET_TICKS) return;

        this.disassemblingTicks = 0;
        this.disassemblyReadyTicks = 0;
    }

    @Inject(method = "placeIntoWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$requireFullScale(final CallbackInfo ci) {
        if (this.pym$expanding == null) return;
        if (!DisassemblyScaleAlignment.align((BlockEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "stopDisassembling", at = @At("RETURN"), remap = false)
    private void pym$releaseExpansion(final CallbackInfo ci) {
        DisassemblyScaleAlignment.end(this.pym$expanding);
        this.pym$expanding = null;
        this.pym$expansionTicks = 0;
    }
}

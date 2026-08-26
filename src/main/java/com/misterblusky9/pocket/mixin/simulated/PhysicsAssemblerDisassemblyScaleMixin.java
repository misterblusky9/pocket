package com.misterblusky9.pocket.mixin.simulated;

import com.misterblusky9.pocket.scale.DisassemblyScaleAlignment;
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

    @Unique private UUID pocket$expanding;
    @Unique private int pocket$expansionTicks;

    @Inject(method = "assembleOrDisassemble", at = @At("RETURN"), remap = false)
    private void pocket$expandBeforeDisassembly(final CallbackInfo ci) {
        if (!this.disassembling) return;
        this.pocket$expanding = DisassemblyScaleAlignment.begin((BlockEntity) (Object) this);
    }

    @Inject(method = "tickDisassembling", at = @At("HEAD"), remap = false)
    private void pocket$alignScale(final CallbackInfo ci) {
        if (this.pocket$expanding == null) return;
        if (DisassemblyScaleAlignment.align((BlockEntity) (Object) this)) return;
        if (this.pocket$expansionTicks++ >= DisassemblyScaleAlignment.EXPANSION_BUDGET_TICKS) return;

        this.disassemblingTicks = 0;
        this.disassemblyReadyTicks = 0;
    }

    @Inject(method = "placeIntoWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$requireFullScale(final CallbackInfo ci) {
        if (this.pocket$expanding == null) return;
        if (!DisassemblyScaleAlignment.align((BlockEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "stopDisassembling", at = @At("RETURN"), remap = false)
    private void pocket$releaseExpansion(final CallbackInfo ci) {
        DisassemblyScaleAlignment.end(this.pocket$expanding);
        this.pocket$expanding = null;
        this.pocket$expansionTicks = 0;
    }
}

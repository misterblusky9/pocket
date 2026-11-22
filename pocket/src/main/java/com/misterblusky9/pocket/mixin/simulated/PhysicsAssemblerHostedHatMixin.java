package com.misterblusky9.pocket.mixin.simulated;

import com.misterblusky9.pocket.build.CarriedBuilds;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity",
        remap = false
)
public abstract class PhysicsAssemblerHostedHatMixin {
    @Inject(method = "assembleOrDisassemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$rejectHostedHat(CallbackInfo ci) {
        if (CarriedBuilds.belongsToHat(Sable.HELPER.getContaining((BlockEntity) (Object) this))) ci.cancel();
    }
}

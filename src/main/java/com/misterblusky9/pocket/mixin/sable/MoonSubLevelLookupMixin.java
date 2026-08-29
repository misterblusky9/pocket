package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.moon.MoonPhysicsTarget;
import com.misterblusky9.pocket.moon.MoonSubLevel;
import com.misterblusky9.pocket.moon.MoonSubLevels;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// The shim is not in the container's grid, so uuid lookups have to find it here.
// This is the only seam Simulated needs to address the moon as a sublevel.
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MoonSubLevelLookupMixin {
    @Inject(method = "getSubLevel(Ljava/util/UUID;)Ldev/ryanhcode/sable/sublevel/SubLevel;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$findMoonSubLevel(final UUID uuid, final CallbackInfoReturnable<SubLevel> cir) {
        if (!MoonPhysicsTarget.isId(uuid)) return;
        final SubLevelContainer self = (SubLevelContainer) (Object) this;
        if (!(self.getLevel() instanceof final ServerLevel level)) return;

        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim != null) cir.setReturnValue(shim);
    }
}

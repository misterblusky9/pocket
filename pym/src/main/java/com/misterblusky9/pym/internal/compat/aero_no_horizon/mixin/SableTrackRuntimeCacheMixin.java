package com.misterblusky9.pym.internal.compat.aero_no_horizon.mixin;

import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "com.github.aero_no_horizon.trackwork.content.track.SableTrackRuntimeCache", remap = false)
public abstract class SableTrackRuntimeCacheMixin {
    @Shadow @Final private static Map<UUID, ?> SUSPENSION_SAMPLES;
    @Shadow @Final private static Map<UUID, ?> SUSPENSION_FORCES;
    @Shadow @Final private static Map<UUID, ?> DAMPING_FILTERS;
    @Shadow @Final private static Map<UUID, ?> DRIVE_SPEEDS;
    @Unique private static final Map<UUID, Double> pym$scales = new ConcurrentHashMap<>();

    @Inject(method = "getSuspensionSample", at = @At("HEAD"), remap = false, require = 0)
    private static void pym$resetOnResize(ServerSubLevel subLevel, BlockPos pos, CallbackInfoReturnable<?> cir) {
        UUID id = subLevel.getUniqueId();
        double scale = Pym.scale().of(subLevel);
        Double previous = pym$scales.put(id, scale);
        if (previous != null && previous > 0.0D && scale > 0.0D) {
            double ratio = scale / previous;
            if (ratio >= 0.5D && ratio <= 2.0D) return;
        }
        SUSPENSION_SAMPLES.remove(id);
        SUSPENSION_FORCES.remove(id);
        DAMPING_FILTERS.remove(id);
        DRIVE_SPEEDS.remove(id);
    }
}

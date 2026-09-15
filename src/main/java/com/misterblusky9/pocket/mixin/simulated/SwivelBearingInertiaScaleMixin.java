package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.WeldedAssembly;
import com.misterblusky9.pocket.physics.ScaledMassData;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public abstract class SwivelBearingInertiaScaleMixin {
    @WrapOperation(
            method = "updateServoCoefficients",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;getMassTracker()Ldev/ryanhcode/sable/api/physics/mass/MassData;"
            ),
            remap = false,
            require = 2
    )
    private MassData pocket$solverInertia(final ServerSubLevel subLevel, final Operation<MassData> original) {
        final MassData raw = original.call(subLevel);
        final double scale = pocket$scale(subLevel);
        if (raw == null || scale == 1.0D) return raw;
        return new ScaledMassData(raw, scale, WeldedAssembly.solverFloor(subLevel));
    }

    @WrapOperation(
            method = "updateServoCoefficients",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D", ordinal = 1),
            remap = false,
            require = 1
    )
    private double pocket$scaledInertiaFloor(
            final double floor,
            final double selected,
            final Operation<Double> original,
            @Local(ordinal = 0) final SubLevel containing,
            @Local(ordinal = 1) final SubLevel attached,
            @Local(ordinal = 0) final double containingInertia,
            @Local(ordinal = 1) final double attachedInertia
    ) {
        final double factor = selected == containingInertia
                ? pocket$inertiaScale(containing)
                : pocket$inertiaScale(attached);
        return original.call(floor * factor, selected);
    }

    @Unique
    private static double pocket$inertiaScale(final SubLevel subLevel) {
        if (!(subLevel instanceof final ServerSubLevel server)) return 1.0D;
        final double scale = pocket$scale(server);
        final MassData raw = server.getMassTracker();
        if (raw == null || scale == 1.0D) return 1.0D;
        return ScaledMassData.forceFactors(raw.getMass(), scale, WeldedAssembly.solverFloor(server))[1];
    }

    @Unique
    private static double pocket$scale(final SubLevel subLevel) {
        final double scale = ScaleState.getScale(subLevel);
        if (!PocketSized.isValidScale(scale)) return 1.0D;
        final double clamped = PocketSized.clampScale(scale);
        return Math.abs(clamped - 1.0D) <= PocketSized.EPSILON ? 1.0D : clamped;
    }
}

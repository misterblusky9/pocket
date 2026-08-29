package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.MoonPickTarget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// The staff picks its target with player.pick(). Substituting a moon hit here - and
// nowhere else - lets the whole of Simulated's client handler run against the moon:
// sounds, staff animation, beam from the tip, scroll distance, rotate mode, and the
// drag packets that carry the moon's uuid to the server.
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffMoonPickMixin {
    @WrapOperation(
            method = "onItemUsed",
            at = @At(value = "INVOKE", target = "pick(DFZ)Lnet/minecraft/world/phys/HitResult;"),
            remap = false,
            require = 1
    )
    private HitResult pocket$pickMoon(
            final LocalPlayer player,
            final double range,
            final float partialTick,
            final boolean hitFluids,
            final Operation<HitResult> original
    ) {
        final HitResult vanilla = original.call(player, range, partialTick, hitFluids);
        final BlockHitResult moon = MoonPickTarget.pick(player, range, vanilla);
        return moon != null ? moon : vanilla;
    }
}

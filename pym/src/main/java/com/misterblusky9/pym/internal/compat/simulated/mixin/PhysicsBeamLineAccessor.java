package com.misterblusky9.pym.internal.compat.simulated.mixin;

import net.createmod.catnip.outliner.LineOutline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler$PhysicsBeam", remap = false)
public interface PhysicsBeamLineAccessor {
    @Accessor(value = "line", remap = false)
    LineOutline pym$line();
}

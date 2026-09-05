package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.compat.simulated.PhysicsStaffScale;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffPickupTargetMixin {
    @WrapMethod(method = "startDraggingSubLevel")
    private void pocket$markPickupTarget(
            final SubLevel subLevel,
            final BlockPos grabbed,
            final LocalPlayer player,
            final InteractionHand hand,
            final Operation<Void> original
    ) {
        PhysicsStaffScale.beginPickup(subLevel);
        try {
            original.call(subLevel, grabbed, player, hand);
        } finally {
            PhysicsStaffScale.endPickup();
        }
    }
}

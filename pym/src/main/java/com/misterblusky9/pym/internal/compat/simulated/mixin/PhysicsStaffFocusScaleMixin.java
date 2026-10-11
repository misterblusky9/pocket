package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffFocusScaleMixin {
    @WrapMethod(method = "getStaffFocusPos", remap = false)
    private static Vec3 pym$focusAtHolderScale(
            final Player player,
            final boolean mainHand,
            final float partialTick,
            final Operation<Vec3> original
    ) {
        final Vec3 focus = original.call(player, mainHand, partialTick);
        final double scale = Pym.entities().renderScale(player, partialTick);
        if (!ScaleBounds.isValid(scale) || ScaleBounds.same(scale, ScaleBounds.FULL)) return focus;

        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final Vec3 anchor = player.isLocalPlayer() && !camera.isDetached()
                ? camera.getPosition()
                : player.getPosition(partialTick);
        return anchor.add(focus.subtract(anchor).scale(scale));
    }
}

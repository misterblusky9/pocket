package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class HostedFrameMixin {
    @WrapMethod(method = "readPose", remap = false)
    private Pose3d pym$shownPose(ServerSubLevel subLevel, Pose3d dest, Operation<Pose3d> original) {
        Pose3d read = original.call(subLevel, dest);
        ServerSubLevel root = Hosted.rootOf(subLevel);
        if (root == null) return read;
        Hosted.readPhysical(subLevel, read);
        if (root != subLevel) Hosted.readPhysical(root, original.call(root, new Pose3d()));
        Pose3d shown = Hosted.presented(subLevel);
        if (shown == null) return read;
        read.position().set(shown.position());
        read.orientation().set(shown.orientation());
        return read;
    }

    @WrapMethod(method = "teleport", remap = false)
    private void pym$physicalTeleport(PhysicsPipelineBody body, Vector3dc position, Quaterniondc orientation,
                                      Operation<Void> original) {
        Pose3d physical = body instanceof ServerSubLevel subLevel
                ? Hosted.toPhysical(subLevel, position, orientation) : null;
        if (physical == null) {
            original.call(body, position, orientation);
            return;
        }
        ServerSubLevel subLevel = (ServerSubLevel) body;
        Vector3d shownPosition = new Vector3d(position);
        Quaterniond shownOrientation = new Quaterniond(orientation);
        original.call(body, physical.position(), physical.orientation());
        subLevel.logicalPose().position().set(shownPosition);
        subLevel.logicalPose().orientation().set(shownOrientation);
    }

    @ModifyReturnValue(method = "getLinearVelocity", at = @At("RETURN"), remap = false)
    private Vector3d pym$shownLinearVelocity(Vector3d velocity, PhysicsPipelineBody body) {
        Quaterniond frame = Hosted.frameRotation(body instanceof ServerSubLevel subLevel ? subLevel : null);
        return frame == null ? velocity : frame.transform(velocity).mul(Hosted.frameScale((ServerSubLevel) body));
    }

    @ModifyReturnValue(method = "getAngularVelocity", at = @At("RETURN"), remap = false)
    private Vector3d pym$shownAngularVelocity(Vector3d velocity, PhysicsPipelineBody body) {
        Quaterniond frame = Hosted.frameRotation(body instanceof ServerSubLevel subLevel ? subLevel : null);
        return frame == null ? velocity : frame.transform(velocity);
    }

    @WrapMethod(method = "addLinearAndAngularVelocity", remap = false)
    private void pym$physicalVelocity(PhysicsPipelineBody body, Vector3dc linear, Vector3dc angular,
                                      Operation<Void> original) {
        Quaterniond frame = Hosted.frameRotation(body instanceof ServerSubLevel subLevel ? subLevel : null);
        if (frame == null) {
            original.call(body, linear, angular);
            return;
        }
        frame.conjugate();
        original.call(body,
                frame.transform(new Vector3d(linear)).div(Hosted.frameScale((ServerSubLevel) body)),
                frame.transform(new Vector3d(angular)));
    }
}

package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.physics.CoupledMass;
import com.misterblusky9.pym.internal.physics.ScaledMassData;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerboundPunchSubLevelPacket.class, remap = false)
public abstract class PunchStrengthScaleMixin {
    @Unique
    private static final ThreadLocal<Double> pym$puncherScale = ThreadLocal.withInitial(() -> 1.0D);

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/network/packets/tcp/ServerboundPunchSubLevelPacket;computeStrengthScalar("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)D"),
            remap = false)
    private double pym$scaleWithPuncher(
            ServerSubLevel subLevel, Vector3dc position, Vector3dc direction,
            Operation<Double> original, @Local Player player
    ) {
        if (Detached.isDetached(subLevel)) return 0.0D;
        double scale = player == null ? 1.0D : Pym.entities().scaleOf(player);
        pym$puncherScale.set(scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D);
        try {
            return original.call(subLevel, position, direction);
        } finally {
            pym$puncherScale.set(1.0D);
        }
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormal(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false)
    private Vector3d pym$unitToWorld(Pose3d pose, Vector3d normal, Operation<Vector3d> original) {
        return original.call(pose, normal).div(pym$poseScale(pose));
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false)
    private Vector3d pym$unitToLocal(Pose3d pose, Vector3d normal, Operation<Vector3d> original) {
        return original.call(pose, normal).mul(pym$poseScale(pose));
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false)
    private Vector3d pym$unitToLocalInto(Pose3d pose, Vector3dc normal, Vector3d dest, Operation<Vector3d> original) {
        return original.call(pose, normal, dest).mul(pym$poseScale(pose));
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;applyImpulse("
                    + "Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"),
            remap = false)
    private void pym$noPunchingDetached(PhysicsPipeline pipeline, PhysicsPipelineBody body, Vector3dc position,
                                        Vector3dc impulse, Operation<Void> original) {
        if (body instanceof SubLevel subLevel && Detached.isDetached(subLevel)) return;
        original.call(pipeline, body, position, impulse);
    }

    @WrapOperation(method = "computeStrengthScalar", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/mass/MassData;getInverseNormalMass(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)D"),
            remap = false)
    private static double pym$physicalInverseMass(MassData mass, Vector3dc position, Vector3dc direction,
                                                  Operation<Double> original, @Local(argsOnly = true) ServerSubLevel subLevel) {
        double inverse = original.call(mass, position, direction);
        double scale = ScaleState.getServerScale(subLevel);
        if (!(scale > 0.0D) || !Double.isFinite(scale) || scale == 1.0D) return inverse;
        double massScale = ScaledMassData.forceFactors(mass.getMass(), scale, CoupledMass.solverFloor(subLevel))[0];
        return massScale > 0.0D && Double.isFinite(massScale) ? inverse / massScale : inverse;
    }

    @WrapOperation(method = "computeStrengthScalar", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/network/packets/tcp/ServerboundPunchSubLevelPacket;punchCurve(D)D"),
            remap = false)
    private static double pym$punchAsPuncherSize(double mass, Operation<Double> original) {
        double scale = pym$puncherScale.get();
        if (scale == 1.0D) return original.call(mass);
        double massUnit = scale * scale * scale;
        return massUnit * Math.sqrt(scale) * original.call(mass / massUnit);
    }

    @Unique
    private static double pym$poseScale(Pose3d pose) {
        double scale = pose.scale().x();
        return scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D;
    }
}

package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.internal.physics.InternalForceScaleContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScaledMagnetForces {
    private static ForceGroup magneticForce;

    public static boolean scaled(SimMagnet magnet1, SimMagnet magnet2) {
        return scaleOf(magnet1.getLatestSubLevel()) != 1.0D || scaleOf(magnet2.getLatestSubLevel()) != 1.0D;
    }

    public static double unit(SimMagnet magnet1, SimMagnet magnet2) {
        return Math.sqrt(scaleOf(magnet1.getLatestSubLevel()) * scaleOf(magnet2.getLatestSubLevel()));
    }

    public static void apply(
            Level level,
            MagnetLaw law,
            SimMagnet magnet1,
            SimMagnet magnet2,
            double timeStep,
            double accelerationLimit,
            double angularAccelerationLimit
    ) {
        ServerSubLevel body1 = (ServerSubLevel) magnet1.getLatestSubLevel();
        ServerSubLevel body2 = (ServerSubLevel) magnet2.getLatestSubLevel();
        if (body1 == null && body2 == null) return;

        double unit = unit(magnet1, magnet2);
        Quaterniondc orientation1 = orientationOf(body1);
        Quaterniondc orientation2 = orientationOf(body2);

        Vector3d r = MagnetPair.getRelativePosition(magnet1, magnet2, new Vector3d()).div(unit);
        Vector3d m1 = orientation1.transform(magnet1.setMagneticMoment(new Vector3d()));
        Vector3d m2 = orientation2.transform(magnet2.setMagneticMoment(new Vector3d()));

        if (!law.canConnect(r, m1, m2) || r.lengthSquared() <= 0.0D) return;
        double distance = r.length();
        double forceScale = law.forceDistanceScale(distance);
        double torqueScale = law.torqueDistanceScale(distance);

        Vector3d symmetric = law.symmetricTorque(m1, m2, torqueScale);
        Vector3d force = law.force(r, m1, m2, forceScale);
        Vector3d torque1 = law.torque(r, m1, m2, distance, torqueScale).add(symmetric);
        Vector3d torque2 = law.torque(r.negate(new Vector3d()), m2, m1, distance, torqueScale).sub(symmetric);

        double unit3 = unit * unit * unit;
        force.mul(unit3);
        torque1.mul(unit3 * unit);
        torque2.mul(unit3 * unit);

        Vector3dc pos1 = JOMLConversion.toJOML(magnet1.getMagnetPosition());
        Vector3dc pos2 = JOMLConversion.toJOML(magnet2.getMagnetPosition());

        double linearInverseMass = linearInverseMass(body1, orientation1, pos1, force)
                + linearInverseMass(body2, orientation2, pos2, force);
        double acceleration = force.length() * linearInverseMass;
        double forceClamp = acceleration > accelerationLimit ? accelerationLimit / acceleration : 1.0D;

        Vector3d relativeTorque = torque1.sub(torque2, new Vector3d());
        double angularInverseMass = angularInverseMass(body1, orientation1, relativeTorque)
                + angularInverseMass(body2, orientation2, relativeTorque);
        double angularAcceleration = relativeTorque.length() * angularInverseMass;
        double angularLimit = angularAccelerationLimit / unit;
        double torqueClamp = angularAcceleration > angularLimit ? angularLimit / angularAcceleration : 1.0D;

        double clamp = Math.min(forceClamp, torqueClamp);
        force.mul(clamp);
        torque1.mul(clamp);
        torque2.mul(clamp);
        relativeTorque.mul(clamp);

        double damping = law.dampingRatio();
        if (damping > 0.0D) {
            double stiffness = -m1.dot(m2);

            Vector3d velocity1 = Sable.HELPER.getVelocity(level, pos1, new Vector3d());
            Vector3d velocity2 = Sable.HELPER.getVelocity(level, pos2, new Vector3d());
            Vector3d relativeVelocity = velocity2.sub(velocity1);
            double inverseMass = linearInverseMass(body1, orientation1, pos1, relativeVelocity)
                    + linearInverseMass(body2, orientation2, pos2, relativeVelocity);
            double k = stiffness * forceClamp * forceScale;
            if (inverseMass > 0.0D && k / inverseMass > 0.0D) {
                force.fma(2.0D * damping * unit * Math.sqrt(k / inverseMass), relativeVelocity);
            }

            Vector3d dampingTorque = new Vector3d(relativeTorque);
            double inverseInertia = angularInverseMass(body1, orientation1, relativeTorque)
                    + angularInverseMass(body2, orientation2, relativeTorque);
            Vector3d spin = angularVelocity(body2).sub(angularVelocity(body1));
            relativeTorque.mul(relativeTorque.dot(spin), spin).div(relativeTorque.lengthSquared() + 1.0E-12D);
            double kt = stiffness * torqueClamp * torqueScale;
            if (inverseInertia > 0.0D && kt / inverseInertia > 0.0D) {
                dampingTorque.set(spin).mul(2.0D * damping * unit * unit * Math.sqrt(kt / inverseInertia));
            }
            torque1.add(dampingTorque);
            torque2.sub(dampingTorque);
        }

        force.mul(timeStep);
        torque1.mul(timeStep);
        torque2.mul(timeStep);

        queue(body1, orientation1, pos1, force, torque1);
        queue(body2, orientation2, pos2, force.negate(), torque2);
    }

    private static void queue(
            ServerSubLevel body,
            Quaterniondc orientation,
            Vector3dc magnetPos,
            Vector3dc worldImpulse,
            Vector3dc worldTorque
    ) {
        if (body == null || body.isRemoved()) return;
        var tracker = body.getMassTracker();
        Vector3dc center = tracker == null ? null : tracker.getCenterOfMass();
        if (center == null) return;

        Vector3d linear = orientation.transformInverse(new Vector3d(worldImpulse));
        Vector3d angular = orientation.transformInverse(new Vector3d(worldTorque));
        Vector3d lever = new Vector3d(magnetPos).sub(center).mul(scaleOf(body));
        angular.add(lever.cross(linear, new Vector3d()));

        double[] factors = InternalForceScaleContext.queuedFactors(body);
        linear.div(factors[0]);
        angular.div(factors[1]);

        ForceGroup magnetic = magneticForce();
        if (magnetic == null) return;
        QueuedForceGroup group = body.getOrCreateQueuedForceGroup(magnetic);
        group.getForceTotal().applyLinearAndAngularImpulse(linear, angular);
        group.recordPointForce(magnetPos, linear);
    }

    private static double linearInverseMass(
            ServerSubLevel body,
            Quaterniondc orientation,
            Vector3dc magnetPos,
            Vector3dc direction
    ) {
        if (body == null || body.getMassTracker() == null) return 0.0D;
        Vector3d local = orientation.transformInverse(new Vector3d(direction));
        double raw = body.getMassTracker().getInverseNormalMass(magnetPos, local);
        return raw / InternalForceScaleContext.queuedFactors(body)[0];
    }

    private static double angularInverseMass(ServerSubLevel body, Quaterniondc orientation, Vector3dc direction) {
        if (body == null || body.getMassTracker() == null) return 0.0D;
        Vector3d v = orientation.transformInverse(new Vector3d(direction));
        body.getMassTracker().getInverseInertiaTensor().transform(v);
        orientation.transform(v);
        double raw = v.dot(direction) / (direction.lengthSquared() + 1.0E-12D);
        return raw / InternalForceScaleContext.queuedFactors(body)[1];
    }

    private static Vector3d angularVelocity(ServerSubLevel body) {
        Vector3d spin = new Vector3d();
        if (body != null) RigidBodyHandle.of(body).getAngularVelocity(spin);
        return spin;
    }

    private static ForceGroup magneticForce() {
        ForceGroup group = magneticForce;
        if (group == null) {
            group = ForceGroups.REGISTRY.get(Sable.sablePath("magnetic_force"));
            magneticForce = group;
        }
        return group;
    }

    private static Quaterniondc orientationOf(ServerSubLevel body) {
        return body == null ? new Quaterniond() : new Quaterniond(body.logicalPose().orientation());
    }

    static double scaleOf(SubLevel subLevel) {
        return subLevel instanceof ServerSubLevel server ? InternalForceScaleContext.scaleOf(server) : 1.0D;
    }

    private ScaledMagnetForces() {}
}

package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.internal.compat.simulated.mixin.DockingConnectorAccess;
import com.misterblusky9.pym.internal.compat.simulated.mixin.DockingConnectorPairAccess;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public interface MagnetLaw {
    boolean canConnect(Vector3dc r, Vector3dc m1, Vector3dc m2);

    double forceDistanceScale(double distance);

    double torqueDistanceScale(double distance);

    Vector3d force(Vector3dc r, Vector3dc m1, Vector3dc m2, double forceScale);

    Vector3d torque(Vector3dc r, Vector3dc m1, Vector3dc m2, double distance, double torqueScale);

    default Vector3d symmetricTorque(Vector3dc m1, Vector3dc m2, double torqueScale) {
        return new Vector3d();
    }

    default double dampingRatio() {
        return 0.0D;
    }

    MagnetLaw REDSTONE = new MagnetLaw() {
        @Override
        public boolean canConnect(Vector3dc r, Vector3dc m1, Vector3dc m2) {
            return r.lengthSquared() < ScaledMagnetField.REACH * ScaledMagnetField.REACH;
        }

        @Override
        public double forceDistanceScale(double distance) {
            if (distance < 1.0D) return 1.0D;
            double d2 = distance * distance;
            return 1.0D / (d2 * d2);
        }

        @Override
        public double torqueDistanceScale(double distance) {
            return distance < 1.0D ? 1.0D : 1.0D / (distance * distance);
        }

        @Override
        public Vector3d force(Vector3dc r, Vector3dc m1, Vector3dc m2, double forceScale) {
            Vector3d f = new Vector3d(m2).mul(m1.dot(r));
            f.fma(m2.dot(r), m1);
            f.fma(m1.dot(m2), r);
            f.fma(-5.0D * m1.dot(r) * m2.dot(r) / r.lengthSquared(), r);
            return f.mul(forceScale / 2.0D);
        }

        @Override
        public Vector3d torque(Vector3dc r, Vector3dc m1, Vector3dc m2, double distance, double torqueScale) {
            Vector3d t = new Vector3d(r).mul(3.0D * m2.dot(r) / (distance * distance));
            t.sub(m2);
            m1.cross(t, t);
            return t.mul(torqueScale / 6.0D);
        }
    };

    static MagnetLaw docking(DockingConnectorPair pair, SimMagnet magnet1, SimMagnet magnet2, double unit) {
        double closest = Math.min(
                ((DockingConnectorAccess) magnet1).pym$closestPairDistance(),
                ((DockingConnectorAccess) magnet2).pym$closestPairDistance()) / unit;

        return new MagnetLaw() {
            private double preventPairFighting(double distance) {
                if (closest < distance * 0.9D) {
                    double k = closest / (distance + 1.0E-5D);
                    return k * k;
                }
                return 1.0D;
            }

            @Override
            public boolean canConnect(Vector3dc r, Vector3dc m1, Vector3dc m2) {
                return r.lengthSquared() < 16.0D && m1.dot(m2) < 0.0D;
            }

            @Override
            public double forceDistanceScale(double distance) {
                double d = 1.0D + Math.max(0.0D, distance - 0.25D);
                d *= d;
                d *= d;
                return 4.0D * this.preventPairFighting(distance) / d;
            }

            @Override
            public double torqueDistanceScale(double distance) {
                double d = 1.0D + Math.max(0.0D, distance - 0.5D);
                return this.preventPairFighting(distance) / (d * d);
            }

            @Override
            public Vector3d force(Vector3dc r, Vector3dc m1, Vector3dc m2, double forceScale) {
                Vector3d f = new Vector3d(m2).mul(-0.5D * m1.dot(r));
                f.fma(-0.5D * m2.dot(r), m1);
                f.fma(m1.dot(m2), r);
                f.fma(3.0D * m1.dot(r) * m2.dot(r) / (r.lengthSquared() + 1.0E-12D), r);
                return f.mul(forceScale / 3.0D);
            }

            @Override
            public Vector3d torque(Vector3dc r, Vector3dc m1, Vector3dc m2, double distance, double torqueScale) {
                return m2.cross(m1, new Vector3d()).mul(torqueScale);
            }

            @Override
            public Vector3d symmetricTorque(Vector3dc m1, Vector3dc m2, double torqueScale) {
                Vector3d t = ((DockingConnectorPairAccess) pair)
                        .pym$relativeOrientationDifference(magnet1, magnet2, new Vector3d());
                Vector3d v = new Vector3d(m1).sub(m2).div(2.0D);
                v.mul(v.dot(t), t);
                return t.mul(torqueScale);
            }

            @Override
            public double dampingRatio() {
                return pair.getDampingRatio();
            }
        };
    }
}

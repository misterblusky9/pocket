package com.misterblusky9.pocket.collision;

import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class FastObbSat {
    private static final double AXIS_EPSILON_SQUARED = 1.0E-24;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    public static Vector3d sat(
            final OrientedBoundingBox3d a,
            final OrientedBoundingBox3d b,
            final Vector3d dest
    ) {
        final Vector3d aPos = a.getPosition();
        final Vector3d bPos = b.getPosition();
        final Vector3d aDim = a.getDimensions();
        final Vector3d bDim = b.getDimensions();

        final double dx = aPos.x - bPos.x;
        final double dy = aPos.y - bPos.y;
        final double dz = aPos.z - bPos.z;

        final double aRadius = 0.5 * Math.sqrt(aDim.x * aDim.x + aDim.y * aDim.y + aDim.z * aDim.z);
        final double bRadius = 0.5 * Math.sqrt(bDim.x * bDim.x + bDim.y * bDim.y + bDim.z * bDim.z);
        final double radius = aRadius + bRadius;
        if (dx * dx + dy * dy + dz * dz > radius * radius) {
            return dest.zero();
        }

        final Scratch s = SCRATCH.get();
        s.reset(a.getOrientation(), b.getOrientation());

        final double ahx = Math.abs(aDim.x) * 0.5;
        final double ahy = Math.abs(aDim.y) * 0.5;
        final double ahz = Math.abs(aDim.z) * 0.5;
        final double bhx = Math.abs(bDim.x) * 0.5;
        final double bhy = Math.abs(bDim.y) * 0.5;
        final double bhz = Math.abs(bDim.z) * 0.5;

        if (!s.test(s.a0.x, s.a0.y, s.a0.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.test(s.a1.x, s.a1.y, s.a1.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.test(s.a2.x, s.a2.y, s.a2.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.test(s.b0.x, s.b0.y, s.b0.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.test(s.b1.x, s.b1.y, s.b1.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.test(s.b2.x, s.b2.y, s.b2.z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();

        if (!s.testCross(s.a0, s.b0, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a0, s.b1, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a0, s.b2, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a1, s.b0, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a1, s.b1, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a1, s.b2, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a2, s.b0, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a2, s.b1, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();
        if (!s.testCross(s.a2, s.b2, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz)) return dest.zero();

        return dest.set(s.bestX * s.minOverlap, s.bestY * s.minOverlap, s.bestZ * s.minOverlap);
    }

    private static final class Scratch {
        private final Vector3d a0 = new Vector3d();
        private final Vector3d a1 = new Vector3d();
        private final Vector3d a2 = new Vector3d();
        private final Vector3d b0 = new Vector3d();
        private final Vector3d b1 = new Vector3d();
        private final Vector3d b2 = new Vector3d();

        private long aqx;
        private long aqy;
        private long aqz;
        private long aqw;
        private long bqx;
        private long bqy;
        private long bqz;
        private long bqw;
        private boolean initialized;

        private double minOverlap;
        private double bestX;
        private double bestY;
        private double bestZ;

        private void reset(final Quaterniondc aOrientation, final Quaterniondc bOrientation) {
            final long naqx = Double.doubleToLongBits(aOrientation.x());
            final long naqy = Double.doubleToLongBits(aOrientation.y());
            final long naqz = Double.doubleToLongBits(aOrientation.z());
            final long naqw = Double.doubleToLongBits(aOrientation.w());
            final long nbqx = Double.doubleToLongBits(bOrientation.x());
            final long nbqy = Double.doubleToLongBits(bOrientation.y());
            final long nbqz = Double.doubleToLongBits(bOrientation.z());
            final long nbqw = Double.doubleToLongBits(bOrientation.w());

            if (!initialized || naqx != aqx || naqy != aqy || naqz != aqz || naqw != aqw) {
                aOrientation.transform(a0.set(1.0, 0.0, 0.0));
                aOrientation.transform(a1.set(0.0, 1.0, 0.0));
                aOrientation.transform(a2.set(0.0, 0.0, 1.0));
                aqx = naqx;
                aqy = naqy;
                aqz = naqz;
                aqw = naqw;
            }

            if (!initialized || nbqx != bqx || nbqy != bqy || nbqz != bqz || nbqw != bqw) {
                bOrientation.transform(b0.set(1.0, 0.0, 0.0));
                bOrientation.transform(b1.set(0.0, 1.0, 0.0));
                bOrientation.transform(b2.set(0.0, 0.0, 1.0));
                bqx = nbqx;
                bqy = nbqy;
                bqz = nbqz;
                bqw = nbqw;
            }

            initialized = true;
            minOverlap = Double.MAX_VALUE;
            bestX = 0.0;
            bestY = 0.0;
            bestZ = 0.0;
        }

        private boolean testCross(
                final Vector3d aAxis,
                final Vector3d bAxis,
                final double dx,
                final double dy,
                final double dz,
                final double ahx,
                final double ahy,
                final double ahz,
                final double bhx,
                final double bhy,
                final double bhz
        ) {
            final double x = aAxis.y * bAxis.z - aAxis.z * bAxis.y;
            final double y = aAxis.z * bAxis.x - aAxis.x * bAxis.z;
            final double z = aAxis.x * bAxis.y - aAxis.y * bAxis.x;
            return test(x, y, z, dx, dy, dz, ahx, ahy, ahz, bhx, bhy, bhz);
        }

        private boolean test(
                final double axisX,
                final double axisY,
                final double axisZ,
                final double dx,
                final double dy,
                final double dz,
                final double ahx,
                final double ahy,
                final double ahz,
                final double bhx,
                final double bhy,
                final double bhz
        ) {
            final double len2 = axisX * axisX + axisY * axisY + axisZ * axisZ;
            if (len2 <= AXIS_EPSILON_SQUARED) {
                return true;
            }

            final double invLen = 1.0 / Math.sqrt(len2);
            double nx = axisX * invLen;
            double ny = axisY * invLen;
            double nz = axisZ * invLen;

            final double center = dx * nx + dy * ny + dz * nz;
            final double radiusA =
                    ahx * Math.abs(a0.x * nx + a0.y * ny + a0.z * nz) +
                    ahy * Math.abs(a1.x * nx + a1.y * ny + a1.z * nz) +
                    ahz * Math.abs(a2.x * nx + a2.y * ny + a2.z * nz);
            final double radiusB =
                    bhx * Math.abs(b0.x * nx + b0.y * ny + b0.z * nz) +
                    bhy * Math.abs(b1.x * nx + b1.y * ny + b1.z * nz) +
                    bhz * Math.abs(b2.x * nx + b2.y * ny + b2.z * nz);

            final double overlap = radiusA + radiusB - Math.abs(center);
            if (overlap <= 0.0) {
                return false;
            }

            if (overlap < minOverlap) {
                if (center < 0.0) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                minOverlap = overlap;
                bestX = nx;
                bestY = ny;
                bestZ = nz;
            }

            return true;
        }
    }

    private FastObbSat() {}
}

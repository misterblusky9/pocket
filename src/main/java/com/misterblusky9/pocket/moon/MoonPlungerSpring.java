package com.misterblusky9.pocket.moon;

// The plunger pair spring, matching LaunchedPlungerEntity.physicsTick.
// The effective-mass factor is the part that must not be dropped: without it the
// impulse is mass-independent and a heavy body barely responds.
public final class MoonPlungerSpring {
    public static final double MAX_STRETCH = 12.0D;
    public static final double FORCE = 40.0D;
    public static final double IMPULSE_FACTOR = 0.07D;
    private static final double MIN_FORCE_SQR = 1.0E-6D;

    public record Impulse(double x, double y, double z) {
        public static final Impulse ZERO = new Impulse(0.0D, 0.0D, 0.0D);

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public Impulse negated() {
            return new Impulse(-x, -y, -z);
        }
    }

    // Sable reports inverse normal mass at the anchor along the force; the pair uses
    // whichever body yields more easily. Absent bodies pass a non-positive value.
    public static double effectiveMass(final double inverseNormalMassA, final double inverseNormalMassB) {
        final double inverse = Math.max(inverseNormalMassA, inverseNormalMassB);
        if (!(inverse > 0.0D) || !Double.isFinite(inverse)) return 0.0D;
        return 1.0D / inverse;
    }

    public static Impulse impulse(
            final double deltaX,
            final double deltaY,
            final double deltaZ,
            final double inverseNormalMassA,
            final double inverseNormalMassB,
            final double timeStep
    ) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY) || !Double.isFinite(deltaZ)) return Impulse.ZERO;
        if (!(timeStep > 0.0D) || !Double.isFinite(timeStep)) return Impulse.ZERO;

        double x = deltaX;
        double y = deltaY;
        double z = deltaZ;

        final double lengthSqr = x * x + y * y + z * z;
        if (lengthSqr > MAX_STRETCH * MAX_STRETCH) {
            final double factor = MAX_STRETCH / Math.sqrt(lengthSqr);
            x *= factor;
            y *= factor;
            z *= factor;
        }

        x *= FORCE;
        y *= FORCE;
        z *= FORCE;

        if (x * x + y * y + z * z < MIN_FORCE_SQR) return Impulse.ZERO;

        final double mass = effectiveMass(inverseNormalMassA, inverseNormalMassB);
        if (!(mass > 0.0D) || !Double.isFinite(mass)) return Impulse.ZERO;

        final double scale = mass * IMPULSE_FACTOR * timeStep;
        return new Impulse(x * scale, y * scale, z * scale);
    }

    private MoonPlungerSpring() {}
}

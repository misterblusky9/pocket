package com.misterblusky9.pym.internal.client;

import net.minecraft.client.particle.Particle;

/**
 * A tick is evaluated in vanilla-sized velocity units, then movement and the
 * resulting velocity are converted back to world units. No particle whitelist:
 * the particle's normal virtual move() is used for all implementations.
 */
public final class ParticleMotionScale {
    private static final ThreadLocal<Active> ACTIVE = new ThreadLocal<>();

    public interface Carrier {
        double pym$getEmissionScale();
        void pym$setEmissionScale(double scale);
        boolean pym$isInitialized();
        void pym$setInitialized(boolean initialized);
        boolean pym$isApplied();
        void pym$setApplied(boolean applied);
    }

    public static double movementScale(Particle particle) {
        Active active = ACTIVE.get();
        return active != null && active.particle == particle ? active.scale : 1.0D;
    }

    public static Scope begin(Particle particle, double scale) {
        Active previous = ACTIVE.get();
        ACTIVE.set(new Active(particle, scale));
        return new Scope(previous);
    }

    private record Active(Particle particle, double scale) {}

    public static final class Scope implements AutoCloseable {
        private final Active previous;
        private Scope(Active previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    private ParticleMotionScale() {}
}

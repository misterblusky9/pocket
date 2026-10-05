package com.misterblusky9.pym.internal.physics;

public interface RepointableConstraint {
    record Motor(
            double target,
            double stiffness,
            double damping,
            boolean hasForceLimit,
            double maxForce
    ) {}

    long pym$nativeHandle();

    long pym$sceneHandle();

    boolean pym$isSceneLive();

    boolean pym$isKnownRemoved();

    void pym$markRemoved();

    void pym$repoint(long nativeHandle);

    void pym$replayMotors();

    void pym$replayContacts();
}

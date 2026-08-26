package com.misterblusky9.pocket.physics;

public interface RepointableConstraint {
    record Motor(
            double target,
            double stiffness,
            double damping,
            boolean hasForceLimit,
            double maxForce
    ) {}

    long pocket$nativeHandle();

    long pocket$sceneHandle();

    boolean pocket$isSceneLive();

    boolean pocket$isKnownRemoved();

    void pocket$markRemoved();

    void pocket$repoint(long nativeHandle);

    void pocket$replayMotors();
}

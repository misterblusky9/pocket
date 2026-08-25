package com.misterblusky9.pocket.physics;

public interface RepointableConstraint {
    long pocket$nativeHandle();

    long pocket$sceneHandle();

    boolean pocket$isSceneLive();

    boolean pocket$isKnownRemoved();

    void pocket$markRemoved();

    void pocket$repoint(long nativeHandle);
}

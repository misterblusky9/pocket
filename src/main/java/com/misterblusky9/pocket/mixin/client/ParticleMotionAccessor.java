package com.misterblusky9.pocket.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleMotionAccessor {
    @Accessor("xd")
    double pocket$getXd();

    @Accessor("xd")
    void pocket$setXd(double value);

    @Accessor("yd")
    double pocket$getYd();

    @Accessor("yd")
    void pocket$setYd(double value);

    @Accessor("zd")
    double pocket$getZd();

    @Accessor("zd")
    void pocket$setZd(double value);

    @Accessor("gravity")
    float pocket$getGravity();

    @Accessor("gravity")
    void pocket$setGravity(float value);

    @Accessor("age")
    int pocket$getAge();

    @Accessor("stoppedByCollision")
    boolean pocket$getStoppedByCollision();

    @Accessor("stoppedByCollision")
    void pocket$setStoppedByCollision(boolean value);
}

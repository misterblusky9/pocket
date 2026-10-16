package com.misterblusky9.pym.internal.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleMotionAccessor {
    @Accessor("xd")
    double pym$getXd();

    @Accessor("xd")
    void pym$setXd(double value);

    @Accessor("yd")
    double pym$getYd();

    @Accessor("yd")
    void pym$setYd(double value);

    @Accessor("zd")
    double pym$getZd();

    @Accessor("zd")
    void pym$setZd(double value);

    @Accessor("gravity")
    float pym$getGravity();

    @Accessor("gravity")
    void pym$setGravity(float value);

    @Accessor("age")
    int pym$getAge();

    @Accessor("x")
    double pym$getX();

    @Accessor("y")
    double pym$getY();

    @Accessor("z")
    double pym$getZ();

    @Accessor("level")
    ClientLevel pym$getLevel();

    @Accessor("stoppedByCollision")
    boolean pym$getStoppedByCollision();

    @Accessor("stoppedByCollision")
    void pym$setStoppedByCollision(boolean value);
}

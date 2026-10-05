package com.misterblusky9.pym.api;

import com.misterblusky9.pym.api.spi.JointConductor;
import com.misterblusky9.pym.api.spi.Participation;
import com.misterblusky9.pym.api.spi.ResizeFollower;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

public final class Extensions {
    static final Extensions INSTANCE = new Extensions();

    private Extensions() {}

    public void register(final ResizePolicy policy) {
        PymExtensions.register(policy);
    }

    public void register(final Participation participation) {
        PymExtensions.register(participation);
    }

    public void register(final JointConductor conductor) {
        PymExtensions.register(conductor);
    }

    public void register(final ResizeFollower follower) {
        PymExtensions.register(follower);
    }

    public void coupling(final ScaleCoupling coupling) {
        PymExtensions.coupling(coupling);
    }

    public void nativeStaticEntity(final Predicate<Entity> predicate) {
        PymExtensions.nativeStaticEntity(predicate);
    }
}

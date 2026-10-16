package com.misterblusky9.pym.api;

import com.misterblusky9.pym.api.spi.JointConductor;
import com.misterblusky9.pym.api.spi.Participation;
import com.misterblusky9.pym.api.spi.ResizeFollower;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Extensions {
    static final Extensions INSTANCE = new Extensions();

    private Extensions() {}

    public void register(ResizePolicy policy) {
        PymExtensions.register(policy);
    }

    public void register(Participation participation) {
        PymExtensions.register(participation);
    }

    public void register(JointConductor conductor) {
        PymExtensions.register(conductor);
    }

    public void register(ResizeFollower follower) {
        PymExtensions.register(follower);
    }

    public void coupling(ScaleCoupling coupling) {
        PymExtensions.coupling(coupling);
    }

    public void massCoupling(ScaleCoupling coupling) {
        PymExtensions.massCoupling(coupling);
    }

    public void links(Function<ServerSubLevel, Collection<UUID>> linked) {
        PymExtensions.links(linked);
    }

    public void nativeStaticEntity(Predicate<Entity> predicate) {
        PymExtensions.nativeStaticEntity(predicate);
    }
}

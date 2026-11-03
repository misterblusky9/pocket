package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

interface CompressionBeamOwner {
    Vec3 muzzle(float partialTick);

    Vec3 aimOrigin();

    Vec3 aimDirection();

    double range();

    double scale();

    @Nullable Entity emitter();

    @Nullable EntityCompressionTargeting.Target entityTarget(double range);

    default void chargedUp() {}
}

package com.misterblusky9.pocket.client;

import net.minecraft.world.phys.Vec3;

public interface PocketLaserBeamColour {
    int pocket$colour();

    void pocket$colour(int colour);

    int pocket$shooter();

    void pocket$shooter(int shooter);

    Vec3 pocket$end();
}

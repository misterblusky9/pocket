package com.misterblusky9.pocket.compat.simulated;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public interface MoonPlungerAttachment {
    boolean pocket$isMoonAttached();
    Vector3dc pocket$moonNormalizedAnchor();
    Vector3d pocket$moonLocalAnchor(ServerLevel level);
    Vec3 pocket$moonWorldAnchor(ServerLevel level);
}

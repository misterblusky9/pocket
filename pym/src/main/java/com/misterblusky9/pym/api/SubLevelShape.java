package com.misterblusky9.pym.api;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public interface SubLevelShape {
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    List<Box> boxes();

    @Nullable
    Vector3d closestPointToRay(Vector3dc origin, Vector3dc direction, double length);
}

package com.misterblusky9.pym.internal.compat.bits_n_tracks;

import org.joml.Vector3d;

public final class UnitNormals {
    public static Vector3d unit(Vector3d result, Vector3d argument) {
        normalize(argument);
        if (result != argument) normalize(result);
        return result;
    }

    private static void normalize(Vector3d vector) {
        if (vector != null && vector.lengthSquared() > 1.0E-12D) vector.normalize();
    }

    private UnitNormals() {}
}

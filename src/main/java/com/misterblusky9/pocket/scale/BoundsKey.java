package com.misterblusky9.pocket.scale;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;

public record BoundsKey(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static BoundsKey of(final BoundingBox3ic box) {
        return new BoundsKey(
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()
        );
    }

    public boolean matches(final BoundingBox3ic box) {
        return box != null
                && this.minX == box.minX() && this.minY == box.minY() && this.minZ == box.minZ()
                && this.maxX == box.maxX() && this.maxY == box.maxY() && this.maxZ == box.maxZ();
    }
}

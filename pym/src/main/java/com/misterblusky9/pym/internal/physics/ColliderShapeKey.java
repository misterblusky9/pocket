package com.misterblusky9.pym.internal.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ColliderShapeKey(List<Face> faces, ColliderMaterial material) {
    public ColliderShapeKey {
        if (material == null) material = ColliderMaterial.DEFAULT;
    }

    public record Face(
            long minX, long minY, long minZ,
            long maxX, long maxY, long maxZ
    ) {
        public double minXd() { return Double.longBitsToDouble(minX); }
        public double minYd() { return Double.longBitsToDouble(minY); }
        public double minZd() { return Double.longBitsToDouble(minZ); }
        public double maxXd() { return Double.longBitsToDouble(maxX); }
        public double maxYd() { return Double.longBitsToDouble(maxY); }
        public double maxZd() { return Double.longBitsToDouble(maxZ); }

        public boolean degenerate() {
            return maxXd() <= minXd() || maxYd() <= minYd() || maxZd() <= minZd();
        }
    }

    public static Face face(
            double scale,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        return new Face(
                bits(minX), bits(minY), bits(minZ),
                bits(maxX), bits(maxY), bits(maxZ));
    }

    public static ColliderShapeKey of(List<Face> faces) {
        return of(faces, ColliderMaterial.DEFAULT);
    }

    public static ColliderShapeKey of(List<Face> faces, ColliderMaterial material) {
        ArrayList<Face> canonical = new ArrayList<>(faces == null ? List.of() : faces);
        canonical.removeIf(Face::degenerate);
        canonical.sort(Comparator
                .comparingLong(Face::minX).thenComparingLong(Face::minY).thenComparingLong(Face::minZ)
                .thenComparingLong(Face::maxX).thenComparingLong(Face::maxY).thenComparingLong(Face::maxZ));
        for (int i = canonical.size() - 1; i > 0; i--) {
            if (canonical.get(i).equals(canonical.get(i - 1))) canonical.remove(i);
        }
        return new ColliderShapeKey(List.copyOf(canonical), material);
    }

    public static ColliderShapeKey of(Face face) {
        if (face == null || face.degenerate()) return new ColliderShapeKey(List.of(), ColliderMaterial.DEFAULT);
        return new ColliderShapeKey(List.of(face), ColliderMaterial.DEFAULT);
    }

    public double volume() {
        double total = 0.0D;
        for (Face face : this.faces) {
            total += (face.maxXd() - face.minXd())
                    * (face.maxYd() - face.minYd())
                    * (face.maxZd() - face.minZd());
        }
        return total;
    }

    private static long bits(double value) {
        float bounded = (float) Math.max(0.0D, Math.min(1.0D, value));
        float canonical = bounded == 0.0F ? 0.0F : bounded;
        return Double.doubleToLongBits((double) canonical);
    }
}

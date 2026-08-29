package com.misterblusky9.pocket.moon;

// Body-space <-> plot-space for the moon's sublevel shim.
// Sable validates every constraint anchor on a ServerSubLevel with plot.contains(anchor),
// in plot block coordinates. The moon's own anchors are centred on its body origin, so
// they have to be carried into the plot before they reach the pipeline.
public record MoonPlotFrame(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public record Anchor(double x, double y, double z) {}

    public MoonPlotFrame {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("inverted plot bounds");
        }
    }

    public static MoonPlotFrame cube(final int minX, final int minY, final int minZ, final int sideBlocks) {
        if (sideBlocks <= 0) throw new IllegalArgumentException("plot side must be positive");
        return new MoonPlotFrame(
                minX, minY, minZ,
                minX + sideBlocks - 1, minY + sideBlocks - 1, minZ + sideBlocks - 1
        );
    }

    // Plot bounds are inclusive block coordinates; the occupied volume runs to max + 1.
    public double sizeX() { return (maxX - minX) + 1.0D; }
    public double sizeY() { return (maxY - minY) + 1.0D; }
    public double sizeZ() { return (maxZ - minZ) + 1.0D; }

    public double centerX() { return minX + sizeX() * 0.5D; }
    public double centerY() { return minY + sizeY() * 0.5D; }
    public double centerZ() { return minZ + sizeZ() * 0.5D; }

    // The body origin sits at the plot centre; this is what the shim reports as its
    // rotation point, the way a real sublevel reports its centre of mass.
    public Anchor origin() {
        return new Anchor(centerX(), centerY(), centerZ());
    }

    public Anchor toPlotAnchor(final double localX, final double localY, final double localZ) {
        return new Anchor(centerX() + localX, centerY() + localY, centerZ() + localZ);
    }

    public Anchor toBodyLocal(final Anchor plotAnchor) {
        return new Anchor(
                plotAnchor.x() - centerX(),
                plotAnchor.y() - centerY(),
                plotAnchor.z() - centerZ()
        );
    }

    public boolean contains(final Anchor plotAnchor) {
        return plotAnchor.x() >= minX && plotAnchor.x() <= maxX + 1.0D
                && plotAnchor.y() >= minY && plotAnchor.y() <= maxY + 1.0D
                && plotAnchor.z() >= minZ && plotAnchor.z() <= maxZ + 1.0D;
    }

    // Anchors are body-space, and body space does not rotate - the pose does.
    // So the plot has to hold the axis-aligned half extent, not the rotated diagonal.
    public double maxHalfExtent() {
        return Math.min(sizeX(), Math.min(sizeY(), sizeZ())) * 0.5D;
    }

    public boolean fits(final double halfExtent) {
        return halfExtent > 0.0D
                && Double.isFinite(halfExtent)
                && halfExtent <= maxHalfExtent();
    }
}

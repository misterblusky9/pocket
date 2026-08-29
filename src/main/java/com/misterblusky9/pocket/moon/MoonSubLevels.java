package com.misterblusky9.pocket.moon;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector2i;

import java.util.Map;
import java.util.WeakHashMap;

// The moon's sublevel shim is deliberately NOT registered with the container.
//
// Registration fires SubLevelPhysicsSystem.onSubLevelAdded, which calls
// pipeline.add(subLevel, pose) -> Rapier3D.createSubLevel(scene, getID(body)). The shim
// reports the moon box's runtime id, so that creates a second native body under an id
// that already exists and corrupts the scene - a hard JVM crash with no Java stack.
// Registration also enrols the shim in every getAllSubLevels() sweep and in Sable's
// occupancy save data.
//
// So the shim is held here and surfaced only through the uuid lookup that Simulated's
// staff handler uses. The moon stays one body: the box.
public final class MoonSubLevels {
    private static final Map<ServerLevel, MoonSubLevel> SHIMS = new WeakHashMap<>();

    public static synchronized MoonSubLevel get(final ServerLevel level) {
        if (level == null) return null;
        final MoonSubLevel shim = SHIMS.get(level);
        if (shim == null) return null;
        if (MoonPhysicsTarget.body(level) == null) {
            SHIMS.remove(level);
            return null;
        }
        return shim;
    }

    public static synchronized MoonSubLevel getOrCreate(final ServerLevel level) {
        final MoonSubLevel existing = get(level);
        if (existing != null) {
            existing.syncFromBody();
            return existing;
        }
        if (level == null || MoonPhysicsTarget.body(level) == null) return null;

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        final Vector2i plot = freePlot(container);
        if (plot == null) return null;

        final MoonSubLevel shim = new MoonSubLevel(level, plot.x, plot.y, new Pose3d());
        shim.setUniqueId(MoonPhysicsTarget.ID);
        shim.centerOnPlot();
        shim.syncFromBody();
        SHIMS.put(level, shim);
        return shim;
    }

    public static synchronized void release(final ServerLevel level) {
        if (level == null) return;
        final MoonSubLevel shim = SHIMS.remove(level);
        if (shim != null) shim.markRemoved();
    }

    public static boolean isMoon(final SubLevel subLevel) {
        return subLevel instanceof MoonSubLevel;
    }

    public static MoonPlotFrame frame(final ServerLevel level, final MoonSubLevel moon) {
        final LevelPlot plot = moon.getPlot();
        final ChunkPos min = plot.getChunkMin();
        final ChunkPos max = plot.getChunkMax();
        return new MoonPlotFrame(
                SectionPos.sectionToBlockCoord(min.x),
                level.getMinBuildHeight(),
                SectionPos.sectionToBlockCoord(min.z),
                SectionPos.sectionToBlockCoord(max.x) + 15,
                level.getMaxBuildHeight() - 1,
                SectionPos.sectionToBlockCoord(max.z) + 15
        );
    }

    // A plot region no real sublevel occupies, in global plot coordinates.
    private static Vector2i freePlot(final ServerSubLevelContainer container) {
        final Vector2i origin = container.getOrigin();
        final int side = 1 << container.getLogSideLength();
        for (int x = side - 1; x >= 0; x--) {
            for (int z = side - 1; z >= 0; z--) {
                if (container.getSubLevel(x, z) != null) continue;
                return new Vector2i(x + origin.x, z + origin.y);
            }
        }
        return null;
    }

    private MoonSubLevels() {}
}

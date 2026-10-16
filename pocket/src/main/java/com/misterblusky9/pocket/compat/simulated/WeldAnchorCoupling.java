package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.spi.ScaleCoupling;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public final class WeldAnchorCoupling implements ScaleCoupling {
    @Override
    public Graph graph(SubLevelContainer container) {
        return Graph.EMPTY;
    }

    @Override
    public Vector3d worldAnchor(SubLevelContainer container, UUID origin) {
        if (container == null || origin == null
                || !(container.getLevel() instanceof ServerLevel level)) return null;

        List<WeldRecord> touching = WeldStore.get(level).touching(origin);
        if (touching.size() != 1) return null;

        if (!(container.getSubLevel(origin) instanceof ServerSubLevel subLevel)
                || subLevel.isRemoved()) return null;

        WeldRecord weld = touching.get(0);
        Vector3d anchor = weld.anchorFor(origin.equals(weld.smallSubLevel()));
        subLevel.logicalPose().transformPosition(anchor);
        return Double.isFinite(anchor.x) && Double.isFinite(anchor.y) && Double.isFinite(anchor.z)
                ? anchor : null;
    }
}

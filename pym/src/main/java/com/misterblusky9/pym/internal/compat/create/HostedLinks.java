package com.misterblusky9.pym.internal.compat.create;

import com.misterblusky9.pym.api.Hosted;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class HostedLinks {
    public static @Nullable Boolean inRange(IRedstoneLinkable from, IRedstoneLinkable to, LevelAccessor accessor) {
        if (from == to || !(accessor instanceof Level level)) return null;
        Vector3d fromPos = JOMLConversion.atCenterOf(from.getLocation());
        Vector3d toPos = JOMLConversion.atCenterOf(to.getLocation());
        SubLevel fromSubLevel = Sable.HELPER.getContaining(level, fromPos);
        SubLevel toSubLevel = Sable.HELPER.getContaining(level, toPos);
        if (!Hosted.isHosted(fromSubLevel) && !Hosted.isHosted(toSubLevel)) return null;
        project(fromSubLevel, fromPos);
        project(toSubLevel, toPos);
        double range = AllConfigs.server().logistics.linkRange.get();
        return fromPos.distanceSquared(toPos) < range * range;
    }

    private static void project(@Nullable SubLevel subLevel, Vector3d position) {
        if (subLevel == null) return;
        Pose3dc worn = Hosted.presented(subLevel);
        (worn != null ? worn : subLevel.logicalPose()).transformPosition(position);
    }

    private HostedLinks() {}
}

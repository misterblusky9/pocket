package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.event.ScaleTickEvent;
import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterRivets;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.network.ScaleNetwork;
import com.misterblusky9.pym.internal.physics.ConstraintRefresh;
import com.misterblusky9.pym.internal.physics.CoupledMass;
import com.misterblusky9.pym.internal.physics.KinematicCollisionSuppression;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;

public final class ScaleTicker {
    public static void tick(ServerSubLevelContainer container) {
        long now = container.getLevel().getGameTime();

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (live(subLevel)) ScaleDrivers.tick(subLevel, now);
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (!live(subLevel)) continue;
            ScaleRecord record = ScaleState.recordOf(subLevel.getUniqueId());
            if (record != null) step(container, pipeline, subLevel, record);
        }

        if (container.getLevel() instanceof ServerLevel level) SubLevelParentage.tick(container, level);
        ConstraintRefresh.refreshStale(container);
        CoupledMass.tick(container);
        NeoForge.EVENT_BUS.post(new ScaleTickEvent(container));
    }

    private static void step(
            ServerSubLevelContainer container,
            PhysicsPipeline pipeline,
            ServerSubLevel subLevel,
            ScaleRecord record
    ) {
        double before = record.current();
        boolean moving = record.moving();
        boolean arrived = false;

        if (moving) {
            double next = record.step();
            record.show(next);
            boolean clearFloor = record.alone() && !CoasterRivets.isRivetSubLevel(subLevel);
            ScaleMotion.apply(container, subLevel, before, next, record.pivot(), clearFloor);
            arrived = !record.moving();
        } else {
            ScaleMotion.setPoseScale(subLevel, record.current());
        }

        boolean changed = !ScaleBounds.same(before, record.current());
        ScaleDriver driver = record.driver();
        if (arrived) record.settle(record.target());

        if (ScaleBounds.same(record.current(), ScaleBounds.FULL)) {
            KinematicCollisionSuppression.ensureRestored(subLevel, pipeline);
        } else {
            KinematicCollisionSuppression.ensureSuppressed(subLevel, pipeline);
        }
        ScalePhysicsTransitions.drive(subLevel, before, record.current(), record.target(),
                changed, arrived, ScaleState.serverBoundsChanged(subLevel));

        if (record.dirty()) ScalePersistence.persist(subLevel);
        if (changed || arrived) ScaleNetwork.sendScale(subLevel, record.current(), record.target());

        if (arrived) {
            NeoForge.EVENT_BUS.post(new SubLevelScaleEvent.Settled(subLevel, record.current()));
            if (driver != null) driver.onTransitionCompleted(subLevel, record.current());
        }
    }

    private static boolean live(ServerSubLevel subLevel) {
        return subLevel != null && !subLevel.isRemoved() && subLevel.getUniqueId() != null
                && !PymExtensions.excludesSubLevel(subLevel);
    }

    private ScaleTicker() {}
}

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
    public static void tick(final ServerSubLevelContainer container) {
        final long now = container.getLevel().getGameTime();

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (live(subLevel)) ScaleDrivers.tick(subLevel, now);
        }

        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (!live(subLevel)) continue;
            final ScaleRecord record = ScaleState.recordOf(subLevel.getUniqueId());
            if (record != null) step(container, pipeline, subLevel, record);
        }

        if (container.getLevel() instanceof final ServerLevel level) SubLevelParentage.tick(container, level);
        ConstraintRefresh.refreshStale(container);
        CoupledMass.tick(container);
        NeoForge.EVENT_BUS.post(new ScaleTickEvent(container));
    }

    private static void step(
            final ServerSubLevelContainer container,
            final PhysicsPipeline pipeline,
            final ServerSubLevel subLevel,
            final ScaleRecord record
    ) {
        final double before = record.current();
        final boolean moving = record.moving();
        boolean arrived = false;

        if (moving) {
            final double next = record.step();
            record.show(next);
            final boolean clearFloor = record.alone() && !CoasterRivets.isRivetSubLevel(subLevel);
            ScaleMotion.apply(container, subLevel, before, next, record.pivot(), clearFloor);
            arrived = !record.moving();
        } else {
            ScaleMotion.setPoseScale(subLevel, record.current());
        }

        final boolean changed = !ScaleBounds.same(before, record.current());
        final ScaleDriver driver = record.driver();
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

    private static boolean live(final ServerSubLevel subLevel) {
        return subLevel != null && !subLevel.isRemoved() && subLevel.getUniqueId() != null
                && !PymExtensions.excludesSubLevel(subLevel);
    }

    private ScaleTicker() {}
}

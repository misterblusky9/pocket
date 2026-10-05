package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.event.PhysicsSceneClosingEvent;
import com.misterblusky9.pym.api.event.ScaleTickEvent;
import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.api.spi.Participation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.UUID;

public final class PocketPymIntegration {
    public static void register() {
        Pym.extensions().register(new PocketParticipation());
        Pym.extensions().register(new CreateSeatResizeFollower());

        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onSettled);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onReleased);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onScaleTick);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onSceneClosing);
    }

    private static void onSettled(final SubLevelScaleEvent.Settled event) {
        CrossScaleWelds.restageWorldWelds(event.subLevel(), event.scale());
    }

    private static void onReleased(final SubLevelScaleEvent.Released event) {
        final ServerSubLevel subLevel = event.subLevel();
        final UUID id = subLevel.getUniqueId();

        CompressionSessions.releaseSubLevel(subLevel);
        if (event.reason() == SubLevelRemovalReason.REMOVED) releaseWelds(subLevel, id);

    }

    private static void releaseWelds(final ServerSubLevel subLevel, final UUID id) {
        if (!(subLevel.getLevel() instanceof final ServerLevel serverLevel)) return;
        final List<WeldRecord> cut = WeldStore.get(serverLevel).removeAllTouching(id);
        if (cut.isEmpty()) return;
        for (final WeldRecord record : cut) WeldRuntime.drop(record.weldId());
        CrossScaleWeldSync.broadcast(serverLevel);
    }

    private static void onScaleTick(final ScaleTickEvent event) {
        if (event.container().getLevel() instanceof final ServerLevel serverLevel) {
            WeldRuntime.tick(serverLevel, event.container());
        }
    }

    private static void onSceneClosing(final PhysicsSceneClosingEvent event) {
        WeldRuntime.forget(event.level());
    }

    private static final class PocketParticipation implements Participation {
        @Override
        public boolean holdsScale(final UUID subLevelId) {
            return CompressionSessions.isHeld(subLevelId);
        }

        @Override
        public boolean exemptsEntity(final Entity entity) {
            return entity instanceof final ItemEntity item
                    && item.getItem().getItem() instanceof PocketCaseItem
                    && PocketCaseItem.isFilled(item.getItem());
        }
    }

    private PocketPymIntegration() {}
}

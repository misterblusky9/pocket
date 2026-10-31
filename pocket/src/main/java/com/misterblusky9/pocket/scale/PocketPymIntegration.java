package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldAnchorCoupling;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldMergeMigration;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.compression.PersonalScale;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.event.PhysicsSceneClosingEvent;
import com.misterblusky9.pym.api.event.ResizeFollowedEvent;
import com.misterblusky9.pym.api.event.ScaleTickEvent;
import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.api.spi.Participation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.UUID;

public final class PocketPymIntegration {
    public static void register() {
        Pym.extensions().coupling(new WeldAnchorCoupling());
        Pym.extensions().register(new PocketParticipation());
        Pym.extensions().register(new PortableCompressorLockPolicy());
        Pym.extensions().register(new CreateSeatResizeFollower());
        Pym.extensions().register(new TrackingResizeFollower());
        Pym.extensions().massCoupling(CrossScaleWelds::snapshot);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerSpawnScale::onJoin);

        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onSettled);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onReleased);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onScaleTick);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onSceneClosing);
        NeoForge.EVENT_BUS.addListener(PocketPymIntegration::onFollowed);
    }

    private static void onFollowed(ResizeFollowedEvent event) {
        if (event.entity() instanceof ServerPlayer player) PersonalScale.remember(player, event.scale());
    }

    private static void onSettled(SubLevelScaleEvent.Settled event) {
        CrossScaleWelds.restageWelds(event.subLevel(), event.scale());
    }

    private static void onReleased(SubLevelScaleEvent.Released event) {
        ServerSubLevel subLevel = event.subLevel();
        UUID id = subLevel.getUniqueId();

        CrossScaleWelds.invalidate(subLevel.getLevel());
        CompressionSessions.releaseSubLevel(subLevel);
        if (event.reason() == SubLevelRemovalReason.REMOVED) releaseWelds(subLevel, id);

    }

    private static void releaseWelds(ServerSubLevel subLevel, UUID id) {
        if (!(subLevel.getLevel() instanceof ServerLevel serverLevel)) return;
        if (WeldMergeMigration.isMoving(serverLevel, id)) return;
        List<WeldRecord> cut = WeldStore.get(serverLevel).removeAllTouching(id);
        if (cut.isEmpty()) return;
        for (WeldRecord record : cut) WeldRuntime.drop(record.weldId());
        CrossScaleWeldSync.broadcast(serverLevel);
    }

    private static void onScaleTick(ScaleTickEvent event) {
        if (event.container().getLevel() instanceof ServerLevel serverLevel) {
            WeldRuntime.tick(serverLevel, event.container());
        }
    }

    private static void onSceneClosing(PhysicsSceneClosingEvent event) {
        WeldRuntime.forget(event.level());
    }

    private static final class PocketParticipation implements Participation {
        @Override
        public boolean holdsScale(UUID subLevelId) {
            return CompressionSessions.isHeld(subLevelId);
        }
    }

    private PocketPymIntegration() {}
}

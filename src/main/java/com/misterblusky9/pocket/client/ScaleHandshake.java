package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.network.ScaleRequestPayload;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class ScaleHandshake {
    private static final Set<UUID> ASKED = new HashSet<>();

    private static final Set<UUID> PRESENT = new HashSet<>();
    private static int retryTicker;

    public static void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null || minecraft.getConnection() == null) {
            clear();
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            clear();
            return;
        }

        PRESENT.clear();
        retryTicker = (retryTicker + 1) % 20;
        final boolean retryUnknown = retryTicker == 0;
        for (final SubLevel subLevel : container.getAllSubLevels()) {
            final UUID id = subLevel.getUniqueId();
            if (id == null) continue;

            PRESENT.add(id);
            final boolean firstSight = ASKED.add(id);
            if (firstSight || (retryUnknown && !ScaleState.hasClientSnapshot(id))) {
                PacketDistributor.sendToServer(new ScaleRequestPayload(id));
            }
        }

        final Iterator<UUID> iterator = ASKED.iterator();
        while (iterator.hasNext()) {
            final UUID id = iterator.next();
            if (PRESENT.contains(id)) continue;
            iterator.remove();
            ScaleState.forgetClientSnapshot(id);
        }
    }

    public static void clear() {
        ASKED.clear();
        PRESENT.clear();
        retryTicker = 0;
        ScaleState.clearClientSnapshots();
    }

    private ScaleHandshake() {}
}

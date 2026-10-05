package com.misterblusky9.pym.api.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public final class PhysicsSceneClosingEvent extends Event {
    private final ServerLevel level;

    public PhysicsSceneClosingEvent(final ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return this.level;
    }
}

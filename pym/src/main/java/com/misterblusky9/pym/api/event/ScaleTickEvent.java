package com.misterblusky9.pym.api.event;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.neoforged.bus.api.Event;

public final class ScaleTickEvent extends Event {
    private final ServerSubLevelContainer container;

    public ScaleTickEvent(final ServerSubLevelContainer container) {
        this.container = container;
    }

    public ServerSubLevelContainer container() {
        return this.container;
    }
}

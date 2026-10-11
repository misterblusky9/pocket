package com.misterblusky9.pym.api.event;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

public final class ResizeFollowedEvent extends Event {
    private final Entity entity;
    private final double scale;

    public ResizeFollowedEvent(final Entity entity, final double scale) {
        this.entity = entity;
        this.scale = scale;
    }

    public Entity entity() {
        return this.entity;
    }

    public double scale() {
        return this.scale;
    }
}

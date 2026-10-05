package com.misterblusky9.pym.api.event;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.neoforged.bus.api.Event;

public abstract class SubLevelScaleEvent extends Event {
    private final ServerSubLevel subLevel;

    protected SubLevelScaleEvent(final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public ServerSubLevel subLevel() {
        return this.subLevel;
    }

    public static final class Settled extends SubLevelScaleEvent {
        private final double scale;

        public Settled(final ServerSubLevel subLevel, final double scale) {
            super(subLevel);
            this.scale = scale;
        }

        public double scale() {
            return this.scale;
        }
    }

    public static final class Released extends SubLevelScaleEvent {
        private final SubLevelRemovalReason reason;

        public Released(final ServerSubLevel subLevel, final SubLevelRemovalReason reason) {
            super(subLevel);
            this.reason = reason;
        }

        public SubLevelRemovalReason reason() {
            return this.reason;
        }
    }
}

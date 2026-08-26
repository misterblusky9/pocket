package com.misterblusky9.pocket.item;

public enum CompressionGunTargetingMode {
    SUBLEVEL(0, "Sublevel"),
    CONNECTED_SUBLEVELS(1, "Connected Sublevels"),
    SELF(2, "Self (Pehkui)");

    private final int id;
    private final String label;

    CompressionGunTargetingMode(final int id, final String label) {
        this.id = id;
        this.label = label;
    }

    public int id() {
        return this.id;
    }

    public String label() {
        return this.label;
    }

    public static CompressionGunTargetingMode fromId(final int id) {
        for (final CompressionGunTargetingMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return SUBLEVEL;
    }
}

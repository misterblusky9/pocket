package com.misterblusky9.pocket.debug;

public enum ColliderDebugLayers {
    COLLIDER("native collider"),

    IDEAL("ideal block shape"),

    ENTITY("entity collision"),

    ALL("all layers");

    private static volatile ColliderDebugLayers current = COLLIDER;

    private final String label;

    ColliderDebugLayers(final String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public static ColliderDebugLayers current() {
        return current;
    }

    public static ColliderDebugLayers cycle() {
        final ColliderDebugLayers[] all = values();
        current = all[(current.ordinal() + 1) % all.length];
        return current;
    }

    public boolean showsCollider() {
        return this == COLLIDER || this == ALL;
    }

    public boolean showsIdeal() {
        return this == IDEAL || this == ALL;
    }

    public boolean showsEntity() {
        return this == ENTITY || this == ALL;
    }
}

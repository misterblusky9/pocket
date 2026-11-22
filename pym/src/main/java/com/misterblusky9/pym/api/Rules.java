package com.misterblusky9.pym.api;

public record Rules(boolean interact, boolean split, boolean collide) {
    public static final Rules EDITABLE = new Rules(true, true, true);
    public static final Rules COSMETIC = new Rules(false, false, false);

    public Rules interact(boolean interact) {
        return new Rules(interact, split, collide);
    }

    public Rules split(boolean split) {
        return new Rules(interact, split, collide);
    }

    public Rules collide(boolean collide) {
        return new Rules(interact, split, collide);
    }
}

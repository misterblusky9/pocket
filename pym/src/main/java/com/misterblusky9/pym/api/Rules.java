package com.misterblusky9.pym.api;

public record Rules(boolean interact, boolean split) {
    public static final Rules EDITABLE = new Rules(true, true);
    public static final Rules COSMETIC = new Rules(false, false);

    public Rules interact(boolean interact) {
        return new Rules(interact, split);
    }

    public Rules split(boolean split) {
        return new Rules(interact, split);
    }
}

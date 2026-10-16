package com.misterblusky9.pocket.item;

/** Resource policy: never charge levitite and durability for the same activation. */
public final class LevititeUseCost {
    private LevititeUseCost() {}

    public record Charge(int levitite, int durability) {}

    public static Charge plan(int storedMb, int levititePerUseMb, int durabilityPerUse) {
        if (levititePerUseMb <= 0) return new Charge(0, 0);
        if (storedMb > 0) {
            // Consume the last partial dose as a final fuelled use rather than wear a filled tool.
            return new Charge(Math.min(storedMb, levititePerUseMb), 0);
        }
        return new Charge(0, Math.max(0, durabilityPerUse));
    }
}

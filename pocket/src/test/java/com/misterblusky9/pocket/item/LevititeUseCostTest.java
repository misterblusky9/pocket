package com.misterblusky9.pocket.item;

public final class LevititeUseCostTest {
    private static int tested;

    private static void expect(int stored, int fuelCost, int wearCost, int fuelExpected, int wearExpected) {
        LevititeUseCost.Charge charge = LevititeUseCost.plan(stored, fuelCost, wearCost);
        if (charge.levitite() != fuelExpected || charge.durability() != wearExpected) {
            throw new AssertionError("Stored=" + stored + ": expected " + fuelExpected + " mB and "
                    + wearExpected + " wear, got " + charge);
        }
        if (charge.levitite() > 0 && charge.durability() > 0) {
            throw new AssertionError("Two fuel sources charged on the same activation: " + charge);
        }
        tested++;
    }

    public static void main(String[] args) {
        expect(1000, 100, 4, 100, 0);
        expect(100, 100, 4, 100, 0);
        expect(99, 100, 4, 99, 0);
        expect(50, 100, 4, 50, 0);
        expect(1, 100, 4, 1, 0);
        expect(0, 100, 4, 0, 4);
        expect(-1, 100, 4, 0, 4);
        expect(0, 100, 0, 0, 0);
        expect(25, 0, 4, 0, 0);
        System.out.println("PASS: " + tested + " levitite-first exclusive charge tests");
    }
}

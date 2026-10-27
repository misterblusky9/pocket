package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CompressionGunTank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class BeamSession {
    public static final int HOLD_GRACE_TICKS = 3;
    public static final int PULSE_LEAD_TICKS = 10;

    public record Pace(int baseTicks, float growth, int finalStepExtra) {
        public static final Pace HELD_BEAM = new Pace(14, 0.85F, 22);
        public static final Pace STATIC_COMPRESSOR = new Pace(40, 0.85F, 40);

        public int delay(int completedSteps, boolean finalStep) {
            int base = Math.round(this.baseTicks * (1.0F + completedSteps * this.growth));
            return finalStep ? base + this.finalStepExtra : base;
        }
    }

    public record Tick(boolean pulse, boolean step) {}

    private final UUID holder;
    private final InteractionHand hand;
    private final int acquireTicks;
    private final float levititePerTick;

    private long lastHeldTick;
    private int age;
    private boolean sealed;
    private int sinceStep;
    private int steps;
    private boolean pulsed;
    private float levititeDebt;

    public BeamSession(
            UUID holder,
            InteractionHand hand,
            int acquireTicks,
            float levititePerTick,
            long now
    ) {
        this.holder = holder;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.acquireTicks = Math.max(0, acquireTicks);
        this.levititePerTick = Math.max(0.0F, levititePerTick);
        this.lastHeldTick = now;
        if (this.acquireTicks == 0) seal();
    }

    public UUID holder() { return this.holder; }
    public boolean heldBy(ServerPlayer player) { return player != null && this.holder.equals(player.getUUID()); }
    public int acquireTicks() { return this.acquireTicks; }
    public int age() { return this.age; }
    public boolean sealed() { return this.sealed; }
    public int steps() { return this.steps; }

    public void hold(long now) {
        this.lastHeldTick = now;
    }

    public boolean expired(long now) {
        return now - this.lastHeldTick > HOLD_GRACE_TICKS;
    }

    public boolean acquire(ServerPlayer player) {
        if (this.sealed) return true;
        this.age++;
        if (!drain(player)) return false;
        if (this.age >= this.acquireTicks) seal();
        return true;
    }

    public boolean drain(ServerPlayer player) {
        if (this.levititePerTick <= 0.0F) return true;
        this.levititeDebt += this.levititePerTick;
        int whole = (int) this.levititeDebt;
        if (whole <= 0) return true;
        this.levititeDebt -= whole;

        ItemStack gun = player.getItemInHand(this.hand);
        if (!(gun.getItem() instanceof CompressionGunItem)) return false;
        int drained = CompressionGunTank.drain(gun, whole);
        return drained == whole || CompressionGunTank.hasDurability(gun);
    }

    public Tick advance(Pace pace, boolean finalStep) {
        this.sinceStep++;
        int delay = pace.delay(this.steps, finalStep);
        boolean pulse = !this.pulsed && this.sinceStep >= Math.max(0, delay - PULSE_LEAD_TICKS);
        if (pulse) this.pulsed = true;
        return new Tick(pulse, this.sinceStep >= delay);
    }

    public void stepped() {
        this.sinceStep = 0;
        this.pulsed = false;
        this.steps++;
    }

    private void seal() {
        this.sealed = true;
        this.sinceStep = Integer.MAX_VALUE / 2;
    }
}

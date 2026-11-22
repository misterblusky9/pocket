package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ColliderMaterial(double friction, double restitution, boolean callback) {
    public static final ColliderMaterial DEFAULT = new ColliderMaterial(1.0D, 0.0D, false);
    public static final short DEFAULT_ID = 0;

    private static final Map<ColliderMaterial, Short> IDS = new HashMap<>(Map.of(DEFAULT, DEFAULT_ID));
    private static final List<ColliderMaterial> BY_ID = new ArrayList<>(List.of(DEFAULT));
    private static volatile boolean[] CALLBACK_IDS = new boolean[1];

    public ColliderMaterial(double friction, double restitution) {
        this(friction, restitution, false);
    }

    public static short id(BlockState state) {
        if (state == null) return DEFAULT_ID;
        double friction = PhysicsBlockPropertyHelper.getFriction(state);
        double restitution = PhysicsBlockPropertyHelper.getRestitution(state);
        boolean callback = BlockWithSubLevelCollisionCallback.hasCallback(state);
        if (friction == DEFAULT.friction && restitution == DEFAULT.restitution && !callback) return DEFAULT_ID;
        return intern(new ColliderMaterial(friction, restitution, callback));
    }

    public static boolean special(BlockState state) {
        return id(state) != DEFAULT_ID;
    }

    public static synchronized ColliderMaterial byId(short id) {
        return id > 0 && id < BY_ID.size() ? BY_ID.get(id) : DEFAULT;
    }

    public static boolean hasCallback(short id) {
        boolean[] callbacks = CALLBACK_IDS;
        return id > 0 && id < callbacks.length && callbacks[id];
    }

    public static short withCallback(short id) {
        if (hasCallback(id)) return id;
        ColliderMaterial material = byId(id);
        return intern(new ColliderMaterial(material.friction, material.restitution, true));
    }

    private static synchronized short intern(ColliderMaterial material) {
        Short known = IDS.get(material);
        if (known != null) return known;
        if (BY_ID.size() > Short.MAX_VALUE) return DEFAULT_ID;
        short id = (short) BY_ID.size();
        BY_ID.add(material);
        IDS.put(material, id);
        boolean[] callbacks = Arrays.copyOf(CALLBACK_IDS, BY_ID.size());
        callbacks[id] = material.callback;
        CALLBACK_IDS = callbacks;
        return id;
    }

    public static final class Tally {
        private short first = -1;
        private double firstWeight;
        private Map<Short, Double> mixed;
        private boolean callback;

        public void add(short id, double weight) {
            if (!(weight > 0.0D)) return;
            if (hasCallback(id)) this.callback = true;
            if (this.mixed != null) {
                this.mixed.merge(id, weight, Double::sum);
                return;
            }
            if (this.first < 0 || this.first == id) {
                this.first = id;
                this.firstWeight += weight;
                return;
            }
            this.mixed = new HashMap<>();
            this.mixed.put(this.first, this.firstWeight);
            this.mixed.put(id, weight);
        }

        public boolean isEmpty() {
            return this.first < 0;
        }

        public boolean mixed() {
            return this.mixed != null;
        }

        public boolean callback() {
            return this.callback;
        }

        public short winner() {
            short best = materialWinner();
            return this.callback ? withCallback(best) : best;
        }

        private short materialWinner() {
            if (this.mixed == null) return this.first < 0 ? DEFAULT_ID : this.first;
            short best = DEFAULT_ID;
            double bestWeight = this.mixed.getOrDefault(DEFAULT_ID, 0.0D);
            for (Map.Entry<Short, Double> entry : this.mixed.entrySet()) {
                short id = entry.getKey();
                double weight = entry.getValue();
                if (weight > bestWeight || (weight == bestWeight && best != DEFAULT_ID && id < best)) {
                    best = id;
                    bestWeight = weight;
                }
            }
            return best;
        }
    }
}

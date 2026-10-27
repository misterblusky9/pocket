package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ColliderMaterial(double friction, double restitution) {
    public static final ColliderMaterial DEFAULT = new ColliderMaterial(1.0D, 0.0D);
    public static final short DEFAULT_ID = 0;

    private static final Map<ColliderMaterial, Short> IDS = new HashMap<>(Map.of(DEFAULT, DEFAULT_ID));
    private static final List<ColliderMaterial> BY_ID = new ArrayList<>(List.of(DEFAULT));

    public static short id(BlockState state) {
        if (state == null) return DEFAULT_ID;
        double friction = PhysicsBlockPropertyHelper.getFriction(state);
        double restitution = PhysicsBlockPropertyHelper.getRestitution(state);
        if (friction == DEFAULT.friction && restitution == DEFAULT.restitution) return DEFAULT_ID;
        return intern(new ColliderMaterial(friction, restitution));
    }

    public static boolean special(BlockState state) {
        return id(state) != DEFAULT_ID;
    }

    public static synchronized ColliderMaterial byId(short id) {
        return id > 0 && id < BY_ID.size() ? BY_ID.get(id) : DEFAULT;
    }

    private static synchronized short intern(ColliderMaterial material) {
        Short known = IDS.get(material);
        if (known != null) return known;
        if (BY_ID.size() > Short.MAX_VALUE) return DEFAULT_ID;
        short id = (short) BY_ID.size();
        BY_ID.add(material);
        IDS.put(material, id);
        return id;
    }

    public static final class Tally {
        private short first = -1;
        private double firstWeight;
        private Map<Short, Double> mixed;

        public void add(short id, double weight) {
            if (!(weight > 0.0D)) return;
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

        public short winner() {
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

package com.misterblusky9.pocket.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class HatPlateToggle extends ScrollOptionBehaviour<HatPlateToggle.Shown> {
    public static final BehaviourType<HatPlateToggle> HELMET = new BehaviourType<>("hat_plate_helmet");
    public static final BehaviourType<HatPlateToggle> FIRST_PERSON = new BehaviourType<>("hat_plate_first_person");

    private final BehaviourType<HatPlateToggle> type;
    private final int netId;
    private final BooleanSupplier shown;

    public HatPlateToggle(BehaviourType<HatPlateToggle> type, int netId, Component label, HatStandBlockEntity stand,
                          ValueBoxTransform slot, BooleanSupplier shown, Consumer<Boolean> show) {
        super(Shown.class, label, stand, slot);
        this.type = type;
        this.netId = netId;
        this.shown = shown;
        withCallback(value -> show.accept(value == Shown.ON.ordinal()));
        onlyActiveWhen(() -> !stand.getActivator().isEmpty());
    }

    @Override
    public void tick() {
        super.tick();
        value = current();
    }

    @Override
    public void setValue(int value) {
        this.value = current();
        super.setValue(value);
    }

    private int current() {
        return (shown.getAsBoolean() ? Shown.ON : Shown.OFF).ordinal();
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public BehaviourType<?> getType() {
        return type;
    }

    @Override
    public int netId() {
        return netId;
    }

    public enum Shown implements INamedIconOptions {
        OFF(AllIcons.I_DISABLE),
        ON(AllIcons.I_CONFIRM);

        private final AllIcons icon;

        Shown(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return name().equals("ON") ? "pocket.hat_stand.on" : "pocket.hat_stand.off";
        }
    }
}

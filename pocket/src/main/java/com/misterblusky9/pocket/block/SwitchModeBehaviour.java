package com.misterblusky9.pocket.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class SwitchModeBehaviour extends ScrollOptionBehaviour<SwitchMode> {
    public SwitchModeBehaviour(
            final Component label,
            final SmartBlockEntity blockEntity,
            final ValueBoxTransform slot
    ) {
        super(SwitchMode.class, label, blockEntity, slot);
    }

    @Override
    public void read(
            final CompoundTag nbt,
            final HolderLookup.Provider registries,
            final boolean clientPacket
    ) {
        super.read(nbt, registries, clientPacket);
        value = Mth.clamp(value, 0, SwitchMode.values().length - 1);
    }
}

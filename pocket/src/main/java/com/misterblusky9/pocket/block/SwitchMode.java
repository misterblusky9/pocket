package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.client.PocketIcon;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

public enum SwitchMode implements INamedIconOptions {
    IMPULSE(PocketIcon.SWITCH_IMPULSE, "pocket.switch.control_mode.impulse"),
    TOGGLE(PocketIcon.SWITCH_TOGGLE, "pocket.switch.control_mode.toggle"),
    ANALOG(AllIcons.I_MOVE_GAUGE, "pocket.switch.control_mode.analog");

    private final AllIcons icon;
    private final String translationKey;

    SwitchMode(final AllIcons icon, final String translationKey) {
        this.icon = icon;
        this.translationKey = translationKey;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}

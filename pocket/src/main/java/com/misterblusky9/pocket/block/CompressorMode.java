package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.client.PocketIcon;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

public enum CompressorMode implements INamedIconOptions {
    SHRINK(PocketIcon.SHRINK, CompressionStage.SIXTEENTH),
    GROW(PocketIcon.GROW, CompressionStage.NORMAL);

    private final AllIcons icon;
    private final CompressionStage target;

    CompressorMode(final AllIcons icon, final CompressionStage target) {
        this.icon = icon;
        this.target = target;
    }

    public CompressionStage target() {
        return this.target;
    }

    public boolean isGrowing() {
        return this == GROW;
    }

    @Override
    public AllIcons getIcon() {
        return this.icon;
    }

    @Override
    public String getTranslationKey() {
        return "pocket.static_subspace_compressor.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
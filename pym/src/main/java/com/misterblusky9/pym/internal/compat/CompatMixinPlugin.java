package com.misterblusky9.pym.internal.compat;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> EXACT_VERSIONS = Map.of(
            "com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin.CoasterCartPlotScanOptimizationMixin", "0.1.5");

    private boolean present;
    private String version;

    @Override
    public void onLoad(String mixinPackage) {
        String[] parts = mixinPackage.split("\\.");
        String modId = parts.length >= 2 ? parts[parts.length - 2] : "";
        LoadingModList mods = LoadingModList.get();
        this.present = mods != null && mods.getModFileById(modId) != null;
        if (!this.present) return;
        for (ModInfo mod : mods.getMods()) {
            if (mod.getModId().equals(modId)) {
                this.version = mod.getVersion().toString();
                break;
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!this.present) return false;
        String required = EXACT_VERSIONS.get(mixinClassName);
        return required == null || required.equals(this.version);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

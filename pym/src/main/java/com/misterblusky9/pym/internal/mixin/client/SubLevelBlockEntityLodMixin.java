package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.RenderFrame;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class SubLevelBlockEntityLodMixin {
    @ModifyVariable(method = "renderSectionLayer", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private Iterable<ClientSubLevel> pym$cullOffscreenSubLevels(
            final Iterable<ClientSubLevel> subLevels
    ) {
        return pym$filter(subLevels, RenderFrame::mayBeVisible);
    }

    @ModifyVariable(method = "renderBlockEntities", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private Iterable<ClientSubLevel> pym$cullOffscreenBlockEntities(
            final Iterable<ClientSubLevel> subLevels
    ) {
        return pym$filter(subLevels, RenderFrame::mayBeVisible);
    }

    @Unique
    private static Iterable<ClientSubLevel> pym$filter(
            final Iterable<ClientSubLevel> subLevels,
            final Predicate<ClientSubLevel> keep
    ) {
        if (subLevels == null) return null;

        final List<ClientSubLevel> retained = new ArrayList<>();
        boolean skippedAny = false;
        for (final ClientSubLevel subLevel : subLevels) {
            if (keep.test(subLevel)) retained.add(subLevel);
            else skippedAny = true;
        }

        return skippedAny ? retained : subLevels;
    }
}

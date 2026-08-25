package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketClientFrame;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class SubLevelBlockEntityLodMixin {
    @Inject(method = "renderBlockEntities", at = @At("HEAD"), remap = false)
    private void pocket$enterSubLevelBlockEntityPass(final CallbackInfo ci) {
        PocketClientFrame.beginSubLevelBlockEntityPass();
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"), remap = false)
    private void pocket$leaveSubLevelBlockEntityPass(final CallbackInfo ci) {
        PocketClientFrame.endSubLevelBlockEntityPass();
    }

    @ModifyVariable(method = "renderSectionLayer", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private Iterable<ClientSubLevel> pocket$cullOffscreenSubLevels(
            final Iterable<ClientSubLevel> subLevels
    ) {
        return pocket$filter(subLevels, PocketClientFrame::isPotentiallyVisible);
    }

    @ModifyVariable(method = "renderBlockEntities", at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private Iterable<ClientSubLevel> pocket$cullOffscreenBlockEntities(
            final Iterable<ClientSubLevel> subLevels
    ) {
        return pocket$filter(subLevels, PocketClientFrame::isPotentiallyVisible);
    }

    @Unique
    private static Iterable<ClientSubLevel> pocket$filter(
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

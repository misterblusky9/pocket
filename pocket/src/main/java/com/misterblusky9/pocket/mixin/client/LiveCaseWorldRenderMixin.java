package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.item.LiveCaseHolds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class LiveCaseWorldRenderMixin {
    @ModifyVariable(
            method = {"renderSectionLayer", "renderAfterSections", "renderBlockEntities"},
            at = @At("HEAD"),
            argsOnly = true,
            remap = false
    )
    private Iterable<ClientSubLevel> pocket$hideHeldSubLevels(final Iterable<ClientSubLevel> sublevels) {
        if (!LiveCaseHolds.anyHeldOnClient()) return sublevels;
        final List<ClientSubLevel> shown = new ArrayList<>();
        for (final ClientSubLevel subLevel : sublevels) {
            if (!LiveCaseHolds.heldOnClient(subLevel.getUniqueId())) shown.add(subLevel);
        }
        return shown;
    }
}

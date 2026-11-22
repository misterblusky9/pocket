package com.misterblusky9.pocket.compat.vista.mixin;

import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ViewFinderController.class, remap = false)
public interface ViewFinderControllerAccessor {
    @Accessor(value = "viewFinder", remap = false)
    static ViewFinderBlockEntity pocket$viewFinder() {
        throw new AssertionError();
    }
}

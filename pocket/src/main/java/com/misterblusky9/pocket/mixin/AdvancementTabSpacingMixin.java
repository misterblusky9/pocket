package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.TreeNodePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TreeNodePosition.class)
public abstract class AdvancementTabSpacingMixin {
    @Unique
    private static final float POCKET$COLUMN_SPACING = 1.5F;

    @Inject(method = "run", at = @At("TAIL"))
    private static void pocket$widenColumns(final AdvancementNode root, final CallbackInfo ci) {
        if (!root.holder().id().getNamespace().equals(PocketSized.MOD_ID)) return;
        pocket$spread(root);
    }

    @Unique
    private static void pocket$spread(final AdvancementNode node) {
        node.advancement().display().ifPresent(display ->
                display.setLocation(display.getX() * POCKET$COLUMN_SPACING, display.getY()));
        for (final AdvancementNode child : node.children()) pocket$spread(child);
    }
}

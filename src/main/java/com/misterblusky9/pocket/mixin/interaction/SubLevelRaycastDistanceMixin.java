package com.misterblusky9.pocket.mixin.interaction;

import com.misterblusky9.pocket.interaction.ScaleAwareBlockClip;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = BlockGetter.class, priority = 1200)
public interface SubLevelRaycastDistanceMixin {
    @Overwrite
    default BlockHitResult clip(final ClipContext context) {
        return ScaleAwareBlockClip.clip((BlockGetter) this, context);
    }
}

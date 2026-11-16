package com.misterblusky9.pym.internal.mixin.client;

import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public interface VanillaChunkedSubLevelRenderDataAccessor {
    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher pym$getSectionRenderDispatcher();
}

package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class PocketRenderTypes extends RenderType {
    public static final RenderType LOCK_TAG = create(
            "pocket:lock_tag",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setTextureState(new TextureStateShard(
                            ResourceLocation.fromNamespaceAndPath(
                                    "simulated", "textures/gui/lock.png"),
                            false, false))
                    .createCompositeState(true));

    private PocketRenderTypes(
            final String name,
            final VertexFormat format,
            final VertexFormat.Mode mode,
            final int bufferSize,
            final boolean affectsCrumbling,
            final boolean sortOnUpload,
            final Runnable setupState,
            final Runnable clearState
    ) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException();
    }
}

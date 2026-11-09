package com.misterblusky9.pym.internal.client;

import com.misterblusky9.pym.internal.PymMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public final class PortraitGlint extends RenderType {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static ShaderInstance shader;

    public static RenderType of(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, glint -> create("pym_portrait_glint", DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(glint, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(GLINT_TEXTURING)
                        .createCompositeState(false)));
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(PymMod.MOD_ID, "portrait_glint"), DefaultVertexFormat.BLOCK),
                    loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static boolean ready() {
        return shader != null;
    }

    private PortraitGlint(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                          boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}

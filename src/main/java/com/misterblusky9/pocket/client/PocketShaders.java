package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;

public final class PocketShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance compressionField;
    private PocketShaders() {}

    public static ShaderInstance compressionField() {
        return compressionField;
    }

    public static void register(final RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "compression_field"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> compressionField = shader
            );
        } catch (final IOException exception) {
            LOGGER.error("Could not load shaders", exception);
        }
    }
}

package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PocketKeys {
    public static final KeyMapping WELD_ROTATE = new KeyMapping(
            "key.pocket.weld_rotate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.pocket"
    );

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(WELD_ROTATE);
    }

    private PocketKeys() {}
}

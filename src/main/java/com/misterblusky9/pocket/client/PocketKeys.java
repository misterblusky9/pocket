package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PocketKeys {
    public static final KeyMapping ROTATE = new KeyMapping(
            "key.pocket.tweezer_rotate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "key.categories.pocket"
    );

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(ROTATE);
    }

    public static boolean rotateHeld() {
        return ROTATE.isDown();
    }

    private PocketKeys() {}
}

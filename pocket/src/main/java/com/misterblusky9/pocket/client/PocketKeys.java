package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class PocketKeys {
    private static final String CATEGORY = "key.categories.pocket";
    private static final List<KeyMapping> ALL = new ArrayList<>();

    public static final KeyMapping WELD_ROTATE = key("weld_rotate", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_R);
    public static final KeyMapping MAGNIFIER_TOGGLE = key("magnifier_toggle", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_V);
    public static final KeyMapping PERSONAL_COMPRESSOR = key("personal_compressor", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping COMPRESSION_CANNON_TOGGLE = key("compression_cannon_toggle", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_LEFT_ALT);

    private static KeyMapping key(String name, IKeyConflictContext context, int defaultKey) {
        KeyMapping mapping = new KeyMapping(
                "key.pocket." + name,
                context,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CATEGORY
        );
        ALL.add(mapping);
        return mapping;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
    }

    private PocketKeys() {}
}

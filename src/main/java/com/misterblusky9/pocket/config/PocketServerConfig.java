package com.misterblusky9.pocket.config;

import com.misterblusky9.pocket.compression.CompressionBlacklist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PocketServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NO_SHRINK_BLOCKS;
    private static volatile Set<Block> noShrinkBlocks = Set.of();

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("compression");
        NO_SHRINK_BLOCKS = builder
                .comment(
                        "Additional blocks treated as if they are in #pocket:noshrink.",
                        "Use full registry IDs. Datapacks may still add blocks to the tag normally.",
                        "Example:",
                        "noShrinkBlocks = [\"create:steam_engine\"]"
                )
                .defineListAllowEmpty(
                        "noShrinkBlocks",
                        List.<String>of(),
                        PocketServerConfig::isValidBlockId
                );
        builder.pop();

        SPEC = builder.build();
    }

    public static boolean isNoShrinkBlock(final Block block) {
        return block != null && noShrinkBlocks.contains(block);
    }

    public static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        rebuildNoShrinkBlocks();
    }

    public static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        rebuildNoShrinkBlocks();
    }

    private static boolean isValidBlockId(final Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    private static void rebuildNoShrinkBlocks() {
        final Set<Block> blocks = new HashSet<>();

        for (final String rawId : NO_SHRINK_BLOCKS.get()) {
            final ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) continue;
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
        }

        noShrinkBlocks = Set.copyOf(blocks);
        CompressionBlacklist.invalidateAll();
    }

    private PocketServerConfig() {}
}

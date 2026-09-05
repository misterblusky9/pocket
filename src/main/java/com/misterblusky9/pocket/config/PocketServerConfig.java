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
    private static final ModConfigSpec.BooleanValue SCALE_PLAYER_IN_SHRUNKEN_SEAT;

    private static volatile Set<Block> noShrinkBlocks = Set.of();
    private static volatile boolean scalePlayerInShrunkenSeat = true;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("scaling");
        SCALE_PLAYER_IN_SHRUNKEN_SEAT = builder
                .comment(
                        "Scale the player while they ride a seat inside a shrunken sublevel.",
                        "When false Pocket Sized leaves the player full-size, keeps Create seat",
                        "placement unchanged, and removes only sublevel scale from Sable's rider",
                        "eye offset so first-person eye height stays at the player's normal scale.",
                        "Sublevel rotation is preserved. Pehkui scaling from other mods is untouched."
                )
                .define("scalePlayerInShrunkenSeat", true);
        builder.pop();

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

    public static boolean scalePlayerInShrunkenSeat() {
        return scalePlayerInShrunkenSeat;
    }

    public static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    private static void refresh() {
        scalePlayerInShrunkenSeat = SCALE_PLAYER_IN_SHRUNKEN_SEAT.get();
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

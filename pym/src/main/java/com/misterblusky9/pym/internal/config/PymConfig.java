package com.misterblusky9.pym.internal.config;

import com.misterblusky9.pym.api.client.RenderDetail;
import com.misterblusky9.pym.internal.scale.PlotScan;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PymConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NO_SHRINK_BLOCKS;
    private static final ModConfigSpec.IntValue SHRUNK_BLOCK_LIMIT;
    private static final ModConfigSpec.BooleanValue RESIZE_ENTITIES_WITH_SUBLEVELS;
    private static final ModConfigSpec.BooleanValue SIMURAIL_SUPPORT;
    private static final ModConfigSpec.DoubleValue DETAIL_CUTOFF;

    private static volatile boolean resizeEntitiesWithSublevels = true;
    private static volatile boolean simurailSupport = false;
    private static volatile double detailCutoff = RenderDetail.DEFAULT_RATIO;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("shrinking");
        NO_SHRINK_BLOCKS = builder
                .comment(
                        "Blocks that stop a sublevel from shrinking below 1x, on top of the #pym:noshrink tag.",
                        "Use full registry ids, for example [\"create:steam_engine\"]."
                )
                .defineListAllowEmpty("noShrinkBlocks", List.<String>of(), PymConfig::isBlockId);
        SHRUNK_BLOCK_LIMIT = builder
                .comment("The most blocks a sublevel may hold while it is below 1x. " + Integer.MAX_VALUE + " means no limit.")
                .defineInRange("shrunkBlockLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("entities");
        RESIZE_ENTITIES_WITH_SUBLEVELS = builder
                .comment(
                        "Whether mobs and players standing on a sublevel resize with it.",
                        "Armor stands, paintings, item frames and displays always follow."
                )
                .define("resizeEntitiesWithSublevels", true);
        builder.pop();

        builder.push("experimental");
        SIMURAIL_SUPPORT = builder
                .comment(
                        "Experimental Simurail support: train cars follow a resized track, and rail guides",
                        "neither carry a resize nor get their limits scaled twice."
                )
                .define("simurailSupport", false);
        builder.pop();

        SPEC = builder.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("rendering");
        DETAIL_CUTOFF = client
                .comment(
                        "Skip particles and shadows on a sublevel once the viewer is this many times its scale.",
                        "16 means a 1x player stops drawing them on crafts at 1/16 and smaller."
                )
                .defineInRange("detailCutoffRatio", RenderDetail.DEFAULT_RATIO, 2.0D, 1024.0D);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        modBus.addListener(PymConfig::onLoad);
        modBus.addListener(PymConfig::onReload);
    }

    private static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
        if (event.getConfig().getSpec() == CLIENT_SPEC) refreshClient();
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
        if (event.getConfig().getSpec() == CLIENT_SPEC) refreshClient();
    }

    public static boolean resizeEntitiesWithSublevels() {
        return resizeEntitiesWithSublevels;
    }

    public static boolean simurailSupport() {
        return simurailSupport;
    }

    public static double detailCutoff() {
        return detailCutoff;
    }

    private static void refreshClient() {
        detailCutoff = DETAIL_CUTOFF.get();
    }

    private static void refresh() {
        Set<Block> noShrink = new HashSet<>();
        for (String raw : NO_SHRINK_BLOCKS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(noShrink::add);
        }
        PlotScan.configure(noShrink, SHRUNK_BLOCK_LIMIT.get());
        resizeEntitiesWithSublevels = RESIZE_ENTITIES_WITH_SUBLEVELS.get();
        simurailSupport = SIMURAIL_SUPPORT.get();
    }

    private static boolean isBlockId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    private PymConfig() {}
}

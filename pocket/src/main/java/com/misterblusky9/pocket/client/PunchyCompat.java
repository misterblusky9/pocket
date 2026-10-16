package com.misterblusky9.pocket.client;

import com.google.gson.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.List;

final class PunchyCompat {
    private static final List<String> ITEMS = List.of(
            "pocket:compression_gun", "pocket:pearlescent_compression_gun",
            "pocket:creative_shrink_ray", "pocket:glue_gun",
            "create:potato_cannon", "create:handheld_worldshaper", "simulated:plunger_launcher");

    static void apply() {
        if (!ModList.get().isLoaded("punchy")) return;
        var path = FMLPaths.CONFIGDIR.get().resolve("punchy/punchy_config.json");
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject config = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!config.has("itemBlacklist") || !config.get("itemBlacklist").isJsonArray()) return;
            JsonArray blacklist = config.getAsJsonArray("itemBlacklist");
            JsonObject dual = config.has("blacklistApplyDualHanded")
                    && config.get("blacklistApplyDualHanded").isJsonObject()
                    ? config.getAsJsonObject("blacklistApplyDualHanded") : new JsonObject();
            boolean changed = false;
            for (String id : ITEMS) {
                if (!blacklist.contains(new JsonPrimitive(id))) {
                    blacklist.add(id);
                    changed = true;
                }
                if (!dual.has(id)) { dual.addProperty(id, false); changed = true; }
            }
            if (changed) {
                config.add("blacklistApplyDualHanded", dual);
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(config));
            }
        } catch (Exception ignored) {
        }
    }

    private PunchyCompat() {}
}

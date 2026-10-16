package com.misterblusky9.pocket.ponder;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.BeltScenes;
import com.simibubi.create.infrastructure.ponder.scenes.KineticsScenes;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class PocketPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return PocketSized.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                ModItems.CREATIVE_SHRINK_RAY.getId(),
                ModItems.COMPRESSION_GUN.getId(),
                ModItems.PEARLESCENT_COMPRESSION_GUN.getId()
        ).addStoryBoard("scaling_simulated_contraptions", PocketPonderScenes::scaling);

        helper.forComponents(ModItems.BLUEPRINT_CASING.getId())
                .addStoryBoard(ResourceLocation.fromNamespaceAndPath("create", "shaft/encasing"), KineticsScenes::shaftsCanBeEncased)
                .addStoryBoard(ResourceLocation.fromNamespaceAndPath("create", "belt/encasing"), BeltScenes::beltsCanBeEncased);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.DECORATION).add(ModItems.BLUEPRINT_CASING.getId());
    }
}

package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class BlueprintCasingModel {
    public static final CTSpriteShiftEntry SHIFT = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/blueprint_casing"),
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/blueprint_casing_connected"));

    public static void init() {}

    public static void registerConnectivity(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CreateClient.CASING_CONNECTIVITY.makeCasing(ModBlocks.BLUEPRINT_CASING.get(), SHIFT);
            CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLUEPRINT_ENCASED_SHAFT.get(), SHIFT,
                    (state, face) -> face.getAxis() != state.getValue(EncasedShaftBlock.AXIS));
            CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLUEPRINT_ENCASED_COGWHEEL.get(), SHIFT,
                    BlueprintCasingModel::cogFaceConnects);
            CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get(), SHIFT,
                    BlueprintCasingModel::cogFaceConnects);
        });
    }

    private static boolean cogFaceConnects(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(EncasedCogwheelBlock.AXIS)
                && !state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT);
    }

    public static void swap(ModelEvent.ModifyBakingResult event) {
        wrap(event, ModBlocks.BLUEPRINT_CASING.get(), new EncasedCTBehaviour(SHIFT));
        wrap(event, ModBlocks.BLUEPRINT_ENCASED_SHAFT.get(), new EncasedCTBehaviour(SHIFT));
        wrap(event, ModBlocks.BLUEPRINT_ENCASED_COGWHEEL.get(),
                new EncasedCogCTBehaviour(SHIFT, Couple.create(SHIFT, SHIFT)));
        wrap(event, ModBlocks.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get(), new EncasedCogCTBehaviour(SHIFT));
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block,
                             ConnectedTextureBehaviour behaviour) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = event.getModels().get(location);
            if (original != null) event.getModels().put(location, new CTModel(original, behaviour));
        }
    }

    private BlueprintCasingModel() {}
}

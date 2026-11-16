package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HatStandRecipe extends ShapedRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PocketSized.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HatStandRecipe>> SERIALIZER =
            SERIALIZERS.register("hat_stand", () -> new RecipeSerializer<>() {
                private final MapCodec<HatStandRecipe> codec =
                        ShapedRecipe.Serializer.CODEC.xmap(HatStandRecipe::new, recipe -> recipe);
                private final StreamCodec<RegistryFriendlyByteBuf, HatStandRecipe> streamCodec =
                        ShapedRecipe.Serializer.STREAM_CODEC.map(HatStandRecipe::new, recipe -> recipe);

                @Override
                public MapCodec<HatStandRecipe> codec() {
                    return codec;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, HatStandRecipe> streamCodec() {
                    return streamCodec;
                }
            });

    private HatStandRecipe(ShapedRecipe base) {
        super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack stand = super.assemble(input, registries);
        for (ItemStack ingredient : input.items()) {
            if (!ingredient.is(Items.PLAYER_HEAD)) continue;
            ResolvableProfile profile = ingredient.get(DataComponents.PROFILE);
            if (profile != null && profile.name().isPresent()) HatStandItem.name(stand, profile.name().get());
        }
        return stand;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }
}

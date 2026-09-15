package com.misterblusky9.pocket.advancement;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class HauntedCompressionGunTrigger
        extends SimpleCriterionTrigger<HauntedCompressionGunTrigger.TriggerInstance> {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, HauntedCompressionGunTrigger> HAUNTED =
            TRIGGERS.register("haunted_compression_gun", HauntedCompressionGunTrigger::new);

    private static final double CREDIT_RADIUS = 16.0D;

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public static boolean isHauntingInput(final ItemStack stack) {
        return stack.is(ModItems.COMPRESSION_GUN.get());
    }

    public static boolean isHauntingOutput(final ItemStack stack) {
        return stack.is(ModItems.PEARLESCENT_COMPRESSION_GUN.get());
    }

    public static void credit(final Level level, final Vec3 at, @Nullable final Entity thrower) {
        if (!(level instanceof ServerLevel)) return;
        final Player player = thrower instanceof final ServerPlayer owner && !owner.isSpectator()
                ? owner
                : level.getNearestPlayer(at.x, at.y, at.z, CREDIT_RADIUS, EntitySelector.NO_SPECTATORS);
        if (player instanceof final ServerPlayer serverPlayer) HAUNTED.get().trigger(serverPlayer, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
        ).apply(instance, TriggerInstance::new));
    }
}

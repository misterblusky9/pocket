package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.particle.ModParticles;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class HotGlueParticle extends TerrainParticle {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/hot_glue/hot_glue");

    private HotGlueParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz, SimBlocks.MERGING_GLUE.getDefaultState());
        setSprite(Minecraft.getInstance().getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(TEXTURE));
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpecial(ModParticles.HOT_GLUE.get(), new Provider());
    }

    private static final class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double dx, double dy, double dz
        ) {
            return new HotGlueParticle(level, x, y, z, dx, dy, dz);
        }
    }
}

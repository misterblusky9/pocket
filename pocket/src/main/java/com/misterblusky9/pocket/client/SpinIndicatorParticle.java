package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.particle.ModParticles;
import com.misterblusky9.pocket.particle.SpinIndicatorParticleData;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class SpinIndicatorParticle extends SimpleAnimatedParticle {
    private final Vec3 origin;
    private final Vec3 offset;
    private final Direction.Axis axis;
    private final float radius2;
    private final float speed;
    private float radius;

    private SpinIndicatorParticle(ClientLevel level, double x, double y, double z, SpinIndicatorParticleData data,
                                  SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        xd = 0.0;
        yd = 0.0;
        zd = 0.0;
        origin = new Vec3(x, y, z);
        quadSize *= 0.75F;
        lifetime = data.lifeSpan() + random.nextInt(32);
        setFadeColor(data.color());
        setColor(Color.mixColors(data.color(), 0xFFFFFF, 0.5F));
        setSpriteFromAge(sprites);
        radius = data.radius1();
        radius2 = data.radius2();
        speed = data.speed();
        axis = data.axis();
        offset = axis.isHorizontal() ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        move(0.0, 0.0, 0.0);
        xo = this.x;
        yo = this.y;
        zo = this.z;
    }

    @Override
    public void tick() {
        super.tick();
        radius += (radius2 - radius) * 0.1F;
    }

    @Override
    public void move(double x, double y, double z) {
        float time = AnimationTickHolder.getTicks(level);
        float angle = (time * speed) % 360.0F - speed / 2.0F * age * ((float) age / lifetime);
        if (speed < 0.0F && axis.isVertical()) angle += 180.0F;
        Vec3 position = VecHelper.rotate(offset.scale(radius), angle, axis).add(origin);
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPIN_INDICATOR.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SpinIndicatorParticleData> {
        @Override
        public Particle createParticle(SpinIndicatorParticleData data, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SpinIndicatorParticle(level, x, y, z, data, sprites);
        }
    }
}

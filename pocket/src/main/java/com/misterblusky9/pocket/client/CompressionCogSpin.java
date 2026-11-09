package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CompressionGunItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

final class CompressionCogSpin {
    static final float MAX_SPIN_SPEED = 62.0F;
    private static final float COAST_RATE = 0.045F;
    private static final float GLOW_THRESHOLD = 0.55F;
    private static final float IDLE_SPIN_SPEED = 1.0F;
    private static final float FILL_EASE = 0.2F;

    float angle;
    float speed;
    float lastTime = Float.NaN;
    float fill = Float.NaN;
    float source;
    float sweep;

    float frame(float now) {
        float delta = Float.isNaN(this.lastTime) ? 0.0F : Math.max(0.0F, Math.min(4.0F, now - this.lastTime));
        this.lastTime = now;
        return delta;
    }

    void easeFill(float targetFill, float delta) {
        this.fill = Float.isNaN(this.fill)
                ? targetFill
                : this.fill + (targetFill - this.fill) * Math.min(1.0F, FILL_EASE * delta);
    }

    void turn(float delta, boolean driving, boolean powered, boolean growing, float ticksUsing) {
        if (delta <= 0.0F) return;

        float direction = growing ? 1.0F : -1.0F;
        boolean kickOnly = !powered && ticksUsing >= CompressionGunItem.SPIN_KICK_TICKS;

        if (driving && !kickOnly) {
            float curve = MAX_SPIN_SPEED * CompressionGunItem.spinFraction(ticksUsing);
            float coasting = this.speed * direction > 0.0F ? Math.abs(this.speed) : 0.0F;
            this.speed = direction * Math.max(curve, coasting);
        } else {
            float rest = powered ? IDLE_SPIN_SPEED * direction : 0.0F;
            this.speed += (rest - this.speed) * COAST_RATE * delta;
            if (Math.abs(this.speed - rest) < 0.05F) this.speed = rest;
        }
        this.angle = (this.angle + this.speed * delta) % 360.0F;
    }

    int glowingLight(int light) {
        float heat = heat(this.speed);
        if (heat <= 0.0F) return light;

        int block = LightTexture.block(light);
        int sky = LightTexture.sky(light);
        int lit = Math.round(Mth.lerp(heat, block, 15.0F));
        return LightTexture.pack(Math.max(block, lit), sky);
    }

    private static float heat(float speed) {
        float fraction = Math.abs(speed) / MAX_SPIN_SPEED;
        return Mth.clamp((fraction - GLOW_THRESHOLD) / (1.0F - GLOW_THRESHOLD), 0.0F, 1.0F);
    }
}

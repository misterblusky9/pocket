package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.WeakReference;

@Mixin(value = Outline.OutlineParams.class, remap = false)
public abstract class OutlineParamsEmitterMixin implements OutlineEmitter {
    @Unique
    private WeakReference<Entity> pym$emitter;

    @Unique
    private WeakReference<ClientSubLevel> pym$subLevel;

    @Override
    public Entity pym$emitter() {
        return this.pym$emitter == null ? null : this.pym$emitter.get();
    }

    @Override
    public void pym$emitter(Entity entity) {
        this.pym$emitter = entity == null ? null : new WeakReference<>(entity);
    }

    @Unique
    private float pym$minimumPixels;

    @Override
    public float pym$minimumPixels() {
        return this.pym$minimumPixels;
    }

    @Override
    public void pym$minimumPixels(float pixels) {
        this.pym$minimumPixels = pixels;
    }

    @Override
    public ClientSubLevel pym$subLevel() {
        return this.pym$subLevel == null ? null : this.pym$subLevel.get();
    }

    @Override
    public void pym$subLevel(ClientSubLevel subLevel) {
        this.pym$subLevel = subLevel == null ? null : new WeakReference<>(subLevel);
    }
}

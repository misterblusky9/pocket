package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.OutlineEmitter;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.WeakReference;

@Mixin(value = Outline.OutlineParams.class, remap = false)
public abstract class OutlineParamsEmitterMixin implements OutlineEmitter {
    @Unique
    private WeakReference<Entity> pym$emitter;

    @Override
    public Entity pym$emitter() {
        return this.pym$emitter == null ? null : this.pym$emitter.get();
    }

    @Override
    public void pym$emitter(final Entity entity) {
        this.pym$emitter = entity == null ? null : new WeakReference<>(entity);
    }
}

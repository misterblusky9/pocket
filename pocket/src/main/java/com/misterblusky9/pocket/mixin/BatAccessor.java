package com.misterblusky9.pocket.mixin;

import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Bat.class)
public interface BatAccessor {
    @Invoker("isHalloween")
    static boolean pocket$isHalloween() {
        throw new AssertionError();
    }
}

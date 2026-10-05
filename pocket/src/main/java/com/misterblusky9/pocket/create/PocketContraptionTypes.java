package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.SwitchBearingDebug;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class PocketContraptionTypes {
    public static final Holder.Reference<ContraptionType> SWITCH_BEARING = Registry.registerForHolder(
            CreateBuiltInRegistries.CONTRAPTION_TYPE,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "switch_bearing"),
            new ContraptionType(SwitchBearingContraption::new)
    );

    public static final Holder.Reference<ContraptionType> HELM_BEARING = Registry.registerForHolder(
            CreateBuiltInRegistries.CONTRAPTION_TYPE,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "helm_bearing"),
            new ContraptionType(HelmBearingContraption::new)
    );

    public static final Holder.Reference<ContraptionType> SWITCH_PISTON = Registry.registerForHolder(
            CreateBuiltInRegistries.CONTRAPTION_TYPE,
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "switch_piston"),
            new ContraptionType(SwitchPistonContraption::new)
    );

    private static boolean announced;

    public static void register(final RegisterEvent event) {
        if (!announced) {
            announced = true;
            SwitchBearingDebug.info(
                    "Contraption type bootstrap loaded: {}:switch_bearing, {}:helm_bearing, {}:switch_piston",
                    PocketSized.MOD_ID, PocketSized.MOD_ID, PocketSized.MOD_ID
            );
        }
    }

    private PocketContraptionTypes() {
    }
}

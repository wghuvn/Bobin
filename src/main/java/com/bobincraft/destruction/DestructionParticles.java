package com.bobincraft.destruction;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Layer D (Visual Effects) custom Fabric Particle API types for Scripted Structural Destruction
 * and the 3-layer volumetric industrial smoke & fire system (Section 3.5 & Section 8).
 *
 * <ul>
 *   <li>{@link #SMOKE_CORE}: Layer 1 — Dense lower hydrocarbon smoke core at the base of the fire.</li>
 *   <li>{@link #SMOKE_COLUMN}: Layer 2 — Main vertical rising smoke stem with dark charcoal lobes.</li>
 *   <li>{@link #SMOKE_CANOPY}: Layer 3 — Upper expanding and gradually dissipating smoke canopy.</li>
 *   <li>{@link #FIRE_PLUME}: Non-cubic organic rolling fireball/flame plume at the breach.</li>
 *   <li>{@link #CONCRETE_DUST}: Concrete & masonry fracture dust cloud with short lifetime.</li>
 *   <li>{@link #ELECTRIC_ARC}: High-voltage substation electrical flash & metallic sparks.</li>
 * </ul>
 */
public final class DestructionParticles {
    public static final DefaultParticleType SMOKE_CORE = FabricParticleTypes.simple(true);
    public static final DefaultParticleType SMOKE_COLUMN = FabricParticleTypes.simple(true);
    public static final DefaultParticleType SMOKE_CANOPY = FabricParticleTypes.simple(true);
    public static final DefaultParticleType FIRE_PLUME = FabricParticleTypes.simple(true);
    public static final DefaultParticleType CONCRETE_DUST = FabricParticleTypes.simple(true);
    public static final DefaultParticleType ELECTRIC_ARC = FabricParticleTypes.simple(true);

    private static boolean registered = false;

    private DestructionParticles() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        registerType("industrial_smoke_core", SMOKE_CORE);
        registerType("industrial_smoke_column", SMOKE_COLUMN);
        registerType("industrial_smoke_canopy", SMOKE_CANOPY);
        registerType("industrial_fire_plume", FIRE_PLUME);
        registerType("concrete_dust", CONCRETE_DUST);
        registerType("electric_arc", ELECTRIC_ARC);
    }

    private static void registerType(String path, DefaultParticleType type) {
        Identifier id = new Identifier(BobinCraft.MOD_ID, path);
        try {
            if (!Registries.PARTICLE_TYPE.containsId(id)) {
                Registry.register(Registries.PARTICLE_TYPE, id, type);
            }
        } catch (Throwable ignored) {
        }
    }
}

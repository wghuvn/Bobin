package com.bobincraft.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class CombatDamage {
    public static final RegistryKey<DamageType> DRONE_BLAST = key("drone_blast");
    public static final RegistryKey<DamageType> SHRAPNEL = key("drone_shrapnel");
    public static final RegistryKey<DamageType> BLEEDING = key("bleeding");

    private CombatDamage() { }

    private static RegistryKey<DamageType> key(String path) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("bobin_craft", path));
    }

    public static DamageSource source(ServerWorld world, RegistryKey<DamageType> type, Entity direct, Entity attacker) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(type), direct, attacker);
    }
}

package com.bobincraft.armament.definition;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import java.util.Set;

/** Versioned common definition. Actions are identifiers, not executable mechanics. */
public record WeaponDefinition(int schemaVersion, WeaponId id, Set<WeaponId> actions) {
    public WeaponDefinition {
        Checks.version(schemaVersion);
        Checks.present(id, "$.id");
        Checks.size(actions, 0, 64, "$.actions");
        actions = Set.copyOf(actions);
    }
}

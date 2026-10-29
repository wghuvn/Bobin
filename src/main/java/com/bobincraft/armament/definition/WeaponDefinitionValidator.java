package com.bobincraft.armament.definition;

import com.bobincraft.armament.api.*;
import com.bobincraft.armament.serialization.WeaponJson;
import com.bobincraft.armament.validation.Checks;

/** Structural JSON validation and semantic checks against an explicit definition. */
public final class WeaponDefinitionValidator {
    private WeaponDefinitionValidator() {}

    public static WeaponDefinition parse(String json) { return WeaponJson.readDefinition(json); }

    public static void validate(WeaponDefinition definition, WeaponStateSnapshot snapshot) {
        Checks.present(definition, "$.definition");
        Checks.present(snapshot, "$.snapshot");
        Checks.require(definition.id().equals(snapshot.weaponId()), "$.weapon_id", "does not match definition");
        snapshot.actionId().ifPresent(id -> action(definition, id));
    }

    public static void validate(WeaponDefinition definition, WeaponActionEvent event) {
        Checks.present(definition, "$.definition");
        Checks.present(event, "$.event");
        Checks.require(definition.id().equals(event.weaponId()), "$.weapon_id", "does not match definition");
        action(definition, event.actionId());
    }

    private static void action(WeaponDefinition definition, WeaponId id) {
        Checks.require(definition.actions().contains(id), "$.action_id", "action '" + id + "' is not declared");
    }
}

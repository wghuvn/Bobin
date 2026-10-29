package com.bobincraft.armament.api;

import com.bobincraft.armament.validation.Checks;
import java.util.UUID;

/** Semantic action notification, not a client command or an animation marker. */
public record WeaponActionEvent(UUID ownerId, UUID instanceId, WeaponId weaponId,
        WeaponUserView.Hand hand, WeaponId actionId, long sequence, long startTick) {
    public WeaponActionEvent {
        Checks.present(ownerId, "$.owner_id");
        Checks.present(instanceId, "$.instance_id");
        Checks.present(weaponId, "$.weapon_id");
        Checks.present(hand, "$.hand");
        Checks.present(actionId, "$.action_id");
        Checks.require(sequence > 0, "$.sequence", "must be positive");
        Checks.nonNegative(startTick, "$.start_tick");
    }
}

package com.bobincraft.armament.api;

import com.bobincraft.armament.validation.Checks;
import java.util.Optional;
import java.util.UUID;

/** Immutable server-time data. Does not advance time or implement any action. */
public record WeaponStateSnapshot(UUID ownerId, UUID instanceId, WeaponId weaponId,
        WeaponUserView.Hand hand, long serverTick, long revision,
        Optional<WeaponId> actionId, long actionSequence, long actionStartTick) {
    public WeaponStateSnapshot {
        Checks.present(ownerId, "$.owner_id");
        Checks.present(instanceId, "$.instance_id");
        Checks.present(weaponId, "$.weapon_id");
        Checks.present(hand, "$.hand");
        Checks.present(actionId, "$.action_id");
        Checks.nonNegative(serverTick, "$.server_tick");
        Checks.nonNegative(revision, "$.revision");
        Checks.nonNegative(actionSequence, "$.action_sequence");
        Checks.nonNegative(actionStartTick, "$.action_start_tick");
        Checks.require(actionStartTick <= serverTick, "$.action_start_tick", "must not be after server_tick");
        Checks.require(actionId.isEmpty() || actionSequence > 0, "$.action_sequence", "active action requires a positive sequence");
        Checks.require(actionId.isPresent() || actionStartTick == 0, "$.action_start_tick", "idle snapshot requires zero start tick");
    }
}
